package com.limi.tvdesktop

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

object AccountManager {
    private const val PREFS_NAME = "media_library_prefs"
    private const val KEY_ACCOUNTS = "accounts_json"
    private const val KEY_SELECTED_ACCOUNT = "selected_account_id"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_POSTER_QUALITY = "poster_quality"
    private const val KEY_SHOW_DEMO = "show_demo_when_empty"

    private lateinit var prefs: SharedPreferences

    val accounts = mutableStateListOf<MediaAccount>()
    val selectedAccountId = mutableStateOf<String?>(null)
    val displayMode = mutableStateOf(LibraryDisplayMode.AGGREGATED)
    val posterQuality = mutableStateOf(PosterQuality.AUTO)
    val showDemoWhenEmpty = mutableStateOf(true)

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        accounts.clear()
        val jsonStr = prefs.getString(KEY_ACCOUNTS, "[]").orEmpty()
        runCatching {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                accounts.add(MediaAccount.fromJson(obj))
            }
        }
        selectedAccountId.value = prefs.getString(KEY_SELECTED_ACCOUNT, accounts.firstOrNull()?.id)
        val modeStr = prefs.getString(KEY_DISPLAY_MODE, LibraryDisplayMode.AGGREGATED.name)
        displayMode.value = LibraryDisplayMode.entries.find { it.name == modeStr } ?: LibraryDisplayMode.AGGREGATED

        val qualityStr = prefs.getString(KEY_POSTER_QUALITY, PosterQuality.AUTO.name)
        posterQuality.value = PosterQuality.entries.find { it.name == qualityStr } ?: PosterQuality.AUTO

        showDemoWhenEmpty.value = prefs.getBoolean(KEY_SHOW_DEMO, true)
    }

    fun saveAccounts() {
        val array = JSONArray()
        accounts.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    fun addAccount(account: MediaAccount) {
        accounts.add(account)
        if (selectedAccountId.value == null) {
            selectedAccountId.value = account.id
            prefs.edit().putString(KEY_SELECTED_ACCOUNT, account.id).apply()
        }
        saveAccounts()
    }

    fun updateAccount(account: MediaAccount) {
        val index = accounts.indexOfFirst { it.id == account.id }
        if (index >= 0) {
            accounts[index] = account
            saveAccounts()
        }
    }

    fun removeAccount(id: String) {
        accounts.removeAll { it.id == id }
        if (selectedAccountId.value == id) {
            selectedAccountId.value = accounts.firstOrNull()?.id
            prefs.edit().putString(KEY_SELECTED_ACCOUNT, selectedAccountId.value).apply()
        }
        saveAccounts()
    }

    fun selectAccount(id: String) {
        selectedAccountId.value = id
        prefs.edit().putString(KEY_SELECTED_ACCOUNT, id).apply()
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        displayMode.value = mode
        prefs.edit().putString(KEY_DISPLAY_MODE, mode.name).apply()
    }

    fun setPosterQuality(quality: PosterQuality) {
        posterQuality.value = quality
        prefs.edit().putString(KEY_POSTER_QUALITY, quality.name).apply()
    }

    fun setShowDemoWhenEmpty(show: Boolean) {
        showDemoWhenEmpty.value = show
        prefs.edit().putBoolean(KEY_SHOW_DEMO, show).apply()
    }
}
