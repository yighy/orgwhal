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

    /**
     * A link being typed after "[[": [start] is its first "[", [end] the end of what a link picked
     * replaces (the "]]" closing it included), [text] what is typed so far.
     */
    data class Query(val start: Int, val end: Int, val text: String)

    /** The link being typed just before [cursor] ("[[" then a few letters), or null (id:, file:… have their own). */
    fun queryAt(text: String, cursor: Int): Query? {
        if (cursor < 2 || cursor > text.length) return null
        val start = text.lastIndexOf("[[", cursor - 2)
        if (start < 0) return null
        val typed = text.substring(start + 2, cursor)
        if (typed.length > 60 || typed.any { it == '[' || it == ']' || it == '\n' } || !isInternal(typed)) return null
        val rest = text.substring(cursor)
        val close = CLOSE.find(rest)?.value?.length ?: if (rest.startsWith("]")) 1 else 0
        return Query(start, cursor + close, typed)
    }

    private val CLOSE = Regex("""^[^\[\]\n]*]]""")

    /**
     * A link to [note] by its title, as written in the notebook [from]: [[Title]], or [[*Title]]
     * if a target of that name comes first; null if neither leads to it (another note has its
     * title), the title cannot be a link's (brackets in it), or the note is in another notebook
     * (Emacs looks for a title in the link's file only): an id: link is then the way.
     */
    fun linkTo(notes: List<OrgNote>, note: OrgNote, from: String): String? {
        if (note.fileUri != from) return null
        val title = clean(IdLinks.plainText(note.title))
        if (title.isEmpty() || title.any { it == '[' || it == ']' } || title[0] == '*' || title[0] == '#' || !isInternal(title)) return null
        val order = notes.sortedBy { it.fileUri != from }
        fun leadsHere(target: String) = find(order, target)?.let { it.fileUri == note.fileUri && it.index == note.index } == true
        return when {
            leadsHere(title) -> "[[$title]]"
            leadsHere("*$title") -> "[[*$title]]"
            else -> null
        }
    }

    /** A name a link can lead to, in [note]: a target <<name>> ([target]), or an element's #+NAME. */
    data class Name(val name: String, val note: OrgNote, val target: Boolean)

    /** The names (targets, #+NAME) with every word of [query], those starting with the first word first. */
    fun names(notes: List<OrgNote>, query: String, limit: Int = 10): List<Name> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return notes.asSequence()
            .flatMap { note ->
                TARGET.findAll(note.raw).map { Name(it.groupValues[1].trim(), note, true) } +
                    NAME.findAll(note.content).map { Name(it.groupValues[1].trim(), note, false) }
            }
            .filter { n -> n.name.isNotEmpty() && n.name.none { it == '[' || it == ']' } && words.all { n.name.contains(it, ignoreCase = true) } }
            .distinctBy { it.name }
            .sortedWith(
                compareByDescending<Name> { words.isNotEmpty() && it.name.startsWith(words.first(), ignoreCase = true) }
                    .thenBy { it.name.length }
            )
            .take(limit)
            .toList()
    }

    /** A dedicated target, <<name>> (not a radio one, <<<name>>>). */
    val TARGET = Regex("""(?<!<)<<([^<>\n]+)>>(?!>)""")

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
