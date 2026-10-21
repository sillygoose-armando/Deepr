import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaSmokeTest {
    @Test
    fun freshSchemaCreates() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DeeprDB.Schema.create(driver)
        val q = DeeprDB(driver).deeprQueries
        q.insertProfile("Personal")
        assertEquals(1L, q.countProfiles().executeAsOne())
    }
}
