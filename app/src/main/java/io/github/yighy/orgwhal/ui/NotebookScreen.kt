package io.github.yighy.orgwhal.ui

import androidx.compose.foundation.lazy.LazyListState
import io.github.yighy.orgwhal.data.TableRecalc
import io.github.yighy.orgwhal.core.Blocks
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.animateScrollBy
import io.github.yighy.orgwhal.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import io.github.yighy.orgwhal.core.Checkboxes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.DragInteraction
import kotlinx.coroutines.delay
import io.github.yighy.orgwhal.core.Notebooks
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.core.NoteTree
import io.github.yighy.orgwhal.core.OrgNote
import io.github.yighy.orgwhal.core.Placement
import io.github.yighy.orgwhal.data.NoteRepository

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    file: NoteRepository.LoadedFile,
    snackbar: SnackbarHostState,
) {
    // Raw preface being edited in place, or null
    var prefaceDraft by remember { mutableStateOf<TextFieldValue?>(null) }
    val prefaceHistory = remember { EditHistory() }
    val currentFile by rememberUpdatedState(file)
    // Preface just saved, shown until the file is reloaded with it (else the old text flashes)
    var savedPreface by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file.doc.preface) { savedPreface = null }

    /** Saves the preface being edited, if it changed (no save button). */
    fun commitPreface() {
        val draft = prefaceDraft ?: return
        prefaceDraft = null
        if (draft.text != (savedPreface ?: currentFile.doc.preface.trimEnd())) {
            savedPreface = draft.text
            viewModel.setPreface(currentFile, draft.text)
        }
    }
    /** Edits the preface, with the cursor at [cursor] (the end by default). */
    fun editPreface(cursor: Int? = null) {
        val raw = savedPreface ?: file.doc.preface.trimEnd()
        prefaceDraft = TextFieldValue(raw, TextRange((cursor ?: raw.length).coerceIn(0, raw.length)))
        prefaceHistory.clear()
    }
    val settings by viewModel.settings.collectAsState()

    val selection = viewModel.selection?.takeIf { it.fileUri == file.uri }
    val selecting = selection != null
    val scrollBehavior = largeBarScrollBehavior(selecting)
    val listState = rememberLazyListState()

    BackHandler(enabled = viewModel.searchActive) { viewModel.setSearch(false) }
    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    BackHandler(enabled = prefaceDraft != null) { commitPreface() }
    DisposableEffect(Unit) { onDispose { commitPreface() } }
    // Closing the keyboard ends the preface editing (and saves it)
    ClearFocusWhenKeyboardHides()

    Scaffold(
        modifier = Modifier
            .sharedContainer("notebook-${file.uri}", NotebookCardShape)
            .imePadding(),
        topBar = {
            Box(Modifier.tapToTop { listState.animateScrollToItem(0); scrollBehavior.state.expand() }) {
            TopBarWithSelection(selecting, selectionBar = { SelectionTopBar(viewModel, library, file) }, largeBar = scrollBehavior.state) {
                SearchableTopBar(
                    title = file.title,
                    subtitle = {
                        CountsSubtitle(file.notes.size, file.openTasks, onTodoClick = { viewModel.searchFor("it.todo") })
                    },
                    navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                    navigationDescription = stringResource(R.string.back),
                    onNavigation = { viewModel.back() },
                    searchActive = viewModel.searchActive,
                    onSearchActiveChange = viewModel::setSearch,
                    query = viewModel.query,
                    onQueryChange = { viewModel.query = it },
                    vocabulary = if (viewModel.searchActive) remember(library) { searchVocabulary(library) } else null,
                    searchPlaceholder = stringResource(R.string.search_in, file.title),
                    scrollBehavior = scrollBehavior,
                    extraActions = { FoldAllButton(viewModel, file.notes) },
                    sharedTitleKey = notebookTitleKey(file.uri),
                    // Saved with a b. condition, so it keeps to this notebook
                    onSaveSearch = { viewModel.saveCurrentSearch(file.ref.name.removeSuffix(".org")) }
                        .takeIf { viewModel.query.isNotBlank() || viewModel.tag != null },
                ) { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_preface)) },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { dismiss(); editPreface() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tree_view)) },
                        leadingIcon = { Icon(OrgwhalIcons.Tree, null) },
                        onClick = { dismiss(); viewModel.openTree(file.uri) },
                    )

                }
            }
            }
        },
        floatingActionButton = {
            if (prefaceDraft == null && !selecting) {
                FloatingActionButton(onClick = { viewModel.startNewNote(file.uri) }) { Icon(Icons.Default.Add, stringResource(R.string.new_note)) }
            }
        },
        bottomBar = {
            // List, checkbox, indent… over the keyboard while the preface is edited
            prefaceDraft?.takeIf { editToolbarShown() }?.let { draft ->
                EditToolbar(draft, onChange = { if (prefaceDraft != null) prefaceDraft = it }, history = prefaceHistory)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.searchActive) {
                SearchResults(viewModel, library, file.notes)
            } else {
                NotebookOutline(
                    viewModel = viewModel,
                    file = file,
                    preface = savedPreface?.let(Notebooks::prefaceText) ?: file.preface,
                    prefaceRaw = savedPreface ?: file.doc.preface.trimEnd(),
                    prefaceDraft = prefaceDraft,
                    // Only while editing: the keyboard can still update the field as it goes away
                    onPrefaceDraftChange = { new -> prefaceDraft?.let { prefaceDraft = typedText(it, new, prefaceHistory) } },
                    onCommitPreface = ::commitPreface,
                    onEditPreface = { editPreface(it) },
                    listState = listState,
                    selected = selection?.indices.orEmpty(),
                    showContent = settings.showContentInNotebook,
                    spacing = settings.noteSpacing.padding.dp,
                    scrollConnection = scrollBehavior.nestedScrollConnection,
                    onAddNear = { note, placement -> viewModel.startNewNote(file.uri, note, placement) },
                    onDelete = viewModel::deleteNote,
                )
            }
            if (selecting) {
                SelectionToolbar(viewModel, file, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
            } else if (!viewModel.searchActive) {
                JumpButton(
                    listState,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    enabled = prefaceDraft == null,
                    section = { outlineJumpEnd(listState, file.notes, viewModel::isFolded) },
                )
            }
        }
    }


}

