package io.github.yighy.orgwhal.data

import java.time.LocalDateTime
import io.github.yighy.orgwhal.core.Archive
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.yighy.orgwhal.core.IdLinks
import io.github.yighy.orgwhal.core.Keywords
import io.github.yighy.orgwhal.core.NoteEdit
import io.github.yighy.orgwhal.core.Notebooks
import io.github.yighy.orgwhal.core.Outline
import io.github.yighy.orgwhal.core.Placement
import io.github.yighy.orgwhal.core.Subtree
import io.github.yighy.orgwhal.core.OrgDocument
import io.github.yighy.orgwhal.core.OrgNote
import io.github.yighy.orgwhal.core.SectionEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Thrown when a note was changed by another app since it was loaded. */
class NoteChangedException : Exception("The note was changed by another app. It has been reloaded.")

/**
 * Keeps an in-memory index of all notes in the chosen folder.
 *
 * The .org files are the only storage: every change is written to the file right away,
 * and files modified by other apps are re-read on [refresh].
 */
class NoteRepository(context: Context) {

    data class LoadedFile(val ref: OrgFolder.FileRef, val doc: OrgDocument, val notes: List<OrgNote>) {
        val uri: String get() = ref.uri.toString()
        val title: String by lazy { Notebooks.title(doc, ref.name) }
        val preface: String by lazy { Notebooks.prefaceText(doc) }
        val openTasks: Int by lazy { notes.count { it.state != null && !it.isDone } }
    }

    data class Library(
        val folderUri: Uri? = null,
        val files: List<LoadedFile> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        /** True once the folder has been read at least once. */
        val loaded: Boolean = false,
    ) {
        val notes: List<OrgNote> by lazy { files.flatMap { it.notes } }
        val tags: List<String> by lazy { notes.flatMap { it.tags }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() } }

