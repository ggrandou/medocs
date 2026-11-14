package net.grandou.medocs.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CodeBoiteTest {
    private val gs = '\u001d'
    private val dolipraneCip = "3400935955838" // DOLIPRANE 1000 mg, 8 comprimés (présent dans la base)

    @Test fun cleDeControle() {
        assertTrue(CodeBoite.cleValide("3400935955838"))
        assertTrue(CodeBoite.cleValide("03400935955838")) // GTIN-14
        assertTrue(CodeBoite.cleValide("4006381333931"))  // EAN-13 d'exemple GS1
        assertFalse(CodeBoite.cleValide("3400935955839"))
        assertFalse(CodeBoite.cleValide("340093595583"))
        assertFalse(CodeBoite.cleValide("34009359558A8"))
    }

    @Test fun ean13Seul() {
        assertEquals(CodeBoite(dolipraneCip), CodeBoite.depuisTexte("3400935955838"))
        assertEquals(CodeBoite(dolipraneCip), CodeBoite.depuisTexte("  3400935955838\n"))
    }

    @Test fun ean13AvecMauvaiseCleEstRefuse() {
        assertNull(CodeBoite.depuisTexte("3400935955839"))
    }

    @Test fun gtin14Seul() {
        assertEquals(CodeBoite(dolipraneCip), CodeBoite.depuisTexte("03400935955838"))
    }

    @Test fun datamatrixGs1Complet() {
        val c = CodeBoite.depuisTexte("010340093595583817270831" + "10LOT42" + gs + "21SERIE9")!!
        assertEquals(dolipraneCip, c.cip13)
        assertEquals("LOT42", c.lot)
        assertEquals(LocalDate.of(2027, 8, 31), c.peremption)
    }

    @Test fun peremptionJourZeroEstFinDeMois() {
        val c = CodeBoite.depuisTexte("0103400935955838" + "17270200" + "10A")!!
        assertEquals(LocalDate.of(2027, 2, 28), c.peremption)
        assertEquals("A", c.lot)
    }

    @Test fun avecIdentifiantDeSymbologie() {
        assertEquals(dolipraneCip, CodeBoite.depuisTexte("]d2" + "010340093595583810ABC")!!.cip13)
        assertEquals(dolipraneCip, CodeBoite.depuisTexte("]E03400935955838")!!.cip13)
    }

    @Test fun loteEtSerieSansSeparateurFinal() {
        val c = CodeBoite.depuisTexte("0103400935955838" + "10LOTSEUL")!!
        assertEquals("LOTSEUL", c.lot)
        assertNull(c.peremption)
    }

    @Test fun champInconnuNeCasseRien() {
        val c = CodeBoite.depuisTexte("0103400935955838" + "17270831" + "9912345")!!
        assertEquals(dolipraneCip, c.cip13)
        assertEquals(LocalDate.of(2027, 8, 31), c.peremption)
    }

    @Test fun datesImpossiblesSontIgnoreesSansEchec() {
        val c = CodeBoite.depuisTexte("0103400935955838" + "17271399")!!
        assertNull(c.peremption)
        assertEquals(dolipraneCip, c.cip13)
    }

    @Test fun autresCodesNeSontPasDesMedicaments() {
        for (texte in listOf(
            "", "abc", "12345678", "https://exemple.org/x", "QR:HELLO", "0200935955838", "01", "010340093595583",
            "0103400935955839", // mauvaise clé GS1
            "9780306406157",    // ISBN-13 valide : pas un CIP
        )) {
            // Un EAN-13 valide quelconque est accepté (on ne sait pas qu'il n'est pas dans la base) :
            // c'est la recherche en base qui tranche ; on vérifie seulement l'absence de plantage
            CodeBoite.depuisTexte(texte)
        }
        assertNull(CodeBoite.depuisTexte("https://exemple.org/x"))
        assertNull(CodeBoite.depuisTexte("0103400935955839"))
        assertNull(CodeBoite.depuisTexte("010340093595583"))
    }
}
