package io.github.yighy.orgwhal.core

/**
 * A note's content split into what shows differently: plain text, tables, blocks
 * (#+BEGIN_SRC, #+BEGIN_QUOTE…), drawers (:LOGBOOK:…:END:, folded) and content shown from
 * another note (#+transclude:).
 * Each part has its [Block.range] of lines in the content.
 */
object Blocks {

    sealed interface Block {
        val range: IntRange
    }

    /** Text as written, starting at [range]'s start. */
    data class Text(override val range: IntRange, val text: String) : Block

    /**
     * A table's rows (a null row is a rule), with its #+NAME line above and #+TBLFM lines under
     * it; [range] covers them all, the table's own lines are from [tableStart] to [tableEnd]
     * (exclusive).
     */
    data class Table(
        override val range: IntRange,
        val rows: List<List<String>?>,
        val formulaLines: List<String> = emptyList(),
        val tableEnd: Int = range.last + 1,
        val name: String? = null,
        val tableStart: Int = range.first,
    ) : Block {
        val formulas: List<String> get() = TableFormulas.formulas(formulaLines)

        val columns: Int get() = rows.maxOf { it?.size ?: 0 }

        /** Rows above the first rule are the header, when there are rows below it. */
        val headerRows: Int
            get() {
                val rule = rows.indexOf(null)
                return if (rule > 0 && rows.drop(rule).any { it != null }) rule else 0
            }
    }

    /** #+BEGIN_[kind] … #+END_[kind] (named by a #+NAME line above); [inner] starts at [innerStart] in the content. */
    data class Begin(
        override val range: IntRange,
        val kind: String,
        val args: String,
        val inner: String,
        val innerStart: Int,
        val name: String? = null,
    ) : Block {
        /** Source and example blocks show as code, as do blocks of an unknown kind. */
        val isCode: Boolean get() = kind !in setOf("QUOTE", "VERSE", "CENTER")
        val language: String? get() = args.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { kind == "SRC" && it.isNotEmpty() }
    }

    /** :[name]: … :END:; [inner] starts at [innerStart] in the content. */
    data class Drawer(override val range: IntRange, val name: String, val inner: String, val innerStart: Int) : Block

    /** `#+transclude: [[link]] options` ([text] as written): another note's content shows there. */
    data class Transclude(override val range: IntRange, val text: String, val link: String, val options: String) : Block

    private val TRANSCLUDE = Regex("""^[ 	]*#\+transclude:[ 	]*\[\[([^\]]+)](?:\[[^\]]*])?][ 	]*(.*)$""", RegexOption.IGNORE_CASE)
    private val BEGIN = Regex("""^[ \t]*#\+BEGIN_(\S+)(.*)$""", RegexOption.IGNORE_CASE)
    private val DRAWER_START = Regex("""^[ \t]*:([A-Za-z][\w-]*):[ \t]*$""")
    private val DRAWER_END = Regex("""^[ \t]*:END:[ \t]*$""", RegexOption.IGNORE_CASE)
    private val TABLE_LINE = Regex("""^[ \t]*\|.*$""")
    private val TBLFM = Regex("""^[ \t]*#\+TBLFM:.*$""", RegexOption.IGNORE_CASE)
    private val NAME = Regex("""^[ \t]*#\+NAME:[ \t]*(\S.*?)[ \t]*$""", RegexOption.IGNORE_CASE)
    private val RULE = Regex("""^\|[-+:| ]*-[-+:| ]*\|?$""")

