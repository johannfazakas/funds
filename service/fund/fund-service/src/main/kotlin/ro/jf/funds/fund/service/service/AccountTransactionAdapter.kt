package ro.jf.funds.fund.service.service

import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.sdk.AccountTransactionSdk
import ro.jf.funds.commons.event.RequestProducer
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.service.domain.FundRecord
import ro.jf.funds.fund.service.domain.FundTransaction
import java.util.*

const val METADATA_FUND_ID = "fundId"

class AccountTransactionAdapter(
    private val accountTransactionSdk: AccountTransactionSdk,
    private val accountTransactionsRequestProducer: RequestProducer<CreateAccountTransactionsTO>
) {
    suspend fun listTransactions(userId: UUID): List<FundTransaction> {
        return accountTransactionSdk.listTransactions(userId).items.map { it.toFundTransaction(userId) }
    }

    suspend fun createTransaction(userId: UUID, request: CreateFundTransactionTO): FundTransaction {
        val createAccountTransactionRequest = request.toAccountTransactionTO()
        return accountTransactionSdk.createTransaction(userId, createAccountTransactionRequest)
            .toFundTransaction(userId)
    }

    suspend fun createTransactions(userId: UUID, correlationId: UUID, request: CreateFundTransactionsTO) {
        val createAccountTransactionRequest = CreateAccountTransactionsTO(
            request.transactions.map { it.toAccountTransactionTO() }
        )
        return accountTransactionsRequestProducer.send(userId, correlationId, createAccountTransactionRequest)
    }

    private fun CreateFundTransactionTO.toAccountTransactionTO() =
        CreateAccountTransactionTO(
            dateTime = dateTime,
            records = records.map { record ->
                CreateAccountRecordTO(
                    accountId = record.accountId,
                    amount = record.amount,
                    unit = record.unit,
                    metadata = mapOf(METADATA_FUND_ID to record.fundId.toString())
                )
            },
            metadata = emptyMap()
        )

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        return accountTransactionSdk.deleteTransaction(userId, transactionId)
    }

    private fun AccountTransactionTO.toFundTransaction(userId: UUID): FundTransaction =
        FundTransaction(
            id = this.id,
            userId = userId,
            dateTime = this.dateTime,
            records = this.records.map { recordTO ->
                FundRecord(
                    id = recordTO.id,
                    fundId = recordTO.fundId(),
                    accountId = recordTO.accountId,
                    amount = recordTO.amount
                )
            }
        )

    private fun AccountRecordTO.fundId(): UUID = metadata[METADATA_FUND_ID]
        ?.let(UUID::fromString)
        ?: error("Fund id not found in metadata")
}
