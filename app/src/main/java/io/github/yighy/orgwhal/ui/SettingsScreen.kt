package io.github.yighy.orgwhal.ui

import io.github.yighy.orgwhal.data.TemplatesHidden
import io.github.yighy.orgwhal.data.ReminderButton
import io.github.yighy.orgwhal.data.TableRecalc
import androidx.activity.compose.LocalActivity
import io.github.yighy.orgwhal.data.AppLanguage
import androidx.compose.ui.platform.LocalResources
import io.github.yighy.orgwhal.R
import androidx.compose.ui.res.stringResource
import io.github.yighy.orgwhal.data.ThemeMode
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.yighy.orgwhal.data.AppSettings
import io.github.yighy.orgwhal.data.LinkRenaming
import io.github.yighy.orgwhal.data.NoteSpacing
import io.github.yighy.orgwhal.data.Indentation
import io.github.yighy.orgwhal.data.NoteRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    onPickFolder: () -> Unit,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    var pickAllDayTime by remember { mutableStateOf(false) }
    var editKeywords by remember { mutableStateOf(false) }
    var pickLinkRenaming by remember { mutableStateOf(false) }
    var pickTableRecalc by remember { mutableStateOf(false) }
    var pickTemplatesHidden by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }
    val english = remember { AppLanguage.isEnglish(context) }
    val activity = LocalActivity.current
    var pickSnooze by remember { mutableStateOf(false) }
    var pickReminderButton by remember { mutableStateOf(false) }
    var pickSpacing by remember { mutableStateOf(false) }
    var pickIndentation by remember { mutableStateOf(false) }
    var editHiddenProperties by remember { mutableStateOf(false) }

    // System settings can change while the app is in the background
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    val unrestricted = remember(resumes) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val exactAlarms = remember(resumes) { viewModel.canScheduleExactAlarms }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = blendedTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.settings_appearance))
            ListItem(
                onClick = { pickTheme = true },
                supportingContent = { Text(stringResource(settings.themeMode.label)) },
            ) { Text(stringResource(R.string.settings_theme)) }
            // Wallpaper colors exist from Android 12
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchItem(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
            SwitchItem(
                title = stringResource(R.string.settings_english),
                summary = stringResource(R.string.settings_english_summary),
                checked = english,
                onCheckedChange = { on -> activity?.let { AppLanguage.setEnglish(it, on) } },
            )

            SectionHeader(stringResource(R.string.settings_notes))
            ListItem(
                onClick = onPickFolder,
                supportingContent = { Text(library.folderUri?.let(::folderLabel) ?: stringResource(R.string.none)) },
            ) { Text(stringResource(R.string.settings_folder)) }
            SwitchItem(
                title = stringResource(R.string.settings_show_content),
                summary = stringResource(R.string.settings_show_content_summary),
                checked = settings.showContentInNotebook,
                onCheckedChange = viewModel::setShowContentInNotebook,
            )
            SwitchItem(
                title = stringResource(R.string.settings_strike_done),
                summary = stringResource(R.string.settings_strike_done_summary),
                checked = settings.strikeDone,
                onCheckedChange = viewModel::setStrikeDone,
            )
            SwitchItem(
                title = stringResource(R.string.settings_monospace),
                summary = stringResource(R.string.settings_monospace_summary),
                checked = settings.monospace,
                onCheckedChange = viewModel::setMonospace,
            )
            SwitchItem(
                title = stringResource(R.string.settings_wrap_tables),
                summary = stringResource(R.string.settings_wrap_tables_summary),
                checked = settings.wrapTables,
                onCheckedChange = viewModel::setWrapTables,
            )
            SwitchItem(
                title = stringResource(R.string.settings_freeze_first_column),
                summary = stringResource(R.string.settings_freeze_first_column_summary),
                checked = settings.freezeFirstColumn,
                onCheckedChange = viewModel::setFreezeFirstColumn,
            )
            ListItem(
                onClick = { pickSpacing = true },
                supportingContent = { Text(stringResource(settings.noteSpacing.label)) },
            ) { Text(stringResource(R.string.settings_spacing)) }
            ListItem(
                onClick = { pickIndentation = true },
                supportingContent = { Text(stringResource(settings.indentation.label)) },
            ) { Text(stringResource(R.string.settings_indentation)) }

            ListItem(
                onClick = { editKeywords = true },
                supportingContent = { Text(settings.keywords.toSpec() + "\n" + stringResource(R.string.settings_keywords_summary)) },
            ) { Text(stringResource(R.string.settings_keywords)) }

            SwitchItem(
                title = stringResource(R.string.settings_add_id),
                summary = stringResource(R.string.settings_add_id_summary),
                checked = settings.addIdToNewNotes,
                onCheckedChange = viewModel::setAddIdToNewNotes,
            )
            ListItem(
                onClick = { editHiddenProperties = true },
                supportingContent = {
                    Text(settings.hiddenProperties.sorted().joinToString(" ").ifEmpty { stringResource(R.string.none) } + "\n" + stringResource(R.string.settings_hidden_properties_summary))
                },
            ) { Text(stringResource(R.string.settings_hidden_properties)) }
            SwitchItem(
                title = stringResource(R.string.settings_add_created),
                summary = stringResource(R.string.settings_add_created_summary),
                checked = settings.addCreatedToNewNotes,
                onCheckedChange = viewModel::setAddCreatedToNewNotes,
            )

            ListItem(
                onClick = { pickLinkRenaming = true },
                supportingContent = { Text(stringResource(settings.linkRenaming.label) + "\n" + stringResource(R.string.settings_link_renaming_summary)) },
            ) { Text(stringResource(R.string.settings_link_renaming)) }
            ListItem(
                onClick = { pickTableRecalc = true },
                supportingContent = { Text(stringResource(settings.tableRecalc.label) + "\n" + stringResource(R.string.settings_tables_summary)) },
            ) { Text(stringResource(R.string.settings_tables)) }
            ListItem(
                onClick = { pickTemplatesHidden = true },
                supportingContent = { Text(stringResource(settings.templatesHidden.label) + "\n" + stringResource(R.string.settings_templates_summary)) },
            ) { Text(stringResource(R.string.settings_templates)) }
            SwitchItem(
                title = stringResource(R.string.settings_edit_cells),
                summary = stringResource(R.string.settings_edit_cells_summary),
                checked = settings.editCells,
                onCheckedChange = viewModel::setEditCells,
            )

            SectionHeader(stringResource(R.string.settings_reminders))
            SwitchItem(
                title = stringResource(R.string.settings_remind_scheduled),
                summary = stringResource(R.string.settings_remind_scheduled_summary),
                checked = settings.remindScheduled,
                onCheckedChange = viewModel::setRemindScheduled,
            )
            SwitchItem(
                title = stringResource(R.string.settings_remind_deadline),
                summary = stringResource(R.string.settings_remind_deadline_summary),
                checked = settings.remindDeadline,
                onCheckedChange = viewModel::setRemindDeadline,
            )
            SwitchItem(
                title = stringResource(R.string.settings_alarm_clock),
                summary = stringResource(R.string.settings_alarm_clock_summary),
                checked = settings.useAlarmClock,
                onCheckedChange = viewModel::setUseAlarmClock,
            )
            ListItem(
                onClick = { pickSnooze = true },
                supportingContent = { Text(AppSettings.snoozeLabel(resources, settings.snoozeMinutes) + "\n" + stringResource(R.string.settings_snooze_summary)) },
            ) { Text(stringResource(R.string.settings_snooze)) }
            ListItem(
                onClick = { pickReminderButton = true },
                supportingContent = { Text(stringResource(settings.reminderButton.label) + "\n" + stringResource(R.string.settings_reminder_button_summary)) },
            ) { Text(stringResource(R.string.settings_reminder_button)) }
            SwitchItem(
                title = stringResource(R.string.settings_all_day),
                summary = stringResource(R.string.settings_all_day_summary, settings.allDayTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))),
                checked = settings.remindAllDay,
                onCheckedChange = viewModel::setRemindAllDay,
            )
            if (settings.remindAllDay) {
                ListItem(
                    onClick = { pickAllDayTime = true },
                    supportingContent = { Text(settings.allDayTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) },
                ) { Text(stringResource(R.string.settings_all_day_time)) }
            }

            SectionHeader(stringResource(R.string.settings_reliability))
            StatusItem(
                title = stringResource(R.string.settings_notifications),
                ok = notificationsAllowed,
                okText = stringResource(R.string.settings_allowed),
                problemText = stringResource(R.string.settings_notifications_off),
                onFix = onAllowNotifications,
            )
            StatusItem(
                title = stringResource(R.string.settings_exact_alarms),
                ok = exactAlarms,
                okText = stringResource(R.string.settings_exact_alarms_on),
                problemText = stringResource(R.string.settings_exact_alarms_off),
                onFix = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                        )
                    }
                },
            )
            StatusItem(
                title = stringResource(R.string.settings_battery),
                ok = unrestricted,
                okText = stringResource(R.string.settings_battery_on),
                problemText = stringResource(R.string.settings_battery_off),
                onFix = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                    )
                },
            )

            SectionHeader(stringResource(R.string.settings_about))
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
            ListItem(
                supportingContent = { Text(stringResource(R.string.settings_version, version ?: "?")) },
            ) { Text(stringResource(R.string.app_name)) }
            ListItem(
                onClick = viewModel::addGuide,
                supportingContent = { Text(stringResource(R.string.settings_add_guide_summary)) },
            ) { Text(stringResource(R.string.settings_add_guide)) }
            fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            ListItem(
                onClick = { open(SOURCE_URL) },
                supportingContent = { Text(SOURCE_URL.removePrefix("https://")) },
            ) { Text(stringResource(R.string.settings_source)) }
            ListItem(
                onClick = { open(LICENSE_URL) },
                supportingContent = { Text(stringResource(R.string.settings_license_summary)) },
            ) { Text(stringResource(R.string.settings_license)) }
            ListItem(
                onClick = { open(ORG_JAVA_URL) },
                supportingContent = { Text(stringResource(R.string.settings_credits_summary)) },
            ) { Text(stringResource(R.string.settings_credits)) }
        }
    }

    if (pickTheme) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { stringResource(it.label) },
            selected = stringResource(settings.themeMode.label),
            onDismiss = { pickTheme = false },
            onSelect = { label ->
                pickTheme = false
                ThemeMode.entries.firstOrNull { resources.getString(it.label) == label }?.let(viewModel::setThemeMode)
            },
        )
    }
    if (editHiddenProperties) {
        TextInputDialog(
            title = stringResource(R.string.settings_hidden_properties),
            label = stringResource(R.string.settings_hidden_properties_label),
            confirmLabel = stringResource(R.string.save),
            initial = settings.hiddenProperties.sorted().joinToString(" "),
            allowEmpty = true,
            onDismiss = { editHiddenProperties = false },
            onConfirm = { text ->
                editHiddenProperties = false
                viewModel.setHiddenProperties(AppSettings.parseProperties(text))
            },
        )
    }
    if (pickSpacing) {
        ChoiceDialog(
            title = stringResource(R.string.settings_spacing),
            options = NoteSpacing.entries.map { stringResource(it.label) },
            selected = stringResource(settings.noteSpacing.label),
            onDismiss = { pickSpacing = false },
            onSelect = { label ->
                pickSpacing = false
                NoteSpacing.entries.firstOrNull { resources.getString(it.label) == label }?.let(viewModel::setNoteSpacing)
            },
        )
    }
    if (pickIndentation) {
        ChoiceDialog(
            title = stringResource(R.string.settings_indentation),
            options = Indentation.entries.map { stringResource(it.label) },
            selected = stringResource(settings.indentation.label),
            onDismiss = { pickIndentation = false },
            onSelect = { label ->
                pickIndentation = false
                Indentation.entries.firstOrNull { resources.getString(it.label) == label }?.let(viewModel::setIndentation)
            },
        )
    }
    if (pickReminderButton) {
        ChoiceDialog(
            title = stringResource(R.string.settings_reminder_button),
            options = ReminderButton.entries.map { stringResource(it.label) },
            selected = stringResource(settings.reminderButton.label),
            onDismiss = { pickReminderButton = false },
            onSelect = { label ->
                pickReminderButton = false
                ReminderButton.entries.firstOrNull { resources.getString(it.label) == label }?.let(viewModel::setReminderButton)
            },
        )
    }
    if (pickSnooze) {
        ChoiceDialog(
            title = stringResource(R.string.settings_snooze),
            options = AppSettings.SNOOZE_CHOICES.map { AppSettings.snoozeLabel(resources, it) },
            selected = AppSettings.snoozeLabel(resources, settings.snoozeMinutes),
            onDismiss = { pickSnooze = false },
            onSelect = { label ->
                pickSnooze = false
                AppSettings.SNOOZE_CHOICES.firstOrNull { AppSettings.snoozeLabel(resources, it) == label }?.let(viewModel::setSnoozeMinutes)
            },
        )
    }
    if (pickLinkRenaming) {
        ChoiceDialog(
            title = stringResource(R.string.settings_link_renaming),
            options = LinkRenaming.entries.map { stringResource(it.label) },
            selected = stringResource(settings.linkRenaming.label),
            onDismiss = { pickLinkRenaming = false },
            onSelect = { label ->
                pickLinkRenaming = false
                LinkRenaming.entries.firstOrNull { resources.getString(it.label) == label }?.let(viewModel::setLinkRenaming)
            },
        )
    }
    if (pickTableRecalc) {
        ChoiceDialog(
            title = stringResource(R.string.settings_tables),
            options = TableRecalc.entries.map { stringResource(it.label) },
            selected = stringResource(settings.tableRecalc.label),
            onDismiss = { pickTableRecalc = false },
            onSelect = { label ->
                pickTableRecalc = false
                TableRecalc.entries.firstOrNull { resources.getString(it.label) == label }?.let(viewModel::setTableRecalc)
            },
        )
    }
    if (pickTemplatesHidden) {
        ChoiceDialog(
            title = stringResource(R.string.settings_templates),
            options = TemplatesHidden.entries.map { stringResource(it.label) },
            selected = stringResource(settings.templatesHidden.label),
            onDismiss = { pickTemplatesHidden = false },
            onSelect = { label ->
                pickTemplatesHidden = false
                TemplatesHidden.entries.firstOrNull { resources.getString(it.label) == label }?.let(viewModel::setTemplatesHidden)
            },
        )
    }
    if (editKeywords) {
        TextInputDialog(
            title = stringResource(R.string.settings_keywords),
            label = stringResource(R.string.settings_keywords_label),
            confirmLabel = stringResource(R.string.save),
            initial = settings.keywords.toSpec(),
            onDismiss = { editKeywords = false },
            onConfirm = { spec -> if (viewModel.setKeywords(spec)) editKeywords = false else viewModel.message(R.string.settings_keywords_empty) },
        )
    }

    if (pickAllDayTime) {
        val state = rememberTimePickerState(
            initialHour = settings.allDayTime.hour,
            initialMinute = settings.allDayTime.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { pickAllDayTime = false },
            title = { Text(stringResource(R.string.settings_all_day_time)) },
            text = { TimeInput(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    pickAllDayTime = false
                    viewModel.setAllDayTime(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickAllDayTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchItem(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        onClick = { onCheckedChange(!checked) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    ) { Text(title) }
}

@Composable
private fun StatusItem(title: String, ok: Boolean, okText: String, problemText: String, onFix: () -> Unit) {
    val status: @Composable () -> Unit = {
        Text(
            if (ok) okText else problemText,
            color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
    }
    // A tap fixes it, only when there is something to fix
    if (ok) {
        ListItem(supportingContent = status) { Text(title) }
    } else {
        ListItem(onClick = onFix, supportingContent = status) { Text(title) }
    }
}

private const val SOURCE_URL = "https://github.com/yighy/orgwhal"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val ORG_JAVA_URL = "https://github.com/orgzly/org-java"

/** "primary:Documents/org" → "Documents/org". */
private fun folderLabel(treeUri: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(treeUri).substringAfter(':') }
        .getOrNull()
        ?.ifEmpty { "/" }
        ?: treeUri.toString()
