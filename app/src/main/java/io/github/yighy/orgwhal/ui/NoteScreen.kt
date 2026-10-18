package io.github.yighy.orgwhal.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import io.github.yighy.orgwhal.data.TableRecalc
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import io.github.yighy.orgwhal.core.Links
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import java.time.LocalDateTime
import io.github.yighy.orgwhal.core.Clock
import io.github.yighy.orgwhal.core.Blocks
import androidx.compose.ui.platform.LocalResources
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.yighy.orgwhal.R
import io.github.yighy.orgwhal.core.SearchQuery
import io.github.yighy.orgwhal.core.Checkboxes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.key
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.AnimatedVisibility
import android.content.Intent
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.yighy.orgwhal.core.IdLinks
import io.github.yighy.orgwhal.core.Keywords
import io.github.yighy.orgwhal.core.NoteTree
import io.github.yighy.orgwhal.core.OrgNote
import io.github.yighy.orgwhal.core.OrgTime
import io.github.yighy.orgwhal.core.Placement
import io.github.yighy.orgwhal.data.NoteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import io.github.yighy.orgwhal.core.NoteEdit

/** Which part of the note is being edited. */
private enum class Editing { None, Title, Content }

/** Dates are still picked in dialogs; the rest is edited in place. */
private enum class DateDialog { Scheduled, Deadline }

