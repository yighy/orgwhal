package io.github.yighy.orgwhal.core

import java.time.LocalDate

/**
 * Org habits (org-habit): repeating tasks whose STYLE property is "habit". The days they were
 * done, from the state changes logged as they repeat, make a consistency graph, as org-habit
 * draws it in the agenda.
 */
object Habits {

    /** Days shown before today, as org-habit-preceding-days. */
    const val PRECEDING_DAYS = 21

    /** Days shown from today on, today included, as org-habit-following-days fills them. */
    const val FOLLOWING_DAYS = 7

    /** org-habit's faces: not due yet, can be done, its last day, overdue. */
    enum class Face { Clear, Ready, Alert, Overdue }

    /**
     * A day of the graph. [ahead]: in the lighter shade, as days to come are, and days gone by
     * without being done that were not overdue.
     */
    data class Day(val date: LocalDate, val face: Face, val ahead: Boolean, val done: Boolean, val today: Boolean)

    /**
     * A habit as now scheduled: its repeater's [type] (".+", "++" or "+") every [every] days, to
     * be done within [within] days (the /N part, else the repeater's own), and the days it was done.
     */
    data class Habit(val scheduled: LocalDate, val type: String, val every: Int, val within: Int, val done: List<LocalDate>)

    fun isHabit(note: OrgNote): Boolean = note.property("STYLE").equals("habit", ignoreCase = true)

    /** [note] as a habit: one with a scheduled time repeating by days or more ([doneKeywords]: its file's). */
    fun of(note: OrgNote, doneKeywords: List<String>): Habit? {
        if (!isHabit(note)) return null
        val parts = OrgTime.parts(note.scheduled) ?: return null
        val repeat = OrgTime.Repeat.parse(parts.repeater) ?: return null
        val every = days(repeat.every, repeat.unit) ?: return null
        val within = WITHIN.matchEntire(repeat.rest)?.let { days(it.groupValues[1].toInt(), it.groupValues[2][0]) } ?: every
        return Habit(parts.date, repeat.type, every, maxOf(within, every), doneDates(note.content, doneKeywords))
    }

    /** The days [content] logs a change to one of [doneKeywords], such as `- State "DONE" from "TODO" [2026-11-06 Fri 08:12]`. */
    fun doneDates(content: String, doneKeywords: List<String>): List<LocalDate> =
        STATE.findAll(content)
            .filter { it.groupValues[1] in doneKeywords }
            .map { m -> LocalDate.of(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].toInt()) }
            .toList()

    /** The consistency graph around [today], as org-habit-build-graph makes it. */
    fun graph(habit: Habit, today: LocalDate): List<Day> {
        val all = habit.done.map { it.toEpochDay() }.sorted()
        val scheduled = habit.scheduled.toEpochDay()
        val every = habit.every.toLong()
        val now = today.toEpochDay()
        var day = now - PRECEDING_DAYS
        val end = now + FOLLOWING_DAYS
        // Done dates still to come in the graph, and the last one gone by
        var done = all.dropWhile { it < day }
        var lastDone = all.lastOrNull { it < day }
        val days = mutableListOf<Day>()
        while (day < end) {
            val past = day < now
            val isToday = day == now
            val doneThen = done.firstOrNull() == day
            val face = if (past && lastDone == null && scheduled >= now) {
                // Not done yet: clear until it first was
                if (all.firstOrNull() == day) Face.Ready else Face.Clear
            } else {
                // Scheduled then: as it was when that day was current
                val then = if (past && lastDone != null) scheduledThen(habit.type, scheduled, every, all, done, lastDone) else null
                face(day, then ?: scheduled, habit.within - habit.every, doneThen)
            }
            if (doneThen) {
                while (done.firstOrNull() == day) {
                    lastDone = day
                    done = done.drop(1)
                }
            }
            val ahead = !past && !isToday || past && face != Face.Overdue && !doneThen
            days += Day(LocalDate.ofEpochDay(day), face, ahead, doneThen, isToday)
            day++
        }
        return days
    }

    private fun face(day: Long, scheduled: Long, grace: Int, done: Boolean): Face {
        val deadline = scheduled + grace
        return when {
            day < scheduled -> Face.Clear
            day < deadline -> Face.Ready
            day == deadline -> if (done) Face.Ready else Face.Alert
            else -> Face.Overdue
        }
    }

    /**
     * When the habit was scheduled once done on [lastDone], [done] being the times it was done
     * since, of [all] the times it was.
     */
    private fun scheduledThen(type: String, scheduled: Long, every: Long, all: List<Long>, done: List<Long>, lastDone: Long): Long = when {
        done.isEmpty() -> scheduled
        type == ".+" -> lastDone + every
        // Each time done since moved it by one repeat
        type == "+" -> scheduled - done.size * every
        else -> {
            // ++: the first time past the first done that reaches the scheduled one by repeats,
            // then each later done moves it by as many repeats as get past it, one at least
            val first = all.first()
            val shift = Math.floorMod(scheduled - first, every)
            var then = first + if (shift == 0L) every else shift
            if (first != lastDone) {
                for (d in all.drop(1)) {
                    then += (1 + maxOf(d - then, 0) / every) * every
                    if (d == lastDone) break
                }
            }
            then
        }
    }

    /** As org-habit-duration-to-days; hours make no habit. */
    private fun days(n: Int, unit: Char): Int? = when (unit) {
        'd' -> n
        'w' -> n * 7
        'm' -> n * 30
        'y' -> n * 365
        else -> null
    }

    private val WITHIN = Regex("""^/(\d+)([dwmy])$""")
    private val STATE = Regex("""^[ \t]*-[ \t]+State[ \t]+"([^"]+)"[^\n\[]*\[(\d{4})-(\d{2})-(\d{2})""", RegexOption.MULTILINE)
}
