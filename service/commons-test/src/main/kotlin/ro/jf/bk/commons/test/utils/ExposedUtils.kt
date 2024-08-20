package ro.jf.bk.commons.test.utils

import org.jetbrains.exposed.sql.Database
import ro.jf.bk.commons.test.extension.PostgresContainerExtension

fun createDbConnection() = Database.connect(
    url = PostgresContainerExtension.jdbcUrl,
    user = PostgresContainerExtension.username,
    password = PostgresContainerExtension.password
)
