package io.github.yighy.orgwhal.core

import com.orgzly.org.OrgHead
import com.orgzly.org.OrgProperties
import com.orgzly.org.OrgStringUtils
import com.orgzly.org.parser.OrgParser
import com.orgzly.org.parser.OrgParserSettings
import com.orgzly.org.parser.OrgParserWriter

/**
 * An Org file split into a preface and one section per heading.
 *
 * Sections keep their exact source text, so untouched parts of the file are written back
 * byte for byte. Only an edited section is regenerated.
 */
class OrgDocument private constructor(
    /** File text with "\n" line endings. */
    val text: String,
    /** Line separator used by the file on disk. */
    val lineSeparator: String,
    val preface: String,
    val sections: List<Section>,
    val keywords: Keywords,
    /** Keywords used when the file has no #+TODO line (kept for [rebuild]). */
    private val defaults: Keywords,
) {

    /** A heading and everything up to the next heading. [end] is exclusive. */
    data class Section(val start: Int, val end: Int, val raw: String, val level: Int)

    /** Text to write to disk, with the file's original line endings. */
    fun toFileText(): String =
        if (lineSeparator == "\n") text else text.replace("\n", lineSeparator)

    fun parseHead(section: Section): OrgHead = parseSectionHead(section.raw, keywords)

    /** Returns a document with [section] replaced by [newRaw]. */
    fun replaceSection(section: Section, newRaw: String): OrgDocument =
        rebuild(text.substring(0, section.start) + newRaw + text.substring(section.end))

    /** Returns a document with a new top-level note appended at the end. */
    fun appendNote(title: String, properties: List<Pair<String, String>> = emptyList()): OrgDocument {
        val sb = StringBuilder(text)
        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
        sb.append("* ").append(title.trim()).append('\n')
        if (properties.isNotEmpty()) {
            sb.append(":PROPERTIES:\n")
            properties.forEach { (name, value) -> sb.append(formatProperty(name, value)).append('\n') }
            sb.append(":END:\n")
        }
        return rebuild(sb.toString())
    }

    /**
     * Returns a document with the text before the first heading replaced by [newPreface].
     * Keeps a line break, and a blank line when there are notes, before the first heading.
     */
    fun replacePreface(newPreface: String): OrgDocument {
        val body = text.substring(preface.length)
        val trimmed = newPreface.replace("\r\n", "\n").trimEnd()
        val separator = when {
            trimmed.isEmpty() -> ""
            body.isEmpty() -> "\n"
            else -> "\n\n"
        }
        return rebuild(trimmed + separator + body)
    }

    /** Index of the first section after the subtree of section [index]. */
    fun subtreeEnd(index: Int): Int {
        val level = sections[index].level
        var i = index + 1
        while (i < sections.size && sections[i].level > level) i++
        return i
    }

    /** Returns a document with a new heading inserted before section [atSection] (or at the end). */
    fun insertNote(atSection: Int, level: Int, title: String): OrgDocument {
        val offset = sections.getOrNull(atSection)?.start ?: text.length
        val sb = StringBuilder(text.substring(0, offset))
        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
        sb.append("*".repeat(level)).append(' ').append(title.trim()).append('\n')
        sb.append(text.substring(offset))
        return rebuild(sb.toString())
    }

    /** Same document type (keeps the line separator) with new text. */
    internal fun rebuild(newText: String) = parse(newText, defaults).let {
        OrgDocument(it.text, lineSeparator, it.preface, it.sections, it.keywords, defaults)
    }

    companion object {
        private val HEADING = Regex("""^\*+[ \t]""", RegexOption.MULTILINE)

        /** [defaults] are the state keywords for files without a #+TODO line. */
        fun parse(fileText: String, defaults: Keywords = Keywords.DEFAULT): OrgDocument {
            val lineSeparator = if (fileText.contains("\r\n")) "\r\n" else "\n"
            val text = fileText.replace("\r\n", "\n")

            val starts = HEADING.findAll(text).map { it.range.first }.toList()
            val prefaceEnd = starts.firstOrNull() ?: text.length
            val preface = text.substring(0, prefaceEnd)

            val sections = starts.mapIndexed { i, start ->
                val end = starts.getOrNull(i + 1) ?: text.length
                val raw = text.substring(start, end)
                Section(start, end, raw, raw.takeWhile { it == '*' }.length)
            }

            return OrgDocument(text, lineSeparator, preface, sections, Keywords.fromPreface(preface, defaults), defaults)
        }

        fun parseSectionHead(raw: String, keywords: Keywords): OrgHead {
            val parsed = OrgParser.Builder()
                .setTodoKeywords(keywords.todo.toSet())
                .setDoneKeywords(keywords.done.toSet())
                .setInput(raw)
                .build()
                .parse()
            return parsed.headsInList.first().head
        }

        /** Same layout as Emacs' default org-property-format. */
        fun formatProperty(name: String, value: String): String =
            String.format("%-10s %s", ":$name:", value).trimEnd()
    }
}

