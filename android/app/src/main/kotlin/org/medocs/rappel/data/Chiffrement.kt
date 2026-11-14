package org.medocs.rappel.data

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MotDePasseIncorrect : Exception("Mot de passe incorrect ou fichier modifié")

/**
 * Chiffrement d'un fichier de sauvegarde par mot de passe : PBKDF2-HMAC-SHA256 → clé AES-256,
 * AES-GCM (l'authentification détecte aussi toute modification du fichier).
 * Format : "MEDOCS1" | sel (16) | IV (12) | texte chiffré + étiquette GCM.
 */
object Chiffrement {
    private val MAGIE = "MEDOCS1".toByteArray(Charsets.US_ASCII)
    private const val SEL = 16
    private const val IV = 12
    private const val ITERATIONS = 210_000

    private fun cle(motDePasse: CharArray, sel: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(motDePasse, sel, ITERATIONS, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    fun chiffrer(clair: ByteArray, motDePasse: CharArray): ByteArray {
        val alea = SecureRandom()
        val sel = ByteArray(SEL).also(alea::nextBytes)
        val iv = ByteArray(IV).also(alea::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, cle(motDePasse, sel), GCMParameterSpec(128, iv)) }
        c.updateAAD(MAGIE)
        return MAGIE + sel + iv + c.doFinal(clair)
    }

    fun dechiffrer(fichier: ByteArray, motDePasse: CharArray): ByteArray {
        if (fichier.size < MAGIE.size + SEL + IV + 16 || !fichier.copyOfRange(0, MAGIE.size).contentEquals(MAGIE)) {
            throw SauvegardeInvalide("Ce fichier n'est pas une sauvegarde chiffrée de l'application")
        }
        val sel = fichier.copyOfRange(MAGIE.size, MAGIE.size + SEL)
        val iv = fichier.copyOfRange(MAGIE.size + SEL, MAGIE.size + SEL + IV)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, cle(motDePasse, sel), GCMParameterSpec(128, iv)) }
        c.updateAAD(MAGIE)
        try {
            return c.doFinal(fichier, MAGIE.size + SEL + IV, fichier.size - MAGIE.size - SEL - IV)
        } catch (e: AEADBadTagException) {
            throw MotDePasseIncorrect()
        }
    }
}
