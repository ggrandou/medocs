package net.grandou.medocs.bdpm

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Recherche par code sur la VRAIE base embarquée. */
@RunWith(AndroidJUnit4::class)
class BdpmCodesTest {
    private val bdpm = Bdpm.ouvrir(ApplicationProvider.getApplicationContext())

    @Test fun cip13RetrouveLaPresentationExacte() {
        val t = bdpm.parCip13("3400935955838")!!
        assertEquals("DOLIPRANE 1000 mg, comprimé", t.specialite.nom)
        assertEquals(8.0, t.presentation.nbUnites!!, 0.0)
        assertEquals("comprimé", t.presentation.unite)
        assertTrue(t.presentation.nbUnitesFiable)
        assertNotNull(t.specialite.substances)
    }

    @Test fun codeAbsentDeLaBase() {
        assertNull(bdpm.parCip13("3400000000000"))
    }

    @Test fun laRechercheAccepteUnCip13OuUnCip7Saisi() {
        assertEquals("DOLIPRANE 1000 mg, comprimé", bdpm.rechercher("3400935955838").single().nom)
        assertEquals("DOLIPRANE 1000 mg, comprimé", bdpm.rechercher("3595583").single().nom)
        assertEquals("DOLIPRANE 1000 mg, comprimé", bdpm.rechercher(" 340 093 595 583 8 ").single().nom) // espaces tolérés
        assertTrue(bdpm.rechercher("3400000000000").isEmpty())
    }

    @Test fun laRechercheParNomResteInchangee() {
        assertTrue(bdpm.rechercher("doliprane").size > 3)
    }
}