/** Fields of a note that the editor can change. */
data class NoteEdit(
    val title: String,
    val state: String?,
    val tags: List<String>,
    val properties: List<Pair<String, String>>,
    val content: String,
    val priority: String? = null,
    /** Org timestamps, e.g. "<2026-10-05 Mon 09:00>" */
    val scheduled: String? = null,
    val deadline: String? = null,
    /** Inactive timestamp, e.g. "[2026-10-04 Sun 17:00]" */
    val closed: String? = null,
)

object SectionEditor {

    /** Emacs' default org-tags-column: tags right-aligned at column 77. */
    private const val DEFAULT_TAGS_COLUMN = -77

    private val TAGS = Regex("""[ \t](:[^\s:]+)+:[ \t]*$""")

    /**
     * Regenerates a section from [edit], keeping planning times, clock, the
     * original trailing blank lines, tag alignment and indentation style.
     */
    fun rewrite(raw: String, level: Int, keywords: Keywords, edit: NoteEdit): String {
        val head = OrgDocument.parseSectionHead(raw, keywords)

        head.title = edit.title.replace('\n', ' ').trim()
        head.state = edit.state?.takeIf { it.isNotBlank() }
        head.priority = edit.priority?.takeIf { it.isNotBlank() }
        head.scheduled = edit.scheduled?.let(OrgTime::range)
        head.deadline = edit.deadline?.let(OrgTime::range)
        head.closed = edit.closed?.let(OrgTime::range)
        head.setTags(edit.tags.filter { it.isNotBlank() }.toTypedArray())

        val properties = OrgProperties()
        edit.properties
            .filter { it.first.isNotBlank() }
            .forEach { (name, value) -> properties.put(name.trim(), value.trim()) }
        head.properties = properties

        head.content = edit.content.trimEnd().ifEmpty { null }

        val lines = raw.split('\n')
        val heading = lines.first()
        val settings = OrgParserSettings.getBasic().apply {
            separateHeaderAndContentWithNewLine = false
            separateNotesWithNewLine = OrgParserSettings.SeparateNotesWithNewLine.NEVER
            // Keep the column where this heading's tags ended
            tagsColumn = if (TAGS.containsMatchIn(heading)) -OrgStringUtils.stringWidth(heading.trimEnd()) else DEFAULT_TAGS_COLUMN
        }
        // Indented if the planning line or property drawer under the heading is
        val isIndented = lines.drop(1)
            .firstOrNull { isPlanningLine(it) || it.trimStart().startsWith(":") }
            ?.let { it.startsWith(" ") || it.startsWith("\t") } ?: false

        val generated = OrgParserWriter(settings).whiteSpacedHead(head, level, isIndented)
        return generated.trimEnd('\n') + trailingNewlines(raw)
    }

