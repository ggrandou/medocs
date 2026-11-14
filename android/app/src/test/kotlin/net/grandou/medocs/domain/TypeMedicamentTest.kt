package net.grandou.medocs.domain

import net.grandou.medocs.domain.TypeMedicament.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeMedicamentTest {
    private fun forme(f: String) = TypesMedicament.deduire(f, "unité", "X")

    @Test fun formesRealesDeLaBase() {
        val attendu = mapOf(
            "comprimé pelliculé" to COMPRIME, "comprimé" to COMPRIME, "comprimé sécable" to COMPRIME, "comprimé à libération prolongée" to COMPRIME,
            "comprimé orodispersible" to COMPRIME, "comprimé effervescent(e)" to COMPRIME, "comprimé à croquer" to COMPRIME,
            "comprimé gastro-résistant(e)" to COMPRIME,
            "gélule" to GELULE, "gélule gastro-résistant(e)" to GELULE, "capsule molle" to GELULE, "gélule à libération prolongée" to GELULE,
            "solution injectable" to INJECTION, "solution pour perfusion" to INJECTION, "poudre et  solvant pour solution injectable" to INJECTION,
            "suspension injectable" to INJECTION, "poudre pour solution injectable ou pour perfusion" to INJECTION,
            "solution buvable" to LIQUIDE, "suspension buvable" to LIQUIDE, "sirop" to LIQUIDE, "solution pour application" to LIQUIDE,
            "collyre en solution" to GOUTTES, "solution buvable en gouttes" to GOUTTES, "collyre" to GOUTTES,
            "crème" to TUBE, "gel" to TUBE, "pommade" to TUBE, "gel pour application" to TUBE, "mousse pour application" to TUBE,
            "poudre pour suspension buvable" to SACHET, "poudre pour solution buvable" to SACHET, "granulés" to SACHET,
            "suppositoire" to SUPPOSITOIRE, "ovule" to SUPPOSITOIRE, "suppositoire sécable" to SUPPOSITOIRE,
            "poudre pour inhalation" to SPRAY, "solution pour pulvérisation" to SPRAY, "suspension pour inhalation" to SPRAY,
            "solution pour inhalation par nébuliseur" to SPRAY,
            "dispositif" to GENERIQUE, "vernis à ongles médicamenteux(se)" to GENERIQUE, "film orodispersible" to GENERIQUE,
            "trousse pour préparation radiopharmaceutique" to GENERIQUE,
        )
        for ((f, t) in attendu) assertEquals(f, t, forme(f))
    }

    @Test fun lOrdreDesReglesCompte() {
        // « gélule » contient « gel » : ce n'est pas un tube ; une poudre injectable est une injection, pas un sachet
        assertEquals(GELULE, forme("gélule"))
        assertEquals(INJECTION, forme("poudre pour solution injectable"))
        assertEquals(GOUTTES, forme("solution en gouttes buvable"))
    }

    @Test fun saisieManuelleSansForme() {
        assertEquals(COMPRIME, TypesMedicament.deduire(null, "comprimé", "Mon médicament"))
        assertEquals(COMPRIME, TypesMedicament.deduire(null, "cp", "Mon médicament"))
        assertEquals(LIQUIDE, TypesMedicament.deduire(null, "ml", "Sirop pour la toux"))
        assertEquals(GOUTTES, TypesMedicament.deduire(null, "gouttes", "Vitamine D"))
        assertEquals(SACHET, TypesMedicament.deduire(null, "sachet", "Magnésium"))
        assertEquals(SPRAY, TypesMedicament.deduire(null, "bouffée", "Ventoline"))
        assertEquals(GENERIQUE, TypesMedicament.deduire(null, "unité", "Truc"))
    }

    @Test fun leNomServDeRecoursQuandLaFormeEtLUniteNeDisentRien() {
        assertEquals(COMPRIME, TypesMedicament.deduire(null, "unité", "DOLIPRANE 1000 mg, comprimé"))
        assertEquals(TUBE, TypesMedicament.deduire(null, "unité", "IBUPROFENE ARROW 5 %, gel"))
    }

    @Test fun laFormePrimeSurLUnite() {
        // une gélule comptée en « ml » reste une gélule
        assertEquals(GELULE, TypesMedicament.deduire("gélule", "ml", "X"))
    }

    @Test fun accentsEtCasseSontSansEffet() {
        assertEquals(TUBE, forme("CRÈME")); assertEquals(COMPRIME, forme("COMPRIMÉ")); assertEquals(GELULE, forme("Gélule"))
    }
}
