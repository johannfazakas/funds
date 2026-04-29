package ro.jf.funds.importer.service.service.conversion

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.AccountTO
import ro.jf.funds.fund.api.model.CategoryTO
import ro.jf.funds.fund.api.model.CreateTransactionTO
import ro.jf.funds.conversion.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store

interface ImportTransactionConverter {
    fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountTO>,
    ): Boolean

    fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountTO>,
    ): List<Conversion>

    fun mapToTransaction(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        accountStore: Store<AccountTO>,
        categoryStore: Store<CategoryTO>,
    ): CreateTransactionTO
}
