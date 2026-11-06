package io.github.yighy.orgwhal.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.yighy.orgwhal.R
import io.github.yighy.orgwhal.core.Clock
import io.github.yighy.orgwhal.core.NoteTree
import io.github.yighy.orgwhal.core.Pomodoro
import io.github.yighy.orgwhal.data.NoteRepository
import io.github.yighy.orgwhal.ui.MainActivity
import io.github.yighy.orgwhal.ui.displayTitle
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A silent, ongoing notification for each running clock, its time counting from the clock's
 * start, with buttons to stop it and, for a to-do note, to set it done. Kept in step with the files: a clock started or stopped
 * anywhere (here, in Emacs, on another device) shows or takes away its notification.
 */
object ClockNotifications {

    private const val CHANNEL_ID = "clock"
    private const val TAG = "clock"

    /**
     * A note whose clock runs, and where it is: its notebook, then its ancestors; [canBeDone] when
     * it has a to-do state, which the notification can set done.
     */
    data class Running(
        val fileUri: String,
        val index: Int,
        val title: String,
        val state: String?,
        val canBeDone: Boolean,
        val path: List<String>,
        val start: LocalDateTime,
    )

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_clock), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_clock_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** The running clocks of [library]'s notes. */
    fun running(library: NoteRepository.Library): List<Running> = library.files.flatMap { file ->
        val ancestors by lazy { file.notes.map { NoteTree.ancestors(file.notes, it.index) } }
        file.notes.mapNotNull { note ->
            val clock = Clock.running(note.content) ?: return@mapNotNull null
            val path = listOf(file.title) + ancestors[note.index].map { displayTitle(it.title) }
            Running(note.fileUri, note.index, note.title, note.state, note.state != null && !note.isDone, path, clock.start)
        }
    }

    /**
     * Shows a notification for each of [clocks], and takes away those of clocks no longer running;
     * the clock of the [pomodoro] under way counts down to its end.
     */
    fun show(context: Context, clocks: List<Running>, pomodoro: Pomodoro.State? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ids = clocks.map { id(it) }.toSet()
        manager.activeNotifications.filter { it.tag == TAG && it.id !in ids }.forEach { manager.cancel(TAG, it.id) }
        if (!ReminderNotifications.canPost(context)) return
        clocks.forEach { clock -> post(context, clock, pomodoro?.takeIf { Pomodoro.runs(it, clock.fileUri, clock.title, clock.start) }) }
    }

    private fun post(context: Context, clock: Running, pomodoro: Pomodoro.State?) {
        val id = id(clock)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ReminderNotifications.EXTRA_FILE_URI, clock.fileUri)
                .putExtra(ReminderNotifications.EXTRA_INDEX, clock.index)
                .putExtra(ReminderNotifications.EXTRA_TITLE, clock.title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        /** The receiver's [action] on this clock's note; each its own request code, so both buttons work. */
        fun broadcast(action: String, code: Int) = PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderNotifications.EXTRA_FILE_URI, clock.fileUri)
                .putExtra(ReminderNotifications.EXTRA_INDEX, clock.index)
                .putExtra(ReminderNotifications.EXTRA_TITLE, clock.title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(listOfNotNull(clock.state, displayTitle(clock.title)).joinToString(" "))
            .setContentText(clock.path.joinToString(" › "))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            // The system counts the time from the clock's start: nothing to update meanwhile
            .setWhen(clock.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.notification_stop_clock), broadcast(ReminderReceiver.ACTION_CLOCK_OUT, id))
        // A to-do note can be done from here, which stops its clock too
        if (clock.canBeDone) builder.addAction(0, context.getString(R.string.notification_done), broadcast(ReminderReceiver.ACTION_CLOCK_DONE, id + 1))
        // A pomodoro counts down to its end instead
        if (pomodoro != null) {
            builder
                .setWhen(pomodoro.endsAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .setChronometerCountDown(true)
                .setSubText(context.getString(R.string.pomodoro))
        }
        val notification = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(TAG, id, notification)
        } catch (_: SecurityException) {
            // Permission revoked in the meantime
        }
    }

    private fun id(clock: Running) = "${clock.fileUri}#${clock.index}#${clock.start}".hashCode()
}
