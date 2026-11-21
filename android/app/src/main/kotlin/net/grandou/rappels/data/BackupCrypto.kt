package net.grandou.rappels.data

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The backup file is not usable (not a backup, unsupported version, inconsistent content). */
class BackupInvalid(message: String) : Exception(message)

/** Wrong password, or file modified: AES-GCM authentication cannot tell them apart. */
class WrongPassword : Exception("Wrong password or modified file")

/**
 * Password encryption of a backup file: PBKDF2-HMAC-SHA256 → AES-256 key, AES-GCM (the authentication tag also
 * detects any modification of the file). Format: magic | salt (16) | IV (12) | ciphertext + GCM tag.
 *
 * Two magics are understood. [MAGIC] is the current format; [LEGACY_MAGIC] is the v1 app ("Mes Médocs"), whose
 * parameters are identical, so that its files can still be read.
 */
object BackupCrypto {
    val MAGIC = "RAPPELS1".toByteArray(Charsets.US_ASCII)
    val LEGACY_MAGIC = "MEDOCS1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val IV = 12
    private const val TAG = 16
    private const val ITERATIONS = 210_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        c.updateAAD(MAGIC)
        return MAGIC + salt + iv + c.doFinal(plain)
    }

    /** The magic of [file] if it is one we know, null otherwise. */
    fun magicOf(file: ByteArray): ByteArray? = listOf(MAGIC, LEGACY_MAGIC)
        .firstOrNull { m -> file.size >= m.size + SALT + IV + TAG && file.copyOfRange(0, m.size).contentEquals(m) }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val magic = magicOf(file) ?: throw BackupInvalid("Ce fichier n'est pas une sauvegarde chiffrée de l'application")
        val salt = file.copyOfRange(magic.size, magic.size + SALT)
        val iv = file.copyOfRange(magic.size + SALT, magic.size + SALT + IV)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv)) }
        c.updateAAD(magic)
        try {
            return c.doFinal(file, magic.size + SALT + IV, file.size - magic.size - SALT - IV)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
    }
}
