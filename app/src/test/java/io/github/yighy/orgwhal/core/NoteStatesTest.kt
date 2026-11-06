package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteStatesTest {

    private fun note(raw: String) = OrgNote.fromDocument(OrgDocument.parse(raw), "u", "f.org").first()

    @Test
    fun toggleTargets() {
        val k = Keywords.DEFAULT
        assertEquals("DONE", NoteStates.toggleTarget(note("* TODO a\n"), k))
        assertEquals("DONE", NoteStates.toggleTarget(note("* NEXT a\n"), k))
        assertEquals("TODO", NoteStates.toggleTarget(note("* DONE a\n"), k))
        assertEquals("TODO", NoteStates.toggleTarget(note("* a\n"), k))
    }

    @Test
    fun doneAddsClosedAndTodoRemovesIt() {
        val done = NoteStates.apply(note("* TODO a\n").toEdit(), "DONE", Keywords.DEFAULT)
        assertFalse(done.shifted)
        assertEquals("DONE", done.edit.state)
        assertNotNull(done.edit.closed)
        assertTrue(done.edit.closed!!.startsWith("["))

        val back = NoteStates.apply(done.edit, "TODO", Keywords.DEFAULT)
        assertEquals("TODO", back.edit.state)
        assertNull(back.edit.closed)
    }

    @Test
    fun repeatingTaskMovesForward() {
        val result = NoteStates.apply(note("* TODO a\nSCHEDULED: <2026-10-05 Mon +1w>\n").toEdit(), "DONE", Keywords.DEFAULT)
        assertTrue(result.shifted)
        assertEquals("TODO", result.edit.state)
        assertNull(result.edit.closed)
        assertTrue(result.edit.scheduled!!.contains("+1w"))
        assertFalse(result.edit.scheduled!!.contains("2026-10-05"))
    }

    @Test
    fun doneStopsTheRunningClock() {
        val now = java.time.LocalDateTime.of(2026, 10, 12, 11, 30)
        val clocked = note("* TODO a\n:LOGBOOK:\nCLOCK: [2026-10-12 Mon 10:00]\n:END:\n").toEdit()
        val done = NoteStates.apply(clocked, "DONE", Keywords.DEFAULT, now).edit
        assertNull(Clock.running(done.content))
        assertTrue(done.content.contains("--[2026-10-12 Mon 11:30] =>  1:30"))
        // A repeating task stays to-do, its clock stopped all the same
        val repeating = note("* TODO a\nSCHEDULED: <2026-10-05 Mon +1w>\n:LOGBOOK:\nCLOCK: [2026-10-12 Mon 10:00]\n:END:\n").toEdit()
        assertNull(Clock.running(NoteStates.apply(repeating, "DONE", Keywords.DEFAULT, now).edit.content))
        // Other states keep it running
        assertNotNull(Clock.running(NoteStates.apply(clocked, "NEXT", Keywords.DEFAULT, now).edit.content))
    }

    @Test
    fun repeatingTaskDoneIsLoggedAsOrgDoes() {
        val now = java.time.LocalDateTime.of(2026, 11, 6, 8, 5)
        val line = "- State \"DONE\"       from \"TODO\"       [2026-11-06 Fri 08:05]"
        val repeating = note("* TODO a\nSCHEDULED: <2026-11-06 Fri .+1d>\nSome text\n").toEdit()
        // In a new logbook, before the text, with LAST_REPEAT
        val logged = NoteStates.apply(repeating, "DONE", Keywords.DEFAULT, now).edit
        assertEquals(":LOGBOOK:\n$line\n:END:\nSome text", logged.content)
        assertEquals("[2026-11-06 Fri 08:05]", logged.properties.toMap()["LAST_REPEAT"])
        // Newest first in a logbook already there; LAST_REPEAT replaced
        val again = NoteStates.apply(logged, "DONE", Keywords.DEFAULT, now.plusDays(1)).edit
        assertEquals(":LOGBOOK:\n${line.replace("06 Fri", "07 Sat")}\n$line\n:END:\nSome text", again.content)
        assertEquals(listOf("[2026-11-07 Sat 08:05]"), again.properties.filter { it.first == "LAST_REPEAT" }.map { it.second })
        // In the text with nologdrawer, not at all with nologrepeat
        val body = NoteStates.apply(repeating, "DONE", Keywords.DEFAULT, now, NoteStates.repeatLog("#+STARTUP: content nologdrawer")).edit
        assertEquals("$line\nSome text", body.content)
        val none = NoteStates.apply(repeating, "DONE", Keywords.DEFAULT, now, NoteStates.repeatLog("#+startup: nologrepeat")).edit
        assertEquals("Some text", none.content)
        assertTrue(none.properties.isEmpty())
        // A task without repeater is closed, not logged
        val once = NoteStates.apply(note("* TODO b\n").toEdit(), "DONE", Keywords.DEFAULT, now).edit
        assertEquals("", once.content)
    }
}
