package com.example.personalovertimerecord.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.security.SecureRandom

object DatabaseKeyManager {
    
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "overtime_db_master_key"
    private const val PREFS_NAME = "db_key_prefs"
    private const val DB_PASSPHRASE_KEY = "db_passphrase"
    private const val KEY_SIZE = 32
    
    fun getDatabaseKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val storedPassphrase = prefs.getString(DB_PASSPHRASE_KEY, null)
        
        if (storedPassphrase != null) {
            return Base64.decode(storedPassphrase, Base64.DEFAULT)
        }
        
        val newPassphrase = generateRandomKey()
        val encodedPassphrase = Base64.encodeToString(newPassphrase, Base64.DEFAULT)
        
        prefs.edit()
            .putString(DB_PASSPHRASE_KEY, encodedPassphrase)
            .apply()
        
        ensureKeyStoreKeyExists()
        
        return newPassphrase
    }
    
    private fun generateRandomKey(): ByteArray {
        val secureRandom = SecureRandom()
        val key = ByteArray(KEY_SIZE)
        secureRandom.nextBytes(key)
        return key
    }
    
    private fun ensureKeyStoreKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }
    
    fun getOrCreateMasterKey(): SecretKey {
        ensureKeyStoreKeyExists()
        
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
}
