package org.medocs.rappel.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.medocs.rappel.data.SauvegardeInvalide
import org.medocs.rappel.data.nomPhotoValide
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotosTest {
    private lateinit var ctx: Context
    private val sources = mutableListOf<File>()

    @Before fun setUp() { ctx = ApplicationProvider.getApplicationContext(); Photos.nettoyer(ctx, emptySet()) }
    @After fun tearDown() { Photos.nettoyer(ctx, emptySet()); sources.forEach { it.delete() } }

    /** JPEG de test : moitié haute rouge, moitié basse bleue, avec une orientation EXIF et une position GPS. */
    private fun jpegSource(largeur: Int, hauteur: Int, orientation: Int): Uri {
        val b = Bitmap.createBitmap(largeur, hauteur, Bitmap.Config.ARGB_8888)
        for (y in 0 until hauteur) for (x in 0 until largeur) b.setPixel(x, y, if (y < hauteur / 2) Color.RED else Color.BLUE)
        val f = File(ctx.cacheDir, "source-${System.nanoTime()}.jpg").also(sources::add)
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(f.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setLatLong(48.8566, 2.3522)
            saveAttributes()
        }
        return Uri.fromFile(f)
    }

    private fun dimensions(nom: String): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(Photos.fichier(ctx, nom)!!.path, o)
        return o.outWidth to o.outHeight
    }

    @Test fun reduitUneGrandePhotoEtLaNomme() {
        val nom = Photos.enregistrer(ctx, jpegSource(3000, 2000, ExifInterface.ORIENTATION_NORMAL))!!
        assertTrue(nom, nomPhotoValide(nom))
        val (l, h) = dimensions(nom)
        assertEquals(Photos.TAILLE_MAX, maxOf(l, h))
        assertEquals(3000f / 2000f, l.toFloat() / h, 0.02f) // proportions conservées
    }

    @Test fun neGrossitPasUnePetitePhoto() {
        val nom = Photos.enregistrer(ctx, jpegSource(300, 200, ExifInterface.ORIENTATION_NORMAL))!!
        assertEquals(300 to 200, dimensions(nom))
    }

    @Test fun redresseSelonLOrientationExif() {
        // 600x400 « tourné de 90° » : l'image affichée est 400x600, dessus rouge à l'origine => le rouge passe à droite
        val nom = Photos.enregistrer(ctx, jpegSource(600, 400, ExifInterface.ORIENTATION_ROTATE_90))!!
        assertEquals(400 to 600, dimensions(nom))
        val b = BitmapFactory.decodeFile(Photos.fichier(ctx, nom)!!.path)
        val droite = b.getPixel(b.width - 10, b.height / 2)
        val gauche = b.getPixel(10, b.height / 2)
        assertTrue("rouge à droite", Color.red(droite) > 200 && Color.blue(droite) < 80)
        assertTrue("bleu à gauche", Color.blue(gauche) > 200 && Color.red(gauche) < 80)
    }

    @Test fun supprimeLesMetadonneesDontLaPositionGps() {
        val nom = Photos.enregistrer(ctx, jpegSource(800, 600, ExifInterface.ORIENTATION_ROTATE_90))!!
        val exif = ExifInterface(Photos.fichier(ctx, nom)!!.path)
        assertNull(exif.latLong)
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED))
    }

    @Test fun refuseUnFichierQuiNEstPasUneImage() {
        val f = File(ctx.cacheDir, "texte.jpg").also(sources::add).apply { writeText("pas une image") }
        assertNull(Photos.enregistrer(ctx, Uri.fromFile(f)))
    }

    @Test fun vignetteEnCacheEtPlusPetite() {
        val nom = Photos.enregistrer(ctx, jpegSource(3000, 2000, ExifInterface.ORIENTATION_NORMAL))!!
        val v = Photos.charger(ctx, nom, 128)!!
        assertTrue(maxOf(v.width, v.height) in 128..256)
        assertTrue(Photos.charger(ctx, nom, 128) === v) // deuxième appel servi par le cache
    }

    @Test fun nettoyerNeGardeQueLesPhotosUtilisees() {
        val a = Photos.enregistrer(ctx, jpegSource(200, 200, ExifInterface.ORIENTATION_NORMAL))!!
        val b = Photos.enregistrer(ctx, jpegSource(200, 200, ExifInterface.ORIENTATION_NORMAL))!!
        Photos.nettoyer(ctx, setOf(a))
        assertTrue(Photos.fichier(ctx, a)!!.exists())
        assertTrue(!Photos.fichier(ctx, b)!!.exists())
    }

    @Test fun ecritureDepuisUneSauvegardeVerifieLeNomEtLeContenu() {
        val octets = ByteArrayOutputStream().also { Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        Photos.ecrireOctets(ctx, "ok.jpg", octets)
        assertNotNull(Photos.lireOctets(ctx, "ok.jpg"))
        try { Photos.ecrireOctets(ctx, "../evil.jpg", octets); fail("chemin accepté") } catch (_: SauvegardeInvalide) {}
        try { Photos.ecrireOctets(ctx, "texte.jpg", "pas une image".toByteArray()); fail("contenu accepté") } catch (_: SauvegardeInvalide) {}
        assertNull(Photos.fichier(ctx, "../evil.jpg"))
    }
}
