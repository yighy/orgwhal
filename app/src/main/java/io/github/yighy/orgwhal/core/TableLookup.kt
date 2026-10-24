package io.github.yighy.orgwhal.core

/**
 * The tables remote() reads, for one state of the notes: each note read once for its named tables
 * (#+NAME) and its first one (by its ID), and what is worked out from them kept, so that showing
 * a note again (scrolling back to it) or saving what it shows does not work it out again.
 *
 * [recalculate]: remote tables with formulas give their recalculated values, else as written.
 */
class TableLookup(private val notes: List<OrgNote>, private val recalculate: Boolean) {

    private val named: Map<String, Pair<List<String>, List<String>>> by lazy {
        val tables = HashMap<String, Pair<List<String>, List<String>>>()
        notes.forEach { note ->
            if (!note.content.contains("|")) return@forEach
            Blocks.parse(note.content).filterIsInstance<Blocks.Table>().forEach { table ->
                table.name?.let { tables.putIfAbsent(it, lines(note.content, table)) }
            }
        }
        tables
    }
    private val byId: Map<String, Pair<List<String>, List<String>>> by lazy {
        notes.mapNotNull { note ->
            val id = note.id ?: return@mapNotNull null
            if (!note.content.contains("|")) return@mapNotNull null
            Blocks.parse(note.content).filterIsInstance<Blocks.Table>().firstOrNull()?.let { id to lines(note.content, it) }
        }.toMap()
    }
    private val fresh = HashMap<String, List<String>?>()
    private val results = HashMap<Triple<String, List<String>, List<String>>, TableFormulas.Result>()

    /** remote() as seen from a note with [content]: its own tables first, then the others'. */
    fun from(content: String): Remote = Remote(content)

    inner class Remote(private val content: String) : (String) -> List<String>? {
        private val own: Map<String, Pair<List<String>, List<String>>> by lazy {
            if (!content.contains("|")) emptyMap()
            else Blocks.parse(content).filterIsInstance<Blocks.Table>().mapNotNull { t -> t.name?.let { it to lines(content, t) } }.toMap()
        }

        override fun invoke(name: String): List<String>? {
            own[name]?.let { return fresh(it, REMOTE_DEPTH, withOwn = true) }
            if (name in fresh) return fresh[name]
            // Kept for every note: what it reads in turn is not looked for in this one
            val found = (named[name] ?: byId[name])?.let { fresh(it, REMOTE_DEPTH, withOwn = false) }
            fresh[name] = found
            return found
        }

        /** [lines] recalculated with [formulas], as once already for this note if so. */
        fun recalculate(lines: List<String>, formulas: List<String>): TableFormulas.Result =
            results.getOrPut(Triple(content, lines, formulas)) { TableFormulas.recalculate(lines, formulas, this) }

        private fun fresh(table: Pair<List<String>, List<String>>, depth: Int, withOwn: Boolean): List<String> {
            val (lines, formulas) = table
            if (!recalculate || formulas.isEmpty() || depth == 0) return lines
            return TableFormulas.recalculate(lines, formulas) { name ->
                ((if (withOwn) own[name] else null) ?: named[name] ?: byId[name])?.let { fresh(it, depth - 1, withOwn) }
            }.lines
        }
    }

    private fun lines(text: String, table: Blocks.Table) = text.substring(table.tableStart, table.tableEnd).split('\n') to table.formulas

    private companion object {
        /** How far remote tables reading remote tables are recalculated, as [TableFormulas.freshRemote]. */
        const val REMOTE_DEPTH = 5
    }
}
