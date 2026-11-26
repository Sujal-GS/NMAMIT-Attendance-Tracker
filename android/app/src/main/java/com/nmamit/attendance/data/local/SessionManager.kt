package com.nmamit.attendance.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nmamit.attendance.data.model.StudentInfo
import com.nmamit.attendance.data.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nmamit_attendance_prefs")

data class StoredCredentials(
    val loginId: String,
    val regno: String,
    val password: String,
    val univcode: String,
    val isDemo: Boolean
)

class SessionManager(private val context: Context) {

    companion object {
        private val KEY_SESSION_ID = stringPreferencesKey("session_id")
        private val KEY_LOGIN_ID = stringPreferencesKey("login_id")
        private val KEY_REGNO = stringPreferencesKey("regno")
        private val KEY_UNIVCODE = stringPreferencesKey("univcode")
        private val KEY_ENCRYPTED_PASSWORD = stringPreferencesKey("enc_passwd_v1")
        private val KEY_NAME = stringPreferencesKey("student_name")
        private val KEY_DEGREE = stringPreferencesKey("degree")
        private val KEY_BRANCH = stringPreferencesKey("branch")
        private val KEY_SEMESTER = stringPreferencesKey("semester")
        private val KEY_IS_DEMO = booleanPreferencesKey("is_demo")
    }

    val sessionIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SESSION_ID]
    }

    val isDemoFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_DEMO] ?: false
    }

    val studentInfoFlow: Flow<StudentInfo?> = context.dataStore.data.map { prefs ->
        val regno = prefs[KEY_REGNO]
        if (regno != null) {
            StudentInfo(
                regno = regno,
                name = prefs[KEY_NAME],
                degree = prefs[KEY_DEGREE] ?: "B.Tech",
                branch = prefs[KEY_BRANCH],
                semester = prefs[KEY_SEMESTER]
            )
        } else null
    }

    suspend fun hasSavedSession(): Boolean {
        return !context.dataStore.data.first()[KEY_SESSION_ID].isNullOrBlank()
    }

    suspend fun getUnivCode(): String {
        val raw = context.dataStore.data.first()[KEY_UNIVCODE]
        return raw?.takeIf { Regex("^[a-zA-Z0-9_-]{1,10}$").matches(it) } ?: "049"
    }

    suspend fun getRegno(): String {
        return context.dataStore.data.first()[KEY_REGNO].orEmpty()
    }

    suspend fun isDemoSession(): Boolean {
        return context.dataStore.data.first()[KEY_IS_DEMO] ?: false
    }

    /**
     * Retrieves saved credentials with hardware AES-256-GCM decryption.
     * Uses `loginId` (registered mobile/login ID) for `signin.php` and `regno` (USN) for portal APIs.
     */
    suspend fun getSavedCredentials(): StoredCredentials? {
        val prefs = context.dataStore.data.first()
        val regno = prefs[KEY_REGNO]
        val loginId = prefs[KEY_LOGIN_ID]?.takeIf { it.isNotBlank() } ?: regno
        val univcode = prefs[KEY_UNIVCODE]?.takeIf { Regex("^[a-zA-Z0-9_-]{1,10}$").matches(it) } ?: "049"
        val isDemo = prefs[KEY_IS_DEMO] ?: false
        if (isDemo && !regno.isNullOrBlank()) {
            return StoredCredentials(
                loginId = loginId.orEmpty(),
                regno = regno,
                password = "",
                univcode = univcode,
                isDemo = true
            )
        }

        val encryptedPasswd = prefs[KEY_ENCRYPTED_PASSWORD]
        if (!loginId.isNullOrBlank() && !regno.isNullOrBlank() && !encryptedPasswd.isNullOrBlank()) {
            val decryptedPasswd = CryptoManager.decrypt(encryptedPasswd)
            if (!decryptedPasswd.isNullOrBlank()) {
                return StoredCredentials(
                    loginId = loginId,
                    regno = regno,
                    password = decryptedPasswd,
                    univcode = univcode,
                    isDemo = false
                )
            }
        }
        return null
    }

    /**
     * Encrypts password via Android Hardware Keystore before storing in DataStore.
     */
    suspend fun saveSession(
        sessionId: String,
        loginId: String,
        regno: String,
        plainPassword: String,
        univcode: String,
        studentInfo: StudentInfo?,
        isDemo: Boolean = false
    ) {
        val encryptedPassword = if (isDemo) "" else (CryptoManager.encrypt(plainPassword) ?: "")

        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_ID] = sessionId
            prefs[KEY_LOGIN_ID] = loginId
            prefs[KEY_REGNO] = regno
            prefs[KEY_UNIVCODE] = univcode
            prefs[KEY_ENCRYPTED_PASSWORD] = encryptedPassword
            prefs[KEY_IS_DEMO] = isDemo
            if (studentInfo != null) {
                studentInfo.name?.let { prefs[KEY_NAME] = it }
                studentInfo.degree?.let { prefs[KEY_DEGREE] = it }
                studentInfo.branch?.let { prefs[KEY_BRANCH] = it }
                studentInfo.semester?.let { prefs[KEY_SEMESTER] = it }
            }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SESSION_ID)
            prefs.remove(KEY_LOGIN_ID)
            prefs.remove(KEY_REGNO)
            prefs.remove(KEY_UNIVCODE)
            prefs.remove(KEY_ENCRYPTED_PASSWORD)
            prefs.remove(KEY_NAME)
            prefs.remove(KEY_DEGREE)
            prefs.remove(KEY_BRANCH)
            prefs.remove(KEY_SEMESTER)
            prefs.remove(KEY_IS_DEMO)
        }
    }
}
