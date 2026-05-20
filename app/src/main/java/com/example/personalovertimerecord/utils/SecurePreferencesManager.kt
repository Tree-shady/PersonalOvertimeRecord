package com.example.personalovertimerecord.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.security.crypto.MasterKey.Builder

object SecurePreferencesManager {
    
    private const val ENCRYPTED_PREFS_NAME = "secure_overtime_prefs"
    private const val KEYSTORE_ALIAS = "overtime_master_key"
    
    private var encryptedPrefs: SharedPreferences? = null
    
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        if (encryptedPrefs == null) {
            encryptedPrefs = createEncryptedPrefs(context)
        }
        return encryptedPrefs!!
    }
    
    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    fun clearCache() {
        encryptedPrefs = null
    }
}
