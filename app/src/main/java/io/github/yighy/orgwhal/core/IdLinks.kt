package io.github.yighy.orgwhal.core

/** Org "id:" links: completion while typing, and parsing for display. */
object IdLinks {

    /** Text typed after "id:", from [start] (the "i") to [end] (the cursor). */
    data class Query(val start: Int, val end: Int, val text: String)

    data class Link(val range: IntRange, val id: String, val description: String?)

    private const val MAX_QUERY_LENGTH = 60

    /**
     * Returns the "id:" query (or another [prefix], such as "file:") being typed just before
     * [cursor], or null. Ignores one inside an existing link ("[[id:...") or in the middle of a word.
     */
    fun queryAt(text: String, cursor: Int, prefix: String = "id:"): Query? {
        if (cursor < prefix.length || cursor > text.length) return null
        val before = text.substring(0, cursor)
        val start = before.lastIndexOf(prefix)
        if (start < 0) return null

        val prev = before.getOrNull(start - 1)
        if (prev != null && !prev.isWhitespace() && prev != '(') return null

        val query = before.substring(start + prefix.length)
        if (query.length > MAX_QUERY_LENGTH || query.any { it == '\n' || it == '[' || it == ']' }) return null

        return Query(start, cursor, query)
    }

    /** Link to a note titled [title] (its links shown as their description). */
    fun format(id: String, title: String): String {
        val description = description(title)
        return if (description.isEmpty()) "[[id:$id]]" else "[[id:$id][$description]]"
    }

    /** Description of a link to a note titled [title]. */
    fun description(title: String): String = plainText(title).replace('[', '(').replace(']', ')').trim()

    /** [text] with its links shown as their description (else their target), without emphasis markers. */
    fun plainText(text: String): String {
        val links = Links.find(text)
        if (links.isEmpty()) return stripEmphasis(text)
        return buildString {
            var last = 0
            links.forEach { link ->
                append(text, last, link.range.first)
                append(link.shown)
                last = link.range.last + 1
            }
            append(text, last, text.length)
        }.let(::stripEmphasis)
    }

    private fun stripEmphasis(text: String): String {
        val emphasis = Markup.find(text)
        if (emphasis.isEmpty()) return text
        return buildString {
            var last = 0
            emphasis.forEach { e ->
                append(text, last, e.range.first)
                append(e.content)
                last = e.range.last + 1
            }
            append(text, last, text.length)
        }
    }

    /** The [[id:…]] links of [text]. */
    fun find(text: String): List<Link> = Links.find(text)
        .filter { it.target.startsWith("id:", ignoreCase = true) }
        .map { Link(it.range, it.target.substring(3).trim(), it.description) }

    /**
     * Gives the links to [id] described as [oldTitle] the description of [newTitle].
     * Links with another description (written by hand) are left as they are.
     */
    fun renameLinks(text: String, id: String, oldTitle: String, newTitle: String): String {
        val old = description(oldTitle)
        val new = description(newTitle)
        if (old.isEmpty() || old == new) return text
        val links = find(text).filter { it.id.equals(id, ignoreCase = true) && it.description == old }
        if (links.isEmpty()) return text
        return buildString {
            var last = 0
            links.forEach { link ->
                append(text, last, link.range.first)
                append(format(link.id, newTitle))
                last = link.range.last + 1
            }
            append(text, last, text.length)
        }
    }

    /**
     * Notes whose title, or else [path] (its notebook and ancestors' titles, the outermost first),
     * contains every word of [query], best matches first: titles starting with the first word,
     * titles with every word, then notes found by their path, the nearest ancestor first.
     */
    fun suggestions(
        notes: List<OrgNote>,
        query: String,
        limit: Int = 20,
        path: (OrgNote) -> List<String> = { emptyList() },
    ): List<OrgNote> {
        // Whatever the case and accents: "seance" finds "Séance"
        val words = SearchQuery.fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return notes
            .mapNotNull { note ->
                if (note.title.isBlank()) return@mapNotNull null
                val missing = words.filterNot { it in note.foldedTitle }
                if (missing.isEmpty()) return@mapNotNull note to 0
                // How far up the path the words missing from the title are: the parent is 1
                val titles = path(note).map(SearchQuery::fold)
                note to missing.maxOf { word ->
                    val at = titles.indexOfLast { word in it }
                    if (at < 0) return@mapNotNull null
                    titles.size - at
                }
            }
            .sortedWith(
                compareBy<Pair<OrgNote, Int>> { it.second }
                    .thenByDescending { words.isNotEmpty() && it.first.foldedTitle.startsWith(words.first()) }
                    .thenBy { it.first.title.length }
            )
            .take(limit)
            .map { it.first }
    }
}
