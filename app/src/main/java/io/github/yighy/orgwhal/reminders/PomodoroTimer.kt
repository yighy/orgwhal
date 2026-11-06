package io.github.yighy.orgwhal.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.yighy.orgwhal.R
import io.github.yighy.orgwhal.core.Pomodoro
import io.github.yighy.orgwhal.ui.MainActivity
import io.github.yighy.orgwhal.ui.displayTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The pomodoro under way ([Pomodoro]), kept across restarts, with an exact alarm at the end of
 * each phase: [ReminderReceiver] then stops the clock and starts the break, or says the break is
 * over. While the pomodoro runs, the clock's notification counts down ([ClockNotifications]);
 * breaks have a notification of their own, which rings as they start and end.
 */
class PomodoroTimer(private val context: Context) {

    private val prefs = context.getSharedPreferences("pomodoro", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Pomodoro.State?> = _state.asStateFlow()

    /** Sets [state] as the one under way (null: none), its alarm with it ([alarmClock]: as an alarm clock). */
    @Synchronized
    fun set(state: Pomodoro.State?, alarmClock: Boolean) {
        _state.value = state
        prefs.edit().apply { if (state == null) remove(KEY) else putString(KEY, save(state)) }.apply()
        alarms.cancel(alarmIntent())
        if (state == null || state.phase == Pomodoro.Phase.Over) return
        val at = toMillis(state.endsAt)
        val canExact = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        when {
            alarmClock && canExact -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(0)), alarmIntent())
            canExact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent())
        }
    }

    /** The break's notification: counting down, ringing as it starts; or saying it is over, with the next pomodoro. */
    fun showBreak(state: Pomodoro.State) {
        if (!ReminderNotifications.canPost(context)) return
        val over = state.phase == Pomodoro.Phase.Over
        val title = context.getString(
            when (state.phase) {
                Pomodoro.Phase.LongBreak -> R.string.pomodoro_long_break
                Pomodoro.Phase.Over -> R.string.pomodoro_break_over
                else -> R.string.pomodoro_break
            },
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.resources.getQuantityString(R.plurals.pomodoro_done, state.done, state.done, displayTitle(state.title)))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(1, state))
            .addAction(0, context.getString(if (over) R.string.pomodoro_resume else R.string.pomodoro_skip_break), broadcast(ReminderReceiver.ACTION_POMODORO_NEXT, 1))
            .addAction(0, context.getString(R.string.pomodoro_stop), broadcast(ReminderReceiver.ACTION_POMODORO_STOP, 2))
        if (!over) {
            // The break counts down by itself, ongoing until it ends
            builder
                .setWhen(toMillis(state.endsAt))
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setOngoing(true)
        } else {
            builder.setAutoCancel(true)
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked in the meantime
        }
    }

    fun hideBreak() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun broadcast(action: String, code: Int) = PendingIntent.getBroadcast(
        context,
        REQUEST_BASE + code,
        Intent(context, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Opens the app, on the pomodoro's note when given. */
    private fun openApp(code: Int, state: Pomodoro.State? = null): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_BASE + 10 + code,
        Intent(context, MainActivity::class.java).apply {
            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (state != null) {
                putExtra(ReminderNotifications.EXTRA_FILE_URI, state.fileUri)
                putExtra(ReminderNotifications.EXTRA_INDEX, state.index)
                putExtra(ReminderNotifications.EXTRA_TITLE, state.title)
            }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_BASE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_POMODORO_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun load(): Pomodoro.State? = runCatching {
        val o = JSONObject(prefs.getString(KEY, null) ?: return null)
        Pomodoro.State(
            o.getString("uri"),
            o.getInt("index"),
            o.getString("title"),
            Pomodoro.Phase.valueOf(o.getString("phase")),
            toLocal(o.getLong("started")),
            toLocal(o.getLong("ends")),
            o.getInt("done"),
        )
    }.getOrNull()

    private fun save(s: Pomodoro.State) = JSONObject()
        .put("uri", s.fileUri).put("index", s.index).put("title", s.title).put("phase", s.phase.name)
        .put("started", toMillis(s.startedAt)).put("ends", toMillis(s.endsAt)).put("done", s.done)
        .toString()

    private fun toMillis(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun toLocal(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    companion object {
        private const val CHANNEL_ID = "pomodoro"
        private const val KEY = "state"
        private const val NOTIFICATION_ID = 0x504F4D
        private const val REQUEST_BASE = 7000

        /** Breaks ring: a channel of their own, with sound. */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_pomodoro), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_pomodoro_description)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
