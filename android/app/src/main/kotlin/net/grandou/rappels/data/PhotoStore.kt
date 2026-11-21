package net.grandou.rappels.data

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.util.Base64

/**
 * Photos of the items, local files (`filesDir/photos/<name>.jpg`). Only used by the backup for now: the photo
 * feature (capture, thumbnails) will build on it. Names come from untrusted files and are always validated.
 */
object PhotoStore {
    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    private fun file(ctx: Context, name: String): File? = if (isValidPhotoName(name)) File(dir(ctx), name) else null

    /** Base64 content of the photos in [names] that exist on disk. */
    fun readAll(ctx: Context, names: Collection<String>): Map<String, String> =
        names.distinct().mapNotNull { n ->
            file(ctx, n)?.takeIf { it.exists() }?.let { n to Base64.getEncoder().encodeToString(it.readBytes()) }
        }.toMap()

    /** Checks every photo (valid name, real image) without writing anything, and returns their bytes. */
    fun decodeAll(photos: Map<String, String>): Map<String, ByteArray> = photos.mapValues { (name, b64) ->
        if (!isValidPhotoName(name)) throw BackupInvalid("Nom de photo invalide")
        val bytes = try { Base64.getDecoder().decode(b64) } catch (e: IllegalArgumentException) { throw BackupInvalid("Une photo de la sauvegarde est corrompue") }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw BackupInvalid("Une photo de la sauvegarde est illisible")
        bytes
    }

    fun write(ctx: Context, name: String, bytes: ByteArray) {
        (file(ctx, name) ?: throw BackupInvalid("Nom de photo invalide")).writeBytes(bytes)
    }

    /** Deletes the photos no item uses any more (replaced, removed, left over after a restore). */
    fun clean(ctx: Context, used: Set<String>) {
        dir(ctx).listFiles()?.forEach { if (it.name !in used) it.delete() }
    }
}
