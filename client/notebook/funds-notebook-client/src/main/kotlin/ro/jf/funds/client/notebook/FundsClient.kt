package ro.jf.funds.client.notebook

import kotlinx.coroutines.runBlocking
import ro.jf.funds.user.api.model.UserTO
import ro.jf.funds.user.sdk.UserSdk

class FundsClient(
    private val userSdk: UserSdk = UserSdk(),
) {
    fun ensureUserExists(username: String): UserTO = runBlocking {
        userSdk.findUserByUsername(username)
            ?: userSdk.createUser(username)
    }
}
