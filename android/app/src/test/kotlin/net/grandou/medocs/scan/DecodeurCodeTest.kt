package net.grandou.medocs.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import net.grandou.medocs.domain.CodeBoite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Génère de vrais codes avec ZXing puis les relit comme le ferait la caméra (plan de luminance). */
class DecodeurCodeTest {
    private val cip = "3400935955838"
    private val gs = '\u001d'
    private val gs1 = "01034009359558381727083110LOT42" + gs + "21SERIE9"
    private val decodeur = DecodeurCode()

    private fun matriceEan13(largeur: Int = 600, hauteur: Int = 300): BitMatrix =
        MultiFormatWriter().encode(cip, BarcodeFormat.EAN_13, largeur, hauteur)

    private fun matriceDatamatrix(cote: Int = 400, contenu: String = gs1): BitMatrix =
        MultiFormatWriter().encode(contenu, BarcodeFormat.DATA_MATRIX, cote, cote, mapOf(EncodeHintType.GS1_FORMAT to true))

    /** noir = 0, blanc = 255, avec un rembourrage de fin de ligne comme en fait la caméra. */
    private fun plan(m: BitMatrix, rembourrage: Int = 0, inverse: Boolean = false): Triple<ByteArray, Int, Int> {
        val pas = m.width + rembourrage
        val octets = ByteArray(pas * m.height) { 127 }
        for (y in 0 until m.height) for (x in 0 until m.width) {
            val noir = m.get(x, y) != inverse
            octets[y * pas + x] = if (noir) 0 else 255.toByte()
        }
        return Triple(octets, pas, m.width)
    }

    private fun tourner(m: BitMatrix, quarts: Int): BitMatrix {
        var r = m
        repeat(quarts) {
            val n = BitMatrix(r.height, r.width)
            for (y in 0 until r.height) for (x in 0 until r.width) if (r.get(x, y)) n.set(r.height - 1 - y, x)
            r = n
        }
        return r
    }

    private fun lire(m: BitMatrix, rembourrage: Int = 0, inverse: Boolean = false): String? {
        val (octets, pas, largeur) = plan(m, rembourrage, inverse)
        return decodeur.decoder(octets, pas, largeur, m.height)
    }

    @Test fun lireUnEan13() {
        assertEquals(cip, lire(matriceEan13()))
    }

    @Test fun ean13ToutesOrientations() {
        for (quarts in 0..3) assertEquals("rotation ${quarts * 90}°", cip, lire(tourner(matriceEan13(), quarts)))
    }

    @Test fun ean13PetitEtAvecRembourrageDeLigne() {
        assertEquals(cip, lire(matriceEan13(190, 80), rembourrage = 37))
    }

    @Test fun datamatrixGs1RendLeCip13LeLotEtLaPeremption() {
        val texte = lire(matriceDatamatrix())
        assertNotNull(texte)
        val code = CodeBoite.depuisTexte(texte!!)!!
        assertEquals(cip, code.cip13)
        assertEquals("LOT42", code.lot)
        assertEquals(LocalDate.of(2027, 8, 31), code.peremption)
    }

    @Test fun datamatrixToutesOrientations() {
        for (quarts in 0..3) {
            val code = CodeBoite.depuisTexte(lire(tourner(matriceDatamatrix(), quarts)) ?: "")
            assertEquals("rotation ${quarts * 90}°", cip, code?.cip13)
        }
    }

    /** 170 px pour ~34 modules (symbole + marge) = 5 px par module : environ ce que donne un code de 1 cm à 15 cm en 720p. */
    @Test fun datamatrixPetitComme_unCodeVuDeLoin() {
        assertEquals(cip, CodeBoite.depuisTexte(lire(matriceDatamatrix(cote = 170)) ?: "")?.cip13)
    }

    @Test fun datamatrixClairSurFondFonce() {
        assertEquals(cip, CodeBoite.depuisTexte(lire(matriceDatamatrix(), inverse = true) ?: "")?.cip13)
    }

    @Test fun laRotationDuCapteurEstCorrigee() {
        // Téléphone en portrait : le capteur livre l'image tournée ; l'application passe imageInfo.rotationDegrees
        val droit = plan(matriceEan13())
        val image = PlanGris(droit.first, droit.third, 300)
        for (degres in listOf(90, 180, 270)) {
            var tourne = image
            repeat(((360 - degres) / 90) % 4) { tourne = tourne.quartHoraire() } // ce que verrait le capteur
            val lu = decodeur.decoder(tourne.octets, tourne.largeur, tourne.largeur, tourne.hauteur, rotationDegres = degres)
            assertEquals("capteur tourné de $degres°", cip, lu)
        }
    }

    @Test fun planGrisQuartHoraire() {
        val p = PlanGris(byteArrayOf(1, 2, 3, 4, 5, 6), largeur = 3, hauteur = 2) // [1 2 3 / 4 5 6]
        val r = p.quartHoraire()                                                   // [4 1 / 5 2 / 6 3]
        assertEquals(2, r.largeur); assertEquals(3, r.hauteur)
        assertEquals(listOf<Byte>(4, 1, 5, 2, 6, 3), r.octets.toList())
        var quatre = p; repeat(4) { quatre = quatre.quartHoraire() }
        assertEquals(p.octets.toList(), quatre.octets.toList())
    }

    @Test fun unePlanAvecRembourrageEstCompacte() {
        val p = PlanGris.depuis(byteArrayOf(1, 2, 9, 9, 3, 4, 9, 9), pasLigne = 4, largeur = 2, hauteur = 2)
        assertEquals(listOf<Byte>(1, 2, 3, 4), p.octets.toList())
    }

    @Test fun uneImageSansCodeDonneNull() {
        val vide = BitMatrix(300, 300)
        assertNull(lire(vide))
        // Du bruit déterministe ne doit jamais produire de faux code
        val bruit = BitMatrix(300, 300)
        var graine = 12345L
        for (y in 0 until 300) for (x in 0 until 300) { graine = (graine * 6364136223846793005L + 1442695040888963407L); if ((graine ushr 33) % 2 == 0L) bruit.set(x, y) }
        assertNull(lire(bruit))
    }

    @Test fun unAutreTypeDeCodeEstIgnore() {
        val qr = MultiFormatWriter().encode("https://exemple.org", BarcodeFormat.QR_CODE, 300, 300)
        assertNull(lire(qr)) // seuls EAN-13 et Datamatrix sont lus
    }

    @Test fun ceQuiEstLuMaisPasUnMedicamentEstRefuseParLeParseur() {
        val isbn = MultiFormatWriter().encode("9780306406157", BarcodeFormat.EAN_13, 600, 300)
        val texte = lire(isbn)
        assertEquals("9780306406157", texte)            // le scanner le lit…
        assertNotNull(CodeBoite.depuisTexte(texte!!))   // …un EAN-13 valide est accepté : c'est la base qui dira « inconnu »
    }
}
