package io.github.yighy.orgwhal.ui

import io.github.yighy.orgwhal.data.TemplatesHidden
import io.github.yighy.orgwhal.data.ReminderButton
import io.github.yighy.orgwhal.core.TableLookup
import io.github.yighy.orgwhal.core.TableFormulas
import io.github.yighy.orgwhal.core.Blocks
import io.github.yighy.orgwhal.data.TableRecalc
import io.github.yighy.orgwhal.core.Folding
import androidx.compose.runtime.mutableIntStateOf
import io.github.yighy.orgwhal.data.AppSettings
import io.github.yighy.orgwhal.core.Guide
import io.github.yighy.orgwhal.core.InternalLinks
import io.github.yighy.orgwhal.core.Cookies
import kotlinx.coroutines.flow.first
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import io.github.yighy.orgwhal.core.Sharing
import io.github.yighy.orgwhal.core.Clock
import io.github.yighy.orgwhal.core.Agenda
import io.github.yighy.orgwhal.data.FileNotSavedException
import io.github.yighy.orgwhal.data.NoteChangedException
import io.github.yighy.orgwhal.R
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.github.yighy.orgwhal.data.NoteSpacing
import io.github.yighy.orgwhal.data.Indentation
import io.github.yighy.orgwhal.data.ThemeMode
import java.time.LocalDateTime
import io.github.yighy.orgwhal.core.SearchQuery
import android.content.ActivityNotFoundException
import android.content.Intent
import io.github.yighy.orgwhal.data.SavedSearch
import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.yighy.orgwhal.core.IdLinks
import io.github.yighy.orgwhal.core.Keywords
import io.github.yighy.orgwhal.core.NoteEdit
import io.github.yighy.orgwhal.core.Outline
import io.github.yighy.orgwhal.core.Placement
import io.github.yighy.orgwhal.core.SectionEditor
import io.github.yighy.orgwhal.core.Subtree
import io.github.yighy.orgwhal.core.NoteStates
import io.github.yighy.orgwhal.core.NoteTree
import io.github.yighy.orgwhal.core.OrgTime
import io.github.yighy.orgwhal.core.OrgNote
import io.github.yighy.orgwhal.OrgwhalApplication
import io.github.yighy.orgwhal.data.LinkRenaming
import io.github.yighy.orgwhal.data.NoteRepository
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Screen {
    data object Notebooks : Screen
    data class Notebook(val fileUri: String) : Screen
    data class Note(val fileUri: String, val index: Int) : Screen
    data class Tree(val fileUri: String) : Screen
    data object Settings : Screen
    data object Agenda : Screen
    data object Searches : Screen

    /**
     * A note being created: nothing is written to the file until it gets a title.
     * [anchor] and [placement] say where it goes; without them it is added at the end of the notebook.
     */
    data class NewNote(val fileUri: String, val anchor: OrgNote? = null, val placement: Placement? = null) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val orgwhal = app as OrgwhalApplication
    private val repository = orgwhal.repository
    val library = repository.library

    private val appSettings = orgwhal.settings
    val settings = appSettings.values

    val canScheduleExactAlarms: Boolean get() = orgwhal.scheduler.canScheduleExact()

    var askedNotificationPermission: Boolean
        get() = appSettings.askedNotificationPermission
        set(value) {
            appSettings.askedNotificationPermission = value
        }

    /** True while a pull-to-refresh is running. */
    var refreshing by mutableStateOf(false)
        private set

    val stack = mutableStateListOf<Screen>(Screen.Notebooks)

    /** Direction of the last navigation, for the screen transition. */
    var navigatedForward by mutableStateOf(true)
        private set

    var searchActive by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var tag by mutableStateOf<String?>(null)

    /** Opens the search (of the current screen) with [query], e.g. "it.todo". */
    fun searchFor(query: String) {
        this.query = query
        tag = null
        searchActive = true
    }

    // ---- Saved searches ----

    val savedSearches = appSettings.savedSearches

    /** Saved search being named or edited (index null: a new one), shown in a dialog. */
    data class SearchDraft(val index: Int?, val name: String, val query: String)

    var searchDraft by mutableStateOf<SearchDraft?>(null)
        private set

    /**
     * Offers to save the search being made (with its tag chip); in a notebook, the search keeps
     * to it with a b. condition.
     */
    fun saveCurrentSearch(notebookName: String?) {
        var text = listOfNotNull(query.trim().takeIf { it.isNotEmpty() }, tag?.let { "t.\"$it\"" }).joinToString(" ")
        if (notebookName != null && !Regex("""(^|\s)\.?b\.""").containsMatchIn(text)) text = "b.\"$notebookName\" $text"
        if (text.isNotBlank()) searchDraft = SearchDraft(null, query.trim().ifEmpty { text }, text)
    }

    fun editSavedSearch(index: Int) {
        val search = savedSearches.value.getOrNull(index) ?: return
        searchDraft = SearchDraft(index, search.name, search.query)
    }

    fun confirmSearchDraft(name: String, query: String) {
        val draft = searchDraft ?: return
        searchDraft = null
        val search = SavedSearch(name.trim(), query.trim())
        val list = savedSearches.value
        appSettings.setSavedSearches(if (draft.index == null) list + search else list.mapIndexed { i, s -> if (i == draft.index) search else s })
        if (draft.index == null) message(R.string.msg_search_saved)
    }

    fun deleteSearchDraft() {
        val index = searchDraft?.index
        searchDraft = null
        if (index != null) appSettings.setSavedSearches(savedSearches.value.filterIndexed { i, _ -> i != index })
    }

    fun dismissSearchDraft() {
        searchDraft = null
    }

    fun deleteSavedSearches(indices: Set<Int>) {
        appSettings.setSavedSearches(savedSearches.value.filterIndexed { i, _ -> i !in indices })
        pluralMessage(R.plurals.msg_searches_deleted, indices.size)
    }

    /** Moves a saved search by [delta] places; returns its new index. */
    fun moveSavedSearch(index: Int, delta: Int): Int {
        val list = savedSearches.value.toMutableList()
        val target = (index + delta).coerceIn(0, list.lastIndex)
        if (target == index) return index
        list.add(target, list.removeAt(index))
        appSettings.setSavedSearches(list)
        return target
    }

    /** Runs a saved search over all notebooks. */
    fun runSavedSearch(search: SavedSearch) {
        openHome()
        searchFor(search.query)
    }

    fun setSearch(active: Boolean) {
        searchActive = active
        if (!active) {
            query = ""
            tag = null
        }
    }

    // ---- Folded notes in the notebooks, kept in the settings by the notes' paths ----

    private val foldsByFile = HashMap<String, Set<String>>()
    /** Changes with the folds, for the screens showing them to follow. */
    private var foldsVersion by mutableIntStateOf(0)
    private val pathsByFile = HashMap<String, Pair<List<OrgNote>, List<String>>>()

    private fun pathsOf(fileUri: String): List<String> {
        val notes = library.value.file(fileUri)?.notes ?: return emptyList()
        pathsByFile[fileUri]?.takeIf { it.first === notes }?.let { return it.second }
        return NoteTree.titlePaths(notes).also { pathsByFile[fileUri] = notes to it }
    }

    /** The notebook's folded notes: as they were left, or else as Org shows the file (#+STARTUP, VISIBILITY). */
    private fun foldsOf(fileUri: String): Set<String> {
        foldsByFile[fileUri]?.let { return it }
        val file = library.value.file(fileUri) ?: return emptySet()
        val paths = pathsOf(fileUri)
        val folds = appSettings.folds(fileUri) ?: Folding.initial(file.doc.preface, file.notes).mapTo(HashSet()) { paths[it] }
        foldsByFile[fileUri] = folds
        return folds
    }

    private fun setFolds(fileUri: String, folds: Set<String>) {
        foldsByFile[fileUri] = folds
        appSettings.setFolds(fileUri, folds)
        foldsVersion++
    }

    /** A message for the snackbar, with a button running [action] (e.g. undo) when [actionLabel] is set. */
    data class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 4)
    val messages: SharedFlow<Message> = _messages

    /** Agenda filters: a notebook (file URI) and a tag, null for all. */
    var agendaNotebook by mutableStateOf<String?>(null)
    var agendaTag by mutableStateOf<String?>(null)

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    private var checking = false

    /** Reloads files changed by other apps (Syncthing, editors…), without showing it. */
    fun checkForChanges() {
        if (checking) return
        checking = true
        viewModelScope.launch {
            runCatching { repository.refresh(quiet = true) }
            checking = false
        }
    }

    fun pullToRefresh() {
        refreshing = true
        viewModelScope.launch {
            repository.refresh()
            refreshing = false
        }
    }

    fun setShowContentInNotebook(show: Boolean) = appSettings.setShowContentInNotebook(show)

    fun setStrikeDone(on: Boolean) = appSettings.setStrikeDone(on)

    fun setMonospace(on: Boolean) = appSettings.setMonospace(on)

    fun setWrapTables(on: Boolean) = appSettings.setWrapTables(on)

    fun setFreezeFirstColumn(on: Boolean) = appSettings.setFreezeFirstColumn(on)

    fun setNoteSpacing(spacing: NoteSpacing) = appSettings.setNoteSpacing(spacing)
    fun setIndentation(indentation: Indentation) = appSettings.setIndentation(indentation)

    fun setHiddenProperties(names: Set<String>) = appSettings.setHiddenProperties(names)
    fun setRemindScheduled(on: Boolean) = appSettings.setRemindScheduled(on)
    fun setRemindDeadline(on: Boolean) = appSettings.setRemindDeadline(on)
    fun setRemindAllDay(on: Boolean) = appSettings.setRemindAllDay(on)
    fun setAllDayTime(time: LocalTime) = appSettings.setAllDayTime(time)
    fun setAddIdToNewNotes(on: Boolean) = appSettings.setAddIdToNewNotes(on)

    fun setAddCreatedToNewNotes(on: Boolean) = appSettings.setAddCreatedToNewNotes(on)
    fun setAddModified(on: Boolean) = appSettings.setAddModified(on)

    fun setUseAlarmClock(on: Boolean) = appSettings.setUseAlarmClock(on)

    fun setSnoozeMinutes(minutes: Int) = appSettings.setSnoozeMinutes(minutes)

    fun setLinkRenaming(mode: LinkRenaming) = appSettings.setLinkRenaming(mode)

    fun setTableRecalc(mode: TableRecalc) = appSettings.setTableRecalc(mode)

    fun setTemplatesHidden(mode: TemplatesHidden) = appSettings.setTemplatesHidden(mode)

    fun setEditCells(on: Boolean) = appSettings.setEditCells(on)

    fun setReminderButton(button: ReminderButton) = appSettings.setReminderButton(button)

    /**
     * remote() lookups for the tables of [content]: named tables of [content] first, then of the
     * other notes, or a note's first table by its ID; recalculated first when tables are
     * recalculated as shown, as written otherwise.
     */
    fun tableLookup(content: String): (String) -> List<String>? {
        val notes = library.value
        val live = settings.value.tableRecalc != TableRecalc.Never
        // One for all the notes until they change: what it works out stays (scrolling back)
        val shared = tables?.takeIf { (forNotes, forLive) -> forNotes === notes && forLive == live }?.third
            ?: TableLookup(notes.notes, live).also { tables = Triple(notes, live, it) }
        return shared.from(content)
    }

    private var tables: Triple<NoteRepository.Library, Boolean, TableLookup>? = null

    /** When tables are recalculated and saved as shown: writes [note]'s tables if their values changed. */
    fun saveShownTables(note: OrgNote) {
        if (settings.value.tableRecalc != TableRecalc.Save) return
        val fresh = Blocks.recalculated(note.content, tableLookup(note.content))
        if (fresh != note.content) update(note, stamp = false) { it.copy(content = fresh) }
    }

    fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)

    fun setDynamicColor(on: Boolean) = appSettings.setDynamicColor(on)

    val notebookIcons = appSettings.notebookIcons

    fun setNotebookIcon(fileUri: String, emoji: String?) = appSettings.setNotebookIcon(fileUri, emoji)

    /** Sets the default state keywords from "TODO NEXT | DONE"; false if there is no keyword. */
    fun setKeywords(spec: String): Boolean {
        val keywords = Keywords.parse(spec) ?: return false
        appSettings.setKeywords(keywords)
        return true
    }

    fun openSettings() = push(Screen.Settings)

    fun openSearches() {
        if (stack.last() != Screen.Searches) push(Screen.Searches)
    }

    fun openAgenda() {
        if (stack.last() != Screen.Agenda) push(Screen.Agenda)
    }

    /** Note to open once the notes are loaded (from a reminder notification). */
    private data class PendingOpen(val fileUri: String, val index: Int, val title: String?)
    private var pendingOpen: PendingOpen? = null

    fun openFromNotification(fileUri: String, index: Int, title: String?) {
        pendingOpen = PendingOpen(fileUri, index, title)
        openPendingNote()
    }

    /** Opens the note requested by a notification, if the notes are loaded. */
    fun openPendingNote() {
        val p = pendingOpen ?: return
        val lib = library.value
        if (!lib.loaded) return
        pendingOpen = null
        val note = lib.note(p.fileUri, p.index)?.takeIf { p.title == null || it.title == p.title }
            ?: lib.file(p.fileUri)?.notes?.firstOrNull { it.title == p.title }
        if (note != null) openNote(note) else message(R.string.msg_note_moved_or_deleted)
    }

    fun setFolder(uri: Uri) {
        // The first time, a guide notebook is offered (unless the folder has it already)
        val firstTime = !appSettings.guideOffered
        if (firstTime) guideChoicePending = true
        viewModelScope.launch {
            repository.setFolder(uri)
            if (firstTime) {
                appSettings.guideOffered = true
                if (guideFile() == null) offerGuide = true else guideChoicePending = false
            }
        }
        stack.clear()
        stack.add(Screen.Notebooks)
    }

    // ---- Guide notebook ----

    /** Whether the guide notebook is being offered. */
    var offerGuide by mutableStateOf(false)
        private set

    /** True from the folder's first choice until the guide is added or declined (other prompts wait). */
    var guideChoicePending by mutableStateOf(false)
        private set

    /** Whether to ask what to do with the guide notebook already in the folder: open or replace it. */
    var guideExists by mutableStateOf(false)
        private set

    fun dismissGuideOffer() {
        offerGuide = false
        guideChoicePending = false
        guideExists = false
    }

    /** The guide notebook in the folder, also by its name when the app was Narwhal. */
    private fun guideFile() = library.value.files.firstOrNull { file ->
        listOf(Guide.FILE_NAME, Guide.OLD_FILE_NAME).any { file.ref.name.equals("$it.org", ignoreCase = true) }
    }

    /** This version's guide, its examples dated from today. */
    private fun guideText(): String {
        val template = getApplication<Application>().assets.open(Guide.ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        return Guide.fill(template, LocalDate.now(), UUID.randomUUID().toString().uppercase())
    }

    /** Adds the guide notebook to the folder and opens it; one already there, asks first. */
    fun addGuide() {
        offerGuide = false
        guideChoicePending = false
        if (guideFile() != null) {
            guideExists = true
            return
        }
        viewModelScope.launch {
            val uri = attempt { repository.createFile(Guide.FILE_NAME, guideText()) } ?: return@launch
            openNotebook(uri.toString())
            message(R.string.msg_guide_added)
        }
    }

    /** Opens the guide notebook already in the folder, as it is. */
    fun openGuide() {
        guideExists = false
        guideFile()?.let { openNotebook(it.uri) }
    }

    /** Writes this version's guide in place of the one in the folder (its changes lost), and opens it. */
    fun replaceGuide() {
        guideExists = false
        val file = guideFile() ?: return
        viewModelScope.launch {
            attempt { repository.replaceFile(file.uri, guideText()) } ?: return@launch
            openNotebook(file.uri)
            message(R.string.msg_guide_replaced)
        }
    }

    fun isFolded(note: OrgNote): Boolean {
        // Read so that the screens follow the folds' changes
        if (foldsVersion < 0) return false
        val path = pathsOf(note.fileUri).getOrNull(note.index) ?: return false
        return path in foldsOf(note.fileUri)
    }

    fun toggleFold(note: OrgNote) {
        val path = pathsOf(note.fileUri).getOrNull(note.index) ?: return
        val folds = foldsOf(note.fileUri)
        setFolds(note.fileUri, if (path in folds) folds - path else folds + path)
    }

    /** Folds (or unfolds) every note of a notebook that has children. */
    fun setAllFolded(notes: List<OrgNote>, fold: Boolean) {
        val uri = notes.firstOrNull()?.fileUri ?: return
        val paths = pathsOf(uri)
        setFolds(uri, if (fold) notes.indices.filter { NoteTree.hasChildren(notes, it) }.mapNotNullTo(HashSet()) { paths.getOrNull(it) } else emptySet())
    }

    private fun push(screen: Screen) {
        navigatedForward = true
        stack.add(screen)
    }

    fun openHome() {
        navigatedForward = false
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Goes back to the notebook if it is already open below, otherwise opens it. */
    fun openNotebook(fileUri: String) {
        val existing = stack.indexOfLast { it is Screen.Notebook && it.fileUri == fileUri }
        if (existing >= 0) {
            navigatedForward = false
            while (stack.lastIndex > existing) stack.removeAt(stack.lastIndex)
        } else {
            setSearch(false)
            push(Screen.Notebook(fileUri))
        }
    }

    fun openNote(note: OrgNote) = push(Screen.Note(note.fileUri, note.index))

    fun openTree(fileUri: String) = push(Screen.Tree(fileUri))

    /** How a notebook's tree view was left, kept across sessions. */
    fun treeState(fileUri: String) = appSettings.treeState(fileUri)

    fun setTreeState(fileUri: String, state: AppSettings.TreeState) = appSettings.setTreeState(fileUri, state)

    /**
     * Opens a link's target: id: a note, file: a notebook (.org) or a file of the folder, anything
     * else (https:, mailto:, tel:, sms:, geo:…) in the app Android picks.
     */
    fun openLink(target: String) {
        val link = target.trim()
        when {
            link.startsWith("id:", ignoreCase = true) -> openById(link.substring(3).trim())
            link.startsWith("file:", ignoreCase = true) -> openFileLink(link.substring(5))
            InternalLinks.isInternal(link) -> openInternal(link)
            else -> view(Uri.parse(link))
        }
    }

    /** file:gtd.org opens that notebook; file:Download/document.txt a file of the folder. */
    private fun openFileLink(path: String) {
        // Org's search options (file:gtd.org::*Heading) are not followed: the file opens
        val relative = path.substringBefore("::").trim().removePrefix("./").removePrefix("/")
        val files = library.value.files
        val notebook = files.firstOrNull { it.ref.path.equals(relative, ignoreCase = true) }
            ?: files.firstOrNull { it.ref.name.equals(relative, ignoreCase = true) }
        if (notebook != null) {
            openNotebook(notebook.uri)
            return
        }
        viewModelScope.launch {
            val (uri, type) = attempt { repository.findInFolder(relative) } ?: run {
                message(R.string.msg_no_file, relative)
                return@launch
            }
            view(uri, type)
        }
    }

    private fun view(uri: Uri, type: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, type)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            getApplication<Application>().startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            message(R.string.msg_no_app, uri)
        }
    }

    /**
     * Opens the note an internal link leads to ([[*Title]], [[#custom-id]], [[name]]): in the
     * notebook shown first, as Org does, then in the others.
     */
    private fun openInternal(target: String) {
        val current = when (val screen = stack.lastOrNull()) {
            is Screen.Note -> screen.fileUri
            is Screen.Notebook -> screen.fileUri
            is Screen.Tree -> screen.fileUri
            else -> null
        }
        val notes = library.value.notes.sortedBy { it.fileUri != current }
        val note = InternalLinks.find(notes, target)
        val title = InternalLinks.titleFor(target)
        when {
            note != null -> openNote(note)
            // As Emacs does: a note can be made for it, in the notebook of the link
            title != null && current != null -> missingLink = MissingLink(target, title, current)
            else -> message(R.string.msg_link_not_found, target)
        }
    }

    /** A link to a note not there yet: [title] is the one it gives, [fileUri] the notebook it is in. */
    data class MissingLink(val target: String, val title: String, val fileUri: String)

    /** The missing link whose note is offered to be made. */
    var missingLink by mutableStateOf<MissingLink?>(null)
        private set

    fun dismissMissingLink() {
        missingLink = null
    }

    /**
     * Makes the note of the missing link, at the end of the link's notebook, and opens it. The
     * links in that notebook lead to it by its id from then on: they follow it when renamed.
     */
    fun createMissingLink() {
        val link = missingLink ?: return
        missingLink = null
        viewModelScope.launch {
            val note = attempt {
                val created = repository.createNote(Uri.parse(link.fileUri), link.title)
                val edit = created.toEdit().let { it.copy(properties = it.properties + newNoteProperties(it)) }
                val saved = if (edit != created.toEdit()) repository.saveNote(created, edit) else created
                val (id, stored) = repository.ensureId(saved)
                repository.changeNotes(listOf(link.fileUri)) { InternalLinks.toIdLinks(it, link.target, id, link.title) }
                stored
            } ?: return@launch
            openNote(note)
            message(R.string.msg_shared_added, library.value.file(note.fileUri)?.title.orEmpty())
        }
    }

    fun openById(id: String) {
        val note = library.value.noteById(id)
        if (note != null) openNote(note) else message(R.string.msg_no_note_with_id, id)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        navigatedForward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Saves are queued, each from the note as last written, so quick edits never undo each other. */
    private val saveLock = Mutex()

    /**
     * Saves [change] applied to the note as it is now (not as it was when the edit started).
     * A note being created (not yet in its file) only updates the draft.
     */
    /**
     * Saves [note] as [change] makes it, from its latest version. [stamp]: a change the user made,
     * which sets LAST_MODIFIED when so set (not one the app makes: a cookie, recalculated tables).
     */
    private suspend fun saveLatest(note: OrgNote, stamp: Boolean = true, change: (OrgNote) -> NoteEdit): OrgNote? {
        if (note.index == DRAFT_INDEX) {
            val screen = newNoteScreen ?: return null
            return saveDraft(change(draftNote(screen)))
        }
        return saveLock.withLock {
            val latest = library.value.note(note.fileUri, note.index) ?: note
            val edit = withCookies(latest, change(latest))
            attempt { repository.saveNote(latest, if (stamp) stamped(latest, edit) else edit) }
        }
    }

    /** [edit] of [note] with LAST_MODIFIED set to now, when so set and it changes something. */
    private fun stamped(note: OrgNote, edit: NoteEdit): NoteEdit {
        if (!settings.value.addModified || edit == note.toEdit()) return edit
        val now = SearchQuery.MODIFIED_PROPERTY to OrgTime.inactive(LocalDateTime.now())
        val at = edit.properties.indexOfFirst { it.first.equals(SearchQuery.MODIFIED_PROPERTY, true) }
        return edit.copy(properties = if (at < 0) edit.properties + now else edit.properties.toMutableList().also { it[at] = now })
    }

    /** [edit] with its [/] and [%] cookies counting as they now should (as Org keeps them). */
    private fun withCookies(note: OrgNote, edit: NoteEdit): NoteEdit {
        val content = Cookies.updateContent(edit.content)
        return edit.copy(title = Cookies.updateTitle(edit.title, content, subNotes(note)), content = content)
    }

    /** Direct sub-notes of [note]. */
    private fun subNotes(note: OrgNote): List<OrgNote> {
        val notes = library.value.file(note.fileUri)?.notes ?: return emptyList()
        if (note.index !in notes.indices) return emptyList()
        return notes.drop(note.index + 1).takeWhile { it.level > note.level }.filter { it.level == note.level + 1 }
    }

    /** After [note]'s state changed, the cookie of its parent counting its sub-notes follows. */
    private suspend fun updateParentCookie(note: OrgNote) {
        val notes = library.value.file(note.fileUri)?.notes ?: return
        if (note.index !in notes.indices) return
        val parent = NoteTree.ancestors(notes, note.index).lastOrNull() ?: return
        if (!Cookies.countsSubNotes(parent)) return
        if (Cookies.updateTitle(parent.title, parent.content, subNotes(parent)) != parent.title) saveLatest(parent, stamp = false) { it.toEdit() }
    }

    /** Returns the saved note, or null if it could not be saved. */
    suspend fun save(note: OrgNote, edit: NoteEdit): OrgNote? = saveLatest(note) { edit }

    /** Saves [note] as [change] makes it; [stamp] as for [saveLatest]. */
    fun update(note: OrgNote, stamp: Boolean = true, change: (NoteEdit) -> NoteEdit) {
        viewModelScope.launch { saveLatest(note, stamp) { change(it.toEdit()) } }
    }

    /**
     * Links to a renamed note that still show its old title or lead to it by it, until the user
     * says to update them: id links ([id], if it has one), [[title]] ones if [plain] (the note
     * was the one they led to), [[*title]] ones if [starred].
     */
    data class LinkRename(
        val id: String?,
        val oldTitle: String,
        val newTitle: String,
        val plain: Boolean,
        val starred: Boolean,
        val notes: Int,
        /** A target or #+NAME renamed ([oldTitle] to [newTitle]), not a note: what led to it follows. */
        val name: Boolean = false,
    ) {
        fun apply(text: String): String = when {
            name -> InternalLinks.renameNameLinks(text, oldTitle, newTitle)
            else -> InternalLinks.renameLinks(id?.let { IdLinks.renameLinks(text, it, oldTitle, newTitle) } ?: text, oldTitle, newTitle, plain, starred)
        }
    }

    var linkRename by mutableStateOf<LinkRename?>(null)
        private set

    /**
     * Saves a new title, then, as set in the settings, updates the links to the note that still
     * show the old one, asks first, or leaves them.
     */
    fun rename(note: OrgNote, title: String) {
        val oldTitle = note.title
        // The links by title that led to this note before (not to another with the same name)
        val before = library.value.notes.sortedBy { it.fileUri != note.fileUri }
        fun ledHere(target: String) = InternalLinks.find(before, target)?.let { it.fileUri == note.fileUri && it.index == note.index } == true
        val shown = IdLinks.plainText(oldTitle).trim()
        val plain = ledHere(shown)
        val starred = ledHere("*$shown")
        viewModelScope.launch {
            val saved = saveLatest(note) { it.toEdit().copy(title = title) } ?: return@launch
            val mode = settings.value.linkRenaming
            if (mode == LinkRenaming.Never) return@launch
            val draft = LinkRename(saved.id, oldTitle, title, plain, starred, 0)
            val notes = library.value.notes.count { draft.apply(it.raw) != it.raw }
            if (notes == 0) return@launch
            val rename = draft.copy(notes = notes)
            if (mode == LinkRenaming.Always) renameLinks(rename) else linkRename = rename
        }
    }

    /**
     * Saves a new content; a target (<<name>>) or #+NAME renamed in it then, as set in the
     * settings, has the links and remote() formulas that led to it updated, asks first, or not.
     */
    fun saveContent(note: OrgNote, content: String) {
        val renamed = InternalLinks.renamedName(note.content, content)
        viewModelScope.launch {
            saveLatest(note) { it.toEdit().copy(content = content) } ?: return@launch
            val (old, new) = renamed ?: return@launch
            val mode = settings.value.linkRenaming
            if (mode == LinkRenaming.Never) return@launch
            // Still somewhere else: the links still lead there
            if (library.value.notes.any { old in InternalLinks.namesIn(it.content) }) return@launch
            val draft = LinkRename(null, old, new, plain = false, starred = false, notes = 0, name = true)
            val notes = library.value.notes.count { draft.apply(it.raw) != it.raw }
            if (notes == 0) return@launch
            val rename = draft.copy(notes = notes)
            if (mode == LinkRenaming.Always) renameLinks(rename) else linkRename = rename
        }
    }

    fun updateRenamedLinks() {
        val rename = linkRename ?: return
        linkRename = null
        viewModelScope.launch { renameLinks(rename) }
    }

    private suspend fun renameLinks(rename: LinkRename) {
        val changed = attempt { repository.changeNotes(change = rename::apply) } ?: return
        pluralMessage(R.plurals.msg_links_updated, changed)
    }

    fun keepRenamedLinks() {
        linkRename = null
    }

    /** Changes the state with Org semantics (CLOSED time, repeating tasks moving forward). */
    fun setState(note: OrgNote, target: String?) {
        val keywords = library.value.file(note.fileUri)?.doc?.keywords ?: repository.defaultKeywords
        viewModelScope.launch {
            var shifted = false
            val saved = saveLatest(note) { latest ->
                NoteStates.apply(latest.toEdit(), target, keywords).also { shifted = it.shifted }.edit
            } ?: return@launch
            updateParentCookie(saved)
            if (shifted) {
                val next = displayTime(getApplication<Application>().resources, saved.scheduled ?: saved.deadline)
                message(R.string.msg_repeating_moved, next.orEmpty())
            }
        }
    }

    fun toggleDone(note: OrgNote) {
        val keywords = library.value.file(note.fileUri)?.doc?.keywords ?: Keywords.DEFAULT
        setState(note, NoteStates.toggleTarget(note, keywords))
    }

    /** Outline row whose swipe actions are shown, by "fileUri#index"; only one at a time. */
    var openSwipeRow by mutableStateOf<String?>(null)

    /** Returns the target's ID (created if needed) and the target as now stored. */
    suspend fun idFor(target: OrgNote): Pair<String, OrgNote>? = attempt { repository.ensureId(target) }

    fun createNotebook(name: String) {
        viewModelScope.launch {
            attempt { openNotebook(repository.createFile(name).toString()) }
        }
    }

    fun setPreface(file: NoteRepository.LoadedFile, newPreface: String) {
        viewModelScope.launch {
            attempt { repository.setPreface(file.uri, file.doc.preface, newPreface) }
        }
    }

    /** Adds a note above, under or below [anchor] and opens it. */
    // ---- New note: shown in the note screen, written to the file once it has a title ----

    /** Fields of the note being created, kept in memory until it has a title. */
    var newNoteDraft by mutableStateOf(EMPTY_DRAFT)
        private set

    /** Opens the note screen for a new note, title being edited; see [saveDraft]. */
    fun startNewNote(fileUri: String, anchor: OrgNote? = null, placement: Placement? = null) {
        openSwipeRow = null
        newNoteDraft = EMPTY_DRAFT
        val screen = Screen.NewNote(fileUri, anchor, placement)
        newNoteScreen = screen
        push(screen)
    }

    /** The draft as a note for the note screen; [DRAFT_INDEX] marks it as not in the file yet. */
    fun draftNote(screen: Screen.NewNote): OrgNote {
        val d = newNoteDraft
        val keywords = library.value.file(screen.fileUri)?.doc?.keywords ?: Keywords.DEFAULT
        val level = (screen.anchor?.level ?: 1) + if (screen.placement == Placement.Under) 1 else 0
        return OrgNote(
            fileUri = screen.fileUri,
            fileName = library.value.file(screen.fileUri)?.ref?.name ?: "",
            index = DRAFT_INDEX,
            level = level,
            state = d.state,
            isDone = d.state != null && d.state in keywords.done,
            priority = d.priority,
            title = d.title,
            tags = d.tags,
            scheduled = d.scheduled,
            deadline = d.deadline,
            closed = d.closed,
            properties = d.properties,
            content = d.content,
            raw = "",
        )
    }

    /**
     * Updates the draft; once it has a title, writes the note with all its fields and
     * replaces the new-note screen with the note's screen.
     */
    /** The note being created, even after its screen was left (back saves it). */
    private var newNoteScreen: Screen.NewNote? = null

    /** A new note's screen whose content is being edited while the note gets created. */
    var newNoteEditingContent: Screen.NewNote? = null

    /** The created note whose screen goes on editing its content. */
    private var resumedContentEditing: Screen.Note? = null

    /** Whether [note]'s screen takes over the content edit of its new-note screen (only once). */
    fun takeResumedContentEditing(note: OrgNote): Boolean {
        val resumed = resumedContentEditing?.let { it.fileUri == note.fileUri && it.index == note.index } == true
        if (resumed) resumedContentEditing = null
        return resumed
    }

    /** With the settings on, a new note gets an ID, a CREATED and a LAST_MODIFIED property right away. */
    private fun newNoteProperties(edit: NoteEdit) = buildList {
        if (settings.value.addIdToNewNotes && edit.properties.none { it.first.equals("ID", true) }) {
            add("ID" to UUID.randomUUID().toString().uppercase())
        }
        if (settings.value.addCreatedToNewNotes && edit.properties.none { it.first.equals(SearchQuery.CREATED_PROPERTY, true) }) {
            add(SearchQuery.CREATED_PROPERTY to OrgTime.inactive(LocalDateTime.now()))
        }
        if (settings.value.addModified && edit.properties.none { it.first.equals(SearchQuery.MODIFIED_PROPERTY, true) }) {
            add(SearchQuery.MODIFIED_PROPERTY to OrgTime.inactive(LocalDateTime.now()))
        }
    }

    // ---- Text shared from other apps ----

    /** The note just made from shared text: its screen offers to choose where it goes. */
    var sharedNote by mutableStateOf<Screen.Note?>(null)
        private set

    /** Makes a note of text shared by another app, in the Inbox notebook, and opens it. */
    fun receiveShared(subject: String?, text: String?) {
        val shared = Sharing.note(subject, text) ?: return
        viewModelScope.launch {
            // Once the folder has been read
            val lib = library.first { it.loaded || it.folderUri == null }
            if (lib.folderUri == null) {
                message(R.string.msg_choose_folder_first)
                return@launch
            }
            val note = attempt {
                val uri = inboxUri(lib)
                val created = repository.createNote(Uri.parse(uri), shared.title)
                val edit = created.toEdit().copy(content = shared.content).let { it.copy(properties = it.properties + newNoteProperties(it)) }
                if (edit != created.toEdit()) repository.saveNote(created, edit) else created
            } ?: return@launch
            openNote(note)
            sharedNote = Screen.Note(note.fileUri, note.index)
            message(R.string.msg_shared_added, library.value.file(note.fileUri)?.title.orEmpty())
        }
    }

    /** The notebook named Inbox (made if there is none). */
    private suspend fun inboxUri(lib: NoteRepository.Library): String =
        lib.files.firstOrNull { it.title.equals(INBOX, ignoreCase = true) || it.ref.name.equals("$INBOX.org", ignoreCase = true) }?.uri
            ?: repository.createFile(INBOX).toString()

    // ---- Clocking ----

    /** Starts the clock on [note], stopping the one running on another note. */
    fun clockIn(note: OrgNote) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            library.value.notes
                .filter { (it.fileUri != note.fileUri || it.index != note.index) && Clock.running(it.content) != null }
                .forEach { other -> attempt { saveLatest(other) { it.toEdit().copy(content = Clock.clockOut(it.content, now)) } } }
            attempt { saveLatest(note) { it.toEdit().copy(content = Clock.clockIn(it.content, now)) } }
        }
    }

    fun clockOut(note: OrgNote) = update(note) { it.copy(content = Clock.clockOut(it.content, LocalDateTime.now())) }

    // ---- Agenda ----

    /** Sets [note]'s scheduled or deadline time ([kind]) to [timestamp] (null removes it). */
    fun setPlanning(note: OrgNote, kind: Agenda.Kind, timestamp: String?) = update(note) {
        when (kind) {
            Agenda.Kind.Scheduled -> it.copy(scheduled = timestamp)
            Agenda.Kind.Deadline -> it.copy(deadline = timestamp)
            Agenda.Kind.Event -> it
        }
    }

    /** Puts [note]'s time off by [days], from today if it is overdue; time of day and repeater stay. */
    fun postpone(note: OrgNote, kind: Agenda.Kind, days: Long) {
        val timestamp = (if (kind == Agenda.Kind.Scheduled) note.scheduled else note.deadline) ?: return
        val parts = OrgTime.parts(timestamp) ?: return
        val date = maxOf(parts.date, LocalDate.now()).plusDays(days)
        setPlanning(note, kind, OrgTime.build(date, parts.time, timestamp))
        message(R.string.msg_postponed, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }

    private suspend fun saveDraft(edit: NoteEdit): OrgNote? {
        val screen = newNoteScreen ?: return null
        newNoteDraft = edit
        if (edit.title.isBlank()) {
            return draftNote(screen)
        }
        val added = newNoteProperties(edit)
        val full = if (added.isEmpty()) edit else edit.copy(properties = edit.properties + added)
        val note = attempt {
            val created = if (screen.anchor != null && screen.placement != null) {
                repository.createNoteNear(screen.anchor, screen.placement, edit.title)
            } else {
                repository.createNote(Uri.parse(screen.fileUri), edit.title)
            }
            // Fields set before the title (tags, state, dates, content…) and the ID
            if (full != created.toEdit().copy(title = full.title)) repository.saveNote(created, full) else created
        } ?: return null
        newNoteScreen = null
        // If the screen is still shown, it becomes the note's screen
        val i = stack.indexOf(screen)
        if (i >= 0) {
            navigatedForward = true
            val next = Screen.Note(note.fileUri, note.index)
            if (newNoteEditingContent == screen) resumedContentEditing = next
            stack[i] = next
        }
        newNoteEditingContent = null
        return note
    }

    // ---- Multiple selection of notebooks on the home screen ----

    var selectedNotebooks by mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleNotebookSelected(fileUri: String) {
        selectedNotebooks = if (fileUri in selectedNotebooks) selectedNotebooks - fileUri else selectedNotebooks + fileUri
    }

    fun clearNotebookSelection() {
        selectedNotebooks = emptySet()
    }

    /** Deletes the selected notebooks' files. */
    /** Renames the selected notebook (its file, and its #+TITLE if it has one). */
    fun renameSelectedNotebook(title: String) {
        val uri = selectedNotebooks.singleOrNull() ?: return
        selectedNotebooks = emptySet()
        viewModelScope.launch {
            val newUri = attempt { repository.renameFile(uri, title) } ?: return@launch
            // The icon follows the file
            notebookIcons.value[uri]?.takeIf { newUri != uri }?.let {
                appSettings.setNotebookIcon(newUri, it)
                appSettings.setNotebookIcon(uri, null)
            }
            // And so do its tree view and folds
            appSettings.treeState(uri)?.takeIf { newUri != uri }?.let {
                appSettings.setTreeState(newUri, it)
                appSettings.setTreeState(uri, null)
            }
            appSettings.folds(uri)?.takeIf { newUri != uri }?.let {
                appSettings.setFolds(newUri, it)
                appSettings.setFolds(uri, null)
            }
        }
    }

    fun deleteSelectedNotebooks() {
        val uris = selectedNotebooks
        selectedNotebooks = emptySet()
        uris.forEach {
            appSettings.setNotebookIcon(it, null)
            appSettings.setTreeState(it, null)
            appSettings.setFolds(it, null)
        }
        viewModelScope.launch {
            attempt {
                repository.deleteFiles(uris)
                pluralMessage(R.plurals.msg_notebooks_deleted, uris.size)
            }
        }
    }

    // ---- Multiple selection in a notebook (long press) ----

    data class Selection(val fileUri: String, val indices: Set<Int>)

    var selection by mutableStateOf<Selection?>(null)
        private set

    /** Notes cut or copied, with their sub-notes; can be pasted in any notebook. */
    var clipboard by mutableStateOf<List<Subtree>>(emptyList())
        private set

    /** True while the clipboard holds notes cut and not pasted yet: they keep their IDs (they move), copies don't. */
    private var clipboardCut = false

    fun toggleSelected(note: OrgNote) {
        val current = selection?.takeIf { it.fileUri == note.fileUri }
        val indices = current?.indices.orEmpty().let { if (note.index in it) it - note.index else it + note.index }
        selection = if (indices.isEmpty()) null else Selection(note.fileUri, indices)
    }

    fun clearSelection() {
        selection = null
    }

    fun selectAll(notes: List<OrgNote>) {
        val uri = notes.firstOrNull()?.fileUri ?: return
        selection = Selection(uri, notes.map { it.index }.toSet())
    }

    fun selectedNotes(): List<OrgNote> {
        val s = selection ?: return emptyList()
        return s.indices.sorted().mapNotNull { library.value.note(s.fileUri, it) }
    }

    /** Runs [block] on the selected notes; [keep] returns the selection to keep afterwards (null clears it). */
    private fun withSelection(keep: (List<OrgNote>) -> Set<Int>? = { null }, block: suspend (List<OrgNote>) -> Unit) {
        val notes = selectedNotes()
        if (notes.isEmpty()) return
        viewModelScope.launch {
            attempt { block(notes) }
            selection = keep(notes)?.takeIf { it.isNotEmpty() }?.let { Selection(notes.first().fileUri, it) }
        }
    }

    fun copySelection() = withSelection { notes ->
        clipboard = repository.copySubtrees(notes.first().fileUri, notes).map { it.copy(text = Outline.withoutIds(it.text)) }
        clipboardCut = false
        pluralMessage(R.plurals.msg_notes_copied, clipboard.size)
    }

    fun cutSelection() = withSelection { notes ->
        var cut = emptyList<Subtree>()
        repository.modifyFile(notes.first().fileUri, notes) { doc, indices ->
            cut = Outline.extract(doc, indices)
            Outline.delete(doc, indices)
        }
        clipboard = cut
        clipboardCut = true
        pluralMessage(R.plurals.msg_notes_cut, cut.size)
    }

    fun pasteAtSelection(placement: Placement) = withSelection { notes ->
        val subtrees = clipboard
        repository.modifyFile(notes.first().fileUri, notes.take(1)) { doc, (index) ->
            val (at, level) = Outline.target(doc, index, placement)
            Outline.insert(doc, at, level, subtrees)
        }
        // Cut notes keep their IDs where they are first pasted; pasted again, they are copies
        if (clipboardCut) {
            clipboardCut = false
            clipboard = subtrees.map { it.copy(text = Outline.withoutIds(it.text)) }
        }
    }

    fun deleteSelection() = withSelection { notes -> deleteNotes(notes) }

    fun archiveSelection() = withSelection { notes -> archiveNotes(notes) }

    /** Archives [note], as it is once the edits being saved are (see archiveNotes). */
    fun archiveNote(note: OrgNote) {
        viewModelScope.launch {
            saveLock.withLock {
                val latest = library.value.note(note.fileUri, note.index) ?: note
                // Its screen closes first, else it would show the note taking its place
                if (stack.lastOrNull() == Screen.Note(latest.fileUri, latest.index)) back()
                attempt { archiveNotes(listOf(latest)) }
            }
        }
    }

    /** Moves [notes] to their notebook's archive file (see NoteRepository.archive); the message offers to put them back. */
    private suspend fun archiveNotes(notes: List<OrgNote>) {
        val archived = repository.archive(notes, LocalDateTime.now())
        val app = getApplication<Application>()
        val count = archived.removed.size
        _messages.tryEmit(
            Message(app.resources.getQuantityString(R.plurals.msg_notes_archived, count, count), app.getString(R.string.undo)) {
                viewModelScope.launch { attempt { repository.unarchive(archived) } }
            },
        )
    }

    /** Deletes [note] with its sub-notes. */
    fun deleteNote(note: OrgNote) {
        viewModelScope.launch { attempt { deleteNotes(listOf(note)) } }
    }

    /** Deletes [notes] with their sub-notes; the message offers to put them back. */
    private suspend fun deleteNotes(notes: List<OrgNote>) {
        val uri = notes.first().fileUri
        var deleted = emptyList<Pair<Int, Subtree>>()
        repository.modifyFile(uri, notes) { doc, indices ->
            deleted = Outline.roots(doc, indices).zip(Outline.extract(doc, indices))
            Outline.delete(doc, indices)
        }
        val app = getApplication<Application>()
        _messages.tryEmit(
            Message(app.resources.getQuantityString(R.plurals.msg_notes_deleted, deleted.size, deleted.size), app.getString(R.string.undo)) {
                viewModelScope.launch {
                    attempt { repository.modifyFile(uri, emptyList()) { doc, _ -> Outline.restore(doc, deleted) } }
                }
            },
        )
    }

    enum class Move { Up, Down, Promote, Demote }

    /** Counts the moves asked for, as soon as asked: the outline gets ready to slide its rows. */
    var moveRequests by mutableIntStateOf(0)
        private set

    fun moveSelection(move: Move) {
        val notes = selectedNotes()
        if (notes.isEmpty()) return
        moveRequests++
        var newIndex: Int? = null
        withSelection(keep = { list -> newIndex?.let { setOf(it) } ?: list.map { it.index }.toSet() }) {
            repository.modifyFile(notes.first().fileUri, notes) { doc, indices ->
                when (move) {
                    Move.Up -> Outline.moveUp(doc, indices.single())?.also { newIndex = it.second }?.first ?: doc
                    Move.Down -> Outline.moveDown(doc, indices.single())?.also { newIndex = it.second }?.first ?: doc
                    Move.Promote -> Outline.shift(doc, indices, -1)
                    Move.Demote -> Outline.shift(doc, indices, 1)
                }
            }
        }
    }

    /** Moves the selection under [parent], or to the end of the notebook [fileUri]. */
    fun refileSelection(fileUri: String, parent: OrgNote?) = withSelection { notes -> refile(notes, fileUri, parent) }

    /** Moves [note] under [parent], or to the end of the notebook [fileUri], and shows it there. */
    fun refileNote(note: OrgNote, fileUri: String, parent: OrgNote?) {
        val shared = sharedNote?.let { it.fileUri == note.fileUri && it.index == note.index } == true
        viewModelScope.launch {
            attempt {
                // The screen follows the note before it leaves its notebook (else it would close)
                refile(listOf(note), fileUri, parent) { index ->
                    val i = stack.indexOfLast { it is Screen.Note && it.fileUri == note.fileUri && it.index == note.index }
                    if (i >= 0) stack[i] = Screen.Note(fileUri, index)
                    // A shared note moved can still be moved again from its screen
                    if (shared) sharedNote = Screen.Note(fileUri, index)
                }
                // A shared note put away leaves no empty Inbox behind (the next share makes it
                // again); one with anything else in it, even a title line, stays
                if (shared && fileUri != note.fileUri && library.value.file(note.fileUri)?.doc?.text?.isBlank() == true) {
                    repository.deleteFiles(listOf(note.fileUri))
                }
            }
        }
    }

    private suspend fun refile(notes: List<OrgNote>, fileUri: String, parent: OrgNote?, onPlaced: (Int) -> Unit = {}): Int {
        val index = repository.refile(notes, fileUri, parent, onPlaced)
        val where = parent?.let { displayTitle(it.title) } ?: library.value.file(fileUri)?.title.orEmpty()
        pluralMessage(R.plurals.msg_notes_moved, notes.size, where)
        return index
    }

    /** Applies [change] to every selected note in one write, keeping the selection. */
    private fun editSelection(change: (OrgNote, Keywords) -> NoteEdit) =
        withSelection(keep = { notes -> notes.map { it.index }.toSet() }) { notes ->
            val uri = notes.first().fileUri
            repository.modifyFile(uri, notes) { doc, indices ->
                Outline.replaceSections(doc, indices.withIndex().associate { (k, i) ->
                    val section = doc.sections[i]
                    i to SectionEditor.rewrite(section.raw, section.level, doc.keywords, change(notes[k], doc.keywords))
                })
            }
            // Parents counting their sub-notes, once all are written
            notes.forEach { n -> library.value.note(uri, n.index)?.let { updateParentCookie(it) } }
        }

    fun setStateOfSelection(target: String?) = editSelection { note, keywords ->
        NoteStates.apply(note.toEdit(), target, keywords).edit
    }

    /** Marks the selection done, or back to to-do when all selected notes are done. */
    fun toggleDoneOfSelection() {
        val notes = selectedNotes()
        val allDone = notes.isNotEmpty() && notes.all { it.isDone }
        editSelection { note, keywords ->
            val target = if (allDone) keywords.todo.firstOrNull() else keywords.done.firstOrNull()
            NoteStates.apply(note.toEdit(), target, keywords).edit
        }
    }

    fun setScheduledOfSelection(timestamp: String?) = editSelection { note, _ -> note.toEdit().copy(scheduled = timestamp) }

    fun setDeadlineOfSelection(timestamp: String?) = editSelection { note, _ -> note.toEdit().copy(deadline = timestamp) }

    fun startNewNoteNearSelection(placement: Placement) {
        val note = selectedNotes().singleOrNull() ?: return
        selection = null
        startNewNote(note.fileUri, note, placement)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            when (e) {
                is NoteChangedException -> message(R.string.msg_note_changed)
                is FileNotSavedException -> message(R.string.msg_file_not_saved)
                else -> message(e.message ?: e.toString())
            }
            null
        }

    companion object {
        /** Index of a note that is not in its file yet (being created). */
        const val DRAFT_INDEX = -1

        /** Name of the notebook made for shared text when none is chosen. */
        private const val INBOX = "Inbox"
        private val EMPTY_DRAFT = NoteEdit(title = "", state = null, tags = emptyList(), properties = emptyList(), content = "")
    }

    fun message(text: String) {
        _messages.tryEmit(Message(text))
    }

    fun message(@StringRes text: Int, vararg args: Any) = message(getApplication<Application>().getString(text, *args))

    /** A message that depends on [count] (one note, several notes); [count] is its first argument. */
    private fun pluralMessage(@PluralsRes text: Int, count: Int, vararg args: Any) =
        message(getApplication<Application>().resources.getQuantityString(text, count, count, *args))
}
