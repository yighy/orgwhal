package io.github.yighy.orgwhal.ui

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.TextButton
import io.github.yighy.orgwhal.core.TableFormulas
import androidx.compose.ui.res.stringResource
import io.github.yighy.orgwhal.R
import io.github.yighy.orgwhal.core.ListFolds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.core.Blocks
import io.github.yighy.orgwhal.core.Checkboxes

/**
 * A note's content as Org shows it: text with its links and checkboxes ([LinkedText]), tables,
 * source and example blocks in a monospace box, quotes with a bar; drawers folded under their
 * name, the logbook hidden unless [showLogbook].
 * Taps give positions in [text], as for [LinkedText].
 */
@Composable
fun OrgContent(
    text: String,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTextTap: ((Int) -> Unit)? = null,
    onToggleCheckbox: ((Int) -> Unit)? = null,
    /** Given, tables with formulas offer to recalculate: the new text (null if unchanged), formulas skipped. */
    onRecalculate: ((String?, Int) -> Unit)? = null,
    /** Lines of the table with this name (#+NAME, or a note's ID), for remote() in formulas. */
    tableNamed: (String) -> List<String>? = { null },
    /** Tables with formulas show their values recalculated (the file keeps its own until saved). */
    liveFormulas: Boolean = false,
    /**
     * Given, a tap on a table field edits it in place (not those a formula computes), Enter going
     * on to the field below, and a long press adds or deletes rows and columns: it is given the
     * change to make to the text.
     */
    onEditTable: (((String) -> String) -> Unit)? = null,
    /** Told when a table field starts and stops being edited. */
    onCellEditing: (Boolean) -> Unit = {},
    /** The :LOGBOOK: drawer folds as the others, for its clock lines; else it is hidden. */
    showLogbook: Boolean = false,
    /** Given, holds the table field being edited, for completion while typing in it. */
    cellDraft: CellDraft? = null,
) {
    val blocks = remember(text) { Blocks.parse(text) }
    // Plain text, the usual case, shows as written
    val plain = blocks.size == 1 && blocks[0] is Blocks.Text
    if (plain && !remember(text) { ListFolds.hasFolds(text) }) {
        LinkedText(text, style, onOpenLink, modifier, onTextTap = onTextTap, onToggleCheckbox = onToggleCheckbox)
        return
    }
    val boxes = remember(text) { Checkboxes.find(text) }
    // Positions and checkbox numbers within a part are from its start
    fun tapFrom(start: Int) = onTextTap?.let { tap -> { at: Int -> tap(start + at) } }
    fun toggleFrom(start: Int): ((Int) -> Unit)? {
        val before = boxes.count { it.box < start }
        return onToggleCheckbox?.let { toggle -> { n: Int -> toggle(before + n) } }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                // Drawers open with a tap; the logbook only where asked, the clock's time
                // showing it elsewhere
                is Blocks.Drawer -> if (showLogbook || !block.name.equals("LOGBOOK", ignoreCase = true)) {
                    DrawerBlock(block, style, onOpenLink, tapFrom(block.innerStart), toggleFrom(block.innerStart))
                }
                is Blocks.Text -> FoldableText(block.text, block.range.first, style, onOpenLink, ::tapFrom, ::toggleFrom)
                is Blocks.Table -> Column {
                    block.name?.let { ElementName(it) }
                    // Recalculated, the table shows its new values; the button then saves them
                    val fresh = if (liveFormulas && block.formulas.isNotEmpty()) {
                        remember(text, block, tableNamed) {
                            val lines = text.substring(block.tableStart, block.tableEnd).split('\n')
                            val result = TableFormulas.recalculate(lines, block.formulas, tableNamed).lines
                            if (result == lines) null else Blocks.parse(result.joinToString("\n")).filterIsInstance<Blocks.Table>().firstOrNull()
                        }
                    } else {
                        null
                    }
                    // Fields as the file has them, those a formula computes left out
                    val tableIndex = blocks.filterIsInstance<Blocks.Table>().indexOfFirst { it === block }
                    val computed = remember(text, block) {
                        TableFormulas.computedCells(text.substring(block.tableStart, block.tableEnd).split('\n'), block.formulas)
                    }
                    // The field being edited in place, and its text; kept as the table changes, a
                    // new row or column opening its field
                    var editing by remember(tableIndex) { mutableStateOf<Pair<Int, Int>?>(null) }
                    var draft by remember(tableIndex) { mutableStateOf(TextFieldValue()) }
                    val wrapTables = LocalWrapTables.current
                    // Shared with the screen as it changes (the field alone recomposes then)
                    fun setDraft(value: TextFieldValue) {
                        draft = value
                        cellDraft?.value = value
                    }
                    fun stored(line: Int, column: Int) = block.rows.getOrNull(line)?.getOrNull(column).orEmpty()
                    val scope = rememberCoroutineScope()
                    if (editing != null) {
                        DisposableEffect(Unit) {
                            onCellEditing(true)
                            onDispose {
                                onCellEditing(false)
                                cellDraft?.value = null
                            }
                        }
                        SideEffect { cellDraft?.set = { value -> if (editing != null) setDraft(value) } }
                    }
                    /** Opens the field at [line] and [column], its [value] selected. */
                    fun edit(line: Int, column: Int, value: String = stored(line, column)) {
                        setDraft(TextFieldValue(value, TextRange(0, value.length)))
                        editing = line to column
                    }
                    /** Saves the field being edited, if it changed, then [then] in the same save (a new row or column). */
                    fun save(then: ((String) -> String)? = null) {
                        val pending = editing?.let { (line, column) ->
                            val value = draft.text
                            if (value.trim() == stored(line, column)) null else { t: String -> Blocks.withCell(t, Blocks.CellEdit(tableIndex, line, column, value)) }
                        }
                        editing = null
                        val changes = listOfNotNull(pending, then)
                        if (changes.isNotEmpty()) onEditTable?.invoke { t -> changes.fold(t) { acc, change -> change(acc) } }
                    }
                    /**
                     * Enter, as in Emacs: the field below opens, or on the last row a new row
                     * under it; a field below that a formula computes ends the editing.
                     */
                    fun next() {
                        val (line, column) = editing ?: return
                        val below = (line + 1 until block.rows.size).firstOrNull { block.rows[it] != null }
                        when {
                            below == null -> {
                                save { t -> Blocks.withTableChange(t, tableIndex, line, column, Blocks.TableChange.RowBelow) }
                                edit(line + 1, column, "")
                            }
                            (below to column) !in computed -> {
                                save()
                                edit(below, column)
                            }
                            else -> save()
                        }
                    }
                    OrgTable(
                        fresh?.let { block.copy(rows = it.rows) } ?: block,
                        style,
                        onOpenLink,
                        Modifier.tapToEdit(onTextTap, block.range.first),
                        onCellTap = onEditTable?.let {
                            { line, column ->
                                when {
                                    editing == line to column -> {}
                                    // A field a formula computes: the note's text, as before (only
                                    // saving the field being edited, if one is)
                                    (line to column) in computed -> if (editing != null) save() else onTextTap?.invoke(block.range.first)
                                    else -> {
                                        save()
                                        edit(line, column)
                                    }
                                }
                            }
                        },
                        cellMenu = onEditTable?.let {
                            { line, column, dismiss ->
                                DropdownMenu(expanded = true, onDismissRequest = dismiss) {
                                    /** [change] next to the field, then the new row's or column's field opened. */
                                    @Composable
                                    fun Item(label: Int, change: Blocks.TableChange, open: Pair<Int, Int>? = null, enabled: Boolean = true) = DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        enabled = enabled,
                                        onClick = {
                                            dismiss()
                                            save { t -> Blocks.withTableChange(t, tableIndex, line, column, change) }
                                            // Once the menu is gone: it holds the focus until then,
                                            // and the field could not bring up the keyboard
                                            open?.let { (l, c) ->
                                                scope.launch {
                                                    delay(MENU_CLOSE_MS)
                                                    edit(l, c, "")
                                                }
                                            }
                                        },
                                    )
                                    Item(R.string.table_row_above, Blocks.TableChange.RowAbove, line to column)
                                    Item(R.string.table_row_below, Blocks.TableChange.RowBelow, line + 1 to column)
                                    Item(R.string.table_column_left, Blocks.TableChange.ColumnLeft, line to column)
                                    Item(R.string.table_column_right, Blocks.TableChange.ColumnRight, line to column + 1)
                                    HorizontalDivider()
                                    // Not the last row or column: the table would be gone
                                    Item(R.string.table_delete_row, Blocks.TableChange.DeleteRow, enabled = block.rows.count { it != null } > 1)
                                    Item(R.string.table_delete_column, Blocks.TableChange.DeleteColumn, enabled = block.columns > 1)
                                }
                            }
                        },
                        editing = editing,
                        editor = { line, column, cellStyle ->
                            // Kept in view above the keyboard as it comes up, with the field's
                            // padding and border under it
                            val bring = remember { BringIntoViewRequester() }
                            val density = LocalDensity.current
                            val ime = WindowInsets.ime
                            var size by remember { mutableStateOf(IntSize.Zero) }
                            LaunchedEffect(Unit) {
                                val margin = with(density) { 16.dp.toPx() }
                                snapshotFlow { ime.getBottom(density) }.collect {
                                    bring.bringIntoView(Rect(0f, -margin, size.width.toFloat(), size.height + margin))
                                }
                            }
                            InPlaceField(
                                value = draft,
                                // Only while this field is the one edited: losing the focus, it
                                // sends its text again, which would go into the next field
                                onValueChange = {
                                    when {
                                        editing != line to column -> {}
                                        // Wrapped, a new line typed (or pasted) goes to the next field
                                        '\n' in it.text -> {
                                            setDraft(TextFieldValue(it.text.replace('\n', ' ').trimEnd()))
                                            next()
                                        }
                                        else -> setDraft(it)
                                    }
                                },
                                style = cellStyle,
                                onFocusLost = { if (editing == line to column) save() },
                                onDone = ::next,
                                imeAction = ImeAction.Next,
                                keyboardType = if (TableFormulas.isNumber(stored(line, column))) KeyboardType.Decimal else KeyboardType.Text,
                                // A field is one line: Enter goes to the next. Wrapped as shown,
                                // it takes its column's width
                                singleLine = !wrapTables,
                                modifier = Modifier
                                    .then(if (wrapTables) Modifier.fillMaxWidth() else Modifier.width(IntrinsicSize.Min))
                                    .bringIntoViewRequester(bring)
                                    .onSizeChanged { size = it },
                            )
                        },
                    )
                    if (block.formulaLines.isNotEmpty()) {
                        FormulaBar(block, stale = fresh != null, onRecalculate?.let { done ->
                            {
                                val lines = text.substring(block.tableStart, block.tableEnd).split('\n')
                                val result = TableFormulas.recalculate(lines, block.formulas, tableNamed)
                                val changed = result.lines != lines
                                done(if (changed) text.replaceRange(block.tableStart, block.tableEnd, result.lines.joinToString("\n")) else null, result.skipped)
                            }
                        })
                    }
                }
                is Blocks.Begin -> when {
                    block.isCode -> Column {
                        block.name?.let { ElementName(it) }
                        CodeBlock(block, style, Modifier.tapToEdit(onTextTap, block.innerStart))
                    }
                    block.kind == "QUOTE" -> Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        OrgContent(
                            block.inner,
                            style,
                            onOpenLink,
                            Modifier.weight(1f),
                            onTextTap = tapFrom(block.innerStart),
                            onToggleCheckbox = toggleFrom(block.innerStart),
                        )
                    }
                    // Verse keeps its lines as written; centered text is centered
                    else -> LinkedText(
                        block.inner,
                        if (block.kind == "CENTER") style.copy(textAlign = TextAlign.Center) else style,
                        onOpenLink,
                        Modifier.fillMaxWidth(),
                        onTextTap = tapFrom(block.innerStart),
                        onToggleCheckbox = toggleFrom(block.innerStart),
                    )
                }
            }
        }
    }
}

