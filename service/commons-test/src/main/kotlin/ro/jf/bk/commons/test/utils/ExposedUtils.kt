package ro.jf.bk.commons.test.utils

import org.jetbrains.exposed.sql.Database
import ro.jf.bk.commons.test.extension.PostgresContainerExtension

// TODO(Johann) this is not used
fun createDbConnection() = Database.connect(
    url = PostgresContainerExtension.jdbcUrl,
    user = PostgresContainerExtension.username,
    password = PostgresContainerExtension.password
)
