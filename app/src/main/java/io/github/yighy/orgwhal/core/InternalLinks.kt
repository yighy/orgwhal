package io.github.yighy.orgwhal.core

/**
 * Org's links within the notes, without a scheme: [[*Title]] (a note's title), [[#id]] (its
 * CUSTOM_ID), and [[name]]: a dedicated target <<name>>, an element named with #+NAME: name, or
 * else a note's title.
 */
object InternalLinks {

    private val SCHEME = Regex("""^[a-zA-Z][\w+.-]*:""")

    /** Whether [target] is such a link (not a URL, id:, file:…). */
    fun isInternal(target: String): Boolean = !SCHEME.containsMatchIn(target.trim())

    /** The note [target] leads to among [notes] (in the order to search), or null. */
    fun find(notes: List<OrgNote>, target: String): OrgNote? {
        val t = target.trim()
        return when {
            t.startsWith("*") -> notes.firstOrNull { sameTitle(it, t.substring(1)) }
            t.startsWith("#") -> notes.firstOrNull { it.property("CUSTOM_ID") == t.substring(1) }
            else -> notes.firstOrNull { "<<$t>>" in it.raw }
                ?: notes.firstOrNull { note -> NAME.findAll(note.content).any { it.groupValues[1].trim() == t } }
                ?: notes.firstOrNull { sameTitle(it, t) }
        }
    }

    /** The title of the note [target] would lead to, to make it when missing ([[#id]] gives none). */
    fun titleFor(target: String): String? {
        val t = target.trim()
        return when {
            t.startsWith("#") -> null
            t.startsWith("*") -> t.substring(1).trim().ifEmpty { null }
            else -> t.ifEmpty { null }
        }
    }

    /**
     * [text] with its links to [target] (the note just made for it, titled [title]) made to lead
     * to the note's [id]: [[target]] shows the title, [[target][text]] keeps its text.
     */
    fun toIdLinks(text: String, target: String, id: String, title: String): String =
        replaceLinks(text) { link ->
            if (link.target.trim() != target.trim()) null
            else link.description?.let { "[[id:$id][$it]]" } ?: IdLinks.format(id, title)
        }

    /**
     * [text] with its links to a note by its title [oldTitle] ([[title]] if [plain], [[*title]]
     * if [starred]) made to lead to [newTitle]; their own text is kept, unless it was the old title.
     */
    fun renameLinks(text: String, oldTitle: String, newTitle: String, plain: Boolean, starred: Boolean): String {
        val new = clean(IdLinks.plainText(newTitle))
        if (!plain && !starred || new.isEmpty() || '[' in new || ']' in new) return text
        val old = clean(IdLinks.plainText(oldTitle))
        val oldShown = IdLinks.description(oldTitle)
        return replaceLinks(text) { link ->
            val t = link.target.trim()
            val star = t.startsWith("*")
            if (!(if (star) starred else plain) || !clean(t.removePrefix("*")).equals(old, ignoreCase = true)) return@replaceLinks null
            val target = if (star) "*$new" else new
            val description = link.description?.takeIf { it.trim() != oldShown }
            if (description == null) "[[$target]]" else "[[$target][$description]]"
        }
    }

    /** [text] with its bracket links that [change] gives a new text (else null) replaced by it. */
    private fun replaceLinks(text: String, change: (Links.Link) -> String?): String {
        val changes = Links.find(text)
            .filter { text.startsWith("[[", it.range.first) }
            .mapNotNull { link -> change(link)?.let { link.range to it } }
        if (changes.isEmpty()) return text
        return buildString {
            var last = 0
            changes.forEach { (range, replacement) ->
                append(text, last, range.first)
                append(replacement)
                last = range.last + 1
            }
            append(text, last, text.length)
        }
    }

    private val NAME = Regex("""^[ \t]*#\+NAME:[ \t]*(.+)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    // Titles compare without their links and cookies, as Org's search ignores them
    private fun sameTitle(note: OrgNote, title: String): Boolean =
        clean(IdLinks.plainText(note.title)).equals(clean(title), ignoreCase = true)

    private fun clean(text: String) = Cookies.COOKIE.replace(text, "").replace(Regex("\\s+"), " ").trim()
}
