package io.github.yighy.orgwhal.core

/**
 * Content shown from another note, as org-transclusion writes it: `#+transclude: [[link]] options`.
 * The link leads to a note (id:, [[*Title]], [[Title]]), whose content shows, to a target
 * <<name>>, whose paragraph (or list item) shows, or to an element named with #+NAME (a table, a
 * block), which shows alone; `:lines 3-8` keeps those lines of it.
 */
object Transclusion {

    /** What shows: [text], from [note]. */
    data class Shown(val note: OrgNote, val text: String)

    private val LINES = Regex(""":lines[ \t]+(\d*)-(\d*)""")
    private val NAME_LINE = Regex("""^[ \t]*#\+NAME:.*$""", RegexOption.IGNORE_CASE)

    /** What [link] (with its [options]) shows among [notes], those of the notebook [from] first; null if nothing. */
    fun resolve(notes: List<OrgNote>, link: String, options: String, from: String? = null): Shown? {
        val target = link.trim()
        val order = if (from == null) notes else notes.sortedBy { it.fileUri != from }
        val shown = when {
            target.startsWith("id:", ignoreCase = true) -> {
                val id = target.substring(3).trim()
                order.firstOrNull { it.id.equals(id, ignoreCase = true) }?.let { Shown(it, it.content) }
            }
            !InternalLinks.isInternal(target) -> null
            target.startsWith("*") || target.startsWith("#") -> InternalLinks.find(order, target)?.let { Shown(it, it.content) }
            else -> around(order, target) ?: named(order, target) ?: InternalLinks.find(order, target)?.let { Shown(it, it.content) }
        } ?: return null
        // Its targets go, as when exported: they are where links lead, not what shows
        val text = lines(shown.text.trimIndent().trim('\n'), options)
        return shown.copy(text = InternalLinks.TARGET.replace(text, ""))
    }

    /**
     * The paragraph with the target <<[name]>> (to the lines around it up to blank ones), or the
     * list item it is in with its sub-items; the target itself left out.
     */
    private fun around(notes: List<OrgNote>, name: String): Shown? {
        val marker = "<<$name>>"
        val note = notes.firstOrNull { marker in it.content } ?: return null
        val lines = note.content.trimIndent().split('\n')
        val at = lines.indexOfFirst { marker in it }
        if (at < 0) return null
        val item = ITEM.find(lines[at])
        val range = if (item != null) {
            // The item and the lines more indented than its bullet
            val indent = item.groupValues[1].length
            var last = at
            while (last + 1 < lines.size && lines[last + 1].isNotBlank() && lines[last + 1].indentation() > indent) last++
            at..last
        } else {
            // As Org's paragraph: up to a blank line, a table, a keyword (#+NAME…), a drawer or a list
            fun inParagraph(line: String) = line.isNotBlank() && !OTHER_ELEMENT.containsMatchIn(line) && ITEM.find(line) == null
            var first = at
            while (first > 0 && inParagraph(lines[first - 1])) first--
            var last = at
            while (last + 1 < lines.size && inParagraph(lines[last + 1])) last++
            first..last
        }
        val text = lines.slice(range).joinToString("\n").replace(marker, "").trimIndent()
        return Shown(note, text)
    }

    private val ITEM = Regex("""^([ \t]*)(?:[-+*]|\d+[.)])[ \t]+""")
    private val OTHER_ELEMENT = Regex("""^[ \t]*(?:\||#\+|:[\w-]+:[ \t]*$)""")

    private fun String.indentation() = takeWhile { it == ' ' || it == '\t' }.length

    /** The table or block named [name] (#+NAME), without its name line. */
    private fun named(notes: List<OrgNote>, name: String): Shown? {
        notes.forEach { note ->
            val block = Blocks.parse(note.content).firstOrNull {
                (it is Blocks.Table && it.name == name) || (it is Blocks.Begin && it.name == name)
            } ?: return@forEach
            val text = note.content.substring(block.range).split('\n').dropWhile { NAME_LINE.matches(it) }.joinToString("\n")
            return Shown(note, text)
        }
        return null
    }

    /** The lines of [text] that `:lines from-to` in [options] keeps (from 1, either end left out: to the end). */
    fun lines(text: String, options: String): String {
        val m = LINES.find(options) ?: return text
        val all = text.split('\n')
        val from = ((m.groupValues[1].toIntOrNull() ?: 1) - 1).coerceIn(0, all.size)
        val to = (m.groupValues[2].toIntOrNull() ?: all.size).coerceIn(from, all.size)
        return all.subList(from, to).joinToString("\n")
    }
}
