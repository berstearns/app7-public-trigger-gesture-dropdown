package pl.czak.learnlauncher.data.auth

import pl.czak.learnlauncher.currentTimeMillis
import pl.czak.learnlauncher.viewmodel.SettingsStore

class IOSSettingsStore : SettingsStore {
    private val store = mutableMapOf<String, Any?>()

    override fun getInt(key: String, default: Int): Int = (store[key] as? Int) ?: default
    override fun putInt(key: String, value: Int) { store[key] = value }
    override fun getBoolean(key: String, default: Boolean): Boolean = (store[key] as? Boolean) ?: default
    override fun putBoolean(key: String, value: Boolean) { store[key] = value }
    override fun getString(key: String, default: String?): String? = (store[key] as? String) ?: default
    override fun putString(key: String, value: String?) { store[key] = value }
}

class KeychainAuthManager {
    private var token: String? = null
    private var expiresAt: Long = 0
    private var userId: String? = null

    fun saveToken(token: String, expiresAt: Long, userId: String) {
        this.token = token
        this.expiresAt = expiresAt
        this.userId = userId
    }

    fun isLoggedIn(): Boolean {
        val t = token ?: return false
        return currentTimeMillis() / 1000 < expiresAt
    }

    fun logout() {
        token = null
        expiresAt = 0
        userId = null
    }
}
