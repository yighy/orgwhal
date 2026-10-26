package io.github.yighy.orgwhal.core

/**
 * Completion of the search field ([SearchQuery]'s syntax): the condition prefixes as a word is
 * begun (`t.`, `col.`…), then their values from the notes (tags, notebooks, table columns…).
 */
object SearchCompletion {

    /** What values are proposed from. */
    data class Vocabulary(
        val notebooks: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
        val keywords: List<String> = emptyList(),
        val columns: List<String> = emptyList(),
    )

    /** What a choice is, for its description. */
    enum class Hint {
        Tag, OwnTag, State, StateType, Scheduled, Deadline, Closed, Event, Created, Modified, Notebook,
        Priority, SetPriority, Column, Sort, Agenda, Eq, Ne, Lt, Le, Gt, Ge
    }

    /**
     * [text] replaces the word; [label] is what the list shows. A choice that [continues] (a prefix,
     * a relation) leaves the cursor right after it, a whole condition is followed by a space.
     */
    data class Choice(val text: String, val label: String, val hint: Hint? = null, val continues: Boolean = false)

    /** The word around the cursor: [start] to [end], [word] the part before the cursor. */
    data class Query(val start: Int, val end: Int, val word: String, val wholeQueryEmpty: Boolean)

    private val PREFIXES = listOf(
        "t." to Hint.Tag, "tn." to Hint.OwnTag, "i." to Hint.State, "it." to Hint.StateType,
        "s." to Hint.Scheduled, "d." to Hint.Deadline, "c." to Hint.Closed, "e." to Hint.Event,
        "cr." to Hint.Created, "mod." to Hint.Modified, "b." to Hint.Notebook, "p." to Hint.Priority, "ps." to Hint.SetPriority,
        "col." to Hint.Column, "o." to Hint.Sort, "ad." to Hint.Agenda,
    )
    private val RELATIONS = listOf(
        "eq" to Hint.Eq, "ne" to Hint.Ne, "lt" to Hint.Lt, "le" to Hint.Le, "gt" to Hint.Gt, "ge" to Hint.Ge,
    )
    private val INTERVALS = listOf("today", "tomorrow", "yesterday", "now", "none", "3d", "1w", "1m")
    private val SORTS = listOf("scheduled", "deadline", "priority", "title", "state", "notebook", "created", "modified", "closed", "event", "position")
    private val TIMES = setOf("s", "d", "c", "e", "cr", "mod")

    private fun separates(c: Char) = c.isWhitespace() || c == '(' || c == ')'

    /** The word at [cursor], or null inside quotes. */
    fun queryAt(text: String, cursor: Int): Query? {
        if (cursor < 0 || cursor > text.length) return null
        // Words as the search reads them: spaces inside quotes do not end one
        var start = 0
        var quoted = false
        for (i in 0 until cursor) {
            if (text[i] == '"' && (i == 0 || text[i - 1] != '\\')) quoted = !quoted
            else if (!quoted && separates(text[i])) start = i + 1
        }
        // Inside quotes, the word is free text
        if (quoted) return null
        var end = cursor
        while (end < text.length && !separates(text[end]) && text[end] != '"') end++
        return Query(start, end, text.substring(start, cursor), text.isBlank())
    }

    /** The choices for what is typed, the ones already written in full left out. */
    fun choices(query: Query, vocabulary: Vocabulary): List<Choice> {
        val negation = if (query.word.startsWith(".")) "." else ""
        val body = query.word.removePrefix(negation)
        // Prefixes from the first letter, or all of them in an empty field
        if (body.isEmpty() && !query.wholeQueryEmpty && negation.isEmpty()) return emptyList()
        val dot = body.indexOf('.')
        if (dot < 0) {
            // b. only where there are notebooks to choose from (not in a notebook)
            return PREFIXES.filter { it.first.startsWith(body, ignoreCase = true) && (it.first != "b." || vocabulary.notebooks.isNotEmpty()) }
                .map { (prefix, hint) -> Choice(negation + prefix, prefix, hint, continues = true) }
        }
        val prefix = body.substring(0, dot).lowercase()
        val rest = body.substring(dot + 1)
        val head = negation + prefix + "."
        val choices = when (prefix) {
            "t", "tn" -> values(head, rest, vocabulary.tags)
            "i" -> values(head, rest, vocabulary.keywords)
            "it" -> values(head, rest, listOf("todo", "done", "none"))
            "b" -> values(head, rest, vocabulary.notebooks)
            "p", "ps" -> values(head, rest, listOf("a", "b", "c"))
            "o" -> values(head, rest, SORTS)
            "ad" -> values(head, rest, listOf("7", "14", "30"))
            in TIMES -> {
                val second = rest.indexOf('.')
                if (second < 0) {
                    relations(head, rest) + values(head, rest, INTERVALS)
                } else {
                    val relation = rest.substring(0, second).lowercase()
                    if (RELATIONS.none { it.first == relation }) emptyList()
                    else values("$head$relation.", rest.substring(second + 1), INTERVALS)
                }
            }
            "col" -> {
                // A quoted name can hold dots
                val closing = if (rest.startsWith("\"")) rest.indexOf('"', 1) else 0
                val second = if (closing < 0) -1 else rest.indexOf('.', closing)
                if (second < 0) {
                    vocabulary.columns.filter { SearchQuery.fold(it).startsWith(SearchQuery.fold(rest)) }
                        .map { Choice(head + quoted(it) + ".", it, continues = true) }
                } else {
                    val name = rest.substring(0, second)
                    val after = rest.substring(second + 1)
                    if ('.' in after) emptyList() else relations(head + name + ".", after)
                }
            }
            else -> emptyList()
        }
        return choices.filterNot { it.text.equals(query.word, ignoreCase = true) && !it.continues }
    }

    /** [text] with [choice] in place of the word, and the cursor after it. */
    fun apply(text: String, query: Query, choice: Choice): Pair<String, Int> {
        val after = text.substring(query.end)
        val space = if (choice.continues || after.startsWith(" ")) "" else " "
        val written = text.substring(0, query.start) + choice.text + space + after
        val cursor = query.start + choice.text.length + if (choice.continues) 0 else 1
        return written to cursor
    }

    private fun values(head: String, typed: String, all: List<String>): List<Choice> =
        all.filter { SearchQuery.fold(it).startsWith(SearchQuery.fold(typed)) }
            .map { Choice(head + quoted(it), it) }

    private fun relations(head: String, typed: String): List<Choice> =
        RELATIONS.filter { it.first.startsWith(typed, ignoreCase = true) }
            .map { (relation, hint) -> Choice("$head$relation.", relation, hint, continues = true) }

    private fun quoted(value: String) =
        if (value.any { it.isWhitespace() || it == '.' || it == '(' || it == ')' }) "\"" + value.replace("\"", "\\\"") + "\"" else value
}
