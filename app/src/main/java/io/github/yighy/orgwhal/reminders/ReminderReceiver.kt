package io.github.yighy.orgwhal.reminders

import java.time.LocalDateTime
import io.github.yighy.orgwhal.core.Agenda
import io.github.yighy.orgwhal.core.Clock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.yighy.orgwhal.OrgwhalApplication
import io.github.yighy.orgwhal.core.Keywords
import io.github.yighy.orgwhal.core.NoteStates
import io.github.yighy.orgwhal.core.NoteTree
import io.github.yighy.orgwhal.core.OrgNote
import io.github.yighy.orgwhal.core.Pomodoro
import io.github.yighy.orgwhal.data.NoteRepository
import io.github.yighy.orgwhal.ui.displayTitle
import kotlinx.coroutines.launch

/**
 * Shows due reminders when the alarm fires, marks a note done or starts its clock from its
 * notification, stops a clock from its notification, and sets the alarm again after a reboot, an app update or a clock change.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as OrgwhalApplication
        val pending = goAsync()
        app.scope.launch {
            try {
                handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(app: OrgwhalApplication, intent: Intent) {
        val repository = app.repository
        repository.refresh()
        val library = repository.library.value
        if (!library.loaded) return
        val options = app.settings.values.value.reminderOptions
        val alarmClock = app.settings.values.value.useAlarmClock

        when (intent.action) {
            ACTION_DONE -> {
                val uri = intent.getStringExtra(ReminderNotifications.EXTRA_FILE_URI) ?: return
                val index = intent.getIntExtra(ReminderNotifications.EXTRA_INDEX, -1)
                val title = intent.getStringExtra(ReminderNotifications.EXTRA_TITLE)
                ReminderNotifications.cancel(app, intent.getIntExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, 0))
                // Same position and title as when notified, so a moved note is not touched
                val note = library.note(uri, index)?.takeIf { it.title == title } ?: return
                val keywords = library.file(uri)?.doc?.keywords ?: Keywords.DEFAULT
                val target = keywords.done.firstOrNull() ?: return
                val log = NoteStates.repeatLog(library.file(uri)?.doc?.preface.orEmpty())
                runCatching { repository.saveNote(note, NoteStates.apply(note.toEdit(), target, keywords, log = log).edit) }
            }

            // The reminder's Clock in button: the clock starts as from the note's screen, stopping another one
            ACTION_CLOCK_IN -> {
                val uri = intent.getStringExtra(ReminderNotifications.EXTRA_FILE_URI) ?: return
                val index = intent.getIntExtra(ReminderNotifications.EXTRA_INDEX, -1)
                val title = intent.getStringExtra(ReminderNotifications.EXTRA_TITLE)
                ReminderNotifications.cancel(app, intent.getIntExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, 0))
                val note = library.note(uri, index)?.takeIf { it.title == title }
                    ?: library.file(uri)?.notes?.firstOrNull { it.title == title }
                    ?: return
                clockIn(app, note, LocalDateTime.now())
            }

            // A pomodoro's or a break's end, as their alarm says: the clock stops and the break
            // starts, or the break is over and the next pomodoro waits
            ACTION_POMODORO_END -> {
                val state = app.pomodoro.state.value ?: return
                when (state.phase) {
                    Pomodoro.Phase.Work -> {
                        val note = pomodoroNote(library, state)
                        val clock = note?.let { Clock.running(it.content) }
                        if (note == null || clock == null || !Pomodoro.runs(state, note, clock.start)) {
                            app.pomodoro.set(null, alarmClock)
                        } else {
                            runCatching { repository.saveNote(note, note.toEdit().copy(content = Clock.clockOut(note.content, state.endsAt))) }
                            val pause = Pomodoro.breakAfter(state, app.settings.values.value.pomodoro)
                            app.pomodoro.set(pause, alarmClock)
                            app.pomodoro.showBreak(pause)
                        }
                    }
                    Pomodoro.Phase.ShortBreak, Pomodoro.Phase.LongBreak -> {
                        val over = state.copy(phase = Pomodoro.Phase.Over)
                        app.pomodoro.set(over, alarmClock)
                        app.pomodoro.showBreak(over)
                    }
                    Pomodoro.Phase.Over -> Unit
                }
            }

            // The break's notification: the next pomodoro on the same note (the break cut short), or none
            ACTION_POMODORO_NEXT -> {
                val state = app.pomodoro.state.value ?: return
                app.pomodoro.hideBreak()
                val note = pomodoroNote(library, state)
                if (note == null) {
                    app.pomodoro.set(null, alarmClock)
                } else {
                    val now = LocalDateTime.now()
                    // Set before the clock is written, which would otherwise stop it
                    app.pomodoro.set(Pomodoro.start(note, app.settings.values.value.pomodoro, now, state.done), alarmClock)
                    clockIn(app, note, now)
                }
            }

            ACTION_POMODORO_STOP -> {
                app.pomodoro.hideBreak()
                app.pomodoro.set(null, alarmClock)
            }

            // The clock notification's buttons: Stop, as from the note's screen, or Done, which stops it too
            ACTION_CLOCK_OUT, ACTION_CLOCK_DONE -> {
                val uri = intent.getStringExtra(ReminderNotifications.EXTRA_FILE_URI) ?: return
                val index = intent.getIntExtra(ReminderNotifications.EXTRA_INDEX, -1)
                val title = intent.getStringExtra(ReminderNotifications.EXTRA_TITLE)
                // At its place if it is still there, else the note of that title whose clock runs
                val note = library.note(uri, index)?.takeIf { it.title == title && Clock.running(it.content) != null }
                    ?: library.file(uri)?.notes?.firstOrNull { it.title == title && Clock.running(it.content) != null }
                    ?: return
                val edit = if (intent.action == ACTION_CLOCK_DONE) {
                    val keywords = library.file(uri)?.doc?.keywords ?: Keywords.DEFAULT
                    val target = keywords.done.firstOrNull() ?: return
                    NoteStates.apply(note.toEdit(), target, keywords, log = NoteStates.repeatLog(library.file(uri)?.doc?.preface.orEmpty())).edit
                } else {
                    note.toEdit().copy(content = Clock.clockOut(note.content, LocalDateTime.now()))
                }
                runCatching { repository.saveNote(note, edit) }
            }

            // Shown again later: after the snooze set in the settings, or tomorrow at the same time
            ACTION_SNOOZE -> {
                val uri = intent.getStringExtra(ReminderNotifications.EXTRA_FILE_URI) ?: return
                val title = intent.getStringExtra(ReminderNotifications.EXTRA_TITLE) ?: return
                val kind = intent.getStringExtra(ReminderNotifications.EXTRA_KIND)?.let(Agenda.Kind::valueOf) ?: Agenda.Kind.Scheduled
                val minutes = intent.getLongExtra(ReminderNotifications.EXTRA_SNOOZE_MINUTES, 60)
                ReminderNotifications.cancel(app, intent.getIntExtra(ReminderNotifications.EXTRA_NOTIFICATION_ID, 0))
                val at = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(minutes)
                app.scheduler.snooze(ReminderScheduler.Snooze(uri, title, kind, at))
            }

            // Alarm, or boot / update / clock change: also shows reminders missed meanwhile
            else -> {
                app.scheduler.takeDue(library.notes, options).forEach { reminder ->
                    val file = library.file(reminder.note.fileUri)
                    val notebook = file?.title ?: reminder.note.fileName
                    val ancestors = file?.notes?.let { NoteTree.ancestors(it, reminder.note.index) }.orEmpty().map { displayTitle(it.title) }
                    ReminderNotifications.show(app, reminder, notebook, ancestors, library.notes)
                }
            }
        }

        // Boot, update, clock change, or after showing: set the next alarm
        app.scheduler.reschedule(repository.library.value.notes, options, alarmClock)
    }

    /** Starts [note]'s clock at [now], as from its screen, stopping another one. */
    private suspend fun clockIn(app: OrgwhalApplication, note: OrgNote, now: LocalDateTime) {
        val repository = app.repository
        repository.library.value.notes
            .filter { (it.fileUri != note.fileUri || it.index != note.index) && Clock.running(it.content) != null }
            .forEach { other -> runCatching { repository.saveNote(other, other.toEdit().copy(content = Clock.clockOut(other.content, now))) } }
        // As written now, in case another note of its file was just saved
        val latest = repository.library.value.note(note.fileUri, note.index)?.takeIf { it.title == note.title } ?: note
        runCatching { repository.saveNote(latest, latest.toEdit().copy(content = Clock.clockIn(latest.content, now))) }
    }

    /** The pomodoro's note: at its place if it is still there, else the note of that title. */
    private fun pomodoroNote(library: NoteRepository.Library, state: Pomodoro.State): OrgNote? =
        library.note(state.fileUri, state.index)?.takeIf { it.title == state.title }
            ?: library.file(state.fileUri)?.notes?.firstOrNull { it.title == state.title }

    companion object {
        const val ACTION_REMIND = "io.github.yighy.orgwhal.action.REMIND"
        const val ACTION_DONE = "io.github.yighy.orgwhal.action.DONE"
        const val ACTION_SNOOZE = "io.github.yighy.orgwhal.action.SNOOZE"
        const val ACTION_CLOCK_IN = "io.github.yighy.orgwhal.action.CLOCK_IN"
        const val ACTION_CLOCK_OUT = "io.github.yighy.orgwhal.action.CLOCK_OUT"
        const val ACTION_CLOCK_DONE = "io.github.yighy.orgwhal.action.CLOCK_DONE"
        const val ACTION_POMODORO_END = "io.github.yighy.orgwhal.action.POMODORO_END"
        const val ACTION_POMODORO_NEXT = "io.github.yighy.orgwhal.action.POMODORO_NEXT"
        const val ACTION_POMODORO_STOP = "io.github.yighy.orgwhal.action.POMODORO_STOP"
    }
}
