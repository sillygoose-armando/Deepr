import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import com.yogeshpaliyal.deepr.p2p.core.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Android-like driver: one connection; a statement or a whole transaction holds it exclusively, and a
 * transaction is only visible to the thread that opened it (like SQLiteDatabase's primary connection).
 * shortcut: emulates Android's locking instead of real multi-connection SQLite; native/desktop drivers
 * get their own check when the multiplatform track wires them (see PROGRESS.md).
 */
private class AndroidLikeDriver(private val d: SqlDriver) : SqlDriver by d {
    private val lock = ReentrantLock()

    override fun newTransaction(): QueryResult<Transacter.Transaction> {
        lock.lock()
        val t = d.newTransaction().value
        t.afterCommit { lock.unlock() }
        t.afterRollback { lock.unlock() }
        return QueryResult.Value(t)
    }

    override fun currentTransaction(): Transacter.Transaction? = if (lock.isHeldByCurrentThread) d.currentTransaction() else null

    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> =
        lock.withLock { d.execute(identifier, sql, parameters, binders) }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = lock.withLock { d.executeQuery(identifier, sql, mapper, parameters, binders) }
}

/** Real threads: local edits race with incoming and outgoing sync sessions on both devices. */
class ConcurrencyTest {
    private class Node(val db: DeeprDB, val driver: SqlDriver) {
        lateinit var engine: SyncEngine
        val q get() = db.deeprQueries
        val id: String by lazy { q.getSyncDevice().executeAsOne().deviceId }
        val shared: Long by lazy { q.getProfileByName("Shared").executeAsOne().id }
    }

    private fun node(): Node {
        val driver = AndroidLikeDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") }))
        DeeprDB.Schema.create(driver)
        return Node(DeeprDB(driver), driver).also { it.q.insertProfile("Shared") }
    }

    @Test
    fun concurrentEditsAndSessionsConverge() {
        repeat(5) { round ->
            val a = node()
            val b = node()
            val nodes = mapOf(a.id to a, b.id to b)
            listOf(a to b, b to a).forEach { (me, other) ->
                me.engine = SyncEngine(me.db, { peer, req -> nodes.getValue(peer).engine.handle(me.id, req) }, Dispatchers.IO, 7)
                me.engine.store.shareProfile(me.shared, "s1", 0)
                me.q.insertPeer(other.id, "peer", ByteArray(1), "", 0, 0)
                me.q.linkPeerShare(other.id, "s1")
            }
            // A lock held across the network deadlocks two devices syncing each other: fail fast, don't hang.
            runBlocking(Dispatchers.IO) { withTimeout(60_000) {
                listOf(a, b).flatMap { n ->
                    val other = if (n === a) b else a
                    listOf(
                        async {
                            repeat(150) { i ->
                                // Same pattern as LinkRepositoryImpl.insertDeepr: insert + id lookup in one transaction.
                                val id =
                                    n.db.transactionWithResult {
                                        n.q.insertDeepr("https://x/${n.id.take(4)}/$i", "n$i", 0, "", "", n.shared)
                                        n.q.lastInsertRowId().executeAsOne()
                                    }
                                n.db.transaction {
                                    n.q.insertTag("t${i % 5}")
                                    n.q.addTagToLink(id, n.q.getTagByName("t${i % 5}").executeAsOne().id)
                                }
                                if (i % 7 == 0) n.q.listDeeprAsc().executeAsList().firstOrNull()?.let { n.q.deleteDeeprById(it.id) }
                                if (i % 11 == 0) n.q.updateTag("t${i % 5}-$i", n.q.getTagByName("t${i % 5}").executeAsOne().id)
                            }
                        },
                        // Two concurrent outgoing loops per node: exercises the per-(peer, share) session guard too.
                        async { repeat(40) { n.engine.syncWith(other.id, "s1") } },
                        async { repeat(40) { n.engine.syncWith(other.id, "s1") } },
                    )
                }.awaitAll()
                repeat(3) {
                    a.engine.syncWith(b.id, "s1")
                    b.engine.syncWith(a.id, "s1")
                }
            } }

            fun state(n: Node) =
                n.q.listDeeprAsc().executeAsList().filter { it.profileId == n.shared }
                    .associate { it.syncId!! to it.link + n.q.getTagsForLink(it.id).executeAsList().map { t -> t.name }.sorted() }
            val sa = state(a)
            assertEquals(sa, state(b), "round $round diverged")
            check(sa.size > 100) { "round $round: too little data synced (${sa.size})" }
            listOf(a, b).forEach { n ->
                val ok = n.driver.executeQuery(null, "PRAGMA integrity_check", { c -> QueryResult.Value(if (c.next().value) c.getString(0) else null) }, 0).value
                assertEquals("ok", ok)
                assertEquals(0L, n.q.getSyncDevice().executeAsOne().applying)
            }
        }
    }
}
