package com.example.data.crypto

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private object Base64Codec {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val INV = IntArray(256) { -1 }.apply {
        CHARS.forEachIndexed { idx, c -> this[c.code] = idx }
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

            sb.append(CHARS[b0 ushr 2])
            sb.append(CHARS[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            sb.append(if (hasB1) CHARS[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            sb.append(if (hasB2) CHARS[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    fun decode(input: String): ByteArray {
        val clean = input.trim().filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream((clean.length * 3) / 4)
        var i = 0
        while (i + 3 < clean.length) {
            val c0 = INV[clean[i].code]
            val c1 = INV[clean[i + 1].code]
            val c2 = if (clean[i + 2] == '=') -1 else INV[clean[i + 2].code]
            val c3 = if (clean[i + 3] == '=') -1 else INV[clean[i + 3].code]
            out.write((c0 shl 2) or (c1 ushr 4))
            if (c2 >= 0) out.write(((c1 and 0x0F) shl 4) or (c2 ushr 2))
            if (c3 >= 0) out.write(((c2 and 0x03) shl 6) or c3)
            i += 4
        }
        return out.toByteArray()
    }
}

interface CryptoEngine {
    fun encryptString(plain: String): String
    fun decryptString(stored: String): String
    fun encryptBytes(plain: ByteArray): ByteArray
    fun decryptBytes(cipherBytes: ByteArray): ByteArray
    fun generateSalt(): String
    fun hashPin(pin: String, saltBase64: String): String
    fun encryptWithPassword(plain: String, password: String): String
    fun decryptWithPassword(payload: String, password: String): String
}

class AndroidKeystoreCryptoEngine : CryptoEngine {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "babbal_finance_master_aes_v1"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val ENC_PREFIX = "ENC1:"
        private const val PWD_PREFIX = "BABBAL_ENC_V1:"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 100_000
    }

    private val random = SecureRandom()

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun getOrCreateMasterKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val key = runCatching {
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                if (existing != null) {
                    existing
                } else {
                    val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                    val spec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                    keyGen.init(spec)
                    keyGen.generateKey()
                }
            }.getOrElse {
                val fallbackBytes = ByteArray(32).also { bytes ->
                    val seed = "babbal-local-fallback-key-v1".toByteArray(Charsets.UTF_8)
                    System.arraycopy(seed, 0, bytes, 0, seed.size.coerceAtMost(32))
                }
                SecretKeySpec(fallbackBytes, "AES")
            }
            cachedKey = key
            return key
        }
    }

    override fun encryptString(plain: String): String {
        if (plain.isEmpty()) return ""
        val encrypted = encryptBytes(plain.toByteArray(Charsets.UTF_8))
        return ENC_PREFIX + Base64Codec.encode(encrypted)
    }

    override fun decryptString(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(ENC_PREFIX)) return stored
        return runCatching {
            val raw = Base64Codec.decode(stored.removePrefix(ENC_PREFIX))
            String(decryptBytes(raw), Charsets.UTF_8)
        }.getOrDefault("")
    }

    override fun encryptBytes(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        val combined = ByteArray(1 + iv.size + cipherText.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherText, 0, combined, 1 + iv.size, cipherText.size)
        return combined
    }

    override fun decryptBytes(cipherBytes: ByteArray): ByteArray {
        if (cipherBytes.size < 2) return ByteArray(0)
        val ivLen = cipherBytes[0].toInt() and 0xFF
        if (cipherBytes.size < 1 + ivLen) return ByteArray(0)
        val iv = cipherBytes.copyOfRange(1, 1 + ivLen)
        val encrypted = cipherBytes.copyOfRange(1 + ivLen, cipherBytes.size)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    override fun generateSalt(): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64Codec.encode(salt)
    }

    override fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64Codec.decode(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        return Base64Codec.encode(derived)
    }

    override fun encryptWithPassword(plain: String, password: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
        val key = deriveAesKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val saltB64 = Base64Codec.encode(salt)
        val ivB64 = Base64Codec.encode(iv)
        val dataB64 = Base64Codec.encode(encrypted)
        return "$PWD_PREFIX$saltB64:$ivB64:$dataB64"
    }

    override fun decryptWithPassword(payload: String, password: String): String {
        require(payload.startsWith(PWD_PREFIX)) { "Invalid encrypted backup format" }
        val parts = payload.removePrefix(PWD_PREFIX).split(":")
        require(parts.size == 3) { "Corrupted encrypted backup payload" }
        val salt = Base64Codec.decode(parts[0])
        val iv = Base64Codec.decode(parts[1])
        val data = Base64Codec.decode(parts[2])
        val key = deriveAesKeyFromPassword(password, salt)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val decrypted = cipher.doFinal(data)
        return String(decrypted, Charsets.UTF_8)
    }

    private fun deriveAesKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}

class InMemoryTestCryptoEngine : CryptoEngine {
    private val key = SecretKeySpec("test-aes-256-key-for-unit-tests!".toByteArray(Charsets.UTF_8), "AES")
    private val random = SecureRandom()

    override fun encryptString(plain: String): String {
        if (plain.isEmpty()) return ""
        return "ENC1:" + Base64Codec.encode(encryptBytes(plain.toByteArray(Charsets.UTF_8)))
    }

    override fun decryptString(stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith("ENC1:")) return stored
        val raw = Base64Codec.decode(stored.removePrefix("ENC1:"))
        return String(decryptBytes(raw), Charsets.UTF_8)
    }

    override fun encryptBytes(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { random.nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val out = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + out
    }

    override fun decryptBytes(cipherBytes: ByteArray): ByteArray {
        val ivLen = cipherBytes[0].toInt() and 0xFF
        val iv = cipherBytes.copyOfRange(1, 1 + ivLen)
        val data = cipherBytes.copyOfRange(1 + ivLen, cipherBytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    override fun generateSalt(): String = AndroidKeystoreCryptoEngine().generateSalt()
    override fun hashPin(pin: String, saltBase64: String): String = AndroidKeystoreCryptoEngine().hashPin(pin, saltBase64)
    override fun encryptWithPassword(plain: String, password: String): String =
        AndroidKeystoreCryptoEngine().encryptWithPassword(plain, password)

    override fun decryptWithPassword(payload: String, password: String): String =
        AndroidKeystoreCryptoEngine().decryptWithPassword(payload, password)
}

class ReceiptStorageManager(
    private val context: Context,
    private val cryptoEngine: CryptoEngine
) {
    private val receiptsDir: File
        get() = File(context.filesDir, "receipts").apply { mkdirs() }

    fun saveEncryptedReceipt(sourceUri: Uri): String? {
        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
                ?: return null
            val encrypted = cryptoEngine.encryptBytes(rawBytes)
            val fileName = "rcpt_${UUID.randomUUID()}.enc"
            val dest = File(receiptsDir, fileName)
            dest.writeBytes(encrypted)
            dest.absolutePath
        }.getOrNull()
    }

    fun readDecryptedReceipt(path: String): ByteArray? {
        return runCatching {
            val file = File(path)
            if (!file.exists()) return null
            cryptoEngine.decryptBytes(file.readBytes())
        }.getOrNull()
    }

    fun deleteReceipt(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