@Composable
private fun FoldAllButton(viewModel: AppViewModel, notes: List<OrgNote>) {
    if (notes.indices.none { NoteTree.hasChildren(notes, it) }) return
    val anyExpanded = notes.indices.any { i -> NoteTree.hasChildren(notes, i) && !viewModel.isFolded(notes[i]) }
    IconButton(onClick = { viewModel.setAllFolded(notes, anyExpanded) }) {
        Icon(
            if (anyExpanded) OrgwhalIcons.UnfoldLess else OrgwhalIcons.UnfoldMore,
            stringResource(if (anyExpanded) R.string.collapse_all else R.string.expand_all),
        )
    }
}

/** Preface (editable in place) and the foldable outline of notes. */
@Composable
private fun NotebookOutline(
    viewModel: AppViewModel,
    file: NoteRepository.LoadedFile,
    /** Preface as shown, and as written in the file. */
    preface: String,
    prefaceRaw: String,
    prefaceDraft: TextFieldValue?,
    listState: LazyListState,
    onPrefaceDraftChange: (TextFieldValue) -> Unit,
    onCommitPreface: () -> Unit,
    onEditPreface: (cursor: Int?) -> Unit,
    selected: Set<Int>,
    showContent: Boolean,
    spacing: Dp,
    scrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    onAddNear: (OrgNote, Placement) -> Unit,
    onDelete: (OrgNote) -> Unit,
) {
    val shown = NoteTree.visible(file.notes, viewModel::isFolded).map { it.index }.toSet()
    val library by viewModel.library.collectAsState()
    val tableRecalc = viewModel.settings.collectAsState().value.tableRecalc
    val rowKeys = remember(file.notes) { outlineKeys(file.notes) }
    // The selection follows its notes by their keys: moved, the notes come a little before the
    // selection's new indices, which would mark the notes now in their old places meanwhile
    val selectedKeys = remember(selected) { selected.mapNotNull(rowKeys::getOrNull).toSet() }
    val marked = remember(selectedKeys, rowKeys) { rowKeys.indices.filter { rowKeys[it] in selectedKeys }.toSet() }
    val stagger = remember { FoldStagger() }
    val delays = stagger.delays(shown)
    // Rows slide to their new place only when notes move: when folding, the rows below already
    // follow the ones that grow or shrink, and sliding as well would move them a second time
    val motion = remember { OutlineMotion() }
    val moved = motion.slide(shown, rowKeys)
    // Only while the moved notes travel: afterwards, rows measured again as they come back into
    // view (scrolling up) would slide too, jumping about. Dragging the list ends it at once, else
    // pulling it right after a move sets the rows sliding further and further off
    val move = motion.moves
    val currentMove by rememberUpdatedState(move)
    var settledMove by remember { mutableStateOf(0) }
    // A move asked for slides the rows from then on, before the notes come back moved: the rows
    // must have a placement spec while still in their old places, else the move would bring them
    // in from the end of the list (with one all the time, a fling ending at the top drifts there)
    val request = viewModel.moveRequests
    val currentRequest by rememberUpdatedState(request)
    var settledRequest by remember { mutableStateOf(request) }
    LaunchedEffect(move) {
        delay(MOVE_SETTLE_MS)
        settledMove = move
    }
    LaunchedEffect(request) {
        delay(MOVE_SETTLE_MS)
        settledRequest = request
    }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                settledMove = currentMove
                settledRequest = currentRequest
            }
        }
    }
    val slide = (moved && settledMove != move) || settledRequest != request
    // The cascade follows the system's animation speed (none when animations are off)
    val context = LocalContext.current
    val animationScale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    val ancestorLevels = remember(file.notes) { NoteTree.ancestorLevels(file.notes) }
    val selecting = marked.isNotEmpty()

    PullToRefresh(viewModel) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 120.dp),
            // On the list rather than the Scaffold, so pulling down expands the app bar
            // before pull-to-refresh takes the rest of the gesture
            modifier = Modifier
                .fillMaxSize()
                // A swipe during the screen transition cuts it short
                .cutTransitionOnScroll()
                .nestedScroll(scrollConnection),
        ) {
            item(key = "header") {
                val prefaceStyle = contentStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                if (prefaceDraft != null) {
                    InPlaceField(
                        value = prefaceDraft,
                        onValueChange = onPrefaceDraftChange,
                        style = prefaceStyle,
                        placeholder = stringResource(R.string.preface_placeholder),
                        onFocusLost = onCommitPreface,
                        visualTransformation = TableLinesMonospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                } else if (preface.isNotBlank()) {
                    // The raw preface (shown as is, without its #+ lines) is edited from the
                    // end, or from where the text was tapped when it is shown the same
                    val shownAsRaw = preface == prefaceRaw
                    LinkedText(
                        preface,
                        prefaceStyle,
                        viewModel::openLink,
                        Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = null, indication = null) { onEditPreface(null) }
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        onTextTap = { onEditPreface(if (shownAsRaw) it else null) },
                    )
                }
            }
            if (file.notes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_notes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            // Every note stays in the list; folded ones collapse to zero height so
            // that folding and unfolding animate like an outline
            itemsIndexed(file.notes, key = { i, _ -> rowKeys[i] }) { _, note ->
                // Shown or hidden after its turn in the cascade
                val target = note.index in shown
                var visible by remember { mutableStateOf(target) }
                LaunchedEffect(target) {
                    delay(((delays[note.index] ?: 0L) * animationScale).toLong())
                    visible = target
                }
                AnimatedVisibility(
                    visible = visible,
                    // A moved note slides to its new place
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (slide) MoveSpec else null),
                    enter = expandVertically(FoldSpatialSpec) + fadeIn(FoldEffectsSpec),
                    exit = shrinkVertically(FoldSpatialSpec) + fadeOut(FoldEffectsSpec),
                ) {
                    val rowKey = note.fileUri + "#" + note.index
                    OutlineRow(
                        note = note,
                        ancestorLevels = ancestorLevels[note.index],
                        hasChildren = NoteTree.hasChildren(file.notes, note.index),
                        folded = viewModel.isFolded(note),
                        showContent = showContent,
                        spacing = spacing,
                        selected = note.index in marked,
                        selecting = selecting,
                        swipeOpen = viewModel.openSwipeRow,
                        onSwipeOpenChange = { open ->
                            viewModel.openSwipeRow = if (open) rowKey else viewModel.openSwipeRow.takeIf { it != rowKey }
                        },
                        onToggleDone = { viewModel.toggleDone(note) },
                        onToggleFold = { viewModel.toggleFold(note) },
                        onClick = {
                            when {
                                selecting -> viewModel.toggleSelected(note)
                                // A tap while a row shows its actions just closes it
                                viewModel.openSwipeRow != null -> viewModel.openSwipeRow = null
                                else -> viewModel.openNote(note)
                            }
                        },
                        onLongClick = {
                            viewModel.openSwipeRow = null
                            viewModel.toggleSelected(note)
                        },
                        onOpenLink = viewModel::openLink,
                        onAddNear = { placement -> onAddNear(note, placement) },
                        onDelete = { onDelete(note) },
                        onToggleCheckbox = { n -> viewModel.update(note) { it.copy(content = Checkboxes.toggle(it.content, n)) } },
                        tables = if (tableRecalc == TableRecalc.Never) { _ -> null } else remember(note.content, library, tableRecalc) { viewModel.tableLookup(note.content) },
                        liveFormulas = tableRecalc != TableRecalc.Never,
                        onTablesShown = if (tableRecalc == TableRecalc.Save) { { viewModel.saveShownTables(note) } } else null,
                    )
                }
            }
        }

        // The selected notes stay in view when they move, clear of the selection toolbar
        val toolbarRoom = with(LocalDensity.current) { 96.dp.toPx() }
        LaunchedEffect(marked, rowKeys) {
            val first = marked.minOrNull() ?: return@LaunchedEffect
            val last = marked.max()
            // Once the list is laid out with the notes in their new places
            withFrameNanos { }
            val layout = listState.layoutInfo
            val top = layout.viewportStartOffset
            val bottom = layout.viewportEndOffset - toolbarRoom
            val firstRow = layout.visibleItemsInfo.firstOrNull { it.key == rowKeys.getOrNull(first) }
            val lastRow = layout.visibleItemsInfo.firstOrNull { it.key == rowKeys.getOrNull(last) }
            when {
                // The header comes first, then one item per note
                firstRow == null -> listState.animateScrollToItem(first + 1)
                firstRow.offset < top -> listState.animateScrollBy((firstRow.offset - top).toFloat())
                lastRow == null -> listState.animateScrollBy((firstRow.offset - top).toFloat())
                lastRow.offset + lastRow.size > bottom ->
                    // Down to the last one, without losing the first
                    listState.animateScrollBy(minOf(lastRow.offset + lastRow.size - bottom, (firstRow.offset - top).toFloat()))
            }
        }
    }
}

