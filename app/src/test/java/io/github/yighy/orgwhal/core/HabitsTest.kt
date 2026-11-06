package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HabitsTest {

    private val today = LocalDate.of(2026, 11, 6)

    private val notes = OrgNote.fromDocument(
        OrgDocument.parse(
            """
            * TODO Daily never done
            SCHEDULED: <2026-11-01 Sun .+1d>
            :PROPERTIES:
            :STYLE:    habit
            :END:
            * TODO Daily done often
            SCHEDULED: <2026-11-07 Sat .+1d>
            :PROPERTIES:
            :STYLE:    habit
            :END:
            - State "DONE"       from "TODO"       [2026-11-06 Fri 08:00]
            - State "DONE"       from "TODO"       [2026-11-04 Wed 08:00]
            - State "DONE"       from "TODO"       [2026-11-03 Tue 08:00]
            - State "DONE"       from "TODO"       [2026-10-30 Fri 08:00]
            - State "DONE"       from "TODO"       [2026-10-25 Sun 08:00]
            - State "DONE"       from "TODO"       [2026-10-20 Tue 08:00]
            * TODO Every 2 days within 4, late
            SCHEDULED: <2026-11-02 Mon .+2d/4d>
            :PROPERTIES:
            :STYLE:    habit
            :END:
            - State "DONE"       from "TODO"       [2026-10-31 Sat 08:00]
            - State "DONE"       from "TODO"       [2026-10-27 Tue 08:00]
            - State "DONE"       from "TODO"       [2026-10-20 Tue 08:00]
            * TODO Weekly plus plus
            SCHEDULED: <2026-11-09 Mon ++1w>
            :PROPERTIES:
            :STYLE:    habit
            :END:
            - State "DONE"       from "TODO"       [2026-11-03 Tue 08:00]
            - State "DONE"       from "TODO"       [2026-10-25 Sun 08:00]
            - State "DONE"       from "TODO"       [2026-10-19 Mon 08:00]
            * TODO Plus every 3 days
            SCHEDULED: <2026-11-05 Thu +3d>
            :PROPERTIES:
            :STYLE:    habit
            :END:
            - State "DONE"       from "TODO"       [2026-11-01 Sun 08:00]
            - State "DONE"       from "TODO"       [2026-10-27 Tue 08:00]
            - State "DONE"       from "TODO"       [2026-10-25 Sun 08:00]
            * TODO In the logbook, future start
            SCHEDULED: <2026-11-08 Sun .+3d/5d>
            :PROPERTIES:
            :STYLE:    habit
            :END:
            :LOGBOOK:
            - State "DONE"       from "TODO"       [2026-11-05 Thu 21:30]
            CLOCK: [2026-11-05 Thu 21:00]--[2026-11-05 Thu 21:30] =>  0:30
            :END:
            * TODO Not a habit
            SCHEDULED: <2026-11-01 Sun .+1d>
            * TODO Hourly
            SCHEDULED: <2026-11-01 Sun 10:00 .+2h>
            :PROPERTIES:
            :STYLE:    habit
            :END:
            """.trimIndent() + "\n",
        ),
        "u", "f.org",
    )

    /** As org-habit draws it: the face (lower case in its lighter shade), and * done, ! today. */
    private fun graph(title: String): Pair<String, String> {
        val note = notes.first { it.title == title }
        val days = Habits.graph(Habits.of(note, listOf("DONE"))!!, today)
        val faces = days.joinToString("") { d -> d.face.name.take(1).let { if (d.ahead) it.lowercase() else it } }
        val marks = days.joinToString("") { d -> if (d.done) "*" else if (d.today) "!" else "." }
        return faces to marks
    }

    // Expected graphs as org-habit (Emacs 30.2) builds them for the same notes and day
    @Test
    fun graphsAsOrgHabitDrawsThem() {
        assertEquals("ccccccccccccccccaOOOOOoooooo" to ".....................!......", graph("Daily never done"))
        assertEquals("ccccRaOOOOaOOOOaOOORaCaooooo" to "....*....*....*...**.*......", graph("Daily done often"))
        assertEquals("ccccCcrraOOOcrrRcrraOOoooooo" to "....*......*...*.....!......", graph("Every 2 days within 4, late"))
        assertEquals("cccRcccccCcccccccaOccCccaooo" to "...*.....*........*..!......", graph("Weekly plus plus"))
        assertEquals("cccccccccCcCccccCcccaOoooooo" to ".........*.*....*....!......", graph("Plus every 3 days"))
        assertEquals("ccccccccccccccccccccRCcrraoo" to "....................*!......", graph("In the logbook, future start"))
    }

    @Test
    fun onlyRepeatingByDaysOrMoreWithTheStyle() {
        assertNull(Habits.of(notes.first { it.title == "Not a habit" }, listOf("DONE")))
        assertNull(Habits.of(notes.first { it.title == "Hourly" }, listOf("DONE")))
        val habit = Habits.of(notes.first { it.title == "Every 2 days within 4, late" }, listOf("DONE"))!!
        assertEquals(".+" to (2 to 4), habit.type to (habit.every to habit.within))
    }

    @Test
    fun doneDatesAreTheChangesToADoneKeyword() {
        val content = """
            - State "DONE"       from "TODO"       [2026-11-05 Thu 08:00]
            - State "WAIT"       from "TODO"       [2026-11-04 Wed 08:00]
            - State "CANCELED"   from "TODO"       [2026-11-03 Tue]
            Plain text [2026-11-02 Mon]
        """.trimIndent()
        assertEquals(
            listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 3)),
            Habits.doneDates(content, listOf("DONE", "CANCELED")),
        )
    }
}
