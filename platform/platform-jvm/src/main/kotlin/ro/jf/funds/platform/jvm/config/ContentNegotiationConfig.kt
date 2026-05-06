package ro.jf.funds.platform.jvm.config

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import ro.jf.funds.platform.api.serialization.BigDecimalSerializer

val platformJson = Json {
    serializersModule = SerializersModule {
        contextual(BigDecimal::class, BigDecimalSerializer)
    }
}

fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(platformJson)
    }
}
