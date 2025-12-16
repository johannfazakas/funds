package ro.jf.funds.importer.service.config

import io.ktor.server.application.*
import org.koin.ktor.ext.inject
import ro.jf.funds.platform.jvm.event.Consumer
import ro.jf.funds.platform.jvm.model.GenericResponse

fun Application.configureImportEventHandling() {
    val fundTransactionsBatchCreateResponseConsumer by inject<Consumer<GenericResponse>>(
        CREATE_FUND_TRANSACTIONS_RESPONSE_CONSUMER
    )

    monitor.subscribe(ApplicationStarted) {
        fundTransactionsBatchCreateResponseConsumer.consume()
    }

    monitor.subscribe(ApplicationStopped) {
        fundTransactionsBatchCreateResponseConsumer.close()
    }
}
