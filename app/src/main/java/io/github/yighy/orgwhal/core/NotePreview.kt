package io.github.yighy.orgwhal.core

/**
 * A note's content as a few lines of plain text, for places that show no formatting (a reminder's
 * notification): links and emphasis as their text, list items with a bullet, checkboxes as ☐ / ☑,
 * a table's rows as their fields side by side, what a #+transclude: line shows ([transclude]
 * gives it; else the link) as if written there; drawers, rules, formulas, keywords and targets
 * left out.
 */
object NotePreview {

    private val ITEM = Regex("""^([ \t]*)(?:[-+*]|\d+[.)])[ \t]+(?:\[([ xX-])][ \t]+)?(.*)$""")
    private val KEYWORD = Regex("""^[ \t]*#\+\S*:?.*$""")

    /** The first [max] lines of [content] shown, the last one "…" when there are more. */
    fun lines(content: String, max: Int = 6, transclude: (Blocks.Transclude) -> String? = { null }): List<String> {
        val lines = Blocks.parse(content).flatMap { block ->
            when (block) {
                is Blocks.Text -> block.text.split('\n').mapNotNull(::textLine)
                is Blocks.Table -> block.rows.filterNotNull()
                    .map { row -> row.map(IdLinks::plainText).filter { it.isNotBlank() }.joinToString("  ·  ") }
                    .filter { it.isNotEmpty() }
                is Blocks.Begin -> block.inner.trimIndent().split('\n').map { IdLinks.plainText(it.trimEnd()) }.filter { it.isNotBlank() }
                is Blocks.Drawer -> emptyList()
                is Blocks.Transclude -> transclude(block)?.let { lines(it, Int.MAX_VALUE) }
                    ?: listOf("↪ " + (Links.find(block.text).firstOrNull()?.shown ?: block.link))
            }
        }
        return if (lines.size > max) lines.take(max - 1) + "…" else lines
    }

    private fun textLine(line: String): String? {
        if (line.isBlank() || KEYWORD.matches(line)) return null
        val item = ITEM.matchEntire(line) ?: return IdLinks.plainText(InternalLinks.TARGET.replace(line, "").trim()).ifEmpty { null }
        val (indent, box, text) = item.destructured
        // Sub-items a little further in, as written
        val depth = indent.replace("\t", "  ").length / 2
        val mark = when (box) {
            "" -> "•"
            "x", "X" -> "☑"
            else -> "☐"
        }
        return "  ".repeat(depth) + mark + " " + IdLinks.plainText(InternalLinks.TARGET.replace(text, "").trim())
    }
}
