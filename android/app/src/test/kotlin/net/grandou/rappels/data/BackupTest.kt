package net.grandou.rappels.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class BackupTest {
    private val data = BackupData(
        items = listOf(
            Item(id = 1, name = "Filtre à eau", type = "home", unit = "pièce", packaging = "boîte", unitsPerPackage = 2.0, trackStock = true,
                thresholdDays = 5, thresholdUnits = 1.5, reference = "123", composition = "carbone", form = "cartouche", archived = true),
            Item(id = 2, name = "Saisi à la main"),
        ),
        schedules = listOf(
            Schedule(id = 7, itemId = 1, recurrence = Recurrence.CYCLE, interval = 1, activeDays = 21, pauseDays = 7,
                startDate = LocalDate.of(2026, 11, 14), endDate = LocalDate.of(2026, 12, 31), active = false),
            Schedule(id = 8, itemId = 2, recurrence = Recurrence.RANK_IN_MONTH, weekdays = 1, rank = -1, startDate = LocalDate.of(2026, 1, 1)),
        ),
        scheduleTimes = listOf(ScheduleTime(id = 9, scheduleId = 7, time = LocalTime.of(8, 30), quantity = 0.5)),
        occurrences = listOf(
            Occurrence(id = 3, itemId = 1, scheduleTimeId = 9, scheduledDate = LocalDate.of(2026, 11, 14), scheduledTime = LocalTime.of(8, 30),
                status = Status.SNOOZED, quantity = 0.5, timestamp = 1_000, snoozedUntil = 2_000),
            Occurrence(id = 4, itemId = 2, status = Status.DONE, timestamp = 3_000),
        ),
        stockMovements = listOf(
            StockMovement(id = 1, itemId = 1, delta = 8.0, type = MovementType.PURCHASE, timestamp = 500),
            StockMovement(id = 2, itemId = 2, delta = -10.0, type = MovementType.CONSUMPTION, timestamp = 3_000, occurrenceId = 4),
        ),
    )

    @Test fun roundTripKeepsEverything() {
        val parsed = BackupJson.parse(BackupJson.toJson(data, createdAt = 42))
        assertEquals(data, parsed.data)
        assertEquals(BackupSource.CURRENT, parsed.source)
        assertEquals(42L, parsed.createdAt)
    }

    @Test fun emptyBackupIsValid() {
        assertEquals(BackupData.EMPTY, BackupJson.parse(BackupJson.toJson(BackupData.EMPTY, 0)).data)
    }

    @Test fun refusesFilesThatAreNotBackups() {
        for (text in listOf("not json", "{}", """{"format":"other","version":1}""", """{"format":"rappels","version":99}""", """{"format":"medocs","version":99}""")) {
            try { BackupJson.parse(text); fail("accepted: $text") } catch (_: BackupInvalid) {}
        }
    }

    @Test fun refusesBrokenLinks() {
        val broken = data.copy(schedules = listOf(data.schedules[0].copy(itemId = 99)))
        try { BackupJson.parse(BackupJson.toJson(broken, 0)); fail() } catch (e: BackupInvalid) { assertTrue(e.message!!.contains("rythme")) }
        val broken2 = data.copy(stockMovements = listOf(data.stockMovements[1].copy(occurrenceId = 404)))
        try { BackupJson.parse(BackupJson.toJson(broken2, 0)); fail() } catch (_: BackupInvalid) {}
    }

    @Test fun refusesDuplicatedPlannedOccurrence() {
        val twice = data.copy(occurrences = listOf(data.occurrences[0], data.occurrences[0].copy(id = 5)))
        try { BackupJson.parse(BackupJson.toJson(twice, 0)); fail() } catch (_: BackupInvalid) {}
    }

    @Test fun refusesPhotoNamesThatAreNotPlainFileNames() {
        val bad = data.copy(photos = mapOf("../evil.jpg" to "AAAA"))
        try { BackupJson.parse(BackupJson.toJson(bad, 0)); fail() } catch (_: BackupInvalid) {}
    }

    @Test fun itemLosesMissingPhoto() {
        val withPhoto = data.copy(items = listOf(data.items[0].copy(photoPath = "gone.jpg"), data.items[1]))
        assertNull(BackupJson.parse(BackupJson.toJson(withPhoto, 0)).data.items[0].photoPath)
        val kept = BackupJson.parse(BackupJson.toJson(withPhoto.copy(photos = mapOf("gone.jpg" to "AAAA")), 0)).data
        assertEquals("gone.jpg", kept.items[0].photoPath)
    }

    // A file written by the v1 app ("Mes Médocs"): French field names, 3 recurrence kinds, French statuses.
    private val v1 = """
        {"format":"medocs","version":1,
         "medicaments":[
           {"id":1,"nom":"DOLIPRANE 1000 mg, comprimé","cip13":"3400935955838","substances":"PARACÉTAMOL 1000 mg","forme":"comprimé",
            "unite":"comprimé","unitesParBoite":8,"photoPath":"abc-1.jpg","seuilJours":5,"seuilUnites":2.5,"archive":true},
           {"id":2,"nom":"Saisi à la main","unite":"ml","seuilJours":7,"archive":false}],
         "plannings":[
           {"id":10,"medicamentId":1,"recurrence":"QUOTIDIEN","joursSemaine":0,"intervalleJours":1,"dateDebut":"2026-11-01","actif":true},
           {"id":11,"medicamentId":1,"recurrence":"JOURS_SEMAINE","joursSemaine":21,"intervalleJours":1,"dateDebut":"2026-11-01","dateFin":"2026-12-31","actif":false},
           {"id":12,"medicamentId":2,"recurrence":"TOUS_LES_N_JOURS","joursSemaine":0,"intervalleJours":3,"dateDebut":"2026-11-02","actif":true}],
         "heures":[{"id":20,"planningId":10,"heure":"08:30","quantite":0.5}],
         "prises":[
           {"id":30,"medicamentId":1,"planningHeureId":20,"datePrevue":"2026-11-14","heurePrevue":"08:30","statut":"REPORTEE","quantite":0.5,"horodatage":1000,"reporteJusqua":2000},
           {"id":31,"medicamentId":1,"planningHeureId":20,"datePrevue":"2026-11-15","heurePrevue":"08:30","statut":"PRISE","quantite":0.5,"horodatage":1100},
           {"id":32,"medicamentId":2,"statut":"SAUTEE","quantite":10,"horodatage":3000},
           {"id":33,"medicamentId":2,"statut":"MANQUEE","quantite":1,"horodatage":3100}],
         "mouvements":[
           {"id":40,"medicamentId":1,"delta":8,"type":"ACHAT","horodatage":500},
           {"id":41,"medicamentId":1,"delta":-0.5,"type":"PRISE","horodatage":1100,"priseId":31},
           {"id":42,"medicamentId":1,"delta":2,"type":"CORRECTION","horodatage":1200}],
         "photos":{"abc-1.jpg":"AAAA"}}
    """.trimIndent()

    @Test fun readsV1Backup() {
        val p = BackupJson.parse(v1)
        assertEquals(BackupSource.V1_MEDOCS, p.source)
        assertNull(p.createdAt)
        val d = p.data

        val dolip = d.items[0]
        assertEquals(Item(id = 1, name = "DOLIPRANE 1000 mg, comprimé", type = "medication", unit = "comprimé", packaging = "boîte", unitsPerPackage = 8.0,
            trackStock = true, thresholdDays = 5, thresholdUnits = 2.5, photoPath = "abc-1.jpg", reference = "3400935955838",
            composition = "PARACÉTAMOL 1000 mg", form = "comprimé", archived = true), dolip)
        assertNull(d.items[1].packaging)
        assertNull(d.items[1].reference)

        val (daily, weekdays, everyN) = d.schedules
        assertEquals(Schedule(id = 10, itemId = 1, recurrence = Recurrence.PERIODIC, interval = 1, intervalUnit = IntervalUnit.DAY, startDate = LocalDate.of(2026, 11, 1)), daily)
        assertEquals(Recurrence.WEEKDAYS, weekdays.recurrence)
        assertEquals(21, weekdays.weekdays)
        assertEquals(1, weekdays.interval)
        assertEquals(LocalDate.of(2026, 12, 31), weekdays.endDate)
        assertEquals(false, weekdays.active)
        assertEquals(Recurrence.PERIODIC, everyN.recurrence)
        assertEquals(3, everyN.interval)
        assertEquals(IntervalUnit.DAY, everyN.intervalUnit)

        assertEquals(ScheduleTime(id = 20, scheduleId = 10, time = LocalTime.of(8, 30), quantity = 0.5), d.scheduleTimes.single())

        assertEquals(listOf(Status.SNOOZED, Status.DONE, Status.SKIPPED, Status.MISSED), d.occurrences.map { it.status })
        assertEquals(2_000L, d.occurrences[0].snoozedUntil)
        assertEquals(LocalDate.of(2026, 11, 14), d.occurrences[0].scheduledDate)
        assertNull(d.occurrences[2].scheduleTimeId)

        assertEquals(listOf(MovementType.PURCHASE, MovementType.CONSUMPTION, MovementType.CORRECTION), d.stockMovements.map { it.type })
        assertEquals(31L, d.stockMovements[1].occurrenceId)
        assertEquals(mapOf("abc-1.jpg" to "AAAA"), d.photos)
    }

    @Test fun v1WithoutPhotosSectionStillReads() {
        val p = BackupJson.parse(v1.replace("\"photos\":{\"abc-1.jpg\":\"AAAA\"}", "\"x\":0")).data
        assertNull(p.items[0].photoPath)
    }

    @Test fun v1UnknownEnumIsRejected() {
        try { BackupJson.parse(v1.replace("SAUTEE", "BIZARRE")); fail() } catch (_: BackupInvalid) {}
    }

    @Test fun cryptoRoundTrip() {
        val plain = "données de santé é à ✓".toByteArray()
        val file = BackupCrypto.encrypt(plain, "mot de passe".toCharArray())
        assertArrayEquals(plain, BackupCrypto.decrypt(file, "mot de passe".toCharArray()))
    }

    @Test fun twoEncryptionsOfTheSameTextDiffer() {
        val a = BackupCrypto.encrypt("x".toByteArray(), "pw123456".toCharArray())
        val b = BackupCrypto.encrypt("x".toByteArray(), "pw123456".toCharArray())
        assertTrue(!a.contentEquals(b))
    }

    @Test fun wrongPasswordAndTamperingAreRejected() {
        val file = BackupCrypto.encrypt("secret".toByteArray(), "good-password".toCharArray())
        try { BackupCrypto.decrypt(file, "bad-password".toCharArray()); fail() } catch (_: WrongPassword) {}
        val tampered = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        try { BackupCrypto.decrypt(tampered, "good-password".toCharArray()); fail() } catch (_: WrongPassword) {}
    }

    @Test fun refusesFilesThatAreNotEncryptedBackups() {
        for (bytes in listOf(ByteArray(0), "hello".toByteArray(), ByteArray(200))) {
            try { BackupCrypto.decrypt(bytes, "x".toCharArray()); fail() } catch (_: BackupInvalid) {}
        }
    }

    /** A v1 file is encrypted with the same parameters under the "MEDOCS1" magic: build one the way the v1 app did. */
    @Test fun decryptsFilesEncryptedLikeV1() {
        val password = "ancien-mot-de-passe".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 100).toByte() }
        val key = javax.crypto.spec.SecretKeySpec(
            javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(javax.crypto.spec.PBEKeySpec(password, salt, 210_000, 256)).encoded, "AES",
        )
        val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, key, javax.crypto.spec.GCMParameterSpec(128, iv))
            updateAAD("MEDOCS1".toByteArray())
        }
        val file = "MEDOCS1".toByteArray() + salt + iv + c.doFinal(v1.toByteArray())
        val parsed = BackupJson.parse(String(BackupCrypto.decrypt(file, password)))
        assertEquals(BackupSource.V1_MEDOCS, parsed.source)
        assertEquals(2, parsed.data.items.size)
    }
}
