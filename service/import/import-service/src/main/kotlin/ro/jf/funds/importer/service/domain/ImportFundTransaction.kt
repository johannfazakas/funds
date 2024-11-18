package ro.jf.funds.importer.service.domain

import kotlinx.datetime.LocalDateTime
import ro.jf.funds.commons.model.FinancialUnit
import ro.jf.funds.fund.api.model.CreateFundRecordTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import java.math.BigDecimal
import java.util.*

fun List<ImportFundTransaction>.toRequest(): CreateFundTransactionsTO = CreateFundTransactionsTO(map { it.toRequest() })

data class ImportFundTransaction(
    val dateTime: LocalDateTime,
    val type: Type,
    val records: List<ImportFundRecord>
) {
    enum class Type {
        SINGLE_RECORD,
        TRANSFER,
    }

    fun toRequest(): CreateFundTransactionTO {
        return CreateFundTransactionTO(
            dateTime = dateTime,
            records = records.map { it.toRequest() }
        )
    }
}

data class ImportFundRecord(
    val fundId: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val unit: FinancialUnit
) {
    fun toRequest(): CreateFundRecordTO {
        return CreateFundRecordTO(
            fundId = fundId,
            accountId = accountId,
            amount = amount,
            unit = unit
        )
    }
}