        fun noteById(id: String): OrgNote? = notes.firstOrNull { it.id == id }
        fun file(fileUri: String): LoadedFile? = files.firstOrNull { it.uri == fileUri }
        fun note(fileUri: String, index: Int): OrgNote? = file(fileUri)?.notes?.getOrNull(index)
    }

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val prefs = appContext.getSharedPreferences("orgwhal", Context.MODE_PRIVATE)
    private val writeLock = Mutex()

    private val _library = MutableStateFlow(Library(folderUri = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)))
    val library: StateFlow<Library> = _library

    /** State keywords for files without a #+TODO line (from the settings). */
    @Volatile
    var defaultKeywords: Keywords = Keywords.DEFAULT

    /** Changes the default keywords and re-reads every file with them. */
    suspend fun setDefaultKeywords(keywords: Keywords) {
        if (keywords == defaultKeywords) return
        defaultKeywords = keywords
        _library.update { it.copy(files = emptyList()) }
        refresh()
    }

    private fun folder(): OrgFolder? = _library.value.folderUri?.let { OrgFolder(resolver, it) }

    suspend fun setFolder(uri: Uri) {
        resolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        _library.value = Library(folderUri = uri)
        refresh()
    }

    /** Re-reads files that are new or were modified since the last load. */
    /**
     * Reloads the files changed since they were read. [quiet]: a background check, which shows
     * no loading, keeps errors to itself and leaves the files alone while one is being written.
     */
    suspend fun refresh(quiet: Boolean = false) = withContext(Dispatchers.IO) {
        val folder = folder() ?: return@withContext
        if (quiet) {
            if (!writeLock.tryLock()) return@withContext
            try {
                val cached = _library.value.files.associateBy { it.ref.uri }
                val refs = folder.listOrgFiles()
                if (refs.size == cached.size && refs.all { cached[it.uri]?.ref?.lastModified == it.lastModified }) return@withContext
                val files = refs.map { ref -> cached[ref.uri]?.takeIf { it.ref.lastModified == ref.lastModified } ?: load(folder, ref) }
                _library.update { it.copy(files = files, loaded = true) }
            } catch (_: Exception) {
                // Next check, or the next visible refresh, will tell
            } finally {
                writeLock.unlock()
            }
            return@withContext
        }
        _library.update { it.copy(loading = true, error = null) }
        try {
            val cached = _library.value.files.associateBy { it.ref.uri }
            val files = folder.listOrgFiles().map { ref ->
                cached[ref.uri]?.takeIf { it.ref.lastModified == ref.lastModified } ?: load(folder, ref)
            }
            _library.update { it.copy(files = files, loading = false, loaded = true) }
        } catch (e: Exception) {
            _library.update { it.copy(loading = false, error = e.message ?: e.toString()) }
        }
    }

    private fun load(folder: OrgFolder, ref: OrgFolder.FileRef): LoadedFile {
        val doc = OrgDocument.parse(folder.read(ref.uri), defaultKeywords)
        return LoadedFile(ref, doc, OrgNote.fromDocument(doc, ref.uri.toString(), ref.name))
    }

    suspend fun saveNote(note: OrgNote, edit: NoteEdit): OrgNote =
        modifySection(note) { doc, section -> SectionEditor.rewrite(section.raw, section.level, doc.keywords, edit) }

    /** What archiving changed, to undo it: the notes taken out of [fileUri], and the archive file's text before and after. */
    data class Archived(val fileUri: String, val removed: List<Pair<Int, Subtree>>, val archive: Uri, val before: String?, val after: String)

    /**
     * Moves [notes] (from one notebook, with their sub-notes) to the end of the notebook's archive
     * file (gtd.org_archive, next to it), as Emacs' org-archive-subtree does.
     */
    suspend fun archive(notes: List<OrgNote>, now: LocalDateTime): Archived = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(notes.first().fileUri)
            val ref = _library.value.files.first { it.ref.uri == uri }.ref
            val doc = OrgDocument.parse(folder.read(uri), defaultKeywords)
            val indices = notes.map { locate(folder, uri, doc, it) }
            val roots = Outline.roots(doc, indices)
            val removed = roots.zip(Outline.extract(doc, indices))
            val path = Archive.path(ref.path)
            val existing = folder.find(path)
            val before = existing?.let(folder::read)
            val after = Archive.append(before, ref.name, roots.map { Archive.entry(doc, it, ref.path, now) })
            // The archive written first, so that the notes are always in one of the files
            val archive = existing ?: folder.createFileAt(path)
            folder.write(archive, after)
            folder.write(uri, Outline.delete(doc, indices).toFileText())
            reloadFile(folder, uri)
            Archived(uri.toString(), removed, archive, before, after)
        }
    }

    /** Puts archived notes back, and the archive file as it was if it has not changed since. */
    suspend fun unarchive(archived: Archived) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(archived.fileUri)
            val doc = OrgDocument.parse(folder.read(uri), defaultKeywords)
            folder.write(uri, Outline.restore(doc, archived.removed).toFileText())
            if (runCatching { folder.read(archived.archive) }.getOrNull() == archived.after) {
                if (archived.before == null) folder.delete(archived.archive) else folder.write(archived.archive, archived.before)
            }
            reloadFile(folder, uri)
        }
    }

    /** Returns the note's ID, adding a new one to its file if it has none. */
    suspend fun ensureId(note: OrgNote): Pair<String, OrgNote> {
        note.id?.let { return it to note }
        val id = UUID.randomUUID().toString().uppercase()
        val updated = modifySection(note) { _, section -> SectionEditor.addProperty(section.raw, "ID", id) }
        return id to updated
    }

    suspend fun createNote(fileUri: Uri, title: String): OrgNote = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val doc = OrgDocument.parse(folder.read(fileUri), defaultKeywords).appendNote(title)
            folder.write(fileUri, doc.toFileText())
            reloadFile(folder, fileUri).notes.last()
        }
    }

    /** Deletes notebook files from the folder. */
    suspend fun deleteFiles(fileUris: Collection<String>) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            fileUris.forEach { uri ->
                folder.delete(Uri.parse(uri))
                _library.update { lib -> lib.copy(files = lib.files.filterNot { it.uri == uri }) }
            }
        }
    }

    /**
     * Renames a notebook: its #+TITLE line if it has one (nothing else in the file changes),
     * and its file. Returns the file's new URI.
     */
    suspend fun renameFile(fileUri: String, title: String): String = withContext(Dispatchers.IO) {
        val newUri = writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(fileUri)
            val doc = OrgDocument.parse(folder.read(uri), defaultKeywords)
            Notebooks.retitle(doc.preface, title)?.let { preface ->
                if (preface != doc.preface) folder.write(uri, doc.rebuild(preface + doc.text.substring(doc.preface.length)).toFileText())
            }
            val fileName = title.trim().replace('/', '-').let { if (it.endsWith(".org", ignoreCase = true)) it else "$it.org" }
            val current = _library.value.files.firstOrNull { it.uri == fileUri }?.ref?.name
            if (fileName != current) folder.rename(uri, fileName) else uri
        }
        refresh()
        newUri.toString()
    }

    /** A file of the folder at [relativePath], with its MIME type, or null. */
    suspend fun findInFolder(relativePath: String): Pair<Uri, String?>? = withContext(Dispatchers.IO) {
        val folder = folder() ?: return@withContext null
        folder.find(relativePath)?.let { it to folder.type(it) }
    }

    /** Makes a notebook [name] (.org added) holding [text]. */
    suspend fun createFile(name: String, text: String): Uri = withContext(Dispatchers.IO) {
        val uri = writeLock.withLock {
            val folder = folder() ?: error("No folder")
            folder.createFile(name).also { folder.write(it, text) }
        }
        refresh()
        uri
    }

    /** Writes [text] in place of the whole notebook [fileUri]. */
    suspend fun replaceFile(fileUri: String, text: String) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(fileUri)
            folder.write(uri, text)
            reloadFile(folder, uri)
        }
    }

    suspend fun createFile(name: String): Uri = withContext(Dispatchers.IO) {
        val folder = folder() ?: error("No folder")
        folder.createFile(name).also { refresh() }
    }

    /**
     * Replaces the notebook's preface. Fails if the preface on disk is no longer [expected]
     * (changed by another app since it was loaded).
     */
    suspend fun setPreface(fileUri: String, expected: String, newPreface: String) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(fileUri)
            val doc = OrgDocument.parse(folder.read(uri), defaultKeywords)
            if (doc.preface != expected) {
                reloadFile(folder, uri)
                throw NoteChangedException()
            }
            folder.write(uri, doc.replacePreface(newPreface).toFileText())
            reloadFile(folder, uri)
        }
    }

    /** Adds a note above, under (as last child) or below [anchor] and returns it. */
    suspend fun createNoteNear(anchor: OrgNote, placement: Placement, title: String): OrgNote {
        var at = 0
        val file = modifyFile(anchor.fileUri, listOf(anchor)) { doc, (index) ->
            val (section, level) = Outline.target(doc, index, placement)
            at = section
            doc.insertNote(section, level, title)
        }
        return file.notes[at]
    }

    /** The subtrees of [notes] (with their sub-notes), as they are now in the file. */
    suspend fun copySubtrees(fileUri: String, notes: List<OrgNote>): List<Subtree> = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(fileUri)
            val doc = OrgDocument.parse(folder.read(uri), defaultKeywords)
            Outline.extract(doc, notes.map { locate(folder, uri, doc, it) })
        }
    }

    /**
     * Moves [notes] with their sub-notes under [parent] (after its sub-notes), or to the end of
     * [targetUri] as top-level notes; the notebook can be theirs. Returns the new index of the
     * first moved note, also given to [onPlaced] once the notes are in their new place (and
     * before they leave another notebook).
     */
    suspend fun refile(notes: List<OrgNote>, targetUri: String, parent: OrgNote?, onPlaced: (Int) -> Unit = {}): Int {
        val sourceUri = notes.first().fileUri
        fun target(doc: OrgDocument, parentIndex: Int?) =
            if (parentIndex == null) doc.sections.size to 1 else doc.subtreeEnd(parentIndex) to doc.sections[parentIndex].level + 1
        var newIndex = 0
        if (sourceUri == targetUri) {
            modifyFile(sourceUri, notes + listOfNotNull(parent)) { doc, indices ->
                val roots = Outline.roots(doc, indices.take(notes.size))
                val (at, level) = target(doc, if (parent != null) indices.last() else null)
                // The notes before the target leave their places, which moves it up
                newIndex = at - roots.filter { it < at }.sumOf { doc.subtreeEnd(it) - it }
                Outline.moveWithin(doc, roots, at, level)
            }
            onPlaced(newIndex)
        } else {
            val subtrees = copySubtrees(sourceUri, notes)
            modifyFile(targetUri, listOfNotNull(parent)) { doc, indices ->
                val (at, level) = target(doc, indices.firstOrNull())
                newIndex = at
                Outline.insert(doc, at, level, subtrees)
            }
            onPlaced(newIndex)
            modifyFile(sourceUri, notes) { doc, indices -> Outline.delete(doc, indices) }
        }
        return newIndex
    }

    /**
     * Re-reads the file, finds [notes] by their exact text, applies [transform] and writes the
     * result once. Fails without writing if a note changed on disk since it was loaded.
     */
    suspend fun modifyFile(
        fileUri: String,
        notes: List<OrgNote>,
        transform: (OrgDocument, List<Int>) -> OrgDocument,
    ): LoadedFile = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(fileUri)
            val doc = OrgDocument.parse(folder.read(uri), defaultKeywords)
            val indices = notes.map { locate(folder, uri, doc, it) }
            val updated = transform(doc, indices)
            if (updated.text != doc.text) folder.write(uri, updated.toFileText())
            reloadFile(folder, uri)
        }
    }

    /**
     * Changes the text of the notes with [change], in the files [fileUris], or else in every file.
     * Returns the number of notes changed.
     */
    suspend fun changeNotes(fileUris: Collection<String>? = null, change: (String) -> String): Int {
        fun renamed(raw: String) = change(raw)
        var changed = 0
        _library.value.files.forEach { file ->
            if (fileUris != null && file.uri !in fileUris) return@forEach
            val notes = file.notes.filter { renamed(it.raw) != it.raw }
            if (notes.isEmpty()) return@forEach
            modifyFile(file.uri, notes) { doc, indices ->
                Outline.replaceSections(doc, indices.associateWith { renamed(doc.sections[it].raw) })
            }
            changed += notes.size
        }
        return changed
    }

    /**
     * Index of the note's section in [doc], found by its exact text.
     * Fails if the section no longer exists as loaded, so changes from other apps are never overwritten.
     */
    private fun locate(folder: OrgFolder, uri: Uri, doc: OrgDocument, note: OrgNote): Int {
        val index = doc.sections.getOrNull(note.index)?.takeIf { it.raw == note.raw }?.let { note.index }
            ?: doc.sections.indexOfFirst { it.raw == note.raw }.takeIf { it >= 0 && doc.sections.count { s -> s.raw == note.raw } == 1 }
        if (index == null) {
            reloadFile(folder, uri)
            throw NoteChangedException()
        }
        return index
    }

    /** Re-reads the file and replaces the note's section with [transform]'s result. */
    private suspend fun modifySection(
        note: OrgNote,
        transform: (OrgDocument, OrgDocument.Section) -> String,
    ): OrgNote = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val folder = folder() ?: error("No folder")
            val uri = Uri.parse(note.fileUri)
            val doc = OrgDocument.parse(folder.read(uri), defaultKeywords)
            val index = locate(folder, uri, doc, note)

            val section = doc.sections[index]
            val newRaw = transform(doc, section)
            if (newRaw != section.raw) {
                folder.write(uri, doc.replaceSection(section, newRaw).toFileText())
            }
            reloadFile(folder, uri).notes[index]
        }
    }

    private fun reloadFile(folder: OrgFolder, uri: Uri): LoadedFile {
        val current = _library.value.files.first { it.ref.uri == uri }
        val loaded = load(folder, current.ref.copy(lastModified = folder.lastModified(uri)))
        _library.update { lib -> lib.copy(files = lib.files.map { if (it.ref.uri == uri) loaded else it }) }
        return loaded
    }

    companion object {
        private const val KEY_FOLDER = "folder_uri"
    }
}
