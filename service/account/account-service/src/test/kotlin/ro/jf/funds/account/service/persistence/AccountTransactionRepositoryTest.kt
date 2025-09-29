package ro.jf.funds.account.service.persistence

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import ro.jf.funds.account.api.model.*
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.test.extension.PostgresContainerExtension
import java.math.BigDecimal
import java.util.UUID.randomUUID

@ExtendWith(PostgresContainerExtension::class)
class AccountTransactionRepositoryTest {
    private val accountRepository = AccountRepository(PostgresContainerExtension.connection)
    private val accountTransactionRepository = AccountTransactionRepository(PostgresContainerExtension.connection)

    //    TODO(Johann) investigate this bug. it is interesting. might be a bug in exposed. could debug the library, maybe also raise a ticket.
    @Test
    @Disabled
    fun `subsequent batch save should only save not existing transactions`(): Unit = runBlocking {
        val userId = randomUUID()
        val account = accountRepository.save(userId, CreateAccountTO(AccountName("Test"), Currency.EUR))
        val dateTime = LocalDateTime.parse("2020-12-03T10:15:30")
        val transaction1 = CreateAccountTransactionTO(
            dateTime, "transaction 1", AccountTransactionType.INCOME, listOf(
                CreateAccountRecordTO(account.id, BigDecimal(1.0), Currency.EUR)
            )
        )
        val transaction2 = CreateAccountTransactionTO(
            dateTime, "transaction 2", AccountTransactionType.EXPENSE, listOf(
                CreateAccountRecordTO(account.id, BigDecimal(2.0), Currency.EUR)
            )
        )

        val request1 = CreateAccountTransactionsTO(listOf(transaction1))
        val request2 = CreateAccountTransactionsTO(listOf(transaction1, transaction2))

        accountTransactionRepository.saveAll(userId, request1)
        accountTransactionRepository.saveAll(userId, request2)

        val actual = accountTransactionRepository.list(userId, AccountTransactionFilterTO.empty())
        assertThat(actual).hasSize(2)
    }
}