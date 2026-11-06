package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class ClockReportTest {

    private val today = LocalDate.of(2026, 11, 6) // Friday
    private val now = today.atTime(19, 0)

    private val notes = OrgNote.fromDocument(
        OrgDocument.parse(
            """
            * Project A
            ** Task 1
            :LOGBOOK:
            CLOCK: [2026-11-06 Fri 09:00]--[2026-11-06 Fri 10:30] =>  1:30
            CLOCK: [2026-11-01 Sun 23:00]--[2026-11-02 Mon 01:00] =>  2:00
            :END:
            ** Task 2
            :LOGBOOK:
            CLOCK: [2026-10-28 Wed 10:00]--[2026-10-28 Wed 11:00] =>  1:00
            :END:
            *** Sub
            :LOGBOOK:
            CLOCK: [2026-11-03 Tue 14:00]--[2026-11-03 Tue 14:45] =>  0:45
            :END:
            * Nothing here
            * Other
            :LOGBOOK:
            CLOCK: [2026-11-05 Thu 08:00]--[2026-11-05 Thu 08:20] =>  0:20
            :END:
            * Running
            :LOGBOOK:
            CLOCK: [2026-11-06 Fri 18:00]
            :END:
            """.trimIndent() + "\n",
        ),
        "u", "f.org",
    )

    private fun hm(d: Duration) = Clock.format(d)

    // This week as Emacs's clocktable sums it (:tstart Monday, :tend next Monday): 3:35, Task 1
    // clipped at Monday 0:00; plus the running clock, counted up to now
    @Test
    fun thisWeekAsClocktable() {
        val report = ClockReport.build(notes, ClockReport.range(ClockReport.Period.ThisWeek, today), now).single()
        assertEquals("4:35", hm(report.total))
        assertEquals(
            listOf("Project A 0:00 3:15", "Task 1 2:30 2:30", "Task 2 0:00 0:45", "Sub 0:45 0:45", "Other 0:20 0:20", "Running 1:00 1:00"),
            report.rows.map { "${it.note.title} ${hm(it.own)} ${hm(it.total)}" },
        )
    }

    @Test
    fun periods() {
        fun range(p: ClockReport.Period) = ClockReport.range(p, today)!!.let { it.start.toString() + " " + it.endInclusive }
        assertEquals("2026-11-06T00:00 2026-11-07T00:00", range(ClockReport.Period.Today))
        assertEquals("2026-11-05T00:00 2026-11-06T00:00", range(ClockReport.Period.Yesterday))
        assertEquals("2026-11-02T00:00 2026-11-09T00:00", range(ClockReport.Period.ThisWeek))
        assertEquals("2026-10-26T00:00 2026-11-02T00:00", range(ClockReport.Period.LastWeek))
        assertEquals("2026-11-01T00:00 2026-12-01T00:00", range(ClockReport.Period.ThisMonth))
        assertEquals("2026-10-01T00:00 2026-11-01T00:00", range(ClockReport.Period.LastMonth))
        assertNull(ClockReport.range(ClockReport.Period.All, today))
    }

    @Test
    fun lastWeekAndAllTime() {
        val lastWeek = ClockReport.build(notes, ClockReport.range(ClockReport.Period.LastWeek, today), now).single()
        // Task 2's hour, and the hour of Task 1 before Monday
        assertEquals(listOf("Project A 2:00", "Task 1 1:00", "Task 2 1:00"), lastWeek.rows.map { "${it.note.title} ${hm(it.total)}" })
        val all = ClockReport.build(notes, null, now).single()
        assertEquals("6:35", hm(all.total))
        assertEquals(Duration.ofMinutes(5 * 60 + 15), ClockReport.subtree(notes, 0, now))
        assertEquals(emptyList<ClockReport.Notebook>(), ClockReport.build(notes, ClockReport.range(ClockReport.Period.Today, today.plusDays(30)), now))
    }
}
