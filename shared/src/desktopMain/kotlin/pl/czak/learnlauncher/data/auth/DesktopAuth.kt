package pl.czak.learnlauncher.data.auth

import pl.czak.learnlauncher.currentTimeMillis
import pl.czak.learnlauncher.viewmodel.SettingsStore
import java.io.File
import java.util.Properties

class DesktopSettingsStore : SettingsStore {
    private val configDir = File(System.getProperty("user.home"), ".config/manga-reader").apply { mkdirs() }
    private val propsFile = File(configDir, "settings.properties")
    private val props = Properties().apply {
        if (propsFile.exists()) propsFile.inputStream().use { load(it) }
    }

    private fun save() {
        propsFile.outputStream().use { props.store(it, null) }
    }

    override fun getInt(key: String, default: Int): Int =
        props.getProperty(key)?.toIntOrNull() ?: default

    override fun putInt(key: String, value: Int) {
        props.setProperty(key, value.toString())
        save()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        props.getProperty(key)?.toBooleanStrictOrNull() ?: default

    override fun putBoolean(key: String, value: Boolean) {
        props.setProperty(key, value.toString())
        save()
    }

    override fun getString(key: String, default: String?): String? =
        props.getProperty(key) ?: default

    override fun putString(key: String, value: String?) {
        if (value != null) props.setProperty(key, value)
        else props.remove(key)
        save()
    }
}

class DesktopAuthManager(private val settingsStore: DesktopSettingsStore) {
    private var token: String? = null
    private var expiresAt: Long = 0
    private var userId: String? = null

    init {
        token = settingsStore.getString("auth_token", null)
        expiresAt = settingsStore.getString("auth_expires_at", null)?.toLongOrNull() ?: 0
        userId = settingsStore.getString("auth_user_id", null)
    }

    fun saveToken(token: String, expiresAt: Long, userId: String) {
        this.token = token
        this.expiresAt = expiresAt
        this.userId = userId
        settingsStore.putString("auth_token", token)
        settingsStore.putString("auth_expires_at", expiresAt.toString())
        settingsStore.putString("auth_user_id", userId)
    }

    fun getToken(): String? {
        val t = token ?: return null
        if (currentTimeMillis() / 1000 >= expiresAt) { logout(); return null }
        return t
    }

    fun getUserId(): String? {
        if (!isLoggedIn()) return null
        return userId
    }

    fun isLoggedIn(): Boolean = getToken() != null

    fun logout() {
        token = null
        expiresAt = 0
        userId = null
        settingsStore.putString("auth_token", null)
        settingsStore.putString("auth_expires_at", null)
        settingsStore.putString("auth_user_id", null)
    }
}
