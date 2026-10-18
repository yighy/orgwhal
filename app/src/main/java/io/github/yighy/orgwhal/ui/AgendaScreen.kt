package io.github.yighy.orgwhal.ui

import androidx.compose.runtime.collectAsState
import io.github.yighy.orgwhal.core.Templates
import io.github.yighy.orgwhal.data.TemplatesHidden
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import io.github.yighy.orgwhal.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.core.Agenda
import io.github.yighy.orgwhal.data.NoteRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgendaScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    snackbar: SnackbarHostState,
    notificationsAllowed: Boolean,
    onAllowNotifications: () -> Unit,
) {
    val today = LocalDate.now()
    // A notebook that went away no longer filters
    val notebook = viewModel.agendaNotebook?.takeIf { library.file(it) != null }
    val tag = viewModel.agendaTag
    val skipTemplates = viewModel.settings.collectAsState().value.templatesHidden != TemplatesHidden.Nowhere
    val agenda = remember(library, today, notebook, tag, skipTemplates) {
        val notes = library.notes.filter { n ->
            (notebook == null || n.fileUri == notebook) && (tag == null || n.tags.any { it.equals(tag, ignoreCase = true) }) &&
                !(skipTemplates && Templates.isTemplate(n))
        }
        Agenda.build(notes, today, 7)
    }
    val todayCount = agenda.overdue.size + agenda.days.first().entries.size
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.agenda)) },
                subtitle = { Text(if (todayCount == 0) stringResource(R.string.agenda_nothing_today) else pluralStringResource(R.plurals.agenda_today, todayCount, todayCount)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                colors = blendedTopBarColors(),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.tapToTop { listState.animateScrollToItem(0); scrollBehavior.state.expand() },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefresh(viewModel) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 48.dp),
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                ) {
                    if (!notificationsAllowed) {
                        item(key = "notifications") { NotificationsCard(onAllowNotifications) }
                    }
                    item(key = "filters") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterMenu(
                                label = notebook?.let { library.file(it)?.title } ?: stringResource(R.string.all_notebooks),
                                allLabel = stringResource(R.string.all_notebooks),
                                selected = notebook != null,
                                options = library.files.map { it.uri to it.title },
                                onSelect = { viewModel.agendaNotebook = it },
                            )
                            if (library.tags.isNotEmpty()) {
                                FilterMenu(
                                    label = tag?.let { "#$it" } ?: stringResource(R.string.all_tags),
                                    allLabel = stringResource(R.string.all_tags),
                                    selected = tag != null,
                                    options = library.tags.map { it to "#$it" },
                                    onSelect = { viewModel.agendaTag = it },
                                )
                            }
                        }
                    }
                    agendaItems(viewModel, library, agenda, today)
                }
            }
            JumpButton(listState, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
}

