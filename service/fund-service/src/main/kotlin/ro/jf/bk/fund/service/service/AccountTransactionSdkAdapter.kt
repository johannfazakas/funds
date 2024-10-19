package ro.jf.bk.fund.service.service

import ro.jf.bk.account.api.model.CreateTransactionTO
import ro.jf.bk.account.api.model.RecordTO
import ro.jf.bk.account.api.model.TransactionTO
import ro.jf.bk.account.sdk.TransactionSdk
import ro.jf.bk.fund.service.domain.Record
import ro.jf.bk.fund.service.domain.Transaction
import java.util.*

const val METADATA_FUND_ID = "fundId"

class AccountTransactionSdkAdapter(
    private val transactionSdk: TransactionSdk
) {
    suspend fun listTransactions(userId: UUID): List<Transaction> {
        return transactionSdk.listTransactions(userId)
            .map { transactionTo: TransactionTO ->
                Transaction(
                    id = transactionTo.id,
                    userId = userId,
                    dateTime = transactionTo.dateTime,
                    records = transactionTo.records.map { recordTO ->
                        Record(
                            id = recordTO.id,
                            fundId = recordTO.fundId(),
                            accountId = recordTO.accountId,
                            amount = recordTO.amount
                        )
                    }
                )
            }
    }

    suspend fun createTransaction(command: CreateTransactionTO): Transaction {
        TODO("Not yet implemented")
    }

    suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        return transactionSdk.deleteTransaction(userId, transactionId)
    }
}

private fun RecordTO.fundId(): UUID = metadata[METADATA_FUND_ID]
    ?.let(UUID::fromString)
    ?: error("Fund id not found in metadata")
