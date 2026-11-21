package net.grandou.rappels.ui

import net.grandou.rappels.data.IntervalUnit
import net.grandou.rappels.data.Recurrence
import net.grandou.rappels.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun s(
        r: Recurrence = Recurrence.PERIODIC, n: Int = 1, u: IntervalUnit = IntervalUnit.DAY, wd: Int = 0,
        rank: Int? = null, a: Int? = null, p: Int? = null, start: String = "2026-03-15", end: String? = null,
    ) = Schedule(itemId = 1, recurrence = r, interval = n, intervalUnit = u, weekdays = wd, rank = rank,
        activeDays = a, pauseDays = p, startDate = d(start), endDate = end?.let(::d))

    @Test fun quantities() {
        assertEquals("30", formatQuantity(30.0))
        assertEquals("0,5", formatQuantity(0.5))
        assertEquals("1,25", formatQuantity(1.25))
    }

    @Test fun unitsAndPlurals() {
        assertEquals("1 dose", formatWithUnit(1.0, "dose"))
        assertEquals("1,5 dose", formatWithUnit(1.5, "dose"))
        assertEquals("2 doses", formatWithUnit(2.0, "dose"))
        assertEquals("50 ml", formatWithUnit(50.0, "ml"))
        assertEquals("3 paires", formatWithUnit(3.0, "paire"))
        assertEquals("3 pois", formatWithUnit(3.0, "pois"))
    }

    @Test fun decimalInput() {
        assertEquals(0.5, parseDecimal("0,5")!!, 0.0)
        assertEquals(2.0, parseDecimal(" 2.0 ")!!, 0.0)
        assertNull(parseDecimal("abc"))
    }

    @Test fun relativeDays() {
        val today = d("2026-03-16") // Monday
        assertEquals("aujourd'hui", relativeDay(today, today))
        assertEquals("demain", relativeDay(d("2026-03-17"), today))
        assertEquals("jeudi", relativeDay(d("2026-03-19"), today))
        assertEquals("lundi 23 mars", relativeDay(d("2026-03-23"), today))
    }

    @Test fun rhythms() {
        assertEquals("Tous les jours", rhythmSummary(s()))
        assertEquals("Tous les 3 jours", rhythmSummary(s(n = 3)))
        assertEquals("Toutes les 2 semaines", rhythmSummary(s(n = 2, u = IntervalUnit.WEEK)))
        assertEquals("Tous les 3 mois, le 15", rhythmSummary(s(n = 3, u = IntervalUnit.MONTH)))
        assertEquals("Tous les ans, le 15 mars", rhythmSummary(s(u = IntervalUnit.YEAR)))
        assertEquals("Lun., mer., ven.", rhythmSummary(s(Recurrence.WEEKDAYS, wd = 1 or 4 or 16)))
        assertEquals("Sam., 1 semaine sur 2", rhythmSummary(s(Recurrence.WEEKDAYS, n = 2, wd = 32)))
        assertEquals("Tous les jours", rhythmSummary(s(Recurrence.WEEKDAYS, wd = 127)))
        assertEquals("Le 1er lundi du mois", rhythmSummary(s(Recurrence.RANK_IN_MONTH, wd = 1, rank = 1)))
        assertEquals("Le dernier vendredi du mois, tous les 2 mois", rhythmSummary(s(Recurrence.RANK_IN_MONTH, n = 2, wd = 16, rank = -1)))
        assertEquals("21 jours, puis 7 jours de pause", rhythmSummary(s(Recurrence.CYCLE, a = 21, p = 7)))
    }

    @Test fun periods() {
        val today = d("2026-03-16")
        assertEquals("Depuis le 15/03/2026", periodText(s(), today))
        assertEquals("À partir du 15/03/2026", periodText(s(start = "2026-03-15"), d("2026-03-01")))
        assertEquals("Du 15/03/2026 au 20/03/2026", periodText(s(end = "2026-03-20"), today))
        assertEquals("Le 15/03/2026 (une seule fois)", periodText(s(end = "2026-03-15"), today))
    }
}