/**
 * One note in the notebook outline: fold chevron (or bullet), then the note.
 * Swipe left for new-note actions and delete, swipe right to toggle TODO / DONE, long press to select.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlineRow(
    note: OrgNote,
    ancestorLevels: List<Int>,
    hasChildren: Boolean,
    folded: Boolean,
    showContent: Boolean,
    spacing: Dp,
    selected: Boolean,
    selecting: Boolean,
    swipeOpen: String?,
    onSwipeOpenChange: (Boolean) -> Unit,
    onToggleDone: () -> Unit,
    onToggleFold: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenLink: (String) -> Unit,
    onAddNear: (Placement) -> Unit,
    onDelete: () -> Unit,
    onToggleCheckbox: (Int) -> Unit,
    /** remote() lookups for the note's tables, and whether they show recalculated. */
    tables: (String) -> List<String>? = { null },
    liveFormulas: Boolean = false,
    /** Saves the note's tables recalculated, once its content is shown (when so set). */
    onTablesShown: (() -> Unit)? = null,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (folded) -90f else 0f,
        animationSpec = spring(FOLD_SPATIAL_DAMPING, FOLD_SPATIAL_STIFFNESS),
        label = "chevron",
    )

    // The chevron or bullet, as tall as the title's first line with the spacing around it
    val iconHeight = spacing * 2 + 24.dp
    val key = note.fileUri + "#" + note.index
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    SwipeRow(
        key = key,
        openKey = swipeOpen,
        onOpenChange = onSwipeOpenChange,
        swipeRightIcon = if (note.isDone) OrgwhalIcons.Undo else OrgwhalIcons.Check,
        onSwipeRight = onToggleDone,
        // Delete first, away from the new-note actions
        actions = listOf(SwipeAction(Icons.Default.Delete, stringResource(R.string.delete), onClick = onDelete)) +
            Placement.entries.map { placement ->
                SwipeAction(placementIcon(placement), stringResource(newNoteTitle(placement))) { onAddNear(placement) }
            },
        modifier = Modifier.sharedContainer("note-$key", RectangleShape),
        enabled = !selecting,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .drawBehind {
                    // Vertical guides: one per expanded ancestor, and one under this note's
                    // chevron when it is expanded, so they join into lines along the subtree
                    val stroke = 1.5.dp.toPx()
                    ancestorLevels.forEach { level ->
                        val x = guideX(level).toPx()
                        drawLine(guideColor, Offset(x, 0f), Offset(x, size.height), stroke)
                    }
                    if (hasChildren && !folded) {
                        val x = guideX(note.level).toPx()
                        drawLine(guideColor, Offset(x, (iconHeight / 2 + 16.dp).toPx()), Offset(x, size.height), stroke)
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .heightIn(min = spacing * 2 + 28.dp)
                .padding(start = indent(note.level), end = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (hasChildren) {
                IconButton(onClick = onToggleFold, modifier = Modifier.size(48.dp, iconHeight)) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        stringResource(if (folded) R.string.expand else R.string.collapse),
                        Modifier.rotate(chevronRotation),
                    )
                }
            } else {
                Box(Modifier.size(48.dp, iconHeight), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(vertical = spacing)) {
                // Title and content move into the note screen when the note opens
                NoteLine(
                    note,
                    subtitle = null,
                    onClick = null,
                    onOpenLink = onOpenLink,
                    titleModifier = Modifier.sharedText(noteTitleKey(note)),
                )
                val hasContent = remember(note.content) { !Blocks.showsNothing(note.content) }
                if (showContent && hasContent) {
                    if (onTablesShown != null) LaunchedEffect(note.content, tables) { onTablesShown() }
                    OrgContent(
                        note.content.trimIndent(),
                        contentStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)),
                        onOpenLink,
                        Modifier.padding(top = 4.dp).sharedText(noteContentKey(note)),
                        onToggleCheckbox = onToggleCheckbox,
                        tableNamed = tables,
                        liveFormulas = liveFormulas,
                        // A swipe is the note's: its tables scroll once it is open
                        scrollTables = false,
                    )
                }
            }
        }
    }
}

