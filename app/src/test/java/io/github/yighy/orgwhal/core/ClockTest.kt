package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ClockTest {

    private val ten = LocalDateTime.of(2026, 10, 9, 10, 0)

    @Test
    fun clockInMakesTheLogbook() {
        val started = Clock.clockIn("Some text", ten)
        assertEquals(":LOGBOOK:\nCLOCK: [2026-10-09 Fri 10:00]\n:END:\nSome text", started)
        assertNotNull(Clock.running(started))
        // Not twice
        assertEquals(started, Clock.clockIn(started, ten.plusHours(1)))
    }

    @Test
    fun clockOutWritesTheDuration() {
        val stopped = Clock.clockOut(Clock.clockIn("", ten), ten.plusMinutes(95))
        assertEquals(":LOGBOOK:\nCLOCK: [2026-10-09 Fri 10:00]--[2026-10-09 Fri 11:35] =>  1:35\n:END:", stopped)
        assertNull(Clock.running(stopped))
    }

    @Test
    fun newClockGoesFirstInTheLogbook() {
        val content = "  :LOGBOOK:\n  CLOCK: [2026-10-08 Thu 09:00]--[2026-10-08 Thu 09:30] =>  0:30\n  :END:\nText"
        val started = Clock.clockIn(content, ten)
        assertEquals("  :LOGBOOK:\n  CLOCK: [2026-10-09 Fri 10:00]\n  CLOCK: [2026-10-08 Thu 09:00]--[2026-10-08 Thu 09:30] =>  0:30\n  :END:\nText", started)
        assertEquals(Duration.ofMinutes(45), Clock.total(started, ten.plusMinutes(15)))
    }

    @Test
    fun lastIsTheRunningClockOrElseTheLatest() {
        val content = ":LOGBOOK:\nCLOCK: [2026-10-07 Wed 09:00]--[2026-10-07 Wed 10:00] =>  1:00\nCLOCK: [2026-10-08 Thu 09:00]--[2026-10-08 Thu 09:30] =>  0:30\n:END:"
        assertEquals(Duration.ofMinutes(30), Clock.duration(Clock.last(content)!!, ten))
        val started = Clock.clockIn(content, ten)
        assertEquals(Duration.ofMinutes(20), Clock.duration(Clock.last(started)!!, ten.plusMinutes(20)))
        assertNull(Clock.last("No clocking"))
    }

    @Test
    fun clockingStampsTheModificationDate() {
        val note = OrgNote.fromDocument(OrgDocument.parse("* TODO Task\n:PROPERTIES:\n:LAST_MODIFIED: [2026-10-01 Thu 08:00]\n:END:\n"), "u", "f.org").single()
        val clocked = note.stamped(note.toEdit().copy(content = Clock.clockIn(note.content, ten)), ten)
        assertEquals(listOf("LAST_MODIFIED" to "[2026-10-09 Fri 10:00]"), clocked.properties)
        // Nothing changed, nothing stamped
        assertEquals(note.toEdit(), note.stamped(note.toEdit(), ten))
    }
}
