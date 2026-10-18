package io.github.yighy.orgwhal.ui

import java.time.LocalDate
import java.time.LocalDateTime
import io.github.yighy.orgwhal.core.OrgTime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import io.github.yighy.orgwhal.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.core.TextEditing

/** Undo and redo of a text being edited; typing a word in a row counts as one step. */
class EditHistory {
    private val undos = ArrayDeque<TextFieldValue>()
    private val redos = ArrayDeque<TextFieldValue>()
    private var lastTyping = 0L

    /** Bumped on each change, so buttons follow [canUndo] and [canRedo]. */
    private var version by mutableIntStateOf(0)

    val canUndo: Boolean get() = version >= 0 && undos.isNotEmpty()
    val canRedo: Boolean get() = version >= 0 && redos.isNotEmpty()

    /** Keeps [before] as a step, unless [typing] goes on from the last one. */
    fun record(before: TextFieldValue, typing: Boolean) {
        val now = System.currentTimeMillis()
        if (!typing || now - lastTyping > TYPING_STEP_MS || undos.isEmpty()) {
            undos.addLast(before)
            if (undos.size > MAX_STEPS) undos.removeFirst()
        }
        lastTyping = if (typing) now else 0L
        redos.clear()
        version++
    }

    fun undo(current: TextFieldValue): TextFieldValue? = undos.removeLastOrNull()?.also {
        redos.addLast(current)
        lastTyping = 0L
        version++
    }

    fun redo(current: TextFieldValue): TextFieldValue? = redos.removeLastOrNull()?.also {
        undos.addLast(current)
        lastTyping = 0L
        version++
    }

    // Text as typed before the last help, and as the help made it
    private var helpedFrom: String? = null
    private var helpedTo: TextFieldValue? = null

    fun adjusted(from: String, to: TextFieldValue) {
        helpedFrom = from
        helpedTo = to
    }

    /**
     * Right after a help, the keyboard can send back the text as it last knew it (without the
     * help): the help is kept. Returns what to show instead of [new], or null.
     */
    fun reverted(new: TextFieldValue): TextFieldValue? {
        val from = helpedFrom
        val to = helpedTo
        helpedFrom = null
        helpedTo = null
        return to?.takeIf { new.text == from }
    }

    fun clear() {
        undos.clear()
        redos.clear()
        version++
    }

    private companion object {
        const val TYPING_STEP_MS = 1000L
        const val MAX_STEPS = 100
    }
}

private fun TextFieldValue.toEdit() = TextEditing.Edit(text, selection.min, selection.max)

private fun TextEditing.Edit.toValue() = TextFieldValue(text, TextRange(start, end))

/**
 * What typing in a note's text gives, with its help (lists that go on with [lists], brackets
 * that close); [history] keeps the step.
 */
fun typedText(old: TextFieldValue, new: TextFieldValue, history: EditHistory, lists: Boolean = true): TextFieldValue {
    if (new.text == old.text) return new
    history.reverted(new)?.let { return it }
    // A space or a new line ends a step of typing: undo goes back word by word
    val typedSpace = new.text.length == old.text.length + 1 && new.text.getOrNull(old.selection.min)?.isWhitespace() == true
    history.record(old, typing = !typedSpace)
    val adjusted = TextEditing.afterTyping(old.toEdit(), new.toEdit(), lists)?.toValue() ?: return new
    history.adjusted(new.text, adjusted)
    return adjusted
}

/**
 * Whether the keyboard is up or coming up: false as soon as it starts to go away, so what sits
 * over it goes at once instead of following it down (the system says it is hidden only later).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardShown(): Boolean = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0

/** Whether the bar over the keyboard shows: with the keyboard, or while its own dialog is open. */
@Composable
fun editToolbarShown(): Boolean = keyboardShown() || EditingDialog.open

/**
 * Bar over the keyboard while a note's text is edited: bold, italic, underline, strike-through,
 * code, list, checkbox, indent, outdent, link (id: then its suggestions), date (as for scheduled
 * times, or changing the one at the cursor); undo and redo stay at the end. For a [title], one
 * line: no list nor indent, and the date is inactive and alone, as a journal's entries are titled.
 */
