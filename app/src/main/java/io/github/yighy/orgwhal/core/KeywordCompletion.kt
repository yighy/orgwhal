package io.github.yighy.orgwhal.core

/**
 * "#" typed at the start of a line: the Org keywords Orgwhal shows, as Emacs completes them after
 * "#+" (and as org-tempo writes a whole block).
 */
object KeywordCompletion {

    /** What a choice writes: [before] the cursor, then [after] it (a block's end line). */
    enum class Choice(val label: String, private val before: String, private val after: String = "", val kind: String? = null) {
        Transclude("#+transclude:", "#+transclude: [[", "]]"),
        Name("#+NAME:", "#+NAME: "),
        Formulas("#+TBLFM:", "#+TBLFM: "),
        Source("#+BEGIN_SRC", "#+BEGIN_SRC\n", "\n#+END_SRC", "SRC"),
        Quote("#+BEGIN_QUOTE", "#+BEGIN_QUOTE\n", "\n#+END_QUOTE", "QUOTE"),
        Example("#+BEGIN_EXAMPLE", "#+BEGIN_EXAMPLE\n", "\n#+END_EXAMPLE", "EXAMPLE"),
        Verse("#+BEGIN_VERSE", "#+BEGIN_VERSE\n", "\n#+END_VERSE", "VERSE"),
        Center("#+BEGIN_CENTER", "#+BEGIN_CENTER\n", "\n#+END_CENTER", "CENTER");

        /** Its text, its lines after the first indented as [indent]; the cursor at [Pair.second]. */
        fun text(indent: String): Pair<String, Int> {
            val b = before.replace("\n", "\n$indent")
            return b + after.replace("\n", "\n$indent") to b.length
        }
    }

    /** The keyword being typed: from [start] (its "#") to [end], [indent] before it, [text] after "#" or "#+". */
    data class Query(val start: Int, val end: Int, val indent: String, val text: String)

    private val TYPED = Regex("""^([ \t]*)#(\+?)(\w*)$""")
    private val REST = Regex("""^\w*[ \t]*$""")

    /** The keyword typed just before [cursor], or null ("# " is a comment; a keyword already written stays). */
    fun queryAt(text: String, cursor: Int): Query? {
        if (cursor < 1 || cursor > text.length) return null
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val m = TYPED.matchEntire(text.substring(lineStart, cursor)) ?: return null
        val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val rest = text.substring(cursor, lineEnd)
        if (!REST.matches(rest)) return null
        val word = rest.takeWhile { it.isLetterOrDigit() || it == '_' }
        return Query(lineStart + m.groupValues[1].length, cursor + word.length, m.groupValues[1], m.groupValues[3])
    }

    /** The choices for [query]: their keyword (or a block's kind) starting with what is typed. */
    fun choices(query: Query): List<Choice> = Choice.entries.filter { c ->
        val word = c.label.removePrefix("#+")
        word.startsWith(query.text, ignoreCase = true) || c.kind?.startsWith(query.text, ignoreCase = true) == true
    }
}
