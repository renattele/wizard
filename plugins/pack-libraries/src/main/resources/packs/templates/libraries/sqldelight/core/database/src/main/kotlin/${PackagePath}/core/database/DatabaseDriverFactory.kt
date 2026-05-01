package ${Package}.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class DatabaseDriverFactory(
    private val context: Context,
) {
    fun create(schema: SqlDriver.Schema): SqlDriver =
        AndroidSqliteDriver(schema = schema, context = context, name = "app.db")
}
