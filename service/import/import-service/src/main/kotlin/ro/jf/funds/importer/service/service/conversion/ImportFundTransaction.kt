package ro.jf.funds.importer.service.service.conversion

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ImportFundRecord

// TODO(Johann) is this class actually needed? It only has an extra type
// TODO(Johann) could all the types be extracted as a strategy or something similar?
data class ImportFundTransaction(
    val dateTime: LocalDateTime,
    val type: Type,
    val records: List<ImportFundRecord>
) {
    enum class Type {
        SINGLE_RECORD,
        TRANSFER,
        IMPLICIT_TRANSFER,
        EXCHANGE,
        // TODO(Johann) add EXCHANGE type
    }

    fun toRequest(): CreateFundTransactionTO {
        return CreateFundTransactionTO(
            dateTime = dateTime,
            records = records.map { it.toRequest() }
        )
    }
}