/**
 * Keys of the outline rows that follow a note when it moves: its text without the heading stars
 * (so a change of level keeps it), numbered among identical ones.
 */
/**
 * The list item a short jump goes to in a notebook's outline ([NoteTree.jumpEnd]): the header
 * comes first, then one item per note, folded ones of no height.
 */
private fun outlineJumpEnd(state: LazyListState, notes: List<OrgNote>, isFolded: (OrgNote) -> Boolean): Int? {
    val info = state.layoutInfo
    val shown = NoteTree.visible(notes, isFolded).map { it.index }.toSet()
    val rows = info.visibleItemsInfo.filter { it.index - 1 in shown && it.size > 0 }
    val top = rows.firstOrNull { it.offset + it.size > info.viewportStartOffset } ?: return null
    val bottom = rows.lastOrNull { it.offset + it.size <= info.viewportEndOffset } ?: top
    return NoteTree.jumpEnd(notes, shown, top.index - 1, bottom.index - 1)?.let { it + 1 }
}

internal fun outlineKeys(notes: List<OrgNote>): List<String> {
    val seen = HashMap<String, Int>()
    return notes.map { note ->
        val text = note.raw.trimStart('*')
        val n = seen.merge(text, 1, Int::plus)
        "$n#$text"
    }
}

/**
 * Notes shown or hidden by a fold come one after the other, like cards: from the top when they
 * appear, from the bottom when they go, the whole cascade within [TOTAL_MS].
 */
