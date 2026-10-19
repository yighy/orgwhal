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
                runCatching { repository.saveNote(note, NoteStates.apply(note.toEdit(), target, keywords).edit) }
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
                val now = LocalDateTime.now()
                library.notes
                    .filter { (it.fileUri != note.fileUri || it.index != note.index) && Clock.running(it.content) != null }
                    .forEach { other -> runCatching { repository.saveNote(other, other.toEdit().copy(content = Clock.clockOut(other.content, now))) } }
                // As written now, in case another note of its file was just saved
                val latest = repository.library.value.note(note.fileUri, note.index)?.takeIf { it.title == note.title } ?: note
                runCatching { repository.saveNote(latest, latest.toEdit().copy(content = Clock.clockIn(latest.content, now))) }
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
                    NoteStates.apply(note.toEdit(), target, keywords).edit
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

    companion object {
        const val ACTION_REMIND = "io.github.yighy.orgwhal.action.REMIND"
        const val ACTION_DONE = "io.github.yighy.orgwhal.action.DONE"
        const val ACTION_SNOOZE = "io.github.yighy.orgwhal.action.SNOOZE"
        const val ACTION_CLOCK_IN = "io.github.yighy.orgwhal.action.CLOCK_IN"
        const val ACTION_CLOCK_OUT = "io.github.yighy.orgwhal.action.CLOCK_OUT"
        const val ACTION_CLOCK_DONE = "io.github.yighy.orgwhal.action.CLOCK_DONE"
    }
}
