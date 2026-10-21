import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yogeshpaliyal.deepr.DeeprDB
import java.util.Properties

// Same pragma the Android app sets in onOpen.
fun newDb(): Pair<JdbcSqliteDriver, DeeprDB> {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
    DeeprDB.Schema.create(driver)
    return driver to DeeprDB(driver)
}
