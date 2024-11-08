package ro.jf.funds.commons.persistence

import io.ktor.server.application.*
import org.postgresql.ds.PGSimpleDataSource
import ro.jf.funds.commons.config.getStringProperty
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

private const val DATABASE_URL_PROPERTY = "database.url"
private const val DATABASE_USER_PROPERTY = "database.user"
private const val DATABASE_PASSWORD_PROPERTY = "database.password"

fun ApplicationEnvironment.getDataSource(): DataSource =
    PGSimpleDataSource().apply {
        setURL(getStringProperty(DATABASE_URL_PROPERTY))
        user = getStringProperty(DATABASE_USER_PROPERTY)
        password = getStringProperty(DATABASE_PASSWORD_PROPERTY)
    }

fun ApplicationEnvironment.getDbConnection(): Connection =
    DriverManager.getConnection(
        getStringProperty(DATABASE_URL_PROPERTY),
        getStringProperty(DATABASE_USER_PROPERTY),
        getStringProperty(DATABASE_PASSWORD_PROPERTY)
    )
