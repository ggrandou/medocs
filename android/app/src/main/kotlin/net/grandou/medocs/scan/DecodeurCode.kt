package net.grandou.medocs.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/** Image en niveaux de gris, sans rembourrage : [octets] a exactement [largeur] x [hauteur] éléments. */
class PlanGris(val octets: ByteArray, val largeur: Int, val hauteur: Int) {
    /** Rotation d'un quart de tour dans le sens horaire. */
    fun quartHoraire(): PlanGris {
        val tourne = ByteArray(octets.size)
        for (y in 0 until hauteur) for (x in 0 until largeur) tourne[x * hauteur + (hauteur - 1 - y)] = octets[y * largeur + x]
        return PlanGris(tourne, hauteur, largeur)
    }

    companion object {
        /** Compacte un plan de caméra dont les lignes sont rembourrées jusqu'à [pasLigne] octets. */
        fun depuis(plan: ByteArray, pasLigne: Int, largeur: Int, hauteur: Int): PlanGris {
            if (pasLigne == largeur) return PlanGris(plan.copyOf(largeur * hauteur), largeur, hauteur)
            val compact = ByteArray(largeur * hauteur)
            for (y in 0 until hauteur) System.arraycopy(plan, y * pasLigne, compact, y * largeur, largeur)
            return PlanGris(compact, largeur, hauteur)
        }
    }
}

/**
 * Lit EAN-13 et Datamatrix dans une image de caméra. ZXing ne sait pas pivoter une image de la caméra
 * (`PlanarYUVLuminanceSource` ne gère pas la rotation) : ce décodeur redresse lui-même l'image, puis réessaie
 * à 90° pour les codes-barres 1D tenus dans l'autre sens. Une instance n'est pas thread-safe.
 */
class DecodeurCode {
    private val lecteur = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.DATA_MATRIX),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.ALSO_INVERTED to true, // Datamatrix clair sur fond foncé (gravure, emballages sombres)
            )
        )
    }

    /**
     * @param plan luminance (plan Y de la caméra), [pasLigne] octets par ligne (≥ [largeur] : rembourrage)
     * @param rotationDegres rotation à appliquer pour que l'image soit droite (`imageInfo.rotationDegrees`)
     * @return le texte du code, ou null si aucun code lisible dans cette image
     */
    fun decoder(plan: ByteArray, pasLigne: Int, largeur: Int, hauteur: Int, rotationDegres: Int = 0): String? {
        var image = PlanGris.depuis(plan, pasLigne, largeur, hauteur)
        repeat(((rotationDegres / 90) % 4 + 4) % 4) { image = image.quartHoraire() }
        return lire(image) ?: lire(image.quartHoraire())
    }

    private fun lire(image: PlanGris): String? = try {
        val source = PlanarYUVLuminanceSource(image.octets, image.largeur, image.hauteur, 0, 0, image.largeur, image.hauteur, false)
        lecteur.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (e: ReaderException) {
        null
    } finally {
        lecteur.reset()
    }
}
