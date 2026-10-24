package io.github.yighy.orgwhal.data

import io.github.yighy.orgwhal.R
import android.content.res.Resources
import androidx.annotation.StringRes
import android.content.SharedPreferences
import org.json.JSONObject
import org.json.JSONArray
import android.content.Context
import io.github.yighy.orgwhal.core.Agenda
import io.github.yighy.orgwhal.core.Keywords
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalTime

/** Light or dark appearance: as the system, or always one of them. */
enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark),
}

/** Room around each note of a notebook's outline: [padding] above and below its title. */
enum class NoteSpacing(@StringRes val label: Int, val padding: Int) {
    Comfortable(R.string.spacing_comfortable, 12),
    Compact(R.string.spacing_compact, 7),
    Dense(R.string.spacing_dense, 3),
}

/**
 * How far sub-notes go in from their parent in a notebook's outline: [first] for the first two
 * levels in, [deeper] after them (dp).
 */
enum class Indentation(@StringRes val label: Int, val first: Int, val deeper: Int) {
    Wide(R.string.indentation_wide, 24, 24),
    Tapering(R.string.indentation_tapering, 24, 16),
    // About as Emacs' org-indent-mode, two characters a level
    Compact(R.string.indentation_compact, 16, 16),
}

/** When a note is renamed, links to it that still show its old title can follow. */
enum class LinkRenaming(@StringRes val label: Int) {
    Ask(R.string.link_renaming_ask),
    Always(R.string.link_renaming_always),
    Never(R.string.link_renaming_never),
}

/**
 * Whether a note's tables with formulas are recalculated when they are shown: never (the
 * Recalculate button does it), on screen only (the file keeps its values until saved), or saved.
 */
enum class TableRecalc(@StringRes val label: Int) {
    Never(R.string.tables_never),
    Show(R.string.tables_show),
    Save(R.string.tables_save),
}

/** Where template notes (:template:) are left out: they stay in their notebooks anyway. */
enum class TemplatesHidden(@StringRes val label: Int) {
    Nowhere(R.string.templates_hidden_nowhere),
    Agenda(R.string.templates_hidden_agenda),
    AgendaAndSearch(R.string.templates_hidden_agenda_search),
}

/** The reminder notification's third button, after Done and the snooze: start the clock, or snooze until tomorrow. */
enum class ReminderButton(@StringRes val label: Int) {
    ClockIn(R.string.notification_clock_in),
    Tomorrow(R.string.notification_tomorrow),
}

/** A search kept under a name, run from the drawer (as Orgzly's saved searches). */
data class SavedSearch(val name: String, val query: String)

/** User preferences, stored in SharedPreferences. */
class AppSettings(context: Context) {

