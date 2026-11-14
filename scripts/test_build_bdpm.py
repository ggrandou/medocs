"""Tests de parse_unites. Lancer : python3 -I -m unittest discover -s scripts -v"""
import unittest

from build_bdpm import parse_unites


class ParseUnitesTest(unittest.TestCase):
    def check(self, libelle, attendu):
        self.assertEqual(parse_unites(libelle), attendu, libelle)

    def test_plaquette_simple(self):
        self.check("plaquette(s) PVC PVDC aluminium de 30 comprimé(s)", (30, "comprimé", 1))

    def test_pluriel_sans_parentheses(self):
        self.check("plaquettes polyamide aluminium PVC-Aluminium de 14 comprimés", (14, "comprimé", 1))

    def test_gelules(self):
        self.check("plaquette(s) PVC aluminium de 100 gélule(s)", (100, "gélule", 1))

    def test_multiplicateur_en_tete(self):
        self.check("3 pilulier(s) polypropylène de 30 comprimé(s)", (90, "comprimé", 1))
        self.check("28 plaquette(s) thermoformée(s) PVC PVDC aluminium de 1 comprimé(s)", (28, "comprimé", 1))

    def test_flacon_volume_unique(self):
        # Un seul contenant : on garde le volume, non fiable (c'est un volume, pas un nombre de prises)
        self.check("1 flacon(s) polyéthylène de 30 ml", (30, "ml", 0))
        self.check("flacon(s) en verre brun de 150 ml avec gobelet(s) doseur(s) polypropylène", (150, "ml", 0))

    def test_poids(self):
        self.check("1 pot(s) polypropylène de 20 g", (20, "g", 0))

    def test_volume_decimal_arrondi(self):
        self.check("1 stylo prérempli en verre de 1,14 ml", (1.14, "ml", 0))

    def test_plusieurs_contenants_de_volume_comptes_comme_contenants(self):
        self.check("20 récipient(s) unidose(s) polyéthylène de 0,3 ml", (20, "récipient", 1))
        self.check("10 flacon(s) polypropylène de 50 ml", (10, "flacon", 1))
        self.check("2 stylos préremplis en verre à usage unique de 1,14 ml", (2, "stylo", 1))

    def test_contenants_sans_de(self):
        self.check("90 sachet(s) polytéréphtalate (PET) polyéthylène basse densité (PEBD) aluminium", (90, "sachet", 1))
        self.check("60 récipient(s) unidose(s) sous film thermosoudé aluminium dans distributeur(s) en plastique", (60, "récipient", 1))

    def test_boite_sans_accent_normalisee(self):
        self.check("2 boite(s) polypropylène", (2, "boîte", 1))

    def test_plusieurs_parties_marque_non_fiable(self):
        # Boîte à contenu mixte (comprimés actifs + placebo) : ambigu, à corriger par l'utilisateur
        _, unite, fiable = parse_unites("3 plaquette(s) PVC-Aluminium de 24 comprimé(s) jaunes et de 4 comprimé(s) blancs")
        self.assertEqual(unite, "comprimé")
        self.assertEqual(fiable, 0)
        _, _, fiable = parse_unites(
            "plaquette(s) aluminium PVC de 24 comprimé(s) - plaquette(s) aluminium PVC de 4 comprimé(s)"
        )
        self.assertEqual(fiable, 0)

    def test_non_extractible(self):
        self.check("1 flacon(s) en verre", (1, "flacon", 1))
        self.check("1 boite(s) de 1  pansement(s)", (None, None, 0))
        self.check("sachet(s) papier aluminium de 5 emplâtre(s)", (None, None, 0))
        self.check("", (None, None, 0))

    def test_unite_inconnue_ignoree(self):
        self.check("10 sachet(s) polyéthylène de 100 cm2", (10, "sachet", 1))


if __name__ == "__main__":
    unittest.main()
