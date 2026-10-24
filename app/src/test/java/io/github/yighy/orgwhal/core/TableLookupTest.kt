package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TableLookupTest {

    private val doc = OrgDocument.parse(
        """
        * Budget
        #+NAME: budget
        | A   | 10 |
        | B   | 15 |
        | Sum |  0 |
        #+TBLFM: @>${'$'}2=vsum(@1..@-1)
        * By id
        :PROPERTIES:
        :ID:       abc
        :END:
        | 7 |
        * Loop
        #+NAME: a
        | 1 |
        #+TBLFM: @1${'$'}1=remote(a, @1${'$'}1)+1
        """.trimIndent() + "\n",
    )
    private val notes = OrgNote.fromDocument(doc, "uri", "t.org")

    @Test
    fun remoteTables() {
        val note = "| Total | 0 |\n#+TBLFM: @1\$2=remote(budget, @>\$2)"
        // Recalculated first: the budget's sum counts its rows again
        assertEquals("| Total | 25 |\n#+TBLFM: @1\$2=remote(budget, @>\$2)", Blocks.recalculated(note, TableLookup(notes, true).from(note)))
        // As written, the sum stays the file's
        assertEquals("| Total | 0 |\n#+TBLFM: @1\$2=remote(budget, @>\$2)", Blocks.recalculated(note, TableLookup(notes, false).from(note)))
        // A note's first table by its ID; nothing for an unknown name
        val lookup = TableLookup(notes, true).from("")
        assertEquals(listOf("| 7 |"), lookup("abc"))
        assertNull(lookup("nothing"))
        // Tables reading each other stop after a few rounds instead of looping
        assertEquals(listOf("| 6 |"), lookup("a"))
    }

    @Test
    fun ownTablesFirstAndKeptResults() {
        val note = "#+NAME: budget\n| Mine | 3 |\n\n| Total | 0 |\n#+TBLFM: @1\$2=remote(budget, @1\$2)"
        val remote = TableLookup(notes, true).from(note)
        assertEquals("#+NAME: budget\n| Mine | 3 |\n\n| Total | 3 |\n#+TBLFM: @1\$2=remote(budget, @1\$2)", Blocks.recalculated(note, remote))
        // Worked out once: asked again, the same result
        val lines = listOf("| Total | 0 |")
        val formulas = listOf("@1\$2=remote(budget, @1\$2)")
        assertSame(remote.recalculate(lines, formulas), remote.recalculate(lines, formulas))
    }
}
