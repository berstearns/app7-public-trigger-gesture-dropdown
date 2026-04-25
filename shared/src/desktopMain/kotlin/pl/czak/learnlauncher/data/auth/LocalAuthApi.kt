package pl.czak.learnlauncher.data.auth

import java.util.UUID

data class LoginResult(val token: String, val expiresAt: Long)
class AuthException(message: String) : Exception(message)

class LocalAuthApi {
    private val validUsers = setOf("user9395", "user9266")

    fun login(userId: String): LoginResult {
        if (userId !in validUsers) throw AuthException("Unknown user ID")
        val expiresAt = System.currentTimeMillis() / 1000 + 30 * 24 * 60 * 60
        val token = "local-${UUID.randomUUID()}"
        return LoginResult(token, expiresAt)
    }
}