/** Whether the last change of the outline moved notes (their order changed) rather than folded them. */
private class OutlineMotion {
    private var shown: Set<Int>? = null
    private var keys: List<String>? = null
    private var moved = false

    /** How many times notes moved: the same notes read again (once saved) are no move. */
    var moves = 0
        private set

    fun slide(shown: Set<Int>, keys: List<String>): Boolean {
        if (this.keys != null && keys != this.keys) {
            moved = true
            moves++
        } else if (this.shown != null && shown != this.shown) moved = false
        this.shown = shown
        this.keys = keys
        return moved
    }
}

private class FoldStagger {
    private var last: Set<Int>? = null
    private var lastDelays = emptyMap<Int, Long>()

    /** Delay of each note that changes since the last [shown] set (the first one: none). */
    fun delays(shown: Set<Int>): Map<Int, Long> {
        val before = last
        if (shown == before) return lastDelays
        last = shown
        if (before == null) return emptyMap<Int, Long>().also { lastDelays = it }
        val appearing = (shown - before).sorted()
        val going = (before - shown).sortedDescending()
        lastDelays = cascade(appearing) + cascade(going)
        return lastDelays
    }

    private fun cascade(notes: List<Int>): Map<Int, Long> {
        if (notes.size < 2) return emptyMap()
        val step = minOf(STEP_MS, TOTAL_MS / (notes.size - 1))
        return notes.withIndex().associate { (i, index) -> index to i * step }
    }

    private companion object {
        const val STEP_MS = 30L
        const val TOTAL_MS = 200L
    }
}

// Folding and unfolding: the expressive default springs, four times as stiff to take half the time
private const val FOLD_SPATIAL_DAMPING = 0.8f
private const val FOLD_SPATIAL_STIFFNESS = 380f * 4
private val FoldSpatialSpec = spring(FOLD_SPATIAL_DAMPING, FOLD_SPATIAL_STIFFNESS, IntSize.VisibilityThreshold)
private val FoldEffectsSpec = spring<Float>(1f, 1600f * 4)
private val MoveSpec = spring(FOLD_SPATIAL_DAMPING, FOLD_SPATIAL_STIFFNESS, IntOffset.VisibilityThreshold)

/** How long a moved note may take to reach its new place, after which rows no longer slide. */
private const val MOVE_SETTLE_MS = 800L

/** Start padding of a note at [level] in the outline. */
private fun indent(level: Int) = 4.dp + 24.dp * (level - 1).coerceIn(0, 6)

/** Horizontal position of the guide line under the chevron of a note at [level]. */
private fun guideX(level: Int) = indent(level) + 24.dp
