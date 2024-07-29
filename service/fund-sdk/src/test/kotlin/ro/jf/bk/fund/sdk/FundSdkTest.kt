package ro.jf.bk.fund.sdk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class FundSdkTest {
    @Test
    fun `test list funds`(): Unit = runBlocking {
        val userId = randomUUID()
        val fundSdk = FundSdk()

        val listFunds = fundSdk.listFunds(userId)

        assert(listFunds.isEmpty())
    }
}