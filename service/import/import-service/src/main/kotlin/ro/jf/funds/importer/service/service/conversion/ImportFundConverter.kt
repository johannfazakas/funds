package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedRecord
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store
import java.math.BigDecimal
import java.util.*

interface ImportFundConverter {
    fun matches(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): Boolean

    fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
    ): List<Conversion>

    fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        resolveFundId: ImportParsedRecord.() -> UUID,
        resolveAccount: ImportParsedRecord.() -> AccountTO,
        conversionRateStore: Store<Conversion, BigDecimal>,
    ): ImportFundTransaction
}
