package pl.czak.learnlauncher.data.auth

import android.content.Context

class AuthManager(context: Context) {

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveToken(token: String, expiresAt: Long, userId: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun getUserId(): String? {
        if (!isLoggedIn()) return null
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getToken(): String? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        if (System.currentTimeMillis() / 1000 >= expiresAt) {
            logout()
            return null
        }
        return token
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun logout() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EXPIRES_AT = "jwt_expires_at"
        private const val KEY_USER_ID = "user_id"
    }
}

data class LoginResult(val token: String, val expiresAt: Long)
class AuthException(message: String) : Exception(message)

interface AuthApi {
    suspend fun login(userId: String): LoginResult
}

class LocalAuthApi : AuthApi {
    private val validUsers = setOf("user9395", "user9266")

    override suspend fun login(userId: String): LoginResult {
        if (userId !in validUsers) {
            throw AuthException("Unknown user ID")
        }
        val expiresAt = System.currentTimeMillis() / 1000 + 30 * 24 * 60 * 60
        val token = "local-${java.util.UUID.randomUUID()}"
        return LoginResult(token, expiresAt)
    }
}
