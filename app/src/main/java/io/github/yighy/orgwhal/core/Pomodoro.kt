package io.github.yighy.orgwhal.core

import java.time.Duration
import java.time.LocalDateTime

/**
 * Pomodoros, as org-pomodoro runs them: the note's clock runs for a working time, stops by
 * itself, then comes a short break, or a long one every few pomodoros, after which the next can
 * start on the same note. The clocking is the note's own (CLOCK lines), so it shows in the time
 * report and in Emacs as any other.
 */
object Pomodoro {

    /** Lengths in minutes, and how many pomodoros come before a long break. */
    data class Lengths(val work: Int = 25, val shortBreak: Int = 5, val longBreak: Int = 15, val longEvery: Int = 4)

    enum class Phase { Work, ShortBreak, LongBreak, Over }

    /**
     * A pomodoro run on a note ([fileUri], [index], [title] as when it started): in [phase] until
     * [endsAt], [done] pomodoros done in a row. [Phase.Over]: the break is over, the next one waits.
     */
    data class State(
        val fileUri: String,
        val index: Int,
        val title: String,
        val phase: Phase,
        val startedAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val done: Int,
    )

    /** A pomodoro starting [now] on [note], after [done] others. */
    fun start(note: OrgNote, lengths: Lengths, now: LocalDateTime, done: Int = 0): State =
        State(note.fileUri, note.index, note.title, Phase.Work, now, now.plusMinutes(lengths.work.toLong()), done)

    /** The break after [state]'s pomodoro, which ended at its [State.endsAt]: a long one every [Lengths.longEvery]. */
    fun breakAfter(state: State, lengths: Lengths): State {
        val done = state.done + 1
        val long = lengths.longEvery > 0 && done % lengths.longEvery == 0
        val length = if (long) lengths.longBreak else lengths.shortBreak
        return state.copy(
            phase = if (long) Phase.LongBreak else Phase.ShortBreak,
            startedAt = state.endsAt,
            endsAt = state.endsAt.plusMinutes(length.toLong()),
            done = done,
        )
    }

    /** Whether [state] is the pomodoro of the clock of [note] that started at [clockStart]. */
    fun runs(state: State?, note: OrgNote, clockStart: LocalDateTime): Boolean = runs(state, note.fileUri, note.title, clockStart)

    /** Whether [state] is the pomodoro of the clock started at [clockStart] on the note [title] of [fileUri]. */
    fun runs(state: State?, fileUri: String, title: String, clockStart: LocalDateTime): Boolean =
        state != null && state.phase == Phase.Work && state.fileUri == fileUri && state.title == title &&
            !clockStart.isBefore(state.startedAt.minusMinutes(1)) && !clockStart.isAfter(state.startedAt.plusMinutes(1))

    /** Time left in [state]'s phase at [now], none once it is over. */
    fun left(state: State, now: LocalDateTime): Duration = Duration.between(now, state.endsAt).coerceAtLeast(Duration.ZERO)
}