    data class Values(
        /** Show each note's content under its title in the notebook outline. */
        val showContentInNotebook: Boolean = true,
        /** Strike through the titles of done notes in lists. */
        val strikeDone: Boolean = true,
        /** Notes' content (and notebooks' prefaces) in a fixed-width font, as in Emacs. */
        val monospace: Boolean = false,
        /** Long table fields wrap onto several lines, columns fitting the screen (display only). */
        val wrapTables: Boolean = true,
        /** A table wider than the screen keeps its first column at the left as it scrolls sideways. */
        val freezeFirstColumn: Boolean = false,
        val noteSpacing: NoteSpacing = NoteSpacing.Comfortable,
        val indentation: Indentation = Indentation.Tapering,
        val remindScheduled: Boolean = true,
        val remindDeadline: Boolean = true,
        /** Also remind for dates without a time of day, at [allDayTime]. */
        val remindAllDay: Boolean = false,
        val allDayTime: LocalTime = LocalTime.of(9, 0),
        /** State keywords for files without a #+TODO line. */
        val keywords: Keywords = Keywords.DEFAULT,
        /** Give every new note an ID property, so it can be linked to with id:. */
        val addIdToNewNotes: Boolean = false,
        /** Properties not shown on the note screen (kept in the file), in upper case. */
        val hiddenProperties: Set<String> = emptySet(),
        /** Give every new note a CREATED property with when it was created. */
        val addCreatedToNewNotes: Boolean = false,
        /** Set reminders as alarm clock alarms (the most reliable; an alarm icon shows). */
        val useAlarmClock: Boolean = false,
        /** How long the notification's snooze button puts a reminder off. */
        val snoozeMinutes: Int = 60,
        /** The reminder notification's third button. */
        val reminderButton: ReminderButton = ReminderButton.ClockIn,
        /** What happens to links still showing a note's old title when it is renamed. */
        val linkRenaming: LinkRenaming = LinkRenaming.Ask,
        /** Whether tables with formulas are recalculated when shown. */
        val tableRecalc: TableRecalc = TableRecalc.Never,
        /** Whether a tap on a table field opens it for editing. */
        val editCells: Boolean = true,
        /** Where template notes are left out (with no reminders when out of the agenda). */
        val templatesHidden: TemplatesHidden = TemplatesHidden.AgendaAndSearch,
        val themeMode: ThemeMode = ThemeMode.System,
        /** Colors taken from the wallpaper (Android 12+). */
        val dynamicColor: Boolean = true,
    ) {
        val reminderOptions: Agenda.ReminderOptions
            get() = Agenda.ReminderOptions(remindScheduled, remindDeadline, allDayTime.takeIf { remindAllDay }, skipTemplates = templatesHidden != TemplatesHidden.Nowhere)
    }

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _values = MutableStateFlow(
        Values(
            showContentInNotebook = prefs.getBoolean(KEY_SHOW_CONTENT, true),
            strikeDone = prefs.getBoolean(KEY_STRIKE_DONE, true),
            monospace = prefs.getBoolean(KEY_MONOSPACE, false),
            wrapTables = prefs.getBoolean(KEY_WRAP_TABLES, true),
            freezeFirstColumn = prefs.getBoolean(KEY_FREEZE_FIRST_COLUMN, false),
            noteSpacing = NoteSpacing.entries.firstOrNull { it.name == prefs.getString(KEY_NOTE_SPACING, null) } ?: NoteSpacing.Comfortable,
            indentation = Indentation.entries.firstOrNull { it.name == prefs.getString(KEY_INDENTATION, null) } ?: Indentation.Tapering,
            remindScheduled = prefs.getBoolean(KEY_REMIND_SCHEDULED, true),
            remindDeadline = prefs.getBoolean(KEY_REMIND_DEADLINE, true),
            remindAllDay = prefs.getBoolean(KEY_REMIND_ALL_DAY, false),
            allDayTime = LocalTime.ofSecondOfDay(prefs.getInt(KEY_ALL_DAY_MINUTES, 9 * 60) * 60L),
            keywords = prefs.getString(KEY_KEYWORDS, null)?.let(Keywords::parse) ?: Keywords.DEFAULT,
            addIdToNewNotes = prefs.getBoolean(KEY_ADD_ID, false),
            hiddenProperties = parseProperties(prefs.getString(KEY_HIDDEN_PROPERTIES, null).orEmpty()),
            addCreatedToNewNotes = prefs.getBoolean(KEY_ADD_CREATED, false),
            useAlarmClock = prefs.getBoolean(KEY_ALARM_CLOCK, false),
            snoozeMinutes = prefs.getInt(KEY_SNOOZE_MINUTES, 60),
            reminderButton = ReminderButton.entries.firstOrNull { it.name == prefs.getString(KEY_REMINDER_BUTTON, null) } ?: ReminderButton.ClockIn,
            linkRenaming = LinkRenaming.entries.firstOrNull { it.name == prefs.getString(KEY_LINK_RENAMING, null) }
                ?: LinkRenaming.Ask,
            tableRecalc = TableRecalc.entries.firstOrNull { it.name == prefs.getString(KEY_TABLE_RECALC, null) } ?: TableRecalc.Never,
            templatesHidden = TemplatesHidden.entries.firstOrNull { it.name == prefs.getString(KEY_TEMPLATES_HIDDEN, null) } ?: TemplatesHidden.AgendaAndSearch,
            editCells = prefs.getBoolean(KEY_EDIT_CELLS, true),
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.System,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
        ),
    )
    val values: StateFlow<Values> = _values

    /** Emoji chosen as a notebook's icon, by file URI; kept in the app, not in the files. */
    private val _notebookIcons = MutableStateFlow(
        prefs.all.mapNotNull { (key, value) ->
            if (key.startsWith(ICON_PREFIX) && value is String) key.removePrefix(ICON_PREFIX) to value else null
        }.toMap(),
    )
    val notebookIcons: StateFlow<Map<String, String>> = _notebookIcons

    /** Sets the notebook's icon, or goes back to its initial with null. */
    fun setNotebookIcon(fileUri: String, emoji: String?) {
        prefs.edit().apply { if (emoji == null) remove(ICON_PREFIX + fileUri) else putString(ICON_PREFIX + fileUri, emoji) }.apply()
        _notebookIcons.update { if (emoji == null) it - fileUri else it + (fileUri to emoji) }
    }

    /** How a notebook's tree view was left: zoom, position, and folded notes by their path (see NoteTree.titlePaths). */
    data class TreeState(val scale: Float, val x: Float, val y: Float, val folded: Set<String>)

