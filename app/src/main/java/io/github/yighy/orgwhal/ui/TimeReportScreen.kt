package io.github.yighy.orgwhal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.R
import io.github.yighy.orgwhal.core.Clock
import io.github.yighy.orgwhal.core.ClockReport
import io.github.yighy.orgwhal.data.NoteRepository
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Time clocked over a period chosen at the top (as Org's clocktable blocks), by notebook, then by
 * note in the outline's order: a note's time counts its sub-notes'. A tap opens the note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeReportScreen(viewModel: AppViewModel, library: NoteRepository.Library) {
    var period by rememberSaveable { mutableStateOf(ClockReport.Period.ThisWeek) }
    // A running clock counts up to now
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val today = now.toLocalDate()
    val range = remember(period, today) { ClockReport.range(period, today) }
    val report = remember(library, range, now) { ClockReport.build(library.notes, range, now) }
    val total = report.fold(java.time.Duration.ZERO) { sum, n -> sum + n.total }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.time_report)) },
                colors = blendedTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "periods") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ClockReport.Period.entries.forEach { p ->
                        FilterChip(selected = p == period, onClick = { period = p }, label = { Text(stringResource(periodLabel(p))) })
                    }
                }
            }
            item(key = "total") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                    Text(Clock.format(total), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.width(12.dp))
                    range?.let {
                        Text(
                            span(it.start.toLocalDate(), it.endInclusive.toLocalDate().minusDays(1)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }
            if (report.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.nothing_clocked),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            report.forEach { notebook ->
                item(key = "nb-${notebook.fileUri}") {
                    HorizontalDivider()
                    TimeLine(
                        library.file(notebook.fileUri)?.title ?: notebook.fileUri.substringAfterLast('/'),
                        notebook.total,
                        level = 0,
                        bold = true,
                        onClick = { viewModel.openNotebook(notebook.fileUri) },
                    )
                }
                items(notebook.rows, key = { "row-${notebook.fileUri}#${it.note.index}" }) { row ->
                    TimeLine(displayTitle(row.note.title), row.total, level = row.note.level, bold = false, onClick = { viewModel.openNote(row.note) })
                }
            }
        }
    }
}

/** A notebook (level 0) or a note, indented by its level, with its time at the end. */
@Composable
private fun TimeLine(title: String, time: java.time.Duration, level: Int, bold: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp + 16.dp * (level - 1).coerceAtLeast(0) + if (level > 0) 8.dp else 0.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            Clock.format(time),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun periodLabel(period: ClockReport.Period) = when (period) {
    ClockReport.Period.Today -> R.string.period_today
    ClockReport.Period.Yesterday -> R.string.period_yesterday
    ClockReport.Period.ThisWeek -> R.string.period_this_week
    ClockReport.Period.LastWeek -> R.string.period_last_week
    ClockReport.Period.ThisMonth -> R.string.period_this_month
    ClockReport.Period.LastMonth -> R.string.period_last_month
    ClockReport.Period.All -> R.string.period_all
}

private val spanFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun span(first: LocalDate, last: LocalDate) =
    if (first == last) first.format(spanFormat) else first.format(spanFormat) + " – " + last.format(spanFormat)
