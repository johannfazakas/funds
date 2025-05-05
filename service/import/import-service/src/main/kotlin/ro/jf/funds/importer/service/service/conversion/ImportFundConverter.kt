package ro.jf.funds.importer.service.service.conversion

import ro.jf.funds.account.api.model.AccountName
import ro.jf.funds.account.api.model.AccountTO
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.FundName
import ro.jf.funds.fund.api.model.FundTO
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.importer.service.domain.Conversion
import ro.jf.funds.importer.service.domain.ImportParsedTransaction
import ro.jf.funds.importer.service.domain.Store

interface ImportFundConverter {
    fun matches(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): Boolean

    fun getRequiredConversions(
        transaction: ImportParsedTransaction,
        accountStore: Store<AccountName, AccountTO>,
    ): List<Conversion>

    fun mapToFundTransaction(
        transaction: ImportParsedTransaction,
        conversions: ConversionsResponse,
        fundStore: Store<FundName, FundTO>,
        accountStore: Store<AccountName, AccountTO>,
    ): CreateFundTransactionTO
}
