package ro.jf.funds.client.core.repository

import ro.jf.funds.client.api.model.UserTO
import ro.jf.funds.client.sdk.UserSdk

class AuthRepository(
    private val userSdk: UserSdk = UserSdk()
) {
    suspend fun loginWithUsername(username: String): UserTO? {
        return userSdk.findUserByUsername(username)
    }
}
