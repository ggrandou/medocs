package net.grandou.medocs.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import net.grandou.medocs.data.SauvegardeInvalide
import net.grandou.medocs.data.nomPhotoValide
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Photos des boîtes, stockées en local (`filesDir/photos/<uuid>.jpg`). Chaque photo est réduite,
 * redressée selon l'EXIF puis ré-encodée : le fichier final n'a plus aucune métadonnée (position GPS comprise).
 */
object Photos {
    /** Côté le plus long d'une photo enregistrée. */
    const val TAILLE_MAX = 1024
    private const val QUALITE = 82

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(2 * 1024 * 1024)) {
        override fun sizeOf(cle: String, bitmap: Bitmap) = bitmap.byteCount
    }

    private fun dossier(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** Fichier d'une photo, ou null si le nom n'est pas un nom que l'application a pu produire. */
    fun fichier(ctx: Context, nom: String): File? = if (nomPhotoValide(nom)) File(dossier(ctx), nom) else null

    /** Matrice qui redresse l'image selon son orientation EXIF et la ramène à [TAILLE_MAX]. */
    internal fun matrice(orientation: Int, largeur: Int, hauteur: Int): Matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
        }
        val echelle = min(1f, TAILLE_MAX.toFloat() / max(largeur, hauteur))
        if (echelle < 1f) postScale(echelle, echelle)
    }

    /** Importe une image (capture caméra ou galerie). Retourne le nom du fichier créé, ou null si l'image est illisible. */
    fun enregistrer(ctx: Context, uri: Uri): String? {
        val cr = ctx.contentResolver
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // En mode « bornes seules », decodeStream renvoie toujours null : seule la lecture des dimensions compte
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bornes); true } ?: return null
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null

        var echantillon = 1
        while (max(bornes.outWidth, bornes.outHeight) / (echantillon * 2) >= TAILLE_MAX) echantillon *= 2
        val brut = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = echantillon })
        } ?: return null
        val orientation = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL

        val final = Bitmap.createBitmap(brut, 0, 0, brut.width, brut.height, matrice(orientation, brut.width, brut.height), true)
        val nom = "${UUID.randomUUID()}.jpg"
        try {
            File(dossier(ctx), nom).outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, QUALITE, it) }
        } finally {
            if (final !== brut) brut.recycle()
            final.recycle()
        }
        return nom
    }

    /** Vignette de [cote] pixels (côté le plus long, au minimum), en cache mémoire. */
    fun charger(ctx: Context, nom: String, cote: Int): Bitmap? {
        val cle = "$nom@$cote"
        cache.get(cle)?.let { return it }
        val f = fichier(ctx, nom)?.takeIf { it.exists() } ?: return null
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bornes)
        var echantillon = 1
        while (max(bornes.outWidth, bornes.outHeight) / (echantillon * 2) >= cote) echantillon *= 2
        val bitmap = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = echantillon }) ?: return null
        cache.put(cle, bitmap)
        return bitmap
    }

    fun lireOctets(ctx: Context, nom: String): ByteArray? = fichier(ctx, nom)?.takeIf { it.exists() }?.readBytes()

    /** Écrit une photo venant d'une sauvegarde, après avoir vérifié que c'est bien une image. */
    fun ecrireOctets(ctx: Context, nom: String, octets: ByteArray) {
        val f = fichier(ctx, nom) ?: throw SauvegardeInvalide("Nom de photo invalide")
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) throw SauvegardeInvalide("Une photo de la sauvegarde est illisible")
        f.writeBytes(octets)
    }

    /** Supprime les photos que plus aucun médicament n'utilise (remplacées, retirées, abandonnées avant enregistrement). */
    fun nettoyer(ctx: Context, utilisees: Set<String>) {
        dossier(ctx).listFiles()?.forEach { if (it.name !in utilisees) it.delete() }
        cache.evictAll()
    }
}
