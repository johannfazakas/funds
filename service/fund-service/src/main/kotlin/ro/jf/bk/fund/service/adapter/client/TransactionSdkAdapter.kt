package ro.jf.bk.fund.service.adapter.client

import ro.jf.bk.account.api.model.RecordTO
import ro.jf.bk.account.api.model.TransactionTO
import ro.jf.bk.account.sdk.TransactionSdk
import ro.jf.bk.fund.service.domain.command.CreateTransactionCommand
import ro.jf.bk.fund.service.domain.model.Record
import ro.jf.bk.fund.service.domain.model.Transaction
import ro.jf.bk.fund.service.domain.port.TransactionRepository
import java.util.*

const val FUND_ID = "fundId"

class TransactionSdkAdapter(
    private val transactionSdk: TransactionSdk
) : TransactionRepository {
    override suspend fun listTransactions(userId: UUID): List<Transaction> {
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

    override suspend fun createTransaction(command: CreateTransactionCommand): Transaction {
        TODO("Not yet implemented")
    }

    override suspend fun deleteTransaction(userId: UUID, transactionId: UUID) {
        TODO("Not yet implemented")
    }
}

private fun RecordTO.fundId(): UUID = metadata[FUND_ID]
    ?.let(UUID::fromString)
    ?: error("Fund id not found in metadata")
