import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MigrationTest {
    @Test
    fun upstreamV11MigratesAndBackfillsSyncIds() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val old = javaClass.getResource("/schema_v11_upstream.sql")!!.readText()
        old.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { driver.execute(null, it, 0) }
        driver.execute(null, "INSERT INTO Profile (id, name) VALUES (1, 'Default')", 0)
        driver.execute(null, "INSERT INTO Deepr (link, profileId) VALUES ('https://a', 1), ('https://b', 1)", 0)

        assertEquals(11L, DeeprDB.Schema.version - 1)
        DeeprDB.Schema.migrate(driver, 11, DeeprDB.Schema.version)

        val q = DeeprDB(driver).deeprQueries
        val links = q.listDeeprAsc().executeAsList()
        assertEquals(2, links.size)
        links.forEach { assertEquals(32, assertNotNull(it.syncId).length) }
        assertEquals(2, links.map { it.syncId }.toSet().size)
        assertEquals(32, q.getSyncDevice().executeAsOne().deviceId.length)

        // Triggers are live after migration.
        q.insertShare("s", 1, 0)
        q.insertDeepr("https://c", "", 0, "", "", 1)
        assertEquals(1, q.deltaPage("s", 0, 10).executeAsList().size)
    }
}