    fun treeState(fileUri: String): TreeState? = runCatching {
        val json = JSONObject(prefs.getString(TREE_PREFIX + fileUri, null) ?: return null)
        val folded = json.getJSONArray("folded")
        TreeState(
            json.getDouble("scale").toFloat(),
            json.getDouble("x").toFloat(),
            json.getDouble("y").toFloat(),
            List(folded.length()) { folded.getString(it) }.toSet(),
        )
    }.getOrNull()

    /** Keeps the tree view's [state] (null forgets it). */
    fun setTreeState(fileUri: String, state: TreeState?) {
        prefs.edit().apply {
            if (state == null) {
                remove(TREE_PREFIX + fileUri)
            } else {
                val json = JSONObject().put("scale", state.scale.toDouble()).put("x", state.x.toDouble()).put("y", state.y.toDouble())
                    .put("folded", JSONArray(state.folded.toList()))
                putString(TREE_PREFIX + fileUri, json.toString())
            }
        }.apply()
    }

    /** Folded notes of a notebook's outline, by their paths (see NoteTree.titlePaths); null until they first change. */
    fun folds(fileUri: String): Set<String>? = runCatching {
        val json = JSONArray(prefs.getString(FOLDS_PREFIX + fileUri, null) ?: return null)
        List(json.length()) { json.getString(it) }.toSet()
    }.getOrNull()

    /** Keeps the notebook's folded notes (null forgets them). */
    fun setFolds(fileUri: String, folds: Set<String>?) {
        prefs.edit().apply {
            if (folds == null) remove(FOLDS_PREFIX + fileUri) else putString(FOLDS_PREFIX + fileUri, JSONArray(folds.toList()).toString())
        }.apply()
    }

    private val _savedSearches = MutableStateFlow(readSearches())
    val savedSearches: StateFlow<List<SavedSearch>> = _savedSearches

    fun setSavedSearches(searches: List<SavedSearch>) {
        val json = JSONArray()
        searches.forEach { json.put(JSONObject().put("name", it.name).put("query", it.query)) }
        prefs.edit().putString(KEY_SEARCHES, json.toString()).apply()
        _savedSearches.value = searches
    }

    private fun readSearches(): List<SavedSearch> = runCatching {
        val json = JSONArray(prefs.getString(KEY_SEARCHES, null) ?: return emptyList())
        List(json.length()) { i -> json.getJSONObject(i).let { SavedSearch(it.getString("name"), it.getString("query")) } }
    }.getOrDefault(emptyList())

