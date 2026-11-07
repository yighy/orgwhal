package io.github.yighy.orgwhal.core

import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Archiving as Emacs' org-archive-subtree does with its default location: the note and its
 * sub-notes go to the end of the notebook's archive file (gtd.org → gtd.org_archive), as a
 * top-level note with properties saying when and where from.
 */
object Archive {

    /** The archive file of the notebook at [path] in the folder. */
    fun path(path: String) = path + "_archive"

    /** What Emacs writes at the top of a new archive file. */
    fun header(fileName: String) = "#    -*- mode: org -*-\n\n\nArchived entries from file $fileName\n\n\n"

    /**
     * The subtree of note [index] of [doc] (the notebook at [path] in the folder) as it is
     * archived at [time]: ARCHIVE_TIME, ARCHIVE_FILE, ARCHIVE_OLPATH (its parents' titles, if
     * any), ARCHIVE_CATEGORY and ARCHIVE_TODO (its state, if any) added to its properties.
     */
    fun entry(doc: OrgDocument, index: Int, path: String, time: LocalDateTime): Subtree {
        val subtree = Outline.extract(doc, listOf(index)).single()
        val section = doc.sections[index]
        val head = doc.parseHead(section)
        val parents = mutableListOf<String>()
        var level = section.level
        for (i in index - 1 downTo 0) {
            if (doc.sections[i].level < level) {
                parents.add(0, doc.parseHead(doc.sections[i]).title.orEmpty().trim())
                level = doc.sections[i].level
            }
        }
        val properties = listOfNotNull(
            "ARCHIVE_TIME" to timeText(time),
            "ARCHIVE_FILE" to path,
            parents.takeIf { it.isNotEmpty() }?.let { "ARCHIVE_OLPATH" to it.joinToString("/") },
            "ARCHIVE_CATEGORY" to path.substringAfterLast('/').substringBeforeLast('.'),
            head.state?.let { "ARCHIVE_TODO" to it },
        )
        // The head's own lines are at the start of the subtree's text
        val headEnd = section.raw.length.coerceAtMost(subtree.text.length)
        val newHead = properties.fold(subtree.text.substring(0, headEnd)) { raw, (name, value) -> SectionEditor.addProperty(raw, name, value) }
        return Subtree(newHead + subtree.text.substring(headEnd), subtree.level)
    }

    /** The archive file's text ([archive], or a new one for [fileName]) with [entries] added at its end. */
    fun append(archive: String?, fileName: String, entries: List<Subtree>): String {
        val doc = OrgDocument.parse(archive ?: header(fileName))
        return Outline.insert(doc, doc.sections.size, 1, entries).text
    }

    /**
     * Org's archive tag (org-archive-tag): a note with it stays in its notebook, archived with its
     * sub-notes; kept out of the agenda and the search, folded when the notebook opens.
     */
    const val TAG = "ARCHIVE"

    /** Whether each of [notes] (a notebook's, in order) is in a tree archived with [TAG]: its own or a parent's. */
    fun taggedTrees(notes: List<OrgNote>): BooleanArray {
        val result = BooleanArray(notes.size)
        // The levels of the archived notes above, the nearest last
        val open = ArrayDeque<Int>()
        notes.forEachIndexed { i, note ->
            while (open.isNotEmpty() && open.last() >= note.level) open.removeLast()
            val tagged = TAG in note.tags
            result[i] = tagged || open.isNotEmpty()
            if (tagged) open.addLast(note.level)
        }
        return result
    }

    /** The properties archiving adds, taken away when the note is put back. */
    val PROPERTIES = setOf("ARCHIVE_TIME", "ARCHIVE_FILE", "ARCHIVE_OLPATH", "ARCHIVE_CATEGORY", "ARCHIVE_TODO", "ARCHIVE_ITAGS")

    /**
     * Where an archived note came from: its notebook's [file] as ARCHIVE_FILE gives it (a path in
     * the folder, or Emacs' full path), the titles of its [parents] (ARCHIVE_OLPATH) and [time].
     */
    data class Origin(val file: String, val parents: List<String>, val time: String?)

    fun origin(note: OrgNote): Origin? {
        fun property(name: String) = note.properties.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second?.trim()
        val file = property("ARCHIVE_FILE")?.takeIf { it.isNotEmpty() } ?: return null
        val parents = property("ARCHIVE_OLPATH")?.split('/')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return Origin(file, parents, property("ARCHIVE_TIME"))
    }

    /**
     * Which of the notebooks at [paths] (in the folder) [file] names: the same path, a full path
     * ending with it (as Emacs writes it), else the same file name. Null if none.
     */
    fun notebook(file: String, paths: List<String>): Int? {
        val wanted = file.replace('\\', '/').removePrefix("./")
        paths.indexOfFirst { it.equals(wanted, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
        paths.indexOfFirst { wanted.endsWith("/$it", ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
        val name = wanted.substringAfterLast('/')
        return paths.indexOfFirst { it.substringAfterLast('/').equals(name, ignoreCase = true) }.takeIf { it >= 0 }
    }

    /**
     * The note of [notes] at the end of the titles [parents], or at the end of as many of them as
     * are still there (the nearest place); null when not even the first is: the top level.
     */
    fun parent(notes: List<OrgNote>, parents: List<String>): OrgNote? {
        val paths = notes.map { note -> (NoteTree.ancestors(notes, note.index) + note).map { it.title.trim() } }
        for (n in parents.size downTo 1) {
            val i = paths.indexOfFirst { it == parents.take(n) }
            if (i >= 0) return notes[i]
        }
        return null
    }

    /** [subtree] as put back: without the properties archiving added. */
    fun restored(subtree: Subtree): Subtree = subtree.copy(text = SectionEditor.removeProperties(subtree.text, PROPERTIES))

    /** As Org writes it: 2026-10-10 Sat 14:03. */
    private fun timeText(time: LocalDateTime): String =
        "%s %s %02d:%02d".format(time.toLocalDate(), time.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), time.hour, time.minute)
}
