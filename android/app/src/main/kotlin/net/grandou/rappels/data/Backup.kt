package net.grandou.rappels.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Complete snapshot of the user's data, with the original identifiers (the links depend on them). */
data class BackupData(
    val items: List<Item>,
    val schedules: List<Schedule>,
    val scheduleTimes: List<ScheduleTime>,
    val occurrences: List<Occurrence>,
    val stockMovements: List<StockMovement>,
    /** Photos of the items: file name → JPEG content in Base64. */
    val photos: Map<String, String> = emptyMap(),
) {
    companion object {
        val EMPTY = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Where a backup file comes from: this app, or its predecessor "Mes Médocs" (converted on reading). */
enum class BackupSource { CURRENT, V1_MEDOCS }

/** A backup read from a file: its [data] (already converted to the current model), its origin and its date, if known. */
data class ParsedBackup(val data: BackupData, val source: BackupSource, val createdAt: Long?)

/** Photo names come from an untrusted file: letters, digits, "-" and "_" only, never a path. */
private val PHOTO_NAME = Regex("[A-Za-z0-9_-]{1,64}\\.jpg")
fun isValidPhotoName(name: String): Boolean = PHOTO_NAME.matches(name)

/**
 * JSON serialization of a backup. Writes the current format ("rappels"); reads it and the v1 format ("medocs",
 * French field names), which is converted to the current model: the v1 app's files stay restorable.
 */
object BackupJson {
    /** Version of the file format (independent of the Room schema version). */
    const val VERSION = 1
    private const val FORMAT = "rappels"
    private const val FORMAT_V1 = "medocs"
    private const val VERSION_V1 = 1

    fun toJson(d: BackupData, createdAt: Long): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("createdAt", createdAt)
        put("items", JSONArray().also { a -> d.items.forEach { a.put(item(it)) } })
        put("schedules", JSONArray().also { a -> d.schedules.forEach { a.put(schedule(it)) } })
        put("scheduleTimes", JSONArray().also { a -> d.scheduleTimes.forEach { a.put(scheduleTime(it)) } })
        put("occurrences", JSONArray().also { a -> d.occurrences.forEach { a.put(occurrence(it)) } })
        put("stockMovements", JSONArray().also { a -> d.stockMovements.forEach { a.put(movement(it)) } })
        put("photos", JSONObject().also { p -> d.photos.forEach { (name, b64) -> p.put(name, b64) } })
    }.toString()

    fun parse(text: String): ParsedBackup {
        val o = try { JSONObject(text) } catch (e: Exception) { throw BackupInvalid("Fichier illisible") }
        return try {
            when (o.optString("format")) {
                FORMAT -> {
                    if (o.optInt("version") != VERSION) throw BackupInvalid("Version de sauvegarde non prise en charge : ${o.optInt("version")}")
                    ParsedBackup(readCurrent(o), BackupSource.CURRENT, o.longOrNull("createdAt"))
                }
                FORMAT_V1 -> {
                    if (o.optInt("version") != VERSION_V1) throw BackupInvalid("Version de sauvegarde non prise en charge : ${o.optInt("version")}")
                    ParsedBackup(readV1(o), BackupSource.V1_MEDOCS, null)
                }
                else -> throw BackupInvalid("Ce fichier n'est pas une sauvegarde de l'application")
            }.also { verify(it.data) }
        } catch (e: BackupInvalid) {
            throw e
        } catch (e: Exception) {
            throw BackupInvalid("Sauvegarde corrompue : ${e.message}")
        }
    }

    /** Foreign keys must all point to existing rows, otherwise the insertion would fail halfway. */
    private fun verify(d: BackupData) {
        val items = d.items.map { it.id }.toSet()
        val schedules = d.schedules.map { it.id }.toSet()
        val times = d.scheduleTimes.map { it.id }.toSet()
        val occurrences = d.occurrences.map { it.id }.toSet()
        fun ensure(ok: Boolean, what: String) { if (!ok) throw BackupInvalid("Sauvegarde incohérente : $what") }
        d.photos.keys.forEach { ensure(isValidPhotoName(it), "nom de photo invalide") }
        d.schedules.forEach { ensure(it.itemId in items, "rythme ${it.id} sans élément") }
        d.scheduleTimes.forEach { ensure(it.scheduleId in schedules, "horaire ${it.id} sans rythme") }
        d.occurrences.forEach {
            ensure(it.itemId in items, "occurrence ${it.id} sans élément")
            ensure(it.scheduleTimeId == null || it.scheduleTimeId in times, "occurrence ${it.id} sans horaire")
        }
        d.stockMovements.forEach {
            ensure(it.itemId in items, "mouvement ${it.id} sans élément")
            ensure(it.occurrenceId == null || it.occurrenceId in occurrences, "mouvement ${it.id} sans occurrence")
        }
        ensure(d.occurrences.mapNotNull { o -> o.scheduleTimeId?.let { it to o.scheduledDate } }.let { it.size == it.toSet().size }, "occurrence en double")
    }

    // --- Current format ---

    private fun readCurrent(o: JSONObject): BackupData {
        val photos = readPhotos(o)
        return BackupData(
            // A missing photo is not an error: the item just loses its thumbnail
            items = o.getJSONArray("items").objects().map(::readItem).map {
                if (it.photoPath != null && it.photoPath !in photos) it.copy(photoPath = null) else it
            },
            schedules = o.getJSONArray("schedules").objects().map(::readSchedule),
            scheduleTimes = o.getJSONArray("scheduleTimes").objects().map(::readScheduleTime),
            occurrences = o.getJSONArray("occurrences").objects().map(::readOccurrence),
            stockMovements = o.getJSONArray("stockMovements").objects().map(::readMovement),
            photos = photos,
        )
    }

    private fun readPhotos(o: JSONObject): Map<String, String> =
        o.optJSONObject("photos")?.let { p -> p.keys().asSequence().associateWith { p.getString(it) } } ?: emptyMap()

    private fun item(e: Item) = JSONObject().put("id", e.id).put("name", e.name).put("type", e.type).put("unit", e.unit)
        .put("packaging", e.packaging).put("unitsPerPackage", e.unitsPerPackage).put("trackStock", e.trackStock)
        .put("thresholdDays", e.thresholdDays).put("thresholdUnits", e.thresholdUnits).put("photoPath", e.photoPath)
        .put("reference", e.reference).put("composition", e.composition).put("form", e.form).put("archived", e.archived)

    private fun readItem(o: JSONObject) = Item(
        id = o.getLong("id"), name = o.getString("name"), type = o.stringOrNull("type") ?: "", unit = o.getString("unit"),
        packaging = o.stringOrNull("packaging"), unitsPerPackage = o.doubleOrNull("unitsPerPackage"),
        trackStock = o.getBoolean("trackStock"), thresholdDays = o.getInt("thresholdDays"), thresholdUnits = o.doubleOrNull("thresholdUnits"),
        photoPath = o.stringOrNull("photoPath"), reference = o.stringOrNull("reference"), composition = o.stringOrNull("composition"),
        form = o.stringOrNull("form"), archived = o.getBoolean("archived"),
    )

    private fun schedule(s: Schedule) = JSONObject().put("id", s.id).put("itemId", s.itemId).put("recurrence", s.recurrence.name)
        .put("interval", s.interval).put("intervalUnit", s.intervalUnit.name).put("weekdays", s.weekdays).put("rank", s.rank)
        .put("activeDays", s.activeDays).put("pauseDays", s.pauseDays).put("startDate", s.startDate.toString())
        .put("endDate", s.endDate?.toString()).put("active", s.active)

    private fun readSchedule(o: JSONObject) = Schedule(
        id = o.getLong("id"), itemId = o.getLong("itemId"), recurrence = Recurrence.valueOf(o.getString("recurrence")),
        interval = o.getInt("interval"), intervalUnit = IntervalUnit.valueOf(o.getString("intervalUnit")), weekdays = o.getInt("weekdays"),
        rank = o.intOrNull("rank"), activeDays = o.intOrNull("activeDays"), pauseDays = o.intOrNull("pauseDays"),
        startDate = LocalDate.parse(o.getString("startDate")), endDate = o.stringOrNull("endDate")?.let(LocalDate::parse),
        active = o.getBoolean("active"),
    )

    private fun scheduleTime(t: ScheduleTime) = JSONObject().put("id", t.id).put("scheduleId", t.scheduleId)
        .put("time", t.time.toString()).put("quantity", t.quantity)

    private fun readScheduleTime(o: JSONObject) = ScheduleTime(
        id = o.getLong("id"), scheduleId = o.getLong("scheduleId"), time = LocalTime.parse(o.getString("time")), quantity = o.doubleOrNull("quantity"),
    )

    private fun occurrence(p: Occurrence) = JSONObject().put("id", p.id).put("itemId", p.itemId).put("scheduleTimeId", p.scheduleTimeId)
        .put("scheduledDate", p.scheduledDate?.toString()).put("scheduledTime", p.scheduledTime?.toString()).put("status", p.status.name)
        .put("quantity", p.quantity).put("timestamp", p.timestamp).put("snoozedUntil", p.snoozedUntil)

    private fun readOccurrence(o: JSONObject) = Occurrence(
        id = o.getLong("id"), itemId = o.getLong("itemId"), scheduleTimeId = o.longOrNull("scheduleTimeId"),
        scheduledDate = o.stringOrNull("scheduledDate")?.let(LocalDate::parse), scheduledTime = o.stringOrNull("scheduledTime")?.let(LocalTime::parse),
        status = Status.valueOf(o.getString("status")), quantity = o.doubleOrNull("quantity"), timestamp = o.getLong("timestamp"),
        snoozedUntil = o.longOrNull("snoozedUntil"),
    )

    private fun movement(m: StockMovement) = JSONObject().put("id", m.id).put("itemId", m.itemId).put("delta", m.delta)
        .put("type", m.type.name).put("timestamp", m.timestamp).put("occurrenceId", m.occurrenceId)

    private fun readMovement(o: JSONObject) = StockMovement(
        id = o.getLong("id"), itemId = o.getLong("itemId"), delta = o.getDouble("delta"), type = MovementType.valueOf(o.getString("type")),
        timestamp = o.getLong("timestamp"), occurrenceId = o.longOrNull("occurrenceId"),
    )

    // --- v1 format ("Mes Médocs"): French names, narrower model, converted to the current one ---

    private fun readV1(o: JSONObject): BackupData {
        val photos = readPhotos(o)
        return BackupData(
            items = o.getJSONArray("medicaments").objects().map { m ->
                val perBox = m.doubleOrNull("unitesParBoite")
                val photo = m.stringOrNull("photoPath")?.takeIf { it in photos }
                Item(
                    id = m.getLong("id"), name = m.getString("nom"), type = "medication", unit = m.getString("unite"),
                    packaging = if (perBox != null) "boîte" else null, unitsPerPackage = perBox,
                    trackStock = true, // v1 always tracked the stock
                    thresholdDays = m.getInt("seuilJours"), thresholdUnits = m.doubleOrNull("seuilUnites"), photoPath = photo,
                    reference = m.stringOrNull("cip13"), composition = m.stringOrNull("substances"), form = m.stringOrNull("forme"),
                    archived = m.getBoolean("archive"),
                )
            },
            schedules = o.getJSONArray("plannings").objects().map { p ->
                val base = Schedule(
                    id = p.getLong("id"), itemId = p.getLong("medicamentId"), startDate = LocalDate.parse(p.getString("dateDebut")),
                    endDate = p.stringOrNull("dateFin")?.let(LocalDate::parse), active = p.getBoolean("actif"),
                )
                when (p.getString("recurrence")) {
                    "QUOTIDIEN" -> base.copy(recurrence = Recurrence.PERIODIC, interval = 1, intervalUnit = IntervalUnit.DAY)
                    "JOURS_SEMAINE" -> base.copy(recurrence = Recurrence.WEEKDAYS, interval = 1, weekdays = p.getInt("joursSemaine"))
                    "TOUS_LES_N_JOURS" -> base.copy(
                        recurrence = Recurrence.PERIODIC, interval = p.getInt("intervalleJours").coerceAtLeast(1), intervalUnit = IntervalUnit.DAY,
                    )
                    else -> throw BackupInvalid("Type de rythme inconnu : ${p.getString("recurrence")}")
                }
            },
            scheduleTimes = o.getJSONArray("heures").objects().map {
                ScheduleTime(
                    id = it.getLong("id"), scheduleId = it.getLong("planningId"), time = LocalTime.parse(it.getString("heure")),
                    quantity = it.getDouble("quantite"),
                )
            },
            occurrences = o.getJSONArray("prises").objects().map {
                Occurrence(
                    id = it.getLong("id"), itemId = it.getLong("medicamentId"), scheduleTimeId = it.longOrNull("planningHeureId"),
                    scheduledDate = it.stringOrNull("datePrevue")?.let(LocalDate::parse),
                    scheduledTime = it.stringOrNull("heurePrevue")?.let(LocalTime::parse),
                    status = when (val s = it.getString("statut")) {
                        "REPORTEE" -> Status.SNOOZED
                        "PRISE" -> Status.DONE
                        "SAUTEE" -> Status.SKIPPED
                        "MANQUEE" -> Status.MISSED
                        else -> throw BackupInvalid("Statut inconnu : $s")
                    },
                    quantity = it.getDouble("quantite"), timestamp = it.getLong("horodatage"), snoozedUntil = it.longOrNull("reporteJusqua"),
                )
            },
            stockMovements = o.getJSONArray("mouvements").objects().map {
                StockMovement(
                    id = it.getLong("id"), itemId = it.getLong("medicamentId"), delta = it.getDouble("delta"),
                    type = when (val t = it.getString("type")) {
                        "ACHAT" -> MovementType.PURCHASE
                        "PRISE" -> MovementType.CONSUMPTION
                        "CORRECTION" -> MovementType.CORRECTION
                        else -> throw BackupInvalid("Type de mouvement inconnu : $t")
                    },
                    timestamp = it.getLong("horodatage"), occurrenceId = it.longOrNull("priseId"),
                )
            },
            photos = photos,
        )
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.stringOrNull(k: String): String? = if (isNull(k)) null else getString(k)
    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun JSONObject.intOrNull(k: String): Int? = if (isNull(k)) null else getInt(k)
    private fun JSONObject.doubleOrNull(k: String): Double? = if (isNull(k)) null else getDouble(k)
}
