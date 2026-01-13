package ro.jf.funds.platform.jvm.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import ro.jf.funds.platform.jvm.config.getIntProperty
import ro.jf.funds.platform.jvm.config.getStringProperty
import javax.sql.DataSource

private const val DATABASE_URL_PROPERTY = "database.url"
private const val DATABASE_USER_PROPERTY = "database.user"
private const val DATABASE_PASSWORD_PROPERTY = "database.password"
private const val DATABASE_CONNECTION_POOL_SIZE_PROPERTY = "database.connection-pool-size"

fun ApplicationEnvironment.getDataSource(): DataSource {
    val configuration = HikariConfig().apply {
        this.jdbcUrl = this@getDataSource.getStringProperty(DATABASE_URL_PROPERTY)
        this.username = this@getDataSource.getStringProperty(DATABASE_USER_PROPERTY)
        this.password = this@getDataSource.getStringProperty(DATABASE_PASSWORD_PROPERTY)
        this.maximumPoolSize = this@getDataSource.getIntProperty(DATABASE_CONNECTION_POOL_SIZE_PROPERTY)
        this.connectionTimeout = 30_000
        this.idleTimeout = 60_000
        this.maxLifetime = 30 * 60_000
        addDataSourceProperty("reWriteBatchedInserts", "true")
    }
    return HikariDataSource(configuration)
}
