package io.github.yighy.orgwhal.core

import com.orgzly.org.utils.StateChangeLogic
import java.time.LocalDateTime

/** State changes with Org semantics (CLOSED time, repeating tasks). */
object NoteStates {

    /** TODO ↔ DONE: done notes go back to the first to-do keyword, others to the first done keyword. */
    fun toggleTarget(note: OrgNote, keywords: Keywords): String? = when {
        note.isDone || note.state == null -> keywords.todo.firstOrNull()
        else -> keywords.done.firstOrNull()
    }

    data class Result(val edit: NoteEdit, val shifted: Boolean)

    /** Where a repeating task marked done is logged: nowhere, in its :LOGBOOK:, or in its text. */
    enum class RepeatLog { None, Drawer, Body }

    /**
     * As a file's #+STARTUP sets it: nologrepeat logs nothing; nologdrawer logs in the note's
     * text, as Emacs does by default; else in its :LOGBOOK:, as it is mostly set up.
     */
    fun repeatLog(preface: String): RepeatLog {
        val words = STARTUP.findAll(preface).flatMap { it.groupValues[1].trim().split(Regex("\\s+")) }.map { it.lowercase() }.toList()
        return when {
            "nologrepeat" in words -> RepeatLog.None
            "nologdrawer" in words -> RepeatLog.Body
            else -> RepeatLog.Drawer
        }
    }

    /**
     * Sets [target] as Org does: marking a task done adds a CLOSED time, unless it has a
     * repeater, in which case its dates move forward and it stays to-do; that is logged as [log]
     * says, with a LAST_REPEAT property (habits are drawn from these lines). Going back to to-do
     * removes the CLOSED time. Marked done (repeating or not), its running clock stops at [now],
     * as Emacs does by default (org-clock-out-when-done).
     */
    fun apply(
        edit: NoteEdit,
        target: String?,
        keywords: Keywords,
        now: LocalDateTime = LocalDateTime.now(),
        log: RepeatLog = RepeatLog.Drawer,
    ): Result {
        val logic = StateChangeLogic(keywords.done)
        logic.setState(
            target,
            edit.state,
            edit.scheduled?.let(OrgTime::range),
            edit.deadline?.let(OrgTime::range),
        )
        val content = if (target != null && target in keywords.done) Clock.clockOut(edit.content, now) else edit.content
        val logged = logic.isShifted && target != null && log != RepeatLog.None
        val stamp = OrgTime.inactive(now)
        return Result(
            edit.copy(
                state = logic.state,
                scheduled = logic.scheduled?.toString(),
                deadline = logic.deadline?.toString(),
                closed = logic.closed?.toString(),
                content = if (logged) logState(content, target!!, edit.state, stamp, log) else content,
                properties = if (logged) edit.properties.filterNot { it.first.equals(LAST_REPEAT, ignoreCase = true) } + (LAST_REPEAT to stamp) else edit.properties,
            ),
            logic.isShifted,
        )
    }

    /** [content] with the change from [from] to [to] logged, newest first, as Org writes it. */
    private fun logState(content: String, to: String, from: String?, stamp: String, log: RepeatLog): String {
        val line = "- State %-12s from %-12s %s".format("\"$to\"", from?.let { "\"$it\"" } ?: "", stamp)
        val rest = content.trimStart('\n')
        if (log == RepeatLog.Body) return line + if (rest.isEmpty()) "" else "\n$rest"
        val drawer = LOGBOOK.find(content)
        if (drawer != null) {
            val at = drawer.range.last + 1
            return content.substring(0, at) + "\n" + drawer.groupValues[1] + line + content.substring(at)
        }
        return ":LOGBOOK:\n$line\n:END:" + if (rest.isEmpty()) "" else "\n$rest"
    }

    private const val LAST_REPEAT = "LAST_REPEAT"
    private val LOGBOOK = Regex("""^([ \t]*):LOGBOOK:[ \t]*$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val STARTUP = Regex("""^[ \t]*#\+STARTUP:(.*)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
}