/**
 * Text whose list items with sub-items fold, with a chevron at the end of their line. [start] is
 * where the text is in the content; [tapFrom] and [toggleFrom] give the callbacks for a part
 * starting at a position in the content.
 */
@Composable
private fun FoldableText(
    text: String,
    start: Int,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    tapFrom: (Int) -> ((Int) -> Unit)?,
    toggleFrom: (Int) -> ((Int) -> Unit)?,
) {
    val parts = remember(text) { ListFolds.parse(text) }
    if (parts.none { it is ListFolds.Item }) {
        LinkedText(text, style, onOpenLink, onTextTap = tapFrom(start), onToggleCheckbox = toggleFrom(start))
        return
    }
    Column {
        parts.forEach { part ->
            when (part) {
                is ListFolds.Plain -> LinkedText(
                    part.text,
                    style,
                    onOpenLink,
                    onTextTap = tapFrom(start + part.start),
                    onToggleCheckbox = toggleFrom(start + part.start),
                )
                is ListFolds.Item -> {
                    var open by rememberSaveable(start + part.start) { mutableStateOf(true) }
                    val rotation = chevronRotation(open)
                    Row(verticalAlignment = Alignment.Top) {
                        LinkedText(
                            part.line,
                            style,
                            onOpenLink,
                            Modifier.weight(1f),
                            onTextTap = tapFrom(start + part.start),
                            onToggleCheckbox = toggleFrom(start + part.start),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            stringResource(if (open) R.string.collapse else R.string.expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { open = !open }
                                .padding(horizontal = 4.dp)
                                .size(20.dp)
                                .rotate(rotation),
                        )
                    }
                    Unfold(open) {
                        FoldableText(part.children, start + part.childrenStart, style, onOpenLink, tapFrom, toggleFrom)
                    }
                }
            }
        }
    }
}

/** A table's formulas, folded to "ƒ 2 formulas", with a button to recalculate the table. */
@Composable
private fun FormulaBar(table: Blocks.Table, stale: Boolean, onRecalculate: (() -> Unit)?) {
    var open by rememberSaveable(table.range.first) { mutableStateOf(false) }
    val rotation = chevronRotation(open)
    val count = table.formulas.size
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { open = !open }
                    .padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(rotation),
                )
                Text(
                    "ƒ  " + pluralStringResource(R.plurals.n_formulas, count, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (onRecalculate != null) {
                TextButton(onClick = onRecalculate) { Text(stringResource(if (stale) R.string.save_values else R.string.recalculate)) }
            }
        }
        Unfold(open) {
            Text(
                table.formulas.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()),
                softWrap = false,
            )
        }
    }
}

