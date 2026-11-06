package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class PomodoroTest {

    private val note = OrgNote.fromDocument(OrgDocument.parse("* TODO Write\n"), "u", "f.org").single()
    private val lengths = Pomodoro.Lengths(work = 25, shortBreak = 5, longBreak = 15, longEvery = 4)
    private val now = LocalDateTime.of(2026, 11, 6, 9, 0, 30)

    @Test
    fun workThenBreaksALongOneEveryFour() {
        var state = Pomodoro.start(note, lengths, now)
        assertEquals(Pomodoro.Phase.Work, state.phase)
        assertEquals(now.plusMinutes(25), state.endsAt)
        val phases = (1..8).map {
            state = Pomodoro.breakAfter(state, lengths)
            val pause = state
            // The next starts as the break ends
            state = Pomodoro.start(note, lengths, pause.endsAt, pause.done)
            pause.phase to Duration.between(pause.startedAt, pause.endsAt).toMinutes()
        }
        val short = Pomodoro.Phase.ShortBreak to 5L
        val long = Pomodoro.Phase.LongBreak to 15L
        assertEquals(listOf(short, short, short, long, short, short, short, long), phases)
        assertEquals(8, state.done)
    }

    @Test
    fun runsOnlyForTheClockItStarted() {
        val state = Pomodoro.start(note, lengths, now)
        // The clock line keeps the minute only
        assertTrue(Pomodoro.runs(state, note, now.withSecond(0)))
        assertFalse(Pomodoro.runs(state, note, now.minusMinutes(10)))
        assertFalse(Pomodoro.runs(state.copy(phase = Pomodoro.Phase.ShortBreak), note, now))
        assertFalse(Pomodoro.runs(state, note.copy(title = "Other"), now))
        assertEquals(Duration.ofMinutes(10), Pomodoro.left(state, now.plusMinutes(15)))
        assertEquals(Duration.ZERO, Pomodoro.left(state, now.plusMinutes(40)))
    }
}
