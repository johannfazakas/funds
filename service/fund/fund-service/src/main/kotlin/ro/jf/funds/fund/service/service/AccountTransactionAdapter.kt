package ro.jf.funds.fund.service.service

import ro.jf.funds.account.api.model.*
import ro.jf.funds.account.sdk.AccountTransactionSdk
import ro.jf.funds.commons.event.Event
import ro.jf.funds.commons.event.Producer
import ro.jf.funds.commons.observability.tracing.withSuspendingSpan
import ro.jf.funds.fund.api.model.CreateFundTransactionTO
import ro.jf.funds.fund.api.model.CreateFundTransactionsTO
import ro.jf.funds.fund.api.model.FundTransactionFilterTO
import ro.jf.funds.fund.service.domain.FundRecord
import ro.jf.funds.fund.service.domain.FundTransaction
import java.util.*

const val FUND_ID_PROPERTY = "fundId"

class AccountTransactionAdapter(
    private val accountTransactionSdk: AccountTransactionSdk,
    private val accountTransactionsRequestProducer: Producer<CreateAccountTransactionsTO>,
) {
    suspend fun listTransactions(
        userId: UUID,
        fundId: UUID? = null,
        filter: FundTransactionFilterTO,
    ): List<FundTransaction> = withSuspendingSpan {
        val filter = AccountTransactionFilterTO(
            fromDate = filter.fromDate,
            toDate = filter.toDate,
            transactionProperties = propertiesOf(),
            recordProperties = propertiesOf(
                *listOfNotNull(
                    fundId?.let { FUND_ID_PROPERTY to it.toString() }
                )
                    .toTypedArray<Pair<String, String>>()
            )
        )
        accountTransactionSdk
            .listTransactions(userId, filter)
            .items.map { it.toFundTransaction(userId) }
    }

    suspend fun createTransaction(userId: UUID, request: CreateFundTransactionTO): FundTransaction =
        withSuspendingSpan {
            val createAccountTransactionRequest = request.toAccountTransactionTO()
            accountTransactionSdk.createTransaction(userId, createAccountTransactionRequest)
                .toFundTransaction(userId)
        }

    suspend fun createTransactions(userId: UUID, correlationId: UUID, request: CreateFundTransactionsTO) =
        withSuspendingSpan {
            val createAccountTransactionRequest = CreateAccountTransactionsTO(
                request.transactions.map { it.toAccountTransactionTO() }
            )
            val event = Event(userId, createAccountTransactionRequest, correlationId, userId.toString())
            accountTransactionsRequestProducer.send(event)
        }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) = withSuspendingSpan {
        accountTransactionSdk.deleteTransaction(userId, transactionId)
    }

    private fun CreateFundTransactionTO.toAccountTransactionTO() =
        CreateAccountTransactionTO(
            dateTime = dateTime,
            externalId = externalId,
            records = records.map { record ->
                CreateAccountRecordTO(
                    accountId = record.accountId,
                    amount = record.amount,
                    unit = record.unit,
                    labels = record.labels,
                    properties = propertiesOf(FUND_ID_PROPERTY to record.fundId.toString())
                )
            },
            properties = propertiesOf()
        )

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
                    amount = recordTO.amount,
                    unit = recordTO.unit,
                    labels = recordTO.labels
                )
            }
        )

    private fun AccountRecordTO.fundId(): UUID = properties
        .single { it.key == FUND_ID_PROPERTY }
        .value
        .let(UUID::fromString)
}