    /** Whether the guide notebook was already offered (once, when the folder is first chosen). */
    var guideOffered: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_OFFERED, false)
        set(value) = prefs.edit().putBoolean(KEY_GUIDE_OFFERED, value).apply()

    /** Whether the app already asked for the notification permission once. */
    var askedNotificationPermission: Boolean
        get() = prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, value).apply()

    /** Saves a setting with [edit] and updates [values] with [change]. */
    private fun save(edit: SharedPreferences.Editor.() -> Unit, change: (Values) -> Values) {
        prefs.edit().apply(edit).apply()
        _values.update(change)
    }

    fun setStrikeDone(on: Boolean) = save({ putBoolean(KEY_STRIKE_DONE, on) }) { it.copy(strikeDone = on) }

    fun setMonospace(on: Boolean) = save({ putBoolean(KEY_MONOSPACE, on) }) { it.copy(monospace = on) }

    fun setWrapTables(on: Boolean) = save({ putBoolean(KEY_WRAP_TABLES, on) }) { it.copy(wrapTables = on) }

    fun setFreezeFirstColumn(on: Boolean) = save({ putBoolean(KEY_FREEZE_FIRST_COLUMN, on) }) { it.copy(freezeFirstColumn = on) }

    fun setShowContentInNotebook(show: Boolean) =
        save({ putBoolean(KEY_SHOW_CONTENT, show) }) { it.copy(showContentInNotebook = show) }

    fun setNoteSpacing(spacing: NoteSpacing) = save({ putString(KEY_NOTE_SPACING, spacing.name) }) { it.copy(noteSpacing = spacing) }

    fun setIndentation(indentation: Indentation) =
        save({ putString(KEY_INDENTATION, indentation.name) }) { it.copy(indentation = indentation) }

    fun setRemindScheduled(on: Boolean) = save({ putBoolean(KEY_REMIND_SCHEDULED, on) }) { it.copy(remindScheduled = on) }

    fun setRemindDeadline(on: Boolean) = save({ putBoolean(KEY_REMIND_DEADLINE, on) }) { it.copy(remindDeadline = on) }

    fun setRemindAllDay(on: Boolean) = save({ putBoolean(KEY_REMIND_ALL_DAY, on) }) { it.copy(remindAllDay = on) }

    fun setAllDayTime(time: LocalTime) =
        save({ putInt(KEY_ALL_DAY_MINUTES, time.hour * 60 + time.minute) }) { it.copy(allDayTime = time) }

    fun setAddIdToNewNotes(on: Boolean) = save({ putBoolean(KEY_ADD_ID, on) }) { it.copy(addIdToNewNotes = on) }

    fun setHiddenProperties(names: Set<String>) =
        save({ putString(KEY_HIDDEN_PROPERTIES, names.joinToString(" ")) }) { it.copy(hiddenProperties = names) }

    fun setAddCreatedToNewNotes(on: Boolean) = save({ putBoolean(KEY_ADD_CREATED, on) }) { it.copy(addCreatedToNewNotes = on) }

    fun setUseAlarmClock(on: Boolean) = save({ putBoolean(KEY_ALARM_CLOCK, on) }) { it.copy(useAlarmClock = on) }

    fun setSnoozeMinutes(minutes: Int) = save({ putInt(KEY_SNOOZE_MINUTES, minutes) }) { it.copy(snoozeMinutes = minutes) }

    fun setReminderButton(button: ReminderButton) = save({ putString(KEY_REMINDER_BUTTON, button.name) }) { it.copy(reminderButton = button) }

    fun setLinkRenaming(mode: LinkRenaming) = save({ putString(KEY_LINK_RENAMING, mode.name) }) { it.copy(linkRenaming = mode) }

    fun setTableRecalc(mode: TableRecalc) = save({ putString(KEY_TABLE_RECALC, mode.name) }) { it.copy(tableRecalc = mode) }

    fun setTemplatesHidden(mode: TemplatesHidden) = save({ putString(KEY_TEMPLATES_HIDDEN, mode.name) }) { it.copy(templatesHidden = mode) }

    fun setEditCells(on: Boolean) = save({ putBoolean(KEY_EDIT_CELLS, on) }) { it.copy(editCells = on) }

    fun setThemeMode(mode: ThemeMode) = save({ putString(KEY_THEME, mode.name) }) { it.copy(themeMode = mode) }

    fun setDynamicColor(on: Boolean) = save({ putBoolean(KEY_DYNAMIC_COLOR, on) }) { it.copy(dynamicColor = on) }

    fun setKeywords(keywords: Keywords) = save({ putString(KEY_KEYWORDS, keywords.toSpec()) }) { it.copy(keywords = keywords) }

    companion object {
        /** Property names typed as "ID, created" or "ID CREATED", in upper case. */
        fun parseProperties(text: String): Set<String> =
            text.split(' ', ',', ';').map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toSet()

        /** Snooze lengths offered in the settings, in minutes. */
        val SNOOZE_CHOICES = listOf(5, 10, 15, 30, 60, 120, 180)

        /** "In 15 min", "In 1 hour", "In 1 h 30" for a snooze of [minutes]. */
        fun snoozeLabel(resources: Resources, minutes: Int): String = when {
            minutes < 60 -> resources.getString(R.string.snooze_minutes, minutes)
            minutes % 60 == 0 -> resources.getQuantityString(R.plurals.snooze_hours, minutes / 60, minutes / 60)
            else -> resources.getString(R.string.snooze_hours_minutes, minutes / 60, minutes % 60)
        }

        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_SEARCHES = "saved_searches"
        private const val ICON_PREFIX = "icon:"
        private const val TREE_PREFIX = "tree:"
        private const val FOLDS_PREFIX = "folds:"
        private const val KEY_ADD_ID = "add_id_to_new_notes"
        private const val KEY_HIDDEN_PROPERTIES = "hidden_properties"
        private const val KEY_ADD_CREATED = "add_created_to_new_notes"
        private const val KEY_ALARM_CLOCK = "use_alarm_clock"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_REMINDER_BUTTON = "reminder_button"
        private const val KEY_LINK_RENAMING = "link_renaming"
        private const val KEY_TABLE_RECALC = "table_recalc"
        private const val KEY_TEMPLATES_HIDDEN = "templates_hidden"
        private const val KEY_EDIT_CELLS = "edit_cells"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_SHOW_CONTENT = "show_content_in_notebook"
        private const val KEY_STRIKE_DONE = "strike_done"
        private const val KEY_MONOSPACE = "monospace"
        private const val KEY_WRAP_TABLES = "wrap_tables"
        private const val KEY_FREEZE_FIRST_COLUMN = "freeze_first_column"
        private const val KEY_NOTE_SPACING = "note_spacing"
        private const val KEY_INDENTATION = "indentation"
        private const val KEY_REMIND_SCHEDULED = "remind_scheduled"
        private const val KEY_REMIND_DEADLINE = "remind_deadline"
        private const val KEY_REMIND_ALL_DAY = "remind_all_day"
        private const val KEY_ALL_DAY_MINUTES = "all_day_minutes"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
        private const val KEY_GUIDE_OFFERED = "guide_offered"
    }
}
