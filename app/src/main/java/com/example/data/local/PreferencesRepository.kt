package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.crypto.CryptoEngine
import com.example.domain.model.AppPreferences
import com.example.domain.model.DateFormatOption
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.babbalDataStore: DataStore<Preferences> by preferencesDataStore(name = "babbal_prefs")

class PreferencesRepository(
    private val context: Context,
    private val cryptoEngine: CryptoEngine
) {
    private object Keys {
        val PERSONAL_NAME = stringPreferencesKey("personal_workspace_name")
        val BUSINESS_NAME = stringPreferencesKey("business_workspace_name")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val FY_START_MONTH = intPreferencesKey("fy_start_month")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val LAST_BACKUP_TS = longPreferencesKey("last_backup_ts")
        val BACKUP_DISMISSED_AT = longPreferencesKey("backup_dismissed_at")
        val BACKUP_REMINDER_DAYS = intPreferencesKey("backup_reminder_days")
    }

    val preferencesFlow: Flow<AppPreferences> = context.babbalDataStore.data.map { prefs ->
        AppPreferences(
            personalWorkspaceName = prefs[Keys.PERSONAL_NAME]?.let { cryptoEngine.decryptString(it) }
                ?.ifBlank { "Personal" } ?: "Personal",
            businessWorkspaceName = prefs[Keys.BUSINESS_NAME]?.let { cryptoEngine.decryptString(it) }
                ?.ifBlank { "GVVS Business" } ?: "GVVS Business",
            currencySymbol = prefs[Keys.CURRENCY_SYMBOL] ?: "₹",
            currencyCode = prefs[Keys.CURRENCY_CODE] ?: "INR",
            dateFormat = DateFormatOption.fromId(prefs[Keys.DATE_FORMAT] ?: DateFormatOption.DD_MM_YYYY.id),
            financialYearStartMonth = prefs[Keys.FY_START_MONTH] ?: 4,
            themeMode = ThemeMode.fromId(prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.id),
            pinHash = prefs[Keys.PIN_HASH],
            pinSalt = prefs[Keys.PIN_SALT],
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            autoLockMinutes = prefs[Keys.AUTO_LOCK_MINUTES] ?: 1,
            lastBackupTimestamp = prefs[Keys.LAST_BACKUP_TS] ?: 0L,
            backupReminderDismissedAt = prefs[Keys.BACKUP_DISMISSED_AT] ?: 0L,
            backupReminderDays = prefs[Keys.BACKUP_REMINDER_DAYS] ?: 7
        )
    }

    suspend fun getSnapshot(): AppPreferences = preferencesFlow.first()

    suspend fun updateProfileNames(personalName: String, businessName: String) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.PERSONAL_NAME] = cryptoEngine.encryptString(personalName.trim().ifBlank { "Personal" })
            prefs[Keys.BUSINESS_NAME] = cryptoEngine.encryptString(businessName.trim().ifBlank { "GVVS Business" })
        }
    }

    suspend fun updateCurrency(symbol: String, code: String) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.CURRENCY_SYMBOL] = symbol.ifBlank { "₹" }
            prefs[Keys.CURRENCY_CODE] = code.ifBlank { "INR" }
        }
    }

    suspend fun updateDateFormat(option: DateFormatOption) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.DATE_FORMAT] = option.id
        }
    }

    suspend fun updateFinancialYearStartMonth(month: Int) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.FY_START_MONTH] = month.coerceIn(1, 12)
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.id
        }
    }

    suspend fun setPin(pin: String) {
        val salt = cryptoEngine.generateSalt()
        val hash = cryptoEngine.hashPin(pin, salt)
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = salt
            prefs[Keys.PIN_HASH] = hash
        }
    }

    suspend fun clearPin() {
        context.babbalDataStore.edit { prefs ->
            prefs.remove(Keys.PIN_SALT)
            prefs.remove(Keys.PIN_HASH)
            prefs[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val current = getSnapshot()
        val salt = current.pinSalt ?: return false
        val expectedHash = current.pinHash ?: return false
        return cryptoEngine.hashPin(pin, salt) == expectedHash
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.AUTO_LOCK_MINUTES] = minutes
        }
    }

    suspend fun markBackupCompleted(timestamp: Long = System.currentTimeMillis()) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_TS] = timestamp
        }
    }

    suspend fun dismissBackupReminder(timestamp: Long = System.currentTimeMillis()) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.BACKUP_DISMISSED_AT] = timestamp
        }
    }

    suspend fun restoreNonSecuritySettings(
        personalName: String,
        businessName: String,
        currencySymbol: String,
        currencyCode: String,
        dateFormat: DateFormatOption,
        fyStartMonth: Int,
        themeMode: ThemeMode
    ) {
        context.babbalDataStore.edit { prefs ->
            prefs[Keys.PERSONAL_NAME] = cryptoEngine.encryptString(personalName.ifBlank { "Personal" })
            prefs[Keys.BUSINESS_NAME] = cryptoEngine.encryptString(businessName.ifBlank { "GVVS Business" })
            prefs[Keys.CURRENCY_SYMBOL] = currencySymbol.ifBlank { "₹" }
            prefs[Keys.CURRENCY_CODE] = currencyCode.ifBlank { "INR" }
            prefs[Keys.DATE_FORMAT] = dateFormat.id
            prefs[Keys.FY_START_MONTH] = fyStartMonth.coerceIn(1, 12)
            prefs[Keys.THEME_MODE] = themeMode.id
        }
    }
}