/** Metadata being added or picked in place. */
private enum class MetaEdit { Tags, State, Priority, Property }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoteScreen(
    viewModel: AppViewModel,
    library: NoteRepository.Library,
    note: OrgNote,
    snackbar: SnackbarHostState,
    /** Set while the note is being created: it is written to the file once its title is saved. */
    newNote: Screen.NewNote? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings by viewModel.settings.collectAsState()
    val file = library.file(note.fileUri)
    val keywords = file?.doc?.keywords ?: Keywords.DEFAULT

    // A new note starts with its title being edited; its content edit goes on once it is created
    val resumeContent = remember { newNote == null && viewModel.takeResumedContentEditing(note) }
    var editing by remember {
        mutableStateOf(if (newNote != null) Editing.Title else if (resumeContent) Editing.Content else Editing.None)
    }
    var draft by remember {
        mutableStateOf(if (resumeContent) TextFieldValue(note.content, TextRange(note.content.length)) else TextFieldValue(""))
    }
    // Undo / redo while editing the content
    val history = remember { EditHistory() }
    // Typing help in the title (brackets), without undo
    val titleTyping = remember { EditHistory() }
    var dialog by remember { mutableStateOf<DateDialog?>(null) }
    // Tags being added, choices shown for state or priority, property being added
    var metaEdit by remember { mutableStateOf<MetaEdit?>(null) }
    // Name of the property being added, once saved
    var addedProperty by remember { mutableStateOf<String?>(null) }
    var moreMenu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var refiling by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    // The note as now loaded, also when saving as the screen goes away
    val currentNote by rememberUpdatedState(note)
    // Text just saved, shown until the note is reloaded with it (else the old text flashes)
    var saved by remember { mutableStateOf<Pair<Editing, String>?>(null) }
    LaunchedEffect(note) { saved = null }
    val title = saved?.takeIf { it.first == Editing.Title }?.second ?: note.title
    val content = saved?.takeIf { it.first == Editing.Content }?.second ?: note.content

    fun withText(edit: NoteEdit, what: Editing, text: String) =
        if (what == Editing.Title) edit.copy(title = text) else edit.copy(content = text)

    /** Saves the title or content being edited, if it changed, and leaves edit mode (no save button). */
    fun commit() {
        val what = editing
        if (what == Editing.None) return
        editing = Editing.None
        val text = draft.text
        val before = if (what == Editing.Title) currentNote.title else currentNote.content
        if (text == before) return
        saved = what to text
        // A new title can be offered to the links that still show the old one
        if (what == Editing.Title) viewModel.rename(currentNote, text) else viewModel.update(currentNote) { withText(it, what, text) }
    }

    /** Edits the title or content, with the cursor at [cursor] (the end by default). */
    fun startEditing(what: Editing, cursor: Int? = null) {
        // Saving the title creates the note, whose own screen then takes over the content edit
        if (newNote != null) viewModel.newNoteEditingContent = newNote.takeIf { what == Editing.Content }
        commit()
        val text = if (what == Editing.Title) title else content
        draft = TextFieldValue(text, TextRange((cursor ?: text.length).coerceIn(0, text.length)))
        history.clear()
        editing = what
    }

    fun openDialog(d: DateDialog) {
        commit()
        dialog = d
    }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }

    // A new note is written once it has a title: leaving it without one, after filling it, asks first
    var untitledWarning by remember { mutableStateOf(false) }
    fun leave() {
        val untitled = (if (editing == Editing.Title) draft.text else title).isBlank()
        val filled = (if (editing == Editing.Content) draft.text else content).isNotBlank() ||
            note.state != null || note.priority != null || note.tags.isNotEmpty() || note.properties.isNotEmpty() ||
            note.scheduled != null || note.deadline != null
        if (newNote != null && untitled && filled) {
            untitledWarning = true
        } else {
            commit()
            viewModel.back()
        }
    }
    BackHandler(enabled = editing != Editing.None || newNote != null) { leave() }
    BackHandler(enabled = fabMenu) { fabMenu = false }
    if (untitledWarning) {
        AlertDialog(
            onDismissRequest = { untitledWarning = false },
            title = { Text(stringResource(R.string.untitled_note_title)) },
            text = { Text(stringResource(R.string.untitled_note_text)) },
            confirmButton = {
                TextButton(onClick = { untitledWarning = false; startEditing(Editing.Title) }) { Text(stringResource(R.string.add_title)) }
            },
            dismissButton = {
                TextButton(onClick = { untitledWarning = false; commit(); viewModel.back() }) { Text(stringResource(R.string.discard)) }
            },
        )
    }
    DisposableEffect(Unit) { onDispose { commit() } }
    // Closing the keyboard ends the editing (and saves), instead of leaving a cursor behind
    ClearFocusWhenKeyboardHides()

    // "id:" and "file:" completion while editing the title, the content or a table field, gone
    // with the keyboard
    val cellDraft = remember { CellDraft() }
    val keyboard = keyboardShown()
    val cellTyping = cellDraft.value?.takeIf { editing == Editing.None && it.selection.collapsed && keyboard }
    val typing = cellTyping ?: draft.takeIf { editing != Editing.None && it.selection.collapsed && keyboard }
    val query = typing?.let { IdLinks.queryAt(it.text, it.selection.start) }
    // Each note's notebook and ancestors, so that a parent's title also finds its sub-notes
    val paths = remember(library) {
        library.files.associate { file -> file.uri to NoteTree.ancestorTitles(file.notes).map { listOf(file.title) + it } }
    }
    val suggestions = remember(query?.text, library) {
        query?.let { q -> IdLinks.suggestions(library.notes, q.text) { paths[it.fileUri]?.getOrNull(it.index).orEmpty() } }.orEmpty()
    }
    val fileQuery = typing?.takeIf { query == null }?.let { IdLinks.queryAt(it.text, it.selection.start, "file:") }
    val fileSuggestions = remember(fileQuery?.text, library) {
        fileQuery?.let { q ->
            val words = q.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            library.files
                .filter { f -> words.all { w -> f.ref.path.contains(w, ignoreCase = true) || f.title.contains(w, ignoreCase = true) } }
                .sortedBy { it.title.lowercase() }
                .take(20)
        }.orEmpty()
    }

    // Property names in use in all notebooks, with their values, the most used first
    val knownProperties = remember(library) {
        library.notes.flatMap { it.properties }
            .groupBy({ it.first.uppercase() }, { it.second.trim() })
            .entries.sortedByDescending { it.value.size }
            .associate { (name, values) ->
                name to values.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            }
    }

    /** Puts a link to the notebook in place of the "file:" being typed. */
    fun insertFileLink(file: NoteRepository.LoadedFile) {
        val q = fileQuery ?: return
        val title = file.title.replace('[', '(').replace(']', ')')
        val link = "[[file:${file.ref.path}][${if (cellTyping != null) title.replace('|', '/') else title}]]"
        if (cellTyping != null) {
            cellDraft.replace(TextFieldValue(cellTyping.text.replaceRange(q.start, q.end, link), TextRange(q.start + link.length)))
            return
        }
        draft = TextFieldValue(draft.text.replaceRange(q.start, q.end, link), TextRange(q.start + link.length))
    }

    fun insertLink(target: OrgNote) {
        val q = query ?: return
        // In a table field: its title without "|", which would split the field
        if (cellTyping != null) {
            scope.launch {
                val (id, _) = viewModel.idFor(target) ?: return@launch
                val link = IdLinks.format(id, displayTitle(target.title).replace('|', '/'))
                cellDraft.replace(TextFieldValue(cellTyping.text.replaceRange(q.start, q.end, link), TextRange(q.start + link.length)))
            }
            return
        }
        val what = editing
        val text = draft.text
        scope.launch {
            val (id, _) = viewModel.idFor(target) ?: return@launch
            val link = IdLinks.format(id, displayTitle(target.title))
            val linked = text.replaceRange(q.start, q.end, link)
            if (editing == what) {
                draft = TextFieldValue(linked, TextRange(q.start + link.length))
            } else {
                // The field was saved meanwhile (focus moved): save the link too
                viewModel.update(currentNote) { withText(it, what, linked) }
            }
        }
    }

    val scrollState = rememberScrollState()
    Scaffold(
        modifier = Modifier
            .sharedContainer("note-${note.fileUri}#${note.index}", RectangleShape)
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {},
                colors = blendedTopBarColors(),
                modifier = Modifier.tapToTop { scrollState.animateScrollTo(0) },
                navigationIcon = {
                    IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    // Nothing to link to until the note exists
                    if (newNote == null) Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false; shareMenu = false }) {
                            if (shareMenu) {
                                // The note as Org text (heading, planning, properties, content)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.note)) },
                                    onClick = { moreMenu = false; shareMenu = false; share(note.raw.trimEnd()) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title)) },
                                    onClick = { moreMenu = false; shareMenu = false; share(displayTitle(note.title)) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.content)) },
                                    enabled = note.content.isNotBlank(),
                                    onClick = { moreMenu = false; shareMenu = false; share(IdLinks.plainText(note.content.trimIndent())) },
                                )
                                return@DropdownMenu
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.copy_link)) },
                                leadingIcon = { Icon(OrgwhalIcons.Link, null) },
                                onClick = {
                                    moreMenu = false
                                    scope.launch {
                                        val (id, _) = viewModel.idFor(note) ?: return@launch
                                        context.getSystemService(ClipboardManager::class.java)
                                            .setPrimaryClip(ClipData.newPlainText("Org link", IdLinks.format(id, displayTitle(note.title))))
                                        viewModel.message(R.string.msg_link_copied)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_to)) },
                                leadingIcon = { Icon(OrgwhalIcons.ToNotebook, null) },
                                onClick = { moreMenu = false; refiling = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive)) },
                                leadingIcon = { Icon(OrgwhalIcons.Archive, null) },
                                onClick = { moreMenu = false; commit(); viewModel.archiveNote(note) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                                onClick = { shareMenu = true },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column {
                if (fileSuggestions.isNotEmpty()) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(fileSuggestions, key = { it.uri }) { target ->
                                ListItem(
                                    onClick = { insertFileLink(target) },
                                    supportingContent = { Text(target.ref.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                ) { Text(target.title) }
                            }
                        }
                    }
                }
                if (suggestions.isNotEmpty()) {
                    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                        LazyColumn(Modifier.heightIn(max = 240.dp)) {
                            items(suggestions, key = { it.fileUri + "#" + it.index }) { target ->
                                ListItem(
                                    onClick = { insertLink(target) },
                                    supportingContent = {
                                        val targetFile = library.file(target.fileUri)
                                        val path = listOf(targetFile?.title ?: target.fileName) +
                                            targetFile?.notes?.let { NoteTree.ancestors(it, target.index) }.orEmpty().map { displayTitle(it.title) }
                                        Text(path.joinToString("  ›  "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                ) { Text(displayTitle(target.title)) }
                            }
                        }
                    }
                }
                // List, checkbox, indent… over the keyboard while the content is edited
                if (editing == Editing.Content && editToolbarShown()) {
                    EditToolbar(draft, onChange = { if (editing == Editing.Content) draft = it }, history = history)
                }
            }
        },
        floatingActionButton = {
            // New note above, under or below this one; hidden while typing and until the note exists
            if (newNote == null) {
                FloatingActionButtonMenu(
                    expanded = fabMenu,
                    button = {
                        ToggleFloatingActionButton(
                            checked = fabMenu,
                            onCheckedChange = { commit(); fabMenu = it },
                            modifier = Modifier.animateFloatingActionButton(
                                visible = (editing == Editing.None && !WindowInsets.isImeVisible) || fabMenu,
                                alignment = Alignment.BottomEnd,
                            ),
                        ) {
                            val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add } }
                            Icon(
                                rememberVectorPainter(icon),
                                contentDescription = stringResource(if (fabMenu) R.string.close else R.string.new_note),
                                modifier = Modifier.animateIcon({ checkedProgress }),
                            )
                        }
                    },
                ) {
                    Placement.entries.forEach { placement ->
                        FloatingActionButtonMenuItem(
                            onClick = {
                                fabMenu = false
                                viewModel.startNewNote(note.fileUri, note, placement)
                            },
                            text = { Text(stringResource(newNoteTitle(placement))) },
                            icon = { Icon(placementIcon(placement), null) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val screenFocus = LocalFocusManager.current
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // A swipe during the screen transition cuts it short
                .cutTransitionOnScroll()
                // A tap on nothing (under the content) ends the editing in place, saving it
                .clickable(interactionSource = null, indication = null) { screenFocus.clearFocus() }
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
            // Breadcrumbs: notebook · ancestors (or where a new note goes); for a note just
            // shared from another app, a tap chooses where it goes
            val shared = viewModel.sharedNote?.let { it.fileUri == note.fileUri && it.index == note.index } == true
            FlowRow(verticalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
                val crumbs = buildList<Pair<String, () -> Unit>> {
                    add((file?.title ?: note.fileName) to { if (shared) refiling = true else viewModel.openNotebook(note.fileUri) })
                    if (newNote != null) add(positionLabel(newNote) to {})
                    else file?.notes?.let { notes ->
                        NoteTree.ancestors(notes, note.index).forEach { a ->
                            add(displayTitle(a.title) to { if (shared) refiling = true else viewModel.openNote(a) })
                        }
                    }
                }
                crumbs.forEachIndexed { i, (label, open) ->
                    if (i > 0) Text("  ·  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = open),
                    )
                }
                if (shared) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        stringResource(R.string.move_to),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).clickable { refiling = true },
                    )
                }
            }

            // Title: edited where it is shown, looking the same; a tap puts the cursor there
            val titleStyle = MaterialTheme.typography.headlineMedium
            val titlePlaceholder = stringResource(if (newNote != null) R.string.title_placeholder_new else R.string.untitled)
            if (editing == Editing.Title) {
                InPlaceField(
                    value = draft,
                    onValueChange = {
                        if (editing == Editing.Title) {
                            // Brackets close themselves (links); no lists in a title
                            val typed = typedText(draft, it, titleTyping, lists = false)
                            draft = TextFieldValue(typed.text.replace("\n", " "), typed.selection, typed.composition)
                        }
                    },
                    style = titleStyle,
                    placeholder = titlePlaceholder,
                    onFocusLost = { if (editing == Editing.Title) commit() },
                    onDone = { if (editing == Editing.Title) commit() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = null, indication = null) { startEditing(Editing.Title) }
                        .padding(vertical = 8.dp),
                ) {
                    if (title.isBlank()) {
                        Text(titlePlaceholder, style = titleStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Links in the title open their note
                        LinkedText(
                            title,
                            titleStyle,
                            viewModel::openLink,
                            Modifier.sharedText(noteTitleKey(note)),
                            onTextTap = { startEditing(Editing.Title, it) },
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Metadata: only what is set, edited in place (dates in a dialog); rows unfold
            // when added and fold away when removed
            MetaSlot(note.tags.isNotEmpty() || metaEdit == MetaEdit.Tags) {
                TagsEditor(
                    tags = note.tags,
                    allTags = library.tags,
                    focusOnStart = metaEdit == MetaEdit.Tags,
                    // Tags already there are not added twice (several can be typed before the note reloads)
                    onAdd = { tag ->
                        viewModel.update(note) { e -> if (e.tags.any { it.equals(tag, ignoreCase = true) }) e else e.copy(tags = e.tags + tag) }
                    },
                    onRemove = { tag -> viewModel.update(note) { e -> e.copy(tags = e.tags.filterNot { it.equals(tag, ignoreCase = true) }) } },
                    onFocusLost = { if (metaEdit == MetaEdit.Tags) metaEdit = null },
                )
            }
            val state = lastNonNull(note.state)
            MetaSlot(note.state != null || metaEdit == MetaEdit.State) {
                ChoiceRow(
                    label = stringResource(R.string.state),
                    options = keywords.all,
                    // While folding away, the row keeps showing the state it had
                    selected = note.state ?: state.takeIf { metaEdit != MetaEdit.State },
                    expanded = metaEdit == MetaEdit.State,
                    onExpandedChange = { commit(); metaEdit = if (it) MetaEdit.State else null },
                    onSelect = { viewModel.setState(note, it) },
                ) { StateLabel(it, it in keywords.done) }
            }
            val priority = lastNonNull(note.priority)
            MetaSlot(note.priority != null || metaEdit == MetaEdit.Priority) {
                ChoiceRow(
                    label = stringResource(R.string.priority),
                    options = listOf("A", "B", "C"),
                    selected = note.priority ?: priority.takeIf { metaEdit != MetaEdit.Priority },
                    expanded = metaEdit == MetaEdit.Priority,
                    onExpandedChange = { commit(); metaEdit = if (it) MetaEdit.Priority else null },
                    onSelect = { p -> viewModel.update(note) { it.copy(priority = p) } },
                ) { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
            val scheduled = lastNonNull(note.scheduled)
            MetaSlot(note.scheduled != null) {
                MetaRow(stringResource(R.string.scheduled), displayTime(resources, scheduled).orEmpty(), onClick = { openDialog(DateDialog.Scheduled) })
            }
            val deadline = lastNonNull(note.deadline)
            MetaSlot(note.deadline != null) {
                MetaRow(stringResource(R.string.deadline), displayTime(resources, deadline).orEmpty(), onClick = { openDialog(DateDialog.Deadline) })
            }
            val closed = lastNonNull(note.closed)
            MetaSlot(note.closed != null) {
                MetaRow(stringResource(R.string.closed), displayTime(resources, closed).orEmpty(), onClick = null)
            }
            // Time clocked: a tap starts or stops the clock
            val clocked = remember(content) { Clock.entries(content).isNotEmpty() }
            MetaSlot(clocked) {
                ClockRow(content, onStart = { commit(); viewModel.clockIn(note) }, onStop = { commit(); viewModel.clockOut(note) })
            }
            // Properties chosen in the settings stay hidden (still in the file)
            val takenProperties = note.properties.map { it.first.uppercase() }.toSet()
            note.properties.filter { it.first.uppercase() !in settings.hiddenProperties }.forEach { (name, value) ->
                key(name.uppercase()) {
                    // The property just added replaces its new row without unfolding again
                    Appear(animate = !name.equals(addedProperty, ignoreCase = true)) {
                        if (name.equals("ID", ignoreCase = true)) {
                            // Read-only, so links to the note never break by mistake; a tap copies it
                            MetaRow(name, value, onClick = {
                                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ID", value))
                                viewModel.message(R.string.msg_id_copied)
                            })
                        } else {
                            PropertyRow(
                                name = name,
                                value = value,
                                focusOnStart = false,
                                known = knownProperties,
                                taken = takenProperties,
                                onSave = { n, v ->
                                    viewModel.update(note) { e ->
                                        e.copy(properties = e.properties.map { if (it.first.equals(name, ignoreCase = true)) n to v else it })
                                    }
                                },
                                onRemove = {
                                    viewModel.update(note) { e -> e.copy(properties = e.properties.filterNot { it.first.equals(name, ignoreCase = true) }) }
                                },
                                onOpenLink = viewModel::openLink,
                            )
                        }
                    }
                }
            }
            // The new property stays until the note has it, so it does not blink when saved
            val adding = metaEdit == MetaEdit.Property &&
                note.properties.none { it.first.equals(addedProperty, ignoreCase = true) }
            if (metaEdit == MetaEdit.Property && !adding) metaEdit = null
            if (adding) {
                Appear {
                    PropertyRow(
                        name = "",
                        value = "",
                        focusOnStart = true,
                        known = knownProperties,
                        taken = takenProperties,
                        onSave = { n, v -> addedProperty = n; viewModel.update(note) { e -> e.copy(properties = e.properties + (n to v)) } },
                        onRemove = { metaEdit = null },
                        onOpenLink = viewModel::openLink,
                    )
                }
            }

            // What can still be added, on one line that scrolls
            val addChips = listOfNotNull(
                (stringResource(R.string.tags) to { metaEdit = MetaEdit.Tags }).takeIf { note.tags.isEmpty() && metaEdit != MetaEdit.Tags },
                (stringResource(R.string.state) to { metaEdit = MetaEdit.State }).takeIf { note.state == null && metaEdit != MetaEdit.State },
                (stringResource(R.string.priority) to { metaEdit = MetaEdit.Priority }).takeIf { note.priority == null && metaEdit != MetaEdit.Priority },
                (stringResource(R.string.scheduled) to { openDialog(DateDialog.Scheduled) }).takeIf { note.scheduled == null },
                (stringResource(R.string.deadline) to { openDialog(DateDialog.Deadline) }).takeIf { note.deadline == null },
                (stringResource(R.string.property) to { addedProperty = null; metaEdit = MetaEdit.Property }).takeIf { metaEdit != MetaEdit.Property },
                (stringResource(R.string.clock) to { viewModel.clockIn(note) }).takeIf { !clocked && newNote == null },
            )
            val addRow = rememberLazyListState()
            // A chip coming back at the start is shown, not left scrolled out of sight
            LaunchedEffect(addChips.firstOrNull()?.first) { addRow.animateScrollToItem(0) }
            LazyRow(
                state = addRow,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                // Up to the screen edges, so chips scroll out of sight there
                modifier = Modifier.padding(vertical = 4.dp).layout { measurable, constraints ->
                    val extra = 16.dp.roundToPx() * 2
                    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
                }.fadingEdges(addRow),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(addChips, key = { it.first }) { (label, onClick) ->
                    AssistChip(
                        onClick = { commit(); onClick() },
                        label = { Text(label) },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        modifier = Modifier.animateItem(fadeOutSpec = null),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Content: edited where it is shown, looking the same
            val contentStyle = contentStyle(MaterialTheme.typography.bodyLarge)
            val contentPlaceholder = stringResource(R.string.content_placeholder)
            if (editing == Editing.Content) {
                InPlaceField(
                    value = draft,
                    onValueChange = { if (editing == Editing.Content) draft = typedText(draft, it, history) },
                    style = contentStyle,
                    placeholder = contentPlaceholder,
                    onFocusLost = { if (editing == Editing.Content) commit() },
                    visualTransformation = TableLinesMonospace,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            } else {
                // A table field being edited: a tap elsewhere in the content only saves it (the
                // text editor, opened from the text as it was, would write the field back). The
                // tap can take the focus from the field first, ending its editing just before
                var cellEditing by remember { mutableStateOf(false) }
                var cellEditEnded by remember { mutableLongStateOf(0L) }
                fun editingCell() = cellEditing || SystemClock.uptimeMillis() - cellEditEnded < CELL_TAP_MS
                val focusManager = LocalFocusManager.current
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = null, indication = null) {
                            if (editingCell()) focusManager.clearFocus() else startEditing(Editing.Content)
                        }
                        .padding(vertical = 8.dp),
                ) {
                    // Tables read with remote(), and recalculated as shown if so set
                    val tables = remember(content, library, settings.tableRecalc) { viewModel.tableLookup(content) }
                    if (settings.tableRecalc == TableRecalc.Save) {
                        LaunchedEffect(tables) {
                            val fresh = Blocks.recalculated(content, tables)
                            if (fresh != content) {
                                saved = Editing.Content to fresh
                                viewModel.update(note) { it.copy(content = fresh) }
                            }
                        }
                    }
                    // A logbook alone shows folded, over the placeholder
                    val empty = remember(content) { Blocks.showsNothing(content) }
                    if (content.isNotBlank()) {
                        OrgContent(
                            content,
                            contentStyle,
                            viewModel::openLink,
                            Modifier.sharedText(noteContentKey(note)),
                            onTextTap = { if (editingCell()) focusManager.clearFocus() else startEditing(Editing.Content, it) },
                            onCellEditing = { editing ->
                                cellEditing = editing
                                if (!editing) cellEditEnded = SystemClock.uptimeMillis()
                            },
                            onToggleCheckbox = { n ->
                                saved = Editing.Content to Checkboxes.toggle(content, n)
                                viewModel.update(note) { it.copy(content = Checkboxes.toggle(it.content, n)) }
                            },
                            tableNamed = tables,
                            liveFormulas = settings.tableRecalc != TableRecalc.Never,
                            showLogbook = true,
                            cellDraft = cellDraft,
                            onEditTable = if (settings.editCells) {
                                { change ->
                                    saved = Editing.Content to change(content)
                                    viewModel.update(note) { it.copy(content = change(it.content)) }
                                }
                            } else {
                                null
                            },
                            onRecalculate = { recalculated, skipped ->
                                if (recalculated != null) {
                                    saved = Editing.Content to recalculated
                                    viewModel.update(note) { it.copy(content = recalculated) }
                                }
                                viewModel.message(
                                    when {
                                        skipped > 0 -> resources.getQuantityString(R.plurals.msg_formulas_skipped, skipped, skipped)
                                        recalculated != null -> resources.getString(R.string.msg_recalculated)
                                        else -> resources.getString(R.string.msg_table_up_to_date)
                                    },
                                )
                            },
                        )
                    }
                    if (empty) {
                        Text(
                            contentPlaceholder,
                            style = contentStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (content.isNotBlank()) Modifier.padding(top = 8.dp) else Modifier,
                        )
                    }
                }
            }
            // Notes that link here with id: (as Obsidian's backlinks)
            val id = note.id
            val backlinks = remember(id, library) {
                id?.let { target ->
                    library.notes.filter { other ->
                        (other.fileUri != note.fileUri || other.index != note.index) &&
                            IdLinks.find(other.raw).any { it.id.equals(target, ignoreCase = true) }
                    }
                }.orEmpty()
            }
            if (backlinks.isNotEmpty() && newNote == null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.linked_from),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                backlinks.forEach { other ->
                    NoteLine(
                        note = other,
                        subtitle = library.file(other.fileUri)?.title,
                        onClick = { commit(); viewModel.openNote(other) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clickable(interactionSource = null, indication = null) {
                        if (editing != Editing.Content) startEditing(Editing.Content)
                    },
            )
        }
        // Over the page, not while typing
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.BottomCenter) {
            JumpButton(scrollState, Modifier.padding(bottom = 16.dp), enabled = !keyboard)
        }
    }

    if (refiling) {
        RefileDialog(
            library = library,
            moving = listOf(note),
            onDismiss = { refiling = false },
            onConfirm = { uri, parent ->
                refiling = false
                commit()
                viewModel.refileNote(note, uri, parent)
            },
        )
    }

    when (dialog) {
        null -> Unit
        DateDialog.Scheduled -> TimestampDialog(
            initial = note.scheduled,
            onDismiss = { dialog = null },
            onConfirm = { t -> dialog = null; viewModel.update(note) { it.copy(scheduled = t) } },
        )
        DateDialog.Deadline -> TimestampDialog(
            initial = note.deadline,
            onDismiss = { dialog = null },
            onConfirm = { t -> dialog = null; viewModel.update(note) { it.copy(deadline = t) } },
        )
    }
}

/** "Label    value" line; [onClick] null makes it read-only. */
@Composable
private fun MetaRow(label: String, value: String, onClick: (() -> Unit)?) {
    MetaRow(label, onClick) { Text(value, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun MetaRow(label: String, onClick: (() -> Unit)?, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = metaLabelStyle(), modifier = Modifier.width(130.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { value() }
    }
}

/** A metadata row that unfolds when shown and folds away when hidden. */
@Composable
private fun MetaSlot(visible: Boolean, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(motion.defaultEffectsSpec()),
    ) { content() }
}

/** Unfolds [content] when it first appears (if [animate]). */
@Composable
private fun Appear(animate: Boolean = true, content: @Composable () -> Unit) {
    val visible = remember { MutableTransitionState(!animate).apply { targetState = true } }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visibleState = visible,
        enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(motion.defaultEffectsSpec()),
    ) { content() }
}

/** [value], or the last value it had when it becomes null (for rows folding away). */
@Composable
private fun <T : Any> lastNonNull(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return last[0] as T?
}

@Composable
private fun metaLabelStyle() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)

/** Style of a field typed in place: [style] in the text color, ending on the right if [end]. */
@Composable
private fun fieldStyle(style: TextStyle, end: Boolean = false) =
    style.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = if (end) TextAlign.End else TextAlign.Start)

/**
 * Tags as chips (a tap removes one) followed by a field adding tags: space, ":" or Done adds
 * what was typed, and existing tags containing it are suggested below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    allTags: List<String>,
    focusOnStart: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onFocusLost: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val text = field.text
    fun clear() { field = TextFieldValue("") }
    // The + chip shows a field while typing
    var typing by remember { mutableStateOf(focusOnStart) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(typing) { if (typing) focus.requestFocus() }

    fun add(raw: String) {
        raw.split(Regex("[\\s:]+"))
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .filter { new -> tags.none { it.equals(new, ignoreCase = true) } }
            .forEach(onAdd)
    }
    val suggestions = remember(text, allTags, tags) {
        val typed = text.trim()
        if (typed.isEmpty()) {
            emptyList()
        } else {
            allTags
                .filter { tag -> tag.contains(typed, ignoreCase = true) && tags.none { it.equals(tag, ignoreCase = true) } }
                .sortedByDescending { it.startsWith(typed, ignoreCase = true) }
                .take(8)
        }
    }

    val suggestionRow = rememberLazyListState()
    val motion = MaterialTheme.motionScheme
    // Tags shown, including removed ones while they fold away; those there at first do not unfold
    val initialTags = remember { tags.map { it.lowercase() }.toSet() }
    val shown = remember { mutableStateListOf<String>().apply { addAll(tags) } }
    LaunchedEffect(tags) { tags.forEach { tag -> if (shown.none { it.equals(tag, ignoreCase = true) }) shown.add(tag) } }
    // The field grows with what is typed
    val fieldStyle = MaterialTheme.typography.labelLarge
    val tagPlaceholder = stringResource(R.string.tag)
    val measurer = rememberTextMeasurer()
    val typedWidth = with(LocalDensity.current) { measurer.measure(text.ifEmpty { tagPlaceholder }, fieldStyle).size.width.toDp() }
    val fieldWidth by animateDpAsState(typedWidth + 28.dp, motion.fastSpatialSpec(), label = "tag field")
    val chipShape = AssistChipDefaults.shape
    val chipBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.tags), style = metaLabelStyle(), modifier = Modifier.width(130.dp))
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // Removed tags stay until they have folded away
                shown.toList().forEach { tag ->
                    key(tag.lowercase()) {
                        val present = tags.any { it.equals(tag, ignoreCase = true) }
                        val visible = remember { MutableTransitionState(tag.lowercase() in initialTags).apply { targetState = true } }
                        visible.targetState = present
                        if (visible.isIdle && !visible.currentState) LaunchedEffect(Unit) { shown.remove(tag) }
                        AnimatedVisibility(
                            visibleState = visible,
                            enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                            exit = shrinkHorizontally(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
                        ) {
                            InputChip(
                                selected = false,
                                onClick = { onRemove(tag) },
                                label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.remove_tag, tag), Modifier.size(InputChipDefaults.IconSize)) },
                            )
                        }
                    }
                }
                if (typing) {
                    // A field shaped like the chips
                    BasicTextField(
                        value = field,
                        onValueChange = { typed ->
                            if (typed.text.any { it.isWhitespace() || it == ':' }) {
                                add(typed.text)
                                clear()
                            } else {
                                field = typed
                            }
                        },
                        singleLine = true,
                        textStyle = fieldStyle(MaterialTheme.typography.labelLarge),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add(text); clear() }),
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .width(fieldWidth)
                            .height(32.dp)
                            .focusRequester(focus)
                            .onFocusChanged {
                                if (focused && !it.isFocused) {
                                    add(text)
                                    clear()
                                    typing = false
                                    onFocusLost()
                                }
                                focused = it.isFocused
                            },
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .border(chipBorder, chipShape)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (text.isEmpty()) {
                                    Text(tagPlaceholder, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        },
                    )
                } else {
                    // Small + chip
                    Surface(
                        onClick = { typing = true },
                        shape = chipShape,
                        border = chipBorder,
                        modifier = Modifier.padding(vertical = 8.dp).size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, stringResource(R.string.add_tag), Modifier.size(AssistChipDefaults.IconSize))
                        }
                    }
                }
            }
        }
        AnimatedVisibility(suggestions.isNotEmpty()) {
            LazyRow(
                state = suggestionRow,
                modifier = Modifier.fillMaxWidth().fadingEdges(suggestionRow),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            ) {
                items(suggestions, key = { it.lowercase() }) { tag ->
                    SuggestionChip(onClick = { add(tag); clear() }, label = { Text(tag) }, modifier = Modifier.animateItem(fadeOutSpec = null))
                }
            }
        }
    }
}

/**
 * The value chosen among [options] (or nothing); a tap shows the options as chips, picking one
 * (or the chosen one again, to remove it) hides them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String?) -> Unit,
    shown: @Composable (String) -> Unit,
) {
    Column {
        MetaRow(label, onClick = { onExpandedChange(!expanded) }) {
            if (selected != null) {
                shown(selected)
            } else {
                Text(stringResource(R.string.none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val motion = MaterialTheme.motionScheme
        AnimatedVisibility(
            expanded,
            enter = expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Top) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec(), shrinkTowards = Alignment.Top) + fadeOut(motion.defaultEffectsSpec()),
        ) {
            FlowRow(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = {
                            onSelect(if (option == selected) null else option)
                            onExpandedChange(false)
                        },
                        label = { Text(option) },
                    )
                }
            }
        }
    }
}

/**
 * Time clocked on the note, with a button to start or stop it: while the clock runs, the time
 * since it started (ticking) with the total beside; else the total, with the last clocking beside.
 */
@Composable
private fun ClockRow(content: String, onStart: () -> Unit, onStop: () -> Unit) {
    val running = remember(content) { Clock.running(content) }
    val entries = remember(content) { Clock.entries(content).size }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    if (running != null) {
        LaunchedEffect(running) {
            while (true) {
                now = LocalDateTime.now()
                delay(15_000)
            }
        }
    }
    val total = Clock.format(Clock.total(content, now))
    val last = Clock.last(content)?.let { Clock.format(Clock.duration(it, now)) }
    // The other figure only says something new when there was more than one clocking
    val main = if (running != null) last ?: total else total
    val aside = when {
        entries < 2 -> null
        running != null -> stringResource(R.string.clock_total, total)
        else -> last?.let { stringResource(R.string.clock_last, it) }
    }
    val color by animateColorAsState(
        if (running != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "clock color",
    )
    MetaRow(stringResource(R.string.clock), onClick = if (running != null) onStop else onStart) {
        Row(Modifier.animateContentSize(), verticalAlignment = Alignment.CenterVertically) {
            // Starting the clock brings its session up from below, stopping takes the total back
            // down; the minutes ticking change in place
            AnimatedContent(
                targetState = main to (running != null),
                contentKey = { it.second },
                transitionSpec = {
                    val up = targetState.second
                    (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically { if (up) -it else it } + fadeOut())
                },
                label = "clock time",
            ) { (time, _) ->
                Text(time, style = MaterialTheme.typography.bodyLarge, color = color)
            }
            AnimatedContent(aside, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "clock aside") { text ->
                if (text != null) {
                    Text(
                        " · $text",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                running != null,
                transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
                label = "clock button",
            ) { on ->
                Icon(
                    if (on) OrgwhalIcons.Stop else Icons.Default.PlayArrow,
                    stringResource(if (on) R.string.clock_out else R.string.clock_in),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Properties whose values are not suggested, being different for each note. */
private val UNIQUE_PROPERTIES = setOf("ID", "CUSTOM_ID", SearchQuery.CREATED_PROPERTY)

/**
 * A property edited in place: name on the left, value on the right, saved when the row loses
 * the focus. Emptying the name removes it. The names in use ([known]) and their values are
 * suggested while typing, except names the note already has ([taken]).
 */
@Composable
private fun PropertyRow(
    name: String,
    value: String,
    focusOnStart: Boolean,
    known: Map<String, List<String>>,
    taken: Set<String>,
    onSave: (String, String) -> Unit,
    onRemove: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var n by remember(name) { mutableStateOf(name) }
    var v by remember(value) { mutableStateOf(value) }
    var hasFocus by remember { mutableStateOf(false) }
    var nameFocused by remember { mutableStateOf(false) }
    var valueFocused by remember { mutableStateOf(false) }
    // Edited since it last had the focus, and not saved yet
    var pending by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val valueFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (focusOnStart) focus.requestFocus() }

    fun save() {
        if (!pending) return
        pending = false
        when {
            n.isBlank() -> onRemove()
            n != name || v.trim() != value -> onSave(n, v.trim())
        }
    }
    val currentSave by rememberUpdatedState(::save)
    // Leaving the screen while editing still saves
    DisposableEffect(Unit) { onDispose { currentSave() } }

    // Gone with the keyboard, at once
    val keyboard = keyboardShown()
    val suggestions = when {
        !keyboard -> emptyList()
        nameFocused -> known.keys.filter { it.contains(n, ignoreCase = true) && it != n && (it !in taken || it == name.uppercase()) }
        valueFocused && n !in UNIQUE_PROPERTIES -> known[n].orEmpty().filter { it.contains(v.trim(), ignoreCase = true) && it != v.trim() }
        else -> emptyList()
    }.take(8)

    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged {
                hasFocus = it.hasFocus
                if (it.hasFocus) {
                    pending = true
                } else if (pending) {
                    // Going from the name to the value, the row loses the focus for a moment:
                    // save only if it is still without focus afterwards
                    scope.launch {
                        delay(150)
                        if (!hasFocus) save()
                    }
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = n,
                onValueChange = { typed -> n = typed.filterNot { it.isWhitespace() || it == ':' }.uppercase() },
                singleLine = true,
                textStyle = fieldStyle(metaLabelStyle()),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.width(130.dp).focusRequester(focus).onFocusChanged { nameFocused = it.isFocused },
                decorationBox = { field ->
                    Box {
                        if (n.isEmpty()) Text(stringResource(R.string.property_name), style = metaLabelStyle(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        field()
                    }
                },
            )
            // Links show as links while the value is not edited: a tap on one opens it, beside them edits
            val showLinks = !valueFocused && Links.find(v).isNotEmpty()
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BasicTextField(
                    value = v,
                    onValueChange = { v = it },
                    singleLine = true,
                    textStyle = fieldStyle(MaterialTheme.typography.bodyLarge, end = true),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().alpha(if (showLinks) 0f else 1f).focusRequester(valueFocus).onFocusChanged { valueFocused = it.isFocused },
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (v.isEmpty()) Text(stringResource(R.string.property_value), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            field()
                        }
                    },
                )
                if (showLinks) {
                    LinkedText(
                        v,
                        MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
                        onOpenLink = onOpenLink,
                        // As wide as its text: beside it, the field under takes the taps
                        modifier = Modifier,
                        maxLines = 2,
                        onTextTap = { valueFocus.requestFocus() },
                    )
                }
            }
            // Removing is offered while the property is being edited
            if (hasFocus) {
                IconButton(onClick = { n = ""; focusManager.clearFocus(); save() }) { Icon(Icons.Default.Close, stringResource(R.string.remove_property)) }
            }
        }
        if (suggestions.isNotEmpty()) {
            val suggestionRow = rememberLazyListState()
            LazyRow(
                state = suggestionRow,
                modifier = Modifier.fillMaxWidth().fadingEdges(suggestionRow),
                // Under the field they fill
                horizontalArrangement = Arrangement.spacedBy(6.dp, if (nameFocused) Alignment.Start else Alignment.End),
            ) {
                items(suggestions, key = { it }) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            // A name goes on to its value; a value ends the editing
                            if (nameFocused) {
                                n = suggestion
                                valueFocus.requestFocus()
                            } else {
                                v = suggestion
                                focusManager.clearFocus()
                            }
                        },
                        label = { Text(suggestion) },
                        modifier = Modifier.animateItem(fadeOutSpec = null),
                    )
                }
            }
        }
    }
}

/** The note's title and content move from its row in the notebook into the note screen. */
fun noteTitleKey(note: OrgNote) = "note-title-${note.fileUri}#${note.index}"

fun noteContentKey(note: OrgNote) = "note-content-${note.fileUri}#${note.index}"

@StringRes
fun newNoteTitle(placement: Placement) = when (placement) {
    Placement.Above -> R.string.new_note_above
    Placement.Under -> R.string.new_sub_note
    Placement.Below -> R.string.new_note_below
}

/** Where a new note goes, e.g. "sub-note of Design". */
@Composable
private fun positionLabel(screen: Screen.NewNote): String {
    val anchor = screen.anchor?.let { displayTitle(it.title) } ?: return stringResource(R.string.position_end)
    return when (screen.placement) {
        Placement.Above -> stringResource(R.string.position_above, anchor)
        Placement.Under -> stringResource(R.string.position_under, anchor)
        Placement.Below, null -> stringResource(R.string.position_below, anchor)
    }
}

/** How soon after a table field stops being edited a tap still counts as ending its editing. */
private const val CELL_TAP_MS = 500L