    fun parse(text: String): List<Block> {
        val split = Lines(text)
        val starts = split.starts
        val lines = split.lines
        fun end(line: Int) = split.end(line)

        val blocks = mutableListOf<Block>()
        var textFrom: Int? = null
        fun flushText(before: Int) {
            val from = textFrom ?: return
            textFrom = null
            val raw = text.substring(starts[from], starts[before] - 1).trimEnd()
            if (raw.isNotBlank()) blocks += Text(starts[from] until starts[from] + raw.length, raw)
        }

        /** Line closing the #+BEGIN block on line [i], if it is one. */
        fun blockEnd(i: Int): Int? {
            val begin = BEGIN.matchEntire(lines[i]) ?: return null
            val close = Regex("""^[ \t]*#\+END_${Regex.escape(begin.groupValues[1])}[ \t]*$""", RegexOption.IGNORE_CASE)
            return (i + 1 until lines.size).firstOrNull { close.matches(lines[it]) }
        }

        // #+NAME line naming the table or block right under it
        var name: String? = null
        var nameStart = 0

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val named = NAME.matchEntire(line)
            if (named != null && i + 1 < lines.size && (TABLE_LINE.matches(lines[i + 1]) || blockEnd(i + 1) != null)) {
                flushText(i)
                name = named.groupValues[1]
                nameStart = starts[i]
                i++
                continue
            }
            val transclude = TRANSCLUDE.matchEntire(line)
            if (transclude != null) {
                flushText(i)
                blocks += Transclude(starts[i]..end(i), line.trim(), transclude.groupValues[1], transclude.groupValues[2].trim())
                name = null
                i++
                continue
            }
            val begin = BEGIN.matchEntire(line)
            if (begin != null) {
                val kind = begin.groupValues[1].uppercase()
                val last = blockEnd(i)
                if (last != null) {
                    flushText(i)
                    val innerStart = if (last > i + 1) starts[i + 1] else starts[last]
                    val inner = if (last > i + 1) text.substring(starts[i + 1], starts[last] - 1) else ""
                    blocks += Begin((if (name != null) nameStart else starts[i])..end(last), kind, begin.groupValues[2], inner, innerStart, name)
                    name = null
                    i = last + 1
                    continue
                }
            }
            val drawer = DRAWER_START.matchEntire(line)
            if (drawer != null && !drawer.groupValues[1].equals("END", ignoreCase = true)) {
                val last = (i + 1 until lines.size).firstOrNull { DRAWER_END.matches(lines[it]) }
                if (last != null) {
                    flushText(i)
                    val innerStart = if (last > i + 1) starts[i + 1] else starts[last]
                    val inner = if (last > i + 1) text.substring(starts[i + 1], starts[last] - 1) else ""
                    blocks += Drawer(starts[i]..end(last), drawer.groupValues[1].uppercase(), inner, innerStart)
                    i = last + 1
                    continue
                }
            }
            if (TABLE_LINE.matches(line)) {
                flushText(i)
                var last = i
                while (last + 1 < lines.size && TABLE_LINE.matches(lines[last + 1])) last++
                val rows = (i..last).map { tableRow(lines[it]) }
                // Its formulas, on the lines right under it
                var formulasEnd = last
                while (formulasEnd + 1 < lines.size && TBLFM.matches(lines[formulasEnd + 1])) formulasEnd++
                val formulaLines = (last + 1..formulasEnd).map { lines[it] }
                blocks += Table((if (name != null) nameStart else starts[i])..end(formulasEnd), rows, formulaLines, end(last) + 1, name, starts[i])
                name = null
                i = formulasEnd + 1
                continue
            }
            if (textFrom == null) textFrom = i
            i++
        }
        if (textFrom != null) {
            val from = textFrom!!
            val raw = text.substring(starts[from]).trimEnd()
            if (raw.isNotBlank()) blocks += Text(starts[from] until starts[from] + raw.length, raw)
        }
        return blocks
    }

    /** A new value for a table field: the [table]th table of a note (from 0), its [line] and [column] (from 0). */
    data class CellEdit(val table: Int, val line: Int, val column: Int, val value: String)

    /** [text] with [edit] made: the field set, its table realigned. Unchanged if the table is not there. */
    fun withCell(text: String, edit: CellEdit): String {
        val table = parse(text).filterIsInstance<Table>().getOrNull(edit.table) ?: return text
        val lines = text.substring(table.tableStart, table.tableEnd).split('\n')
        val updated = TableFormulas.setCell(lines, edit.line, edit.column, edit.value)
        return text.replaceRange(table.tableStart, table.tableEnd, updated.joinToString("\n"))
    }

    /** A row or column put into a table or taken out, next to a field. */
    enum class TableChange { RowAbove, RowBelow, ColumnLeft, ColumnRight, DeleteRow, DeleteColumn }

    /**
     * [text] with [change] made to its [table]th table (from 0), next to the field at [line] and
     * [column] (from 0): realigned, its formulas' references moved past the row or column put in
     * or taken out, as Emacs does.
     */
    fun withTableChange(text: String, table: Int, line: Int, column: Int, change: TableChange): String {
        val block = parse(text).filterIsInstance<Table>().getOrNull(table) ?: return text
        val lines = text.substring(block.tableStart, block.tableEnd).split('\n')
        val (updated, shift: (String) -> String) = when (change) {
            TableChange.RowAbove, TableChange.RowBelow -> {
                val (rows, number) = TableFormulas.insertRow(lines, line, below = change == TableChange.RowBelow)
                rows to { f: String -> TableFormulas.shiftRows(f, number, 1) }
            }
            TableChange.ColumnLeft, TableChange.ColumnRight -> {
                val right = change == TableChange.ColumnRight
                TableFormulas.insertColumn(lines, column, right) to { f: String -> TableFormulas.shiftColumns(f, column + if (right) 2 else 1, 1) }
            }
            TableChange.DeleteRow -> {
                val (rows, number) = TableFormulas.deleteRow(lines, line)
                rows to { f: String -> TableFormulas.shiftRows(f, number + 1, -1) }
            }
            TableChange.DeleteColumn ->
                TableFormulas.deleteColumn(lines, column) to { f: String -> TableFormulas.shiftColumns(f, column + 2, -1) }
        }
        // The #+TBLFM lines, after the table's own
        val formulas = text.substring(block.tableEnd, block.range.last + 1).split('\n')
            .joinToString("\n") { if (TBLFM.matches(it)) shift(it) else it }
        return text.replaceRange(block.tableStart, block.range.last + 1, updated.joinToString("\n") + formulas)
    }

    /** Whether [text] shows nothing: blank, or only a logbook (shown as the clock's time). */
    fun showsNothing(text: String): Boolean = parse(text).all { it is Drawer && it.name == "LOGBOOK" }

    /** The table named [name] in [text] (#+NAME: name), or its first table if [name] is null: its lines and formulas. */
    fun tableWithFormulas(text: String, name: String?): Pair<List<String>, List<String>>? {
        val table = parse(text).filterIsInstance<Table>().firstOrNull { name == null || it.name == name } ?: return null
        return text.substring(table.tableStart, table.tableEnd).split('\n') to table.formulas
    }

    /** [text] with each of its tables that has formulas recalculated; [remote] as for [TableFormulas.recalculate]. */
    fun recalculated(text: String, remote: (String) -> List<String>?): String {
        var out = text
        // From the last table, so the ones above keep their places
        parse(text).filterIsInstance<Table>().filter { it.formulas.isNotEmpty() }.asReversed().forEach { table ->
            val lines = text.substring(table.tableStart, table.tableEnd).split('\n')
            val result = TableFormulas.recalculate(lines, table.formulas, remote)
            if (result.lines != lines) out = out.replaceRange(table.tableStart, table.tableEnd, result.lines.joinToString("\n"))
        }
        return out
    }

    /** Lines of the table named [name] in [text] (#+NAME: name), or of its first table if [name] is null. */
    fun tableLines(text: String, name: String?): List<String>? {
        val table = parse(text).filterIsInstance<Table>().firstOrNull { name == null || it.name == name } ?: return null
        return text.substring(table.tableStart, table.tableEnd).split('\n')
    }

    /**
     * Columns of numbers, which align right as Emacs writes them: those where at least half the
     * fields filled are numbers.
     */
    fun numericColumns(rows: List<List<String>>, columns: Int): List<Boolean> = (0 until columns).map { c ->
        val cells = rows.map { it.getOrElse(c) { "" } }.filter { it.isNotEmpty() }
        cells.isNotEmpty() && cells.count { it.toDoubleOrNull() != null } * 2 >= cells.size
    }

    /** A table line's fields, or null for a rule (|---+---|). */
    fun tableRow(line: String): List<String>? {
        val t = line.trim()
        if (RULE.matches(t)) return null
        return t.removePrefix("|").let { if (it.endsWith("|")) it.dropLast(1) else it }.split('|').map { it.trim() }
    }
}