    /**
     * Adds a property to a section without touching the rest of its text: into the
     * existing property drawer, or in a new drawer right after the planning line.
     */
    fun addProperty(raw: String, name: String, value: String): String {
        val lines = raw.split('\n').toMutableList()

        var i = 1
        while (i < lines.size && isPlanningLine(lines[i])) i++

        if (i < lines.size && lines[i].trim().equals(":PROPERTIES:", ignoreCase = true)) {
            val indent = lines[i].takeWhile { it == ' ' || it == '\t' }
            val end = (i + 1 until lines.size).firstOrNull { lines[it].trim().equals(":END:", ignoreCase = true) }
            if (end != null) {
                lines.add(end, indent + OrgDocument.formatProperty(name, value))
                return lines.joinToString("\n")
            }
        }

        // Same indentation as the planning line above, if any
        val indent = if (i > 1) lines[i - 1].takeWhile { it == ' ' || it == '\t' } else ""
        lines.addAll(i, listOf(":PROPERTIES:", OrgDocument.formatProperty(name, value), ":END:").map { indent + it })
        return lines.joinToString("\n")
    }

    /**
     * Removes the properties [names] (ignoring case) from a section's property drawer, and the
     * drawer once it is empty, as org-entry-delete does; the rest of the text stays as it was.
     */
    fun removeProperties(raw: String, names: Set<String>): String {
        val lines = raw.split('\n').toMutableList()
        var i = 1
        while (i < lines.size && isPlanningLine(lines[i])) i++
        if (i >= lines.size || !lines[i].trim().equals(":PROPERTIES:", ignoreCase = true)) return raw
        val end = (i + 1 until lines.size).firstOrNull { lines[it].trim().equals(":END:", ignoreCase = true) } ?: return raw
        val upper = names.map { it.uppercase() }.toSet()
        val kept = lines.subList(i + 1, end).filterNot { line ->
            PROPERTY_NAME.find(line)?.groupValues?.get(1)?.uppercase()?.let { it in upper || it.removeSuffix("+") in upper } == true
        }
        val drawer = if (kept.isEmpty()) emptyList() else listOf(lines[i]) + kept + lines[end]
        return (lines.subList(0, i) + drawer + lines.subList(end + 1, lines.size)).joinToString("\n")
    }

    private val PROPERTY_NAME = Regex("""^\s*:([^:\s]+):""")

    private fun isPlanningLine(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("SCHEDULED:") || t.startsWith("DEADLINE:") || t.startsWith("CLOSED:")
    }

    private fun trailingNewlines(raw: String): String {
        val count = raw.length - raw.trimEnd('\n').length
        return "\n".repeat(count.coerceAtLeast(1))
    }
}

/** TODO keywords, from the file's #+TODO lines or the defaults. */
data class Keywords(val todo: List<String>, val done: List<String>) {
    val all: List<String> get() = todo + done

    /** As written after #+TODO:, e.g. "TODO NEXT | DONE". */
    fun toSpec(): String = (todo + "|" + done).joinToString(" ")

    companion object {
        /** Same as Orgzly's default; Emacs' own default is TODO | DONE. */
        val DEFAULT = Keywords(listOf("TODO", "NEXT"), listOf("DONE"))

        /** Parses "TODO NEXT | DONE" (the #+TODO: syntax); null if there is no keyword. */
        fun parse(spec: String): Keywords? = read("#+TODO: $spec", defaults = null)

        private val LINE = Regex("""^#\+(?:SEQ_|TYP_)?TODO:[ \t]*(.*)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

        fun fromPreface(preface: String, defaults: Keywords = DEFAULT): Keywords = read(preface, defaults)!!

        private fun read(preface: String, defaults: Keywords?): Keywords? {
            val todo = mutableListOf<String>()
            val done = mutableListOf<String>()

            for (match in LINE.findAll(preface)) {
                // Strip fast-access keys and logging options, e.g. "TODO(t)" or "WAIT(w@/!)"
                val words = match.groupValues[1].split(Regex("\\s+")).filter { it.isNotEmpty() }
                    .map { it.substringBefore('(') }
                val bar = words.indexOf("|")
                if (bar >= 0) {
                    todo += words.subList(0, bar)
                    done += words.subList(bar + 1, words.size)
                } else if (words.isNotEmpty()) {
                    todo += words.dropLast(1)
                    done += words.last()
                }
            }

            return if (todo.isEmpty() && done.isEmpty()) defaults else Keywords(todo.distinct(), done.distinct())
        }
    }
}
