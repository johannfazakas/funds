package ro.jf.funds.fund.service.persistence

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import ro.jf.funds.fund.api.model.*
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class TransactionRepositoryTest {
    private val accountRepository = AccountRepository(PostgresContainerExtension.connection)
    private val transactionRepository = TransactionRepository(PostgresContainerExtension.connection)

    //    TODO(Johann) investigate this bug. it is interesting. might be a bug in exposed. could debug the library, maybe also raise a ticket.
    @Test
    @Disabled
    fun `subsequent batch save should only save not existing transactions`(): Unit = runBlocking {
        val userId = randomUUID()
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("Test"), Currency.EUR))
        val dateTime = LocalDateTime.parse("2020-12-03T10:15:30")
        val transaction1 = CreateTransactionTO(
            dateTime, "transaction 1", TransactionType.SINGLE_RECORD, listOf(
                CreateTransactionRecordTO(account.id, randomUUID(), BigDecimal(1.0), Currency.EUR)
            )
        )
        val transaction2 = CreateTransactionTO(
            dateTime, "transaction 2", TransactionType.SINGLE_RECORD, listOf(
                CreateTransactionRecordTO(account.id, randomUUID(), BigDecimal(2.0), Currency.EUR)
            )
        )

        val request1 = CreateTransactionsTO(listOf(transaction1))
        val request2 = CreateTransactionsTO(listOf(transaction1, transaction2))

        transactionRepository.saveAll(userId, request1)
        transactionRepository.saveAll(userId, request2)

        val actual = transactionRepository.list(userId, TransactionFilterTO.empty())
        assertThat(actual).hasSize(2)
    }
}
