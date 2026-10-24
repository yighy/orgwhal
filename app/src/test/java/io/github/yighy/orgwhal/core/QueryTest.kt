package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class QueryTest {

    private val doc = OrgDocument.parse(
        """
        #+TODO: TODO NEXT WAITING | DONE CANCELLED
        * TODO Write report                                        :work:
          SCHEDULED: <2026-10-05 Mon 09:00>
        ** NEXT [#A] Collect numbers
           DEADLINE: <2026-10-07 Wed>
        * DONE Pay rent                                            :home:
          CLOSED: [2026-10-04 Sun 10:00]
        * Shopping list                                            :home:
          milk and "fresh" bread
        * WAITING Call back
          SCHEDULED: <2026-10-01 Thu>
        """.trimIndent() + "\n",
    )
    private val notes = OrgNote.fromDocument(doc, "uri", "work.org")
    private val inherited = NoteTree.inheritedTags(notes)

    private val ctx = SearchQuery.Context(
        now = LocalDateTime.of(2026, 10, 5, 12, 0), // Monday noon
        keywords = { doc.keywords },
        notebookName = { "work" },
        inheritedTags = { inherited[it.index] },
    )

    private fun titles(query: String) = SearchQuery.parse(query).apply(notes, ctx).map { it.title }

    @Test
    fun tableFields() {
        val doc = OrgDocument.parse(
            """
            * Riz
            | Stock | 0 |
            | Prix  | 3 |
            * Pâtes
            | Article  | Stock | Lieu     |
            |----------+-------+----------|
            | Penne    |     4 | placard  |
            | Spaghett |   0,0 | cave     |
            * Farine
            :DATA:
            | Stock | 12 |
            :END:
            * Sel
            | Prix | 0 |
            * Huile
            | En stock | 2 |
            """.trimIndent() + "\n",
        )
        val stock = OrgNote.fromDocument(doc, "uri", "stock.org")
        fun found(query: String) = SearchQuery.parse(query).apply(stock, ctx).map { it.title }
        assertEquals(listOf("Riz", "Pâtes"), found("col.stock.eq.0"))
        assertEquals(listOf("Riz", "Pâtes"), found("col.STOCK.lt.1"))
        // One row is enough: Pâtes has 4 too
        assertEquals(listOf("Pâtes", "Farine"), found("col.stock.gt.3"))
        assertEquals(listOf("Farine", "Sel", "Huile"), found(".col.stock.eq.0"))
        assertEquals(listOf("Pâtes"), found("col.lieu.eq.Cave"))
        assertEquals(listOf("Huile"), found("col.\"en stock\".ge.2"))
    }

    @Test
    fun eventsCreatedAndAgendaDays() {
        val doc = OrgDocument.parse(
            """
            * Meeting
              Room 4 <2026-10-06 Tue 14:00>
            * Old idea
              :PROPERTIES:
              :CREATED:  [2026-09-01 Tue 10:00]
              :END:
            * Plain
            """.trimIndent() + "\n",
        )
        val notes = OrgNote.fromDocument(doc, "u", "n.org")
        val ctx = ctx.copy(keywords = { doc.keywords }, inheritedTags = { emptyList() })
        fun titles(query: String) = SearchQuery.parse(query).apply(notes, ctx).map { it.title }

        assertEquals(listOf("Meeting"), titles("e.tomorrow"))
        assertEquals(listOf("Old idea", "Plain"), titles("e.none"))
        assertEquals(listOf("Old idea"), titles("cr.le.-1m"))
        assertEquals(listOf("Old idea", "Meeting", "Plain"), titles("o.cr"))
        // ad. does not filter, it shows the results as an agenda
        assertEquals(7, SearchQuery.parse(".it.done ad.7").agendaDays)
        assertEquals(listOf("Meeting", "Old idea", "Plain"), titles("ad.7"))

        val agenda = Agenda.build(notes, ctx.now.toLocalDate(), 7)
        assertEquals(listOf(Agenda.Kind.Event), agenda.days[1].entries.map { it.kind })
    }

    @Test
    fun states() {
        assertEquals(listOf("Write report"), titles("i.todo"))
        assertEquals(listOf("Write report", "Collect numbers", "Call back"), titles("it.todo"))
        assertEquals(listOf("Pay rent"), titles("it.done"))
        assertEquals(listOf("Shopping list"), titles("it.none"))
        assertEquals(listOf("Collect numbers", "Pay rent", "Shopping list", "Call back"), titles(".i.todo"))
    }

    @Test
    fun tagsNotebookPriority() {
        // Inherited from "Write report"
        assertEquals(listOf("Write report", "Collect numbers"), titles("t.work"))
        assertEquals(listOf("Write report"), titles("tn.work"))
        assertEquals(notes.size, titles("b.work").size)
        assertTrue(titles("b.other").isEmpty())
        assertEquals(listOf("Collect numbers"), titles("p.a"))
        // Unset priority counts as B, but not for ps.
        assertEquals(4, titles("p.b").size)
        assertTrue(titles("ps.b").isEmpty())
    }

    @Test
    fun times() {
        // Scheduled by today: today 09:00 and the overdue one
        assertEquals(listOf("Write report", "Call back"), titles("s.today"))
        assertEquals(listOf("Write report"), titles("s.eq.today"))
        assertEquals(listOf("Call back"), titles("s.lt.today"))
        assertEquals(listOf("Collect numbers"), titles("d.le.3d"))
        assertTrue(titles("d.le.1d").isEmpty())
        assertEquals(listOf("Pay rent"), titles("c.yesterday"))
        assertEquals(listOf("Collect numbers", "Pay rent", "Shopping list"), titles("s.none"))
        assertEquals(listOf("Write report", "Call back"), titles("s.le.now"))
    }

    @Test
    fun textAndLogic() {
        assertEquals(listOf("Shopping list"), titles("milk"))
        assertEquals(listOf("Shopping list"), titles("\"and \\\"fresh\\\"\""))
        assertEquals(listOf("Write report", "Pay rent"), titles("i.todo or i.done"))
        // AND binds tighter than OR
        assertEquals(listOf("Write report", "Shopping list"), titles("i.todo t.work or t.home it.none"))
        assertEquals(listOf("Write report", "Collect numbers"), titles("t.work (i.todo or i.next)"))
        // Unknown prefix is plain text
        assertTrue(titles("x.y").isEmpty())
    }

    @Test
    fun sortOrders() {
        assertEquals(listOf("Call back", "Write report"), titles("s.today o.s"))
        assertEquals(listOf("Write report", "Call back"), titles("s.today .o.s"))
        assertEquals(listOf("Call back", "Collect numbers", "Pay rent", "Shopping list", "Write report"), titles("o.title"))
        // Priority A first; unset ones count as B
        assertEquals("Collect numbers", titles("o.p").first())
    }
}
