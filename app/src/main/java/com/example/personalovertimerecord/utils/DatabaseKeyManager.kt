package com.example.personalovertimerecord.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 数据库密钥管理器
 *
 * SQLCipher 主密钥（passphrase）不再明文存储，而是：
 * 1. 由安全随机数生成 32 字节 passphrase；
 * 2. 使用 Android Keystore 中的 AES-GCM 密钥加密后存入 SharedPreferences；
 * 3. 每次打开数据库时用 Keystore 解密得到 passphrase。
 *
 * 旧版本（明文存储）的 passphrase 会在首次访问时自动迁移到加密格式。
 *
 * 注意：Keystore 密钥不随系统备份迁移，换机/还原到新设备后数据库将无法解密，
 * 这是"密钥不落盘明文"的安全取舍；迁移逻辑已保留明文数据的平滑升级路径。
 */
object DatabaseKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "overtime_db_master_key"
    private const val PREFS_NAME = "db_key_prefs"

    /** 旧版本明文存储 key（仅用于迁移） */
    private const val DB_PASSPHRASE_KEY = "db_passphrase"

    /** 新版本加密存储 key */
    private const val DB_PASSPHRASE_ENC_KEY = "db_passphrase_enc"

    private const val KEY_SIZE = 32

    /** AES-GCM 默认 IV 长度 */
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    fun getDatabaseKey(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. 迁移旧版明文 passphrase（老版本遗留数据）
        val legacyPassphrase = prefs.getString(DB_PASSPHRASE_KEY, null)
        if (legacyPassphrase != null) {
            val bytes = Base64.decode(legacyPassphrase, Base64.DEFAULT)
            storeEncryptedPassphrase(context, bytes)
            prefs.edit().remove(DB_PASSPHRASE_KEY).apply()
            return bytes
        }

        // 2. 读取加密存储的 passphrase
        val encryptedPassphrase = prefs.getString(DB_PASSPHRASE_ENC_KEY, null)
        if (encryptedPassphrase != null) {
            val encrypted = Base64.decode(encryptedPassphrase, Base64.DEFAULT)
            return decryptWithKeystore(encrypted)
        }

        // 3. 首次运行：生成随机 passphrase 并用 Keystore 加密存储
        val newPassphrase = generateRandomKey()
        storeEncryptedPassphrase(context, newPassphrase)
        return newPassphrase
    }

    /**
     * 用 Keystore 密钥加密 passphrase 并存储
     */
    private fun storeEncryptedPassphrase(context: Context, passphrase: ByteArray) {
        val encrypted = encryptWithKeystore(passphrase)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(DB_PASSPHRASE_ENC_KEY, Base64.encodeToString(encrypted, Base64.DEFAULT))
            .apply()
    }

    private fun encryptWithKeystore(data: ByteArray): ByteArray {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(data)
        // 返回格式：iv(12) + ciphertext(含 GCM tag)
        return iv + cipherText
    }

    private fun decryptWithKeystore(encrypted: ByteArray): ByteArray {
        if (encrypted.size <= GCM_IV_LENGTH) {
            throw IllegalStateException("Invalid encrypted passphrase")
        }
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val cipherText = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText)
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