@Composable
fun EditToolbar(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, history: EditHistory, title: Boolean = false) {
    fun apply(edit: (TextEditing.Edit) -> TextEditing.Edit) {
        val changed = edit(value.toEdit()).toValue()
        if (changed.text == value.text && changed.selection == value.selection) return
        history.record(value, typing = false)
        onChange(changed)
    }
    /** Inserts [text] with a space before it when it would stick to a word. */
    fun insertWord(text: String) = apply { e ->
        val before = e.text.getOrNull(e.start - 1)
        TextEditing.insert(e, if (before == null || before.isWhitespace()) text else " $text")
    }
    fun emphasis(marker: Char) = apply { TextEditing.toggleEmphasis(it, marker) }
    // The timestamp being changed (null for a new one), while the date dialog is open
    var datePicking by remember { mutableStateOf<IntRange?>(null) }
    var dateOpen by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        // Buttons don't take the focus: the text keeps it, and the keyboard stays
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { emphasis('*') }) { Icon(OrgwhalIcons.Bold, stringResource(R.string.bold)) }
                IconButton(onClick = { emphasis('/') }) { Icon(OrgwhalIcons.Italic, stringResource(R.string.italic)) }
                IconButton(onClick = { emphasis('_') }) { Icon(OrgwhalIcons.Underline, stringResource(R.string.underline)) }
                IconButton(onClick = { emphasis('+') }) { Icon(OrgwhalIcons.Strike, stringResource(R.string.strike)) }
                IconButton(onClick = { emphasis('~') }) { Icon(OrgwhalIcons.Code, stringResource(R.string.code)) }
                if (!title) {
                    IconButton(onClick = { apply(TextEditing::toggleList) }) { Icon(OrgwhalIcons.ListBulleted, stringResource(R.string.list)) }
                    IconButton(onClick = { apply(TextEditing::toggleCheckbox) }) { Icon(OrgwhalIcons.CheckBox, stringResource(R.string.checkbox)) }
                    IconButton(onClick = { apply(TextEditing::indent) }) { Icon(OrgwhalIcons.Indent, stringResource(R.string.indent)) }
                    IconButton(onClick = { apply(TextEditing::outdent) }) { Icon(OrgwhalIcons.Outdent, stringResource(R.string.outdent)) }
                }
                IconButton(onClick = { insertWord("id:") }) { Icon(OrgwhalIcons.Link, stringResource(R.string.link_to_note)) }
                IconButton(onClick = {
                    datePicking = TextEditing.timestampAt(value.toEdit())
                    dateOpen = true
                }) { Icon(Icons.Default.DateRange, stringResource(R.string.date)) }
            }
            VerticalDivider(Modifier.height(24.dp))
            IconButton(enabled = history.canUndo, onClick = { history.undo(value)?.let(onChange) }) { Icon(OrgwhalIcons.Undo, stringResource(R.string.undo)) }
            IconButton(enabled = history.canRedo, onClick = { history.redo(value)?.let(onChange) }) { Icon(OrgwhalIcons.Redo, stringResource(R.string.redo)) }
        }
    }

    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(dateOpen) {
        EditingDialog.open = dateOpen
        onDispose {
            // Back to the text, with its keyboard
            if (EditingDialog.open) {
                EditingDialog.open = false
                keyboard?.show()
            }
        }
    }
    if (dateOpen) {
        val range = datePicking
        val initial = range?.let { value.text.substring(it) }
        TimestampDialog(
            initial = initial,
            onDismiss = { dateOpen = false },
            // The current time, inactive as Org writes when something happened (not in the
            // agenda); for a title, today's date alone
            onNow = {
                dateOpen = false
                val now = if (title) OrgTime.inactiveDate(LocalDate.now()) else OrgTime.inactive(LocalDateTime.now())
                if (range != null) apply { TextEditing.insert(TextEditing.Edit(it.text, range.first, range.last + 1), now) } else insertWord(now)
            },
            nowLabel = if (title) R.string.today else R.string.now,
            dateOnly = title,
            onConfirm = { picked ->
                dateOpen = false
                // In a title, inactive unless it was active already
                val timestamp = picked?.let { if (title && initial?.startsWith('<') != true) OrgTime.toInactive(it) else it }
                when {
                    range != null -> apply { TextEditing.insert(TextEditing.Edit(it.text, range.first, range.last + 1), timestamp.orEmpty()) }
                    timestamp != null -> insertWord(timestamp)
                }
            },
        )
    }
}