/** A drawer folded to its name, as in Emacs; a tap shows or hides its text. */
@Composable
private fun DrawerBlock(
    drawer: Blocks.Drawer,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    onTextTap: ((Int) -> Unit)?,
    onToggleCheckbox: ((Int) -> Unit)?,
) {
    var open by rememberSaveable(drawer.name, drawer.innerStart) { mutableStateOf(false) }
    val rotation = chevronRotation(open)
    Column {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = !open }
                .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
            Text(drawer.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Unfold(open) {
            LinkedText(
                drawer.inner,
                style,
                onOpenLink,
                Modifier.padding(start = 20.dp, top = 4.dp),
                onTextTap = onTextTap,
                onToggleCheckbox = onToggleCheckbox,
            )
        }
    }
}

/** The name given to a table or block with #+NAME, which links and remote() refer to. */
@Composable
private fun ElementName(name: String) {
    Text(
        "#$name",
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** Rotation of a fold chevron: pointing down when [open], right when folded. */
@Composable
private fun chevronRotation(open: Boolean): Float =
    animateFloatAsState(if (open) 90f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron").value

/** [content] unfolding and folding away as [open] changes. */
@Composable
private fun Unfold(open: Boolean, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        open,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
    ) { content() }
}

/** A tap edits the text from [at] (no ripple, as on the text around). */
private fun Modifier.tapToEdit(onTextTap: ((Int) -> Unit)?, at: Int) =
    if (onTextTap == null) this else clickable(interactionSource = null, indication = null) { onTextTap(at) }

/** Code in a monospace box that scrolls sideways, with its language above. */
@Composable
private fun CodeBlock(block: Blocks.Begin, style: TextStyle, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            block.language?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                block.inner,
                style = style.copy(fontFamily = FontFamily.Monospace, fontSize = style.fontSize * 0.85f),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * A table in a grid that scrolls sideways; rows above the first rule are the header. Each column
 * is as wide as its widest field and each row as high as its highest; with [LocalWrapTables],
 * the widest columns narrow until the table fits, their fields going on several lines.
 */
@Composable
private fun OrgTable(
    table: Blocks.Table,
    style: TextStyle,
    onOpenLink: (String) -> Unit,
    modifier: Modifier,
    /** Given, a tap on a field calls it with the field's line in the table and its column. */
    onCellTap: ((Int, Int) -> Unit)? = null,
    /** Given, a long press on a field shows it as the field's menu, until it is dismissed. */
    cellMenu: (@Composable (Int, Int, () -> Unit) -> Unit)? = null,
    /** The field shown by [editor] instead of its text, with the style it would have. */
    editing: Pair<Int, Int>? = null,
    editor: @Composable (Int, Int, TextStyle) -> Unit = { _, _, _ -> },
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(8.dp)
    val rows = table.rows.withIndex().mapNotNull { (i, cells) -> cells?.let { i to it } }
    val header = table.headerRows
    // Columns of numbers align right, as Emacs writes them
    val numeric = remember(table) { Blocks.numericColumns(rows.drop(header).map { it.second }, table.columns) }
    val wrap = LocalWrapTables.current
    val floor = with(LocalDensity.current) { MIN_WRAPPED_COLUMN.roundToPx() }
    BoxWithConstraints(modifier) {
        val screen = constraints.maxWidth
        Layout(
            content = {
                rows.forEach { (i, cells) ->
                    repeat(table.columns) { c ->
                        val isHeader = i < header
                        var menu by remember { mutableStateOf(false) }
                        Box(
                            Modifier
                                .then(
                                    if (onCellTap != null) {
                                        Modifier.combinedClickable(
                                            onClick = { onCellTap(i, c) },
                                            onLongClick = cellMenu?.let { { menu = true } },
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .background(if (isHeader) headerColor else Color.Transparent)
                                .border(if (editing == i to c) 2.dp else 0.5.dp, if (editing == i to c) MaterialTheme.colorScheme.primary else line)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = if (numeric[c] && !isHeader) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            val cellStyle = if (isHeader) style.copy(fontWeight = FontWeight.Bold) else style
                            if (editing == i to c) {
                                editor(i, c, if (numeric[c] && !isHeader) cellStyle.copy(textAlign = TextAlign.End) else cellStyle)
                            } else {
                                LinkedText(cells.getOrElse(c) { "" }, cellStyle, onOpenLink, maxLines = if (wrap) Int.MAX_VALUE else 1)
                            }
                            if (menu) cellMenu?.invoke(i, c) { menu = false }
                        }
                    }
                }
            },
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .clip(shape)
                .border(1.dp, line, shape),
        ) { measurables, _ ->
            val grid = measurables.chunked(table.columns)
            val natural = IntArray(table.columns) { c -> grid.maxOfOrNull { it[c].maxIntrinsicWidth(Constraints.Infinity) } ?: 0 }
            val widths = if (wrap && screen != Constraints.Infinity) fitColumns(natural, screen, floor) else natural
            val heights = grid.map { row -> row.indices.maxOf { c -> row[c].minIntrinsicHeight(widths[c]) } }
            val placeables = grid.mapIndexed { r, row -> row.mapIndexed { c, cell -> cell.measure(Constraints.fixed(widths[c], heights[r])) } }
            layout(widths.sum(), heights.sum()) {
                var y = 0
                placeables.forEachIndexed { r, row ->
                    var x = 0
                    row.forEachIndexed { c, cell ->
                        cell.place(x, y)
                        x += widths[c]
                    }
                    y += heights[r]
                }
            }
        }
    }
}

/**
 * Widths for columns [natural]ly this wide that fit in [available]: the widest narrowed to one
 * same width, not below [floor] (the table then wider than [available]).
 */
internal fun fitColumns(natural: IntArray, available: Int, floor: Int): IntArray {
    if (natural.sum() <= available) return natural
    // The width the widest share: what is left once the narrower have theirs
    val sorted = natural.sorted()
    var left = available
    var cap = floor
    for ((k, w) in sorted.withIndex()) {
        val share = left / (sorted.size - k)
        if (w > share) {
            cap = maxOf(share, floor)
            break
        }
        left -= w
    }
    return IntArray(natural.size) { minOf(natural[it], cap) }
}

/** The narrowest a column gets for its fields to wrap. */
private val MIN_WRAPPED_COLUMN = 72.dp

/**
 * The table field being edited in place and its text, for the screen to complete what is typed
 * in it ("id:" links) as in the note's text.
 */
class CellDraft {
    var value by mutableStateOf<TextFieldValue?>(null)
    internal var set: (TextFieldValue) -> Unit = {}

    /** Puts [value] in the field being edited. */
    fun replace(value: TextFieldValue) = set(value)
}

/** How long a menu takes to close, after which a field it opened can take the focus. */
private const val MENU_CLOSE_MS = 250L
