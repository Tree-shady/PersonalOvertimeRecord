package com.example.personalovertimerecord.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密格式兼容测试：
 * - 新格式（magic "G2"，PBKDF2 600,000 次）加解密往返；
 * - 历史 GCM 格式（magic "GM"，PBKDF2 65,536 次）仍可解密（升级强度的前提）；
 * - 错误密码必须失败（GCM 认证）。
 */
class EncryptionUtilsTest {

    private val plaintext = "加班记录_2026-01-05_底薪5000".toByteArray(Charsets.UTF_8)

    private val legacyGcmMagic = byteArrayOf(0x47, 0x4D) // "GM"
    private val legacyIterations = 65536
    private val saltSize = 16
    private val ivLength = 12
    private val tagBits = 128

    @Test
    fun newFormat_roundTrip_andWrongPasswordFails() {
        val encrypted = EncryptionUtils.encrypt(plaintext, "Correct-Pass-1")
        // 新格式以 "G2" magic 开头
        assertEquals(0x47, encrypted[0].toInt() and 0xFF)
        assertEquals(0x32, encrypted[1].toInt() and 0xFF)

        assertArrayEquals(plaintext, EncryptionUtils.decrypt(encrypted, "Correct-Pass-1"))
        assertTrue(EncryptionUtils.verifyPassword(encrypted, "Correct-Pass-1"))
        assertFalse(EncryptionUtils.verifyPassword(encrypted, "Wrong-Pass"))

        try {
            EncryptionUtils.decrypt(encrypted, "Wrong-Pass")
            fail("错误密码应抛出认证异常")
        } catch (e: GeneralSecurityException) {
            // 期望：GCM 认证失败
        }
    }

    @Test
    fun legacyGmFormat_stillDecryptable() {
        // 用历史参数（"GM" + 65,536 次迭代）构造一个旧版密文，验证新版读取端兼容
        val encrypted = encryptLegacyGm(plaintext, "Legacy-Pass")

        assertArrayEquals(plaintext, EncryptionUtils.decrypt(encrypted, "Legacy-Pass"))
        assertTrue(EncryptionUtils.verifyPassword(encrypted, "Legacy-Pass"))
        assertFalse(EncryptionUtils.verifyPassword(encrypted, "Wrong-Pass"))
    }

    /** 复刻旧版写入逻辑，用于兼容性测试（与生产代码无关的独立实现） */
    private fun encryptLegacyGm(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(saltSize).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(ivLength).also { SecureRandom().nextBytes(it) }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, legacyIterations, 256)
        val key = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(tagBits, iv))
        return legacyGcmMagic + salt + iv + cipher.doFinal(data)
    }
}
