package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportConfigurationTO(
    val keys: KeysTO,
    val formatting: FormattingTO,
    val transactionIdentification: TransactionIdentificationTO
)
