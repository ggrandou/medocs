package net.grandou.medocs.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun quantites() {
        assertEquals("30", formaterQuantite(30.0))
        assertEquals("0,5", formaterQuantite(0.5))
        assertEquals("1,25", formaterQuantite(1.25))
    }

    @Test fun pluriels() {
        assertEquals("1 comprimé", formaterAvecUnite(1.0, "comprimé"))
        assertEquals("0,5 comprimé", formaterAvecUnite(0.5, "comprimé"))
        assertEquals("2 comprimés", formaterAvecUnite(2.0, "comprimé"))
    }

    @Test fun plurielsDesValeursNegatives() {
        assertEquals("-7 comprimés", formaterAvecUnite(-7.0, "comprimé"))
        assertEquals("-1 comprimé", formaterAvecUnite(-1.0, "comprimé"))
    }

    @Test fun unitesInvariables() {
        assertEquals("50 ml", formaterAvecUnite(50.0, "ml"))
        assertEquals("3 gouttes", formaterAvecUnite(3.0, "gouttes"))
    }

    @Test fun saisieDecimale() {
        assertEquals(0.5, lireDecimal("0,5")!!, 0.0)
        assertEquals(2.0, lireDecimal(" 2.0 ")!!, 0.0)
        assertEquals(null, lireDecimal("abc"))
    }
}
