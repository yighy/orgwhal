package io.github.yighy.orgwhal.core

import com.orgzly.org.datetime.OrgInterval
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Agenda computed from the notes' SCHEDULED and DEADLINE times and their events (active
 * timestamps in the title or content); reminders from SCHEDULED and DEADLINE.
 */
object Agenda {

    enum class Kind { Scheduled, Deadline, Event }

    /** One occurrence of a planning time; [time] is null for a date without time of day. */
    /** [endTime]: end of the time range, if the timestamp has one. */
    data class Entry(val note: OrgNote, val kind: Kind, val date: LocalDate, val time: LocalTime?, val endTime: LocalTime? = null) {
        fun daysLate(today: LocalDate): Long = ChronoUnit.DAYS.between(date, today)
    }

    data class Day(val date: LocalDate, val entries: List<Entry>)

    data class Result(val overdue: List<Entry>, val days: List<Day>)

    /**
     * Agenda from [today] for [dayCount] days. Notes that are done are left out. A planning
     * time before today goes to "overdue"; repeating times also appear on each later occurrence.
     */
    fun build(notes: List<OrgNote>, today: LocalDate, dayCount: Int = 7): Result {
        val end = today.plusDays(dayCount.toLong())
        val overdue = mutableListOf<Entry>()
        val byDay = mutableMapOf<LocalDate, MutableList<Entry>>()

        for (note in notes) {
            if (note.isDone) continue
            for ((kind, timestamp) in planning(note) + OrgTime.events(note).map { Kind.Event to it }) {
                val planned = Planned.parse(timestamp) ?: continue
                // An event that has passed is over, not overdue
                val isOverdue = planned.start.toLocalDate() < today && kind != Kind.Event
                if (isOverdue) {
                    overdue += Entry(note, kind, planned.start.toLocalDate(), planned.timeOfDay, planned.endTime)
                }
                // An overdue repeating task shows once, as overdue, not again today
                val from = if (isOverdue) today.plusDays(1) else today
                planned.occurrences(from.atStartOfDay(), end.atStartOfDay()).forEach { at ->
                    byDay.getOrPut(at.toLocalDate()) { mutableListOf() } +=
                        Entry(note, kind, at.toLocalDate(), planned.timeOfDay?.let { at.toLocalTime() }, planned.endTime)
                }
            }
        }

        // By time, deadlines first, then by priority (none counts as B, as in Org)
        val order = compareBy<Entry>({ it.time == null }, { it.time }, { it.kind != Kind.Deadline }, { priorityRank(it.note.priority) }, { it.note.title })
        return Result(
            overdue = overdue.sortedWith(compareBy<Entry> { it.date }.then(order)),
            days = (0 until dayCount).map { i ->
                val date = today.plusDays(i.toLong())
                Day(date, byDay[date].orEmpty().sortedWith(order))
            },
        )
    }

    /** Whether [note] has something for the agenda: not done, with a planning time or an event. */
    fun hasDates(note: OrgNote): Boolean =
        !note.isDone && (planning(note).map { it.second } + OrgTime.events(note)).any { Planned.parse(it) != null }

    /** The tags of the notes with something for the agenda, as the tag filter offers them. */
    fun tags(notes: List<OrgNote>): List<String> =
        notes.filter(::hasDates).flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

    private fun priorityRank(priority: String?): Int =
        priority?.uppercase()?.let { "ABC".indexOf(it) }?.takeIf { it >= 0 } ?: 1

    data class Reminder(val note: OrgNote, val kind: Kind, val at: LocalDateTime)

    data class ReminderOptions(
        val scheduled: Boolean = true,
        val deadline: Boolean = true,
        /** Time used for dates without a time of day; null means no reminder for those. */
        val allDayTime: LocalTime? = null,
        /** No reminders for template notes (:template:). */
        val skipTemplates: Boolean = false,
    )

    /** Reminders due after [from] (exclusive) and up to [to] (inclusive). */
    fun reminders(notes: List<OrgNote>, from: LocalDateTime, to: LocalDateTime, options: ReminderOptions): List<Reminder> =
        reminderTimes(notes, options) { planned -> planned.occurrences(from.plusNanos(1), to.plusNanos(1)) }
            .sortedBy { it.at }

    /** Time of the first reminder strictly after [after], looking up to a year ahead. */
    fun nextReminder(notes: List<OrgNote>, after: LocalDateTime, options: ReminderOptions): LocalDateTime? =
        reminderTimes(notes, options) { planned ->
            planned.occurrences(after.plusNanos(1), after.plusYears(1)).take(1)
        }.minOfOrNull { it.at }

    private fun reminderTimes(
        notes: List<OrgNote>,
        options: ReminderOptions,
        occurrences: (Planned) -> Sequence<LocalDateTime>,
    ): List<Reminder> {
        val result = mutableListOf<Reminder>()
        for (note in notes) {
            if (note.isDone || options.skipTemplates && Templates.isTemplate(note)) continue
            for ((kind, timestamp) in planning(note)) {
                if (kind == Kind.Scheduled && !options.scheduled) continue
                if (kind == Kind.Deadline && !options.deadline) continue
                val planned = Planned.parse(timestamp, options.allDayTime) ?: continue
                if (planned.timeOfDay == null) continue
                occurrences(planned).forEach { result += Reminder(note, kind, it) }
            }
        }
        return result
    }

    private fun planning(note: OrgNote) = listOfNotNull(
        note.scheduled?.let { Kind.Scheduled to it },
        note.deadline?.let { Kind.Deadline to it },
    )

    /** A planning timestamp: its start, time of day, and repeater interval if any. */
    private class Planned(
        val start: LocalDateTime,
        val timeOfDay: LocalTime?,
        val step: Long,
        val unit: ChronoUnit?,
        val endTime: LocalTime? = null,
    ) {

        /** Occurrences in [from, before). */
        fun occurrences(from: LocalDateTime, before: LocalDateTime): Sequence<LocalDateTime> {
            if (unit == null || step <= 0) {
                return if (start >= from && start < before) sequenceOf(start) else emptySequence()
            }
            // Jump close to the interval instead of stepping from the start one by one
            var at = start
            if (at < from) {
                val periods = unit.between(at, from) / step
                at = at.plus(periods * step, unit)
                while (at < from) at = at.plus(step, unit)
            }
            return generateSequence(at) { it.plus(step, unit) }.takeWhile { it < before }
        }

        companion object {
            fun parse(timestamp: String, allDayTime: LocalTime? = null): Planned? {
                val parts = OrgTime.parts(timestamp) ?: return null
                val timeOfDay = parts.time ?: allDayTime
                val start = parts.date.atTime(timeOfDay ?: LocalTime.MIDNIGHT)
                val repeater = OrgTime.range(timestamp)?.startTime?.takeIf { it.hasRepeater() }?.repeater
                val unit = when (repeater?.unit) {
                    OrgInterval.Unit.HOUR -> ChronoUnit.HOURS
                    OrgInterval.Unit.DAY -> ChronoUnit.DAYS
                    OrgInterval.Unit.WEEK -> ChronoUnit.WEEKS
                    OrgInterval.Unit.MONTH -> ChronoUnit.MONTHS
                    OrgInterval.Unit.YEAR -> ChronoUnit.YEARS
                    null -> null
                }
                return Planned(start, timeOfDay, repeater?.value?.toLong() ?: 0, unit, parts.endTime)
            }
        }
    }
}
