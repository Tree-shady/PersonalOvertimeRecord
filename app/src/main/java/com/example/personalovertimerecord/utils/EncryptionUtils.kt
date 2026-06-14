package com.example.personalovertimerecord.utils

import android.util.Base64
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object EncryptionUtils {
    
    private const val AES_KEY_SIZE = 256
    private const val IV_SIZE = 16
    private const val PBKDF2_ITERATIONS = 65536
    private const val SALT_SIZE = 16
    
    /**
     * 使用 AES-256-CBC 加密数据
     * @param data 要加密的数据
     * @param password 加密密码
     * @return 加密后的数据（包含 salt + iv + 加密数据）
     */
    fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = generateSalt()
        val iv = generateIV()
        val key = deriveKey(password, salt)
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        
        val encryptedData = cipher.doFinal(data)
        
        // 返回 salt + iv + 加密数据
        return salt + iv + encryptedData
    }
    
    /**
     * 解密数据
     * @param encryptedData 加密的数据（包含 salt + iv + 加密数据）
     * @param password 解密密码
     * @return 解密后的数据
     */
    fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        val salt = encryptedData.copyOfRange(0, SALT_SIZE)
        val iv = encryptedData.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val data = encryptedData.copyOfRange(SALT_SIZE + IV_SIZE, encryptedData.size)
        
        val key = deriveKey(password, salt)
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        
        return cipher.doFinal(data)
    }
    
    /**
     * 生成随机盐值
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom.getInstanceStrong().nextBytes(salt)
        return salt
    }
    
    /**
     * 生成初始化向量
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        SecureRandom.getInstanceStrong().nextBytes(iv)
        return iv
    }
    
    /**
     * 使用 PBKDF2 从密码派生密钥
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
    
    /**
     * 加密字符串（Base64 编码）
     */
    fun encryptString(text: String, password: String): String {
        val encrypted = encrypt(text.toByteArray(Charsets.UTF_8), password)
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }
    
    /**
     * 解密字符串（Base64 解码）
     */
    fun decryptString(encryptedBase64: String, password: String): String {
        val encrypted = Base64.decode(encryptedBase64, Base64.DEFAULT)
        val decrypted = decrypt(encrypted, password)
        return String(decrypted, Charsets.UTF_8)
    }
    
    /**
     * 加密文件
     * @param inputPath 输入文件路径
     * @param outputPath 输出文件路径（加密后）
     * @param password 加密密码
     */
    fun encryptFile(inputPath: String, outputPath: String, password: String) {
        val inputFile = File(inputPath)
        val outputFile = File(outputPath)
        
        val fileBytes = inputFile.readBytes()
        val encryptedBytes = encrypt(fileBytes, password)
        
        outputFile.writeBytes(encryptedBytes)
    }
    
    /**
     * 解密文件
     * @param inputPath 加密文件路径
     * @param outputPath 输出文件路径（解密后）
     * @param password 解密密码
     */
    fun decryptFile(inputPath: String, outputPath: String, password: String): Boolean {
        return try {
            val inputFile = File(inputPath)
            val outputFile = File(outputPath)
            
            val encryptedBytes = inputFile.readBytes()
            val decryptedBytes = decrypt(encryptedBytes, password)
            
            outputFile.writeBytes(decryptedBytes)
            true
        } catch (e: Exception) {
            AppLogger.e("Encryption", "Decrypt file failed", e)
            false
        }
    }
    
    /**
     * 检查数据是否已加密（通过检查文件大小）
     */
    fun isEncrypted(data: ByteArray): Boolean {
        return data.size > SALT_SIZE + IV_SIZE
    }
    
    /**
     * 验证密码是否正确
     */
    fun verifyPassword(encryptedData: ByteArray, password: String): Boolean {
        return try {
            decrypt(encryptedData, password)
            true
        } catch (e: Exception) {
            false
        }
    }
}