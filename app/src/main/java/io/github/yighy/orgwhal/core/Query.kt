package io.github.yighy.orgwhal.core

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Search queries with Orgzly's syntax, e.g. `i.todo t.work s.le.today o.p`.
 *
 * - `b.NAME` notebook, `i.STATE` state keyword, `it.todo|done|none` state type
 * - `t.TAG` tag (own or inherited), `tn.TAG` own tag
 * - `p.X` priority (unset counts as B), `ps.X` priority that is set
 * - `s.`, `d.`, `c.`, `e.`, `cr.` scheduled, deadline, closed, event (an active timestamp in the
 *   title or content), created (CREATED property): `s.today`, `d.le.3d`, `c.eq.yesterday`, `s.none`;
 *   relations eq ne lt le gt ge (default le, eq for closed); intervals now, today, tomorrow,
 *   yesterday, none, or Nh Nd Nw Nm Ny
 * - `col.NAME.REL.VALUE` a table field: under a column headed NAME (in a table with a header), or
 *   else right of a field NAME (`| Stock | 0 |`); one row matching is enough. Numbers compare as
 *   numbers, other values as text; NAME ignores case and accents; quotes keep spaces
 *   (`col."en stock".eq.0`)
 * - `ad.N` shows the results as an agenda of N days (it does not filter)
 * - `.` before a condition negates it; `and` (default), `or`, parentheses
 * - `o.FIELD` sort (notebook, title, scheduled, deadline, closed, event, created, priority, state,
 *   position), `.o.` descending
 * - anything else is text searched in titles, content and tags; "quoted text" keeps spaces
 */
class SearchQuery private constructor(
    private val condition: Condition?,
    private val orders: List<Order>,
    /** Days of agenda to show the results as (`ad.N`), or null for a list. */
    val agendaDays: Int?,
) {

    /** What a note is matched against, besides the note itself. */
    data class Context(
        val now: LocalDateTime,
        val keywords: (OrgNote) -> Keywords,
        val notebookName: (OrgNote) -> String,
        val inheritedTags: (OrgNote) -> List<String>,
    )

    val isEmpty: Boolean get() = condition == null && orders.isEmpty()

    fun matches(note: OrgNote, ctx: Context): Boolean = condition?.matches(note, ctx) ?: true

    /** Matching notes, sorted by the query's `o.` orders (document order otherwise). */
    fun apply(notes: List<OrgNote>, ctx: Context): List<OrgNote> {
        val matching = notes.filter { matches(it, ctx) }
        if (orders.isEmpty()) return matching
        val comparator = orders.map { it.comparator(ctx) }.reduce { a, b -> a.then(b) }
        return matching.sortedWith(comparator)
    }

    // ---- Conditions ----

    sealed interface Condition {
        fun matches(note: OrgNote, ctx: Context): Boolean
    }

    private data class Not(val operand: Condition) : Condition {
        override fun matches(note: OrgNote, ctx: Context) = !operand.matches(note, ctx)
    }

    private data class And(val operands: List<Condition>) : Condition {
        override fun matches(note: OrgNote, ctx: Context) = operands.all { it.matches(note, ctx) }
    }

    private data class Or(val operands: List<Condition>) : Condition {
        override fun matches(note: OrgNote, ctx: Context) = operands.any { it.matches(note, ctx) }
    }

    private data class Text(val text: String) : Condition {
        override fun matches(note: OrgNote, ctx: Context) =
            note.title.contains(text, true) || note.content.contains(text, true) ||
                note.tags.any { it.contains(text, true) }
    }

    private data class InNotebook(val name: String) : Condition {
        override fun matches(note: OrgNote, ctx: Context) = ctx.notebookName(note).equals(name, true)
    }

    private data class HasState(val state: String) : Condition {
        override fun matches(note: OrgNote, ctx: Context) = (note.state ?: "").equals(state, true)
    }

    enum class StateType { TODO, DONE, NONE }

    private data class HasStateType(val type: StateType) : Condition {
        override fun matches(note: OrgNote, ctx: Context): Boolean {
            val state = note.state ?: return type == StateType.NONE
            val keywords = ctx.keywords(note)
            return when (type) {
                StateType.TODO -> state in keywords.todo
                StateType.DONE -> state in keywords.done
                StateType.NONE -> false
            }
        }
    }

    private data class HasPriority(val priority: String, val onlySet: Boolean) : Condition {
        override fun matches(note: OrgNote, ctx: Context): Boolean {
            val p = note.priority ?: if (onlySet) return false else DEFAULT_PRIORITY
            return p.equals(priority, true)
        }
    }

    private data class HasTag(val tag: String, val ownOnly: Boolean) : Condition {
        override fun matches(note: OrgNote, ctx: Context): Boolean {
            val tags = if (ownOnly) note.tags else note.tags + ctx.inheritedTags(note)
            return tags.any { it.contains(tag, true) }
        }
    }

    /** A table field named [name] compared to [value] (see `col.` above). */
    private data class Field(val name: String, val relation: Relation, val value: String) : Condition {
        override fun matches(note: OrgNote, ctx: Context): Boolean =
            fieldValues(note.content, name).any { compare(it, relation, value) }
    }

    enum class TimeField { Scheduled, Deadline, Closed, Event, Created, Modified }
    enum class Relation { EQ, NE, LT, LE, GT, GE }

    /** A planning time compared to a moment relative to now, as Orgzly does. */
    private data class Time(val field: TimeField, val relation: Relation, val interval: Interval) : Condition {
        override fun matches(note: OrgNote, ctx: Context): Boolean {
            val timestamps = timestamps(note, field)
            if (interval.none) return timestamps.isEmpty()
            // A note with several events matches if one of them does
            return timestamps.any { matches(it, ctx) }
        }

        private fun matches(timestamp: String, ctx: Context): Boolean {
            val parts = OrgTime.parts(timestamp) ?: return false
            val at = parts.date.atTime(parts.time ?: LocalTime.MIDNIGHT)

            val from = interval.start(ctx.now)
            val to = interval.end(ctx.now)
            return when (relation) {
                Relation.EQ -> at >= from && at < to
                Relation.NE -> at < from || at >= to
                Relation.LT -> at < from
                Relation.LE -> at < to
                Relation.GT -> at >= to
                Relation.GE -> at >= from
            }
        }
    }

    /** "today", "3d", "now"…: [start] and [end] bound the matching period. */
    data class Interval(val amount: Long, val unit: ChronoUnit?, val none: Boolean = false, val now: Boolean = false) {
        fun start(now: LocalDateTime): LocalDateTime = when {
            this.now -> now
            unit == ChronoUnit.HOURS -> now.plusHours(amount).truncatedTo(ChronoUnit.HOURS)
            else -> now.toLocalDate().plus(amount, unit).atStartOfDay()
        }

        fun end(now: LocalDateTime): LocalDateTime = when {
            this.now -> now
            unit == ChronoUnit.HOURS -> start(now).plusHours(1)
            else -> start(now).plusDays(1)
        }

        companion object {
            private val REGEX = Regex("""^([-+]?\d+)([hdwmy])$""")

            fun parse(str: String): Interval? = when (str.lowercase()) {
                "none", "no" -> Interval(0, null, none = true)
                "now" -> Interval(0, null, now = true)
                "today", "tod" -> Interval(0, ChronoUnit.DAYS)
                "tomorrow", "tmrw", "tom" -> Interval(1, ChronoUnit.DAYS)
                "yesterday" -> Interval(-1, ChronoUnit.DAYS)
                else -> REGEX.find(str)?.let { m ->
                    val unit = when (m.groupValues[2]) {
                        "h" -> ChronoUnit.HOURS
                        "d" -> ChronoUnit.DAYS
                        "w" -> ChronoUnit.WEEKS
                        "m" -> ChronoUnit.MONTHS
                        else -> ChronoUnit.YEARS
                    }
                    Interval(m.groupValues[1].toLong(), unit)
                }
            }
        }
    }

    // ---- Sort orders ----

    enum class OrderField { Notebook, Title, Scheduled, Deadline, Closed, Event, Created, Modified, Priority, State, Position }

    private data class Order(val field: OrderField, val descending: Boolean) {
        fun comparator(ctx: Context): Comparator<OrgNote> {
            fun time(t: String?) = OrgTime.parts(t)?.let { it.date.atTime(it.time ?: LocalTime.MIDNIGHT) }
            // Notes without the time go last, whichever way
            fun byTime(key: (OrgNote) -> LocalDateTime?): Comparator<OrgNote> =
                compareBy(nullsLast(if (descending) reverseOrder() else naturalOrder()), key)
            val timed = when (field) {
                OrderField.Scheduled -> byTime { time(it.scheduled) }
                OrderField.Deadline -> byTime { time(it.deadline) }
                OrderField.Closed -> byTime { time(it.closed) }
                // The earliest event
                OrderField.Event -> byTime { note -> timestamps(note, TimeField.Event).mapNotNull(::time).minOrNull() }
                OrderField.Created -> byTime { time(it.property(CREATED_PROPERTY)) }
                OrderField.Modified -> byTime { time(it.property(MODIFIED_PROPERTY)) }
                else -> null
            }
            if (timed != null) return timed
            val base: Comparator<OrgNote> = when (field) {
                OrderField.Notebook -> compareBy { ctx.notebookName(it).lowercase() }
                OrderField.Title -> compareBy { it.title.lowercase() }
                OrderField.Priority -> compareBy { (it.priority ?: DEFAULT_PRIORITY).uppercase() }
                OrderField.State -> compareBy { note ->
                    val keywords = ctx.keywords(note).all
                    note.state?.let { keywords.indexOf(it).takeIf { i -> i >= 0 } } ?: Int.MAX_VALUE
                }
                OrderField.Position -> compareBy<OrgNote>({ it.fileUri }, { it.index })
                else -> error("Sorted by time: $field")
            }
            return if (descending) base.reversed() else base
        }
    }

    companion object {
        /** Org's default priority, used for notes without one. */
        const val DEFAULT_PRIORITY = "B"

        /** Property holding when a note was created (Orgzly's default). */
        const val CREATED_PROPERTY = "CREATED"

        /** When a note was last changed, as Emacs users keep it (set by Orgwhal when so set). */
        const val MODIFIED_PROPERTY = "LAST_MODIFIED"

        private val AGENDA_DAYS = Regex("""^ad\.(\d{1,3})$""")

        /** The note's times for [field]: one planning time, its events, or its CREATED property. */
        private fun timestamps(note: OrgNote, field: TimeField): List<String> = when (field) {
            TimeField.Scheduled -> listOfNotNull(note.scheduled?.takeIf { it.startsWith("<") })
            TimeField.Deadline -> listOfNotNull(note.deadline?.takeIf { it.startsWith("<") })
            TimeField.Closed -> listOfNotNull(note.closed)
            TimeField.Event -> OrgTime.events(note)
            TimeField.Created -> listOfNotNull(note.property(CREATED_PROPERTY))
            TimeField.Modified -> listOfNotNull(note.property(MODIFIED_PROPERTY))
        }

        // A quoted part can be inside a token (col."en stock".eq.0)
        private val TOKEN = Regex(""""[^"\\]*(?:\\.[^"\\]*)*"|[^")(\s]*"[^"\\]*(?:\\.[^"\\]*)*"[^")(\s]*|\(|\)|[^")(\s]+""")
        private val FIELD = Regex("""^col\.(?:"((?:[^"\\]|\\.)*)"|([^."]+))\.(eq|ne|lt|le|gt|ge)\.(.*)$""")
        private val TIME = Regex("""^(e|s|d|c|cr|mod)(?:\.(eq|ne|lt|le|gt|ge))?\.(.+)$""")
        private val ORDERS = listOf(
            Regex("""^o\.(?:notebook|book|b)$""") to OrderField.Notebook,
            Regex("""^o\.(?:title|t)$""") to OrderField.Title,
            Regex("""^o\.(?:scheduled|sched|s)$""") to OrderField.Scheduled,
            Regex("""^o\.(?:deadline|dead|d)$""") to OrderField.Deadline,
            Regex("""^o\.(?:closed|close|c)$""") to OrderField.Closed,
            Regex("""^o\.(?:event|e)$""") to OrderField.Event,
            Regex("""^o\.(?:created|cr)$""") to OrderField.Created,
            Regex("""^o\.(?:modified|mod)$""") to OrderField.Modified,
            Regex("""^o\.(?:priority|prio|pri|p)$""") to OrderField.Priority,
            Regex("""^o\.(?:state|st)$""") to OrderField.State,
            Regex("""^o\.(?:position|pos)$""") to OrderField.Position,
        )

        fun parse(query: String): SearchQuery {
            val all = TOKEN.findAll(query).map { it.value }.toList()
            // ad.N changes how results are shown, not which ones
            val agendaDays = all.firstNotNullOfOrNull { AGENDA_DAYS.matchEntire(it)?.groupValues?.get(1)?.toInt() }
            val tokens = all.filterNot { AGENDA_DAYS.matches(it) }
            val orders = mutableListOf<Order>()
            val parser = Parser(tokens, orders)
            return SearchQuery(parser.expression(), orders, agendaDays?.coerceIn(1, 366))
        }

        private fun unquote(s: String): String =
            if (s.length >= 2 && s.first() == '"' && s.last() == '"') s.substring(1, s.length - 1).replace("\\\"", "\"") else s

        /** Single token → condition, or null if it's a sort order (recorded) or empty. */
        private fun condition(token: String, orders: MutableList<Order>): Condition? {
            val negated = token.startsWith(".")
            val body = if (negated) token.substring(1) else token

            ORDERS.firstOrNull { it.first.matches(body) }?.let { (_, field) ->
                orders += Order(field, negated)
                return null
            }

            val plain: Condition? = when {
                body.startsWith("b.") -> InNotebook(unquote(body.substring(2)))
                body.startsWith("it.") -> when (body.substring(3).lowercase()) {
                    "todo" -> HasStateType(StateType.TODO)
                    "done" -> HasStateType(StateType.DONE)
                    "none" -> HasStateType(StateType.NONE)
                    else -> null
                }
                body.startsWith("i.") -> HasState(unquote(body.substring(2)))
                body.matches(Regex("^ps\\.[a-zA-Z]$")) -> HasPriority(body.substring(3), onlySet = true)
                body.matches(Regex("^p\\.[a-zA-Z]$")) -> HasPriority(body.substring(2), onlySet = false)
                body.startsWith("col.") -> FIELD.matchEntire(body)?.let { m ->
                    val name = m.groupValues[1].replace("\\\"", "\"").ifEmpty { m.groupValues[2] }
                    Field(name, relation(m.groupValues[3]), unquote(m.groupValues[4]))
                }
                body.startsWith("tn.") -> HasTag(unquote(body.substring(3)), ownOnly = true)
                body.startsWith("t.") -> HasTag(unquote(body.substring(2)), ownOnly = false)
                else -> TIME.find(body)?.let { m -> time(m) }
            }
            if (plain != null) return if (negated) Not(plain) else plain

            // Plain text (the dot is part of it if nothing matched)
            val text = unquote(token)
            return if (text.isNotEmpty()) Text(text) else null
        }

        private fun relation(name: String): Relation = when (name) {
            "eq" -> Relation.EQ
            "ne" -> Relation.NE
            "lt" -> Relation.LT
            "le" -> Relation.LE
            "gt" -> Relation.GT
            else -> Relation.GE
        }

        /** [text] as `col.` compares names: trimmed, lowercase, without accents. */
        fun fold(text: String): String =
            java.text.Normalizer.normalize(text.trim().lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        /** The names `col.` can find in [content]: header cells, and else first cells that are not numbers. */
        fun fieldNames(content: String): List<String> = Blocks.parse(content).flatMap { block ->
            when (block) {
                is Blocks.Drawer -> fieldNames(block.inner)
                is Blocks.Table -> {
                    val rows = block.rows.filterNotNull()
                    val names = if (block.headerRows > 0) {
                        block.rows.take(block.headerRows).filterNotNull().flatten()
                    } else {
                        rows.filter { it.size > 1 }.mapNotNull { it.firstOrNull() }
                    }
                    names.map { it.trim() }.filter { it.isNotEmpty() && number(it) == null }
                }
                else -> emptyList()
            }
        }

        /** The fields of [content]'s tables named [name] (tables in drawers too), as described for `col.`. */
        fun fieldValues(content: String, name: String): List<String> {
            val key = fold(name)
            return Blocks.parse(content).flatMap { block ->
                when (block) {
                    is Blocks.Drawer -> fieldValues(block.inner, name)
                    is Blocks.Table -> tableValues(block, key)
                    else -> emptyList()
                }
            }
        }

        private fun tableValues(table: Blocks.Table, key: String): List<String> {
            val rows = table.rows.filterNotNull()
            // Under a header: the column so headed
            if (table.headerRows > 0) {
                val header = table.rows.take(table.headerRows).filterNotNull()
                val column = header.firstNotNullOfOrNull { row -> row.indexOfFirst { fold(it) == key }.takeIf { it >= 0 } }
                if (column != null) return rows.drop(header.size).mapNotNull { it.getOrNull(column) }
            }
            // Else the field right of one so named
            return rows.mapNotNull { row -> row.indexOfFirst { fold(it) == key }.takeIf { it >= 0 }?.let { row.getOrNull(it + 1) } }
        }

        private fun number(text: String): Double? = text.replace(" ", "").replace(',', '.').toDoubleOrNull()

        private fun compare(field: String, relation: Relation, value: String): Boolean {
            val a = number(field)
            val b = number(value)
            val order = if (a != null && b != null) a.compareTo(b) else fold(field).compareTo(fold(value))
            return when (relation) {
                Relation.EQ -> order == 0
                Relation.NE -> order != 0
                Relation.LT -> order < 0
                Relation.LE -> order <= 0
                Relation.GT -> order > 0
                Relation.GE -> order >= 0
            }
        }

        private fun time(m: MatchResult): Condition? {
            val field = when (m.groupValues[1]) {
                "s" -> TimeField.Scheduled
                "d" -> TimeField.Deadline
                "c" -> TimeField.Closed
                "e" -> TimeField.Event
                "mod" -> TimeField.Modified
                else -> TimeField.Created
            }
            val relation = when (m.groupValues[2]) {
                "eq" -> Relation.EQ
                "ne" -> Relation.NE
                "lt" -> Relation.LT
                "le" -> Relation.LE
                "gt" -> Relation.GT
                "ge" -> Relation.GE
                // Orgzly's defaults: on that day for closed, by then for the others
                else -> if (field == TimeField.Closed) Relation.EQ else Relation.LE
            }
            val interval = Interval.parse(unquote(m.groupValues[3])) ?: return null
            return Time(field, relation, interval)
        }

        /** Same precedence as Orgzly: AND (also implicit) binds tighter than OR. */
        private class Parser(private val tokens: List<String>, private val orders: MutableList<Order>) {
            private var pos = 0

            fun expression(): Condition? {
                val alternatives = mutableListOf<Condition>()
                var current = mutableListOf<Condition>()
                while (pos < tokens.size) {
                    val token = tokens[pos++]
                    when {
                        token == "(" -> expression()?.let { current += it }
                        token == ")" -> break
                        token.equals("and", true) -> Unit
                        token.equals("or", true) -> {
                            if (current.isNotEmpty()) alternatives += group(current)
                            current = mutableListOf()
                        }
                        else -> condition(token, orders)?.let { current += it }
                    }
                }
                if (current.isNotEmpty()) alternatives += group(current)
                return when (alternatives.size) {
                    0 -> null
                    1 -> alternatives.single()
                    else -> Or(alternatives)
                }
            }

            private fun group(conditions: List<Condition>) = conditions.singleOrNull() ?: And(conditions)
        }
    }
}
