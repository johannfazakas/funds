package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ConversionContext
import ro.jf.funds.importer.service.service.conversion.ImportFundConversionService.ConversionRequest
import java.util.*

interface ImportFundConverter {
    fun matches(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): Boolean

    fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): List<ConversionRequest>

    fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        resolveFundId: ImportParsedRecord.() -> UUID,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
        currencyConverter: ConversionContext,
    ): ImportFundTransaction
}
