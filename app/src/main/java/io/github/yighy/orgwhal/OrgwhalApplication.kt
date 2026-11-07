package io.github.yighy.orgwhal

import android.content.Context
import io.github.yighy.orgwhal.data.AppLanguage
import android.app.Application
import io.github.yighy.orgwhal.data.AppSettings
import io.github.yighy.orgwhal.data.NoteRepository
import io.github.yighy.orgwhal.core.Pomodoro
import io.github.yighy.orgwhal.reminders.ClockNotifications
import io.github.yighy.orgwhal.reminders.PomodoroTimer
import java.time.LocalDateTime
import io.github.yighy.orgwhal.reminders.ReminderNotifications
import io.github.yighy.orgwhal.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Holds the app-wide repository and settings, shared by the UI and the reminder receiver,
 * and keeps the next reminder alarm and the running clocks' notifications in sync with the notes.
 */
class OrgwhalApplication : Application() {

    // The language chosen in the settings, before Android 13 (notifications use this context)
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository by lazy { NoteRepository(this) }
    val settings by lazy { AppSettings(this) }
    val scheduler by lazy { ReminderScheduler(this) }
    val pomodoro by lazy { PomodoroTimer(this) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        ClockNotifications.createChannel(this)
        PomodoroTimer.createChannel(this)

        // Default state keywords before any file is read, then follow the setting
        repository.defaultKeywords = settings.values.value.keywords
        scope.launch {
            settings.values.map { it.keywords }.distinctUntilChanged().collect { repository.setDefaultKeywords(it) }
        }

        // Reschedule the next alarm whenever the notes or the reminder settings change
        scope.launch {
            combine(repository.library.filter { it.loaded }, settings.values) { library, values ->
                Triple(library.active, values.reminderOptions, values.useAlarmClock)
            }
                .distinctUntilChanged()
                .debounce(500)
                .collect { (notes, options, alarmClock) -> scheduler.reschedule(notes, options, alarmClock) }
        }

        // A notification for each running clock, as the files say (started or stopped anywhere);
        // a pomodoro whose clock was stopped otherwise (its note's screen, Emacs…) is over
        scope.launch {
            combine(repository.library.filter { it.loaded }.map { ClockNotifications.running(it) }.distinctUntilChanged(), pomodoro.state) { a, b -> a to b }
                .collect { (clocks, state) ->
                    ClockNotifications.show(this@OrgwhalApplication, clocks, state)
                    val stopped = state != null && state.phase == Pomodoro.Phase.Work &&
                        LocalDateTime.now().isAfter(state.startedAt.plusSeconds(20)) &&
                        clocks.none { Pomodoro.runs(state, it.fileUri, it.title, it.start) }
                    if (stopped) pomodoro.set(null, settings.values.value.useAlarmClock)
                }
        }
    }
}
