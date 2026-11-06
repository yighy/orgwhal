package io.github.yighy.orgwhal.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Time clocked over a period, by notebook and note, as Org's clocktable sums it: a note counts
 * its own clocking and its sub-notes'. A clocking across the period's bounds counts within them,
 * a running one up to now.
 */
object ClockReport {

    /** Org's clocktable blocks (:block today, thisweek…), weeks from Monday as in Org. */
    enum class Period { Today, Yesterday, ThisWeek, LastWeek, ThisMonth, LastMonth, All }

    /** From the first instant of [period] to the first after it; null for all time. */
    fun range(period: Period, today: LocalDate): ClosedRange<LocalDateTime>? {
        val (from, until) = when (period) {
            Period.Today -> today to today.plusDays(1)
            Period.Yesterday -> today.minusDays(1) to today
            Period.ThisWeek -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusWeeks(1) }
            Period.LastWeek -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1).let { it to it.plusWeeks(1) }
            Period.ThisMonth -> today.withDayOfMonth(1).let { it to it.plusMonths(1) }
            Period.LastMonth -> today.withDayOfMonth(1).minusMonths(1).let { it to it.plusMonths(1) }
            Period.All -> return null
        }
        return from.atStartOfDay()..until.atStartOfDay()
    }

    /** A note with time clocked: on itself ([own]) and with its sub-notes ([total]). */
    data class Row(val note: OrgNote, val own: Duration, val total: Duration)

    /** A notebook's time, and its notes with some, in the outline's order. */
    data class Notebook(val fileUri: String, val total: Duration, val rows: List<Row>)

    /** The notebooks of [notes] with time clocked within [range] (all time if null), the most first. */
    fun build(notes: List<OrgNote>, range: ClosedRange<LocalDateTime>?, now: LocalDateTime): List<Notebook> =
        notes.groupBy { it.fileUri }.mapNotNull { (uri, inFile) ->
            val sorted = inFile.sortedBy { it.index }
            val own = sorted.map { clocked(it.content, range, now) }
            val total = totals(sorted, own)
            val rows = sorted.indices.filter { !total[it].isZero }.map { Row(sorted[it], own[it], total[it]) }
            if (rows.isEmpty()) null else Notebook(uri, own.fold(Duration.ZERO, Duration::plus), rows)
        }.sortedByDescending { it.total }

    /** Time clocked on the note at [index] of [notes] and its sub-notes, all time. */
    fun subtree(notes: List<OrgNote>, index: Int, now: LocalDateTime): Duration {
        var end = index + 1
        while (end < notes.size && notes[end].level > notes[index].level) end++
        return (index until end).fold(Duration.ZERO) { sum, i -> sum + clocked(notes[i].content, null, now) }
    }

    /** Time clocked in [content] within [range]. */
    fun clocked(content: String, range: ClosedRange<LocalDateTime>?, now: LocalDateTime): Duration =
        Clock.entries(content).fold(Duration.ZERO) { sum, e ->
            var start = e.start
            var end = e.end ?: now
            if (range != null) {
                if (start < range.start) start = range.start
                if (end > range.endInclusive) end = range.endInclusive
            }
            if (end > start) sum + Duration.between(start, end) else sum
        }

    /** Each note's time with its sub-notes', from the deepest up. */
    private fun totals(notes: List<OrgNote>, own: List<Duration>): List<Duration> {
        val total = own.toMutableList()
        val parent = IntArray(notes.size) { -1 }
        val open = ArrayDeque<Int>()
        notes.forEachIndexed { i, note ->
            while (open.isNotEmpty() && notes[open.last()].level >= note.level) open.removeLast()
            parent[i] = open.lastOrNull() ?: -1
            open.addLast(i)
        }
        for (i in notes.indices.reversed()) if (parent[i] >= 0) total[parent[i]] = total[parent[i]] + total[i]
        return total
    }
}
