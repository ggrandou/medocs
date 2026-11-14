package net.grandou.medocs.bdpm

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.Normalizer

data class SpecialiteBdpm(val cis: Long, val nom: String, val forme: String?, val substances: String?)

data class PresentationBdpm(
    val cip13: String,
    val libelle: String,
    val nbUnites: Double?,
    val unite: String?,
    /** Faux quand le nombre d'unités déduit du libellé est ambigu : à faire vérifier par l'utilisateur. */
    val nbUnitesFiable: Boolean,
)

/** Une présentation retrouvée par son code (scan ou saisie) avec sa spécialité. */
data class TrouvailleBdpm(val specialite: SpecialiteBdpm, val presentation: PresentationBdpm)

/** Retire accents et majuscules, comme le script de construction de la base (colonnes *_recherche). */
fun normaliser(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

/** Accès en lecture seule à la base BDPM embarquée (générée par scripts/build_bdpm.py). */
class Bdpm private constructor(private val db: SQLiteDatabase) {

    /** Recherche par nom commercial et/ou molécule ; tous les mots saisis doivent correspondre. */
    fun rechercher(texte: String, limite: Int = 50): List<SpecialiteBdpm> {
        // Un numéro de 7 chiffres (CIP7) ou de 13 chiffres (CIP13) saisi à la main se retrouve comme un scan
        val numero = texte.filter { !it.isWhitespace() }
        if (numero.length in setOf(7, 13) && numero.all { it in '0'..'9' }) return specialitesParCode(numero)
        val mots = normaliser(texte).split(Regex("\\s+")).map { it.replace(Regex("[%_]"), "") }.filter { it.isNotEmpty() }
        if (mots.isEmpty() || texte.trim().length < 2) return emptyList()
        val conditions = mots.joinToString(" AND ") {
            "(nom_recherche LIKE ? OR COALESCE(substances_recherche, '') LIKE ?)"
        }
        val args = mots.flatMap { listOf("%$it%", "%$it%") } + "${mots.first()}%"
        val sql = """SELECT cis, nom, forme, substances FROM specialite WHERE $conditions
                     ORDER BY (nom_recherche LIKE ?) DESC, nom_recherche LIMIT ${limite.toInt()}"""
        return db.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList {
                while (c.moveToNext()) add(SpecialiteBdpm(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
            }
        }
    }

    private fun specialitesParCode(code: String): List<SpecialiteBdpm> =
        db.rawQuery(
            """SELECT DISTINCT s.cis, s.nom, s.forme, s.substances FROM presentation p JOIN specialite s ON s.cis = p.cis
               WHERE p.cip13 = ? OR p.cip7 = ?""",
            arrayOf(code, code),
        ).use { c -> buildList { while (c.moveToNext()) add(SpecialiteBdpm(c.getLong(0), c.getString(1), c.getString(2), c.getString(3))) } }

    /** La présentation exacte d'un CIP13 (code lu sur la boîte), ou null si elle n'est pas dans la base. */
    fun parCip13(cip13: String): TrouvailleBdpm? =
        db.rawQuery(
            """SELECT s.cis, s.nom, s.forme, s.substances, p.cip13, p.libelle, p.nb_unites, p.unite, p.nb_unites_fiable
               FROM presentation p JOIN specialite s ON s.cis = p.cis WHERE p.cip13 = ?""",
            arrayOf(cip13),
        ).use { c ->
            if (!c.moveToFirst()) null else TrouvailleBdpm(
                SpecialiteBdpm(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)),
                PresentationBdpm(
                    cip13 = c.getString(4), libelle = c.getString(5),
                    nbUnites = if (c.isNull(6)) null else c.getDouble(6), unite = c.getString(7), nbUnitesFiable = c.getInt(8) == 1,
                ),
            )
        }

    fun presentations(cis: Long): List<PresentationBdpm> =
        db.rawQuery(
            "SELECT cip13, libelle, nb_unites, unite, nb_unites_fiable FROM presentation WHERE cis = ? ORDER BY libelle",
            arrayOf(cis.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    PresentationBdpm(
                        cip13 = c.getString(0), libelle = c.getString(1),
                        nbUnites = if (c.isNull(2)) null else c.getDouble(2),
                        unite = c.getString(3), nbUnitesFiable = c.getInt(4) == 1,
                    )
                )
            }
        }

    companion object {
        private const val ASSET = "bdpm.sqlite"

        /**
         * Copie la base des assets vers le stockage interne (une fois par installation ou mise à jour
         * de l'application), puis l'ouvre en lecture seule. À appeler hors du thread principal.
         */
        fun ouvrir(context: Context): Bdpm {
            val prefs = context.getSharedPreferences("bdpm", Context.MODE_PRIVATE)
            val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            val fichier = File(context.filesDir, ASSET)
            if (!fichier.exists() || prefs.getLong("version", -1) != version) {
                val tmp = File(context.filesDir, "$ASSET.tmp")
                context.assets.open(ASSET).use { entree -> tmp.outputStream().use { entree.copyTo(it) } }
                check(tmp.renameTo(fichier)) { "Impossible d'installer la base BDPM" }
                prefs.edit().putLong("version", version).apply()
            }
            return Bdpm(SQLiteDatabase.openDatabase(fichier.path, null, SQLiteDatabase.OPEN_READONLY))
        }
    }
}