/** Overdue entries, then each day with its entries (agenda screen, and searches with ad.). */
fun LazyListScope.agendaItems(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    agenda: Agenda.Result,
    today: LocalDate,
) {
    if (agenda.overdue.isNotEmpty()) {
        item(key = "overdue") { DayHeader(stringResource(R.string.overdue), MaterialTheme.colorScheme.error) }
        items(agenda.overdue, key = { "overdue-" + entryKey(it) }) { entry ->
            AgendaRow(viewModel, library, entry, today, overdue = true)
        }
    }
    agenda.days.forEach { day ->
        item(key = "day-${day.date}") { DayHeader(dayLabel(day.date, today), MaterialTheme.colorScheme.primary) }
        if (day.entries.isEmpty()) {
            item(key = "empty-${day.date}") {
                Text(
                    stringResource(R.string.nothing_planned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        items(day.entries, key = { "${day.date}-" + entryKey(it) }) { entry ->
            AgendaRow(viewModel, library, entry, today, overdue = false)
        }
    }
}

private fun entryKey(entry: Agenda.Entry) = "${entry.note.fileUri}#${entry.note.index}#${entry.kind}#${entry.time}"

@Composable
private fun DayHeader(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun weekday(date: LocalDate) =
    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.today_date, date.format(shortDate))
    today.plusDays(1) -> stringResource(R.string.tomorrow_date, date.format(shortDate))
    else -> weekday(date) + " · " + date.format(shortDate)
}

/** A filter chip opening the list of its [options] (value to label), "all" first. */
@Composable
private fun FilterMenu(
    label: String,
    allLabel: String,
    selected: Boolean,
    options: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { open = false; onSelect(null) })
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

/**
 * Time, state and title of an agenda entry; swipe right toggles TODO / DONE, swipe left puts
 * a scheduled or deadline time off (a day, a week, or to a date chosen).
 */
@Composable
private fun AgendaRow(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    entry: Agenda.Entry,
    today: LocalDate,
    overdue: Boolean,
) {
    val note = entry.note
    val key = (if (overdue) "agenda-overdue-" else "agenda-${entry.date}-") + entryKey(entry)
    val timestamp = when (entry.kind) {
        Agenda.Kind.Scheduled -> note.scheduled
        Agenda.Kind.Deadline -> note.deadline
        Agenda.Kind.Event -> null
    }
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        TimestampDialog(
            initial = timestamp,
            onDismiss = { picking = false },
            onConfirm = { t -> picking = false; viewModel.setPlanning(note, entry.kind, t) },
        )
    }
    SwipeRow(
        key = key,
        openKey = viewModel.openSwipeRow,
        onOpenChange = { open -> viewModel.openSwipeRow = if (open) key else viewModel.openSwipeRow.takeIf { it != key } },
        swipeRightIcon = if (note.isDone) OrgwhalIcons.Undo else OrgwhalIcons.Check,
        onSwipeRight = { viewModel.toggleDone(note) },
        actions = if (timestamp == null) emptyList() else listOf(
            SwipeAction(Icons.Default.DateRange, stringResource(R.string.postpone_day), stringResource(R.string.plus_day)) {
                viewModel.postpone(note, entry.kind, 1)
            },
            SwipeAction(Icons.Default.DateRange, stringResource(R.string.postpone_week), stringResource(R.string.plus_week)) {
                viewModel.postpone(note, entry.kind, 7)
            },
            SwipeAction(Icons.Default.DateRange, stringResource(R.string.pick_date)) { picking = true },
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    // A tap while a row shows its actions just closes it
                    if (viewModel.openSwipeRow != null) viewModel.openSwipeRow = null else viewModel.openNote(note)
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start, and the end of a time range under it
            Column(Modifier.width(72.dp)) {
                Text(
                    entry.time?.format(timeFormat) ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.endTime?.let {
                    Text(it.format(timeFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    note.state?.let {
                        StateLabel(it, note.isDone)
                        Spacer(Modifier.width(8.dp))
                    }
                    note.priority?.let {
                        Text("#$it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                    }
                    LinkedText(
                        note.title,
                        MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (note.isDone && LocalStrikeDone.current) TextDecoration.LineThrough else null,
                        ),
                        viewModel::openLink,
                        maxLines = 2,
                    )
                }
                val deadline = entry.kind == Agenda.Kind.Deadline
                val details = buildList {
                    add(
                        when (entry.kind) {
                            Agenda.Kind.Scheduled -> stringResource(R.string.scheduled)
                            Agenda.Kind.Deadline -> stringResource(R.string.deadline)
                            Agenda.Kind.Event -> stringResource(R.string.event)
                        },
                    )
                    if (overdue) {
                        val late = entry.daysLate(today)
                        add(pluralStringResource(R.plurals.days_late, late.toInt(), late.toInt()))
                    }
                    add(library.file(note.fileUri)?.title ?: note.fileName)
                }
                Text(
                    details.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deadline || overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NotificationsCard(onAllow: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.allow_notifications_text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) }
        }
    }
}
