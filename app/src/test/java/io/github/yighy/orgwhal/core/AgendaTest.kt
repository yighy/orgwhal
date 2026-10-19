package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AgendaTest {

    private val today = LocalDate.of(2026, 10, 5) // Monday

    private val notes = OrgNote.fromDocument(
        OrgDocument.parse(
            """
            * TODO Late task
            SCHEDULED: <2026-10-01 Thu>
            * TODO Today at nine
            SCHEDULED: <2026-10-05 Mon 09:00>
            * TODO Weekly
            SCHEDULED: <2026-09-28 Mon 18:00 +1w>
            * NEXT Report
            DEADLINE: <2026-10-07 Wed>
            * DONE Finished
            SCHEDULED: <2026-10-05 Mon 08:00>
            * Plain note
            """.trimIndent() + "\n",
        ),
        "uri", "f.org",
    )

    @Test
    fun onlyTheTagsOfNotesWithDatesAreOffered() {
        val tagged = OrgNote.fromDocument(
            OrgDocument.parse(
                """
                * TODO Run                                     :sport:
                SCHEDULED: <2026-10-06 Tue>
                * Idea                                          :someday:
                * DONE Swim                                     :pool:
                SCHEDULED: <2026-10-05 Mon>
                * Lunch                                          :Food:
                Meet at <2026-10-07 Wed 12:00>.
                """.trimIndent() + "\n",
            ),
            "uri", "f.org",
        )
        assertEquals(listOf("Food", "sport"), Agenda.tags(tagged))
    }

    @Test
    fun agendaDays() {
        val agenda = Agenda.build(notes, today, 7)

        // Oldest first
        assertEquals(listOf("Weekly", "Late task"), agenda.overdue.map { it.note.title })
        assertEquals(7L, agenda.overdue.first().daysLate(today))

        assertEquals(7, agenda.days.size)
        // Today: the done note is left out, and the overdue weekly task is not repeated here
        assertEquals(listOf("Today at nine"), agenda.days[0].entries.map { it.note.title })
        assertEquals(LocalTime.of(9, 0), agenda.days[0].entries[0].time)
        assertEquals(listOf("Report"), agenda.days[2].entries.map { it.note.title })
        assertEquals(Agenda.Kind.Deadline, agenda.days[2].entries[0].kind)
        // Weekly repeat comes back the next Monday, outside the 7 days
        assertTrue(agenda.days.drop(1).none { day -> day.entries.any { it.note.title == "Weekly" } })
    }

    @Test
    fun remindersInWindow() {
        val options = Agenda.ReminderOptions()
        val due = Agenda.reminders(notes, today.atTime(8, 0), today.atTime(20, 0), options)
        assertEquals(listOf("Today at nine", "Weekly"), due.map { it.note.title })
        assertEquals(today.atTime(18, 0), due[1].at)

        // Dates without time only with an all-day time
        assertEquals(
            listOf("Report"),
            Agenda.reminders(notes, LocalDateTime.of(2026, 10, 7, 0, 0), LocalDateTime.of(2026, 10, 7, 12, 0),
                options.copy(allDayTime = LocalTime.of(9, 0))).map { it.note.title },
        )
        assertTrue(Agenda.reminders(notes, LocalDateTime.of(2026, 10, 7, 0, 0), LocalDateTime.of(2026, 10, 7, 12, 0), options).isEmpty())
    }

    @Test
    fun nextReminder() {
        val options = Agenda.ReminderOptions()
        assertEquals(today.atTime(9, 0), Agenda.nextReminder(notes, today.atTime(8, 0), options))
        assertEquals(today.atTime(18, 0), Agenda.nextReminder(notes, today.atTime(9, 0), options))
        // After today's 18:00, the weekly repeat a week later
        assertEquals(LocalDateTime.of(2026, 10, 12, 18, 0), Agenda.nextReminder(notes, today.atTime(18, 0), options))
        assertNull(Agenda.nextReminder(notes, today.atTime(8, 0), options.copy(scheduled = false)))
    }
}
