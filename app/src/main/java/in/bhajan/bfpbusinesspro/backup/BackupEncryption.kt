package `in`.bhajan.bfpbusinesspro.backup

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned password-based envelope for BFP backup archives. Passwords are never persisted here. */
object BackupEncryption {
    private val MAGIC = byteArrayOf(0x42, 0x46, 0x50, 0x45) // BFPE
    private const val VERSION: Int = 1
    private const val ITERATIONS: Int = 310_000
    private const val SALT_BYTES: Int = 16
    private const val NONCE_BYTES: Int = 12
    private const val KEY_BITS: Int = 256
    private const val TAG_BITS: Int = 128
    private const val MIN_PASSWORD_CHARS: Int = 10

    fun isEncrypted(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return FileInputStream(file).use { input ->
            val prefix = ByteArray(4)
            input.read(prefix) == 4 && prefix.contentEquals(MAGIC)
        }
    }

    /** Encrypts a completed ZIP archive. The caller owns and deletes any temporary plaintext archive. */
    fun encrypt(inputFile: File, output: OutputStream, password: CharArray) {
        require(password.size >= MIN_PASSWORD_CHARS) { "पासवर्ड कम-से-कम 10 अक्षरों का होना चाहिए" }
        require(inputFile.isFile && inputFile.length() > 0L) { "बैकअप ZIP खाली है" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, nonce)
        output.write(MAGIC)
        output.write(VERSION)
        output.write(intBytes(ITERATIONS))
        output.write(salt)
        output.write(nonce)
        CipherOutputStream(output, cipher).use { encrypted ->
            FileInputStream(inputFile).use { source -> source.copyTo(encrypted, 8192) }
        }
    }

    /** Decrypts into a private cache file; caller must validate it as a BFP ZIP before using it. */
    fun decrypt(input: InputStream, outputFile: File, password: CharArray) {
        require(password.isNotEmpty()) { "पासवर्ड दर्ज करें" }
        val header = ByteArray(4 + 1 + 4 + SALT_BYTES + NONCE_BYTES)
        var offset = 0
        while (offset < header.size) {
            val count = input.read(header, offset, header.size - offset)
            require(count > 0) { "Encrypted backup header अधूरा है" }
            offset += count
        }
        require(header.copyOfRange(0, 4).contentEquals(MAGIC)) { "यह encrypted BFP backup नहीं है" }
        require(header[4].toInt() == VERSION) { "Encrypted backup संस्करण समर्थित नहीं है" }
        val iterations = readInt(header, 5)
        require(iterations in 200_000..600_000) { "Backup key-derivation सेटिंग मान्य नहीं है" }
        val saltStart = 9
        val salt = header.copyOfRange(saltStart, saltStart + SALT_BYTES)
        val nonce = header.copyOfRange(saltStart + SALT_BYTES, header.size)
        val cipher = cipher(Cipher.DECRYPT_MODE, password, salt, nonce, iterations)
        val temp = File(outputFile.parentFile, outputFile.name + ".partial")
        try {
            CipherInputStream(input, cipher).use { decrypted ->
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(8192)
                    while (true) { val n = decrypted.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                    out.fd.sync()
                }
            }
            require(temp.isFile && temp.length() >= 4L) { "पासवर्ड गलत है या बैकअप खराब है" }
            if (outputFile.exists()) require(outputFile.delete()) { "पुरानी अस्थायी फाइल हटाई नहीं जा सकी" }
            require(temp.renameTo(outputFile)) { "Decrypted backup सुरक्षित नहीं हो सका" }
        } catch (e: Exception) {
            temp.delete()
            if (e is javax.crypto.BadPaddingException || e.cause is AEADBadTagException || e.message?.contains("tag", true) == true) {
                throw IllegalArgumentException("पासवर्ड गलत है या Backup से छेड़छाड़ हुई है", e)
            }
            throw e
        }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, nonce: ByteArray, iterations: Int = ITERATIONS): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            }
        } finally { keyBytes.fill(0) }
    }

    private fun intBytes(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())
    private fun readInt(b: ByteArray, i: Int) = ((b[i].toInt() and 255) shl 24) or ((b[i+1].toInt() and 255) shl 16) or ((b[i+2].toInt() and 255) shl 8) or (b[i+3].toInt() and 255)
}
