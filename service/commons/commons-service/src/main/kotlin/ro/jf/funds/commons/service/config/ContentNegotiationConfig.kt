package ro.jf.funds.commons.service.config

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

// TODO(Johann) move to single commons lib. applicable to everything in commons-service.
fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json()
    }
}
