package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePreviewTest {

    @Test
    fun plainLinesOfTextListsAndTables() {
        val content = """
            :PROPERTIES:
            :ID: a1
            :END:
            Buy for *Sunday*, see [[id:b2][the recipe]].

            - [X] Rice
            - [ ] Chicken
              - thighs
            #+NAME: costs
            | Item    | Price |
            |---------+-------|
            | Rice    |   2.5 |
            #+TBLFM: @>$2=vsum(@I..@II)
        """.trimIndent()
        assertEquals(
            listOf(
                "Buy for Sunday, see the recipe.",
                "☑ Rice",
                "☐ Chicken",
                "  • thighs",
                "Item  ·  Price",
                "Rice  ·  2.5",
            ),
            NotePreview.lines(content),
        )
    }

    @Test
    fun longContentEndsWithEllipsis() {
        val content = (1..12).joinToString("\n") { "Line $it" }
        assertEquals(listOf("Line 1", "Line 2", "…"), NotePreview.lines(content, max = 3))
        assertEquals(emptyList<String>(), NotePreview.lines(":LOGBOOK:\nCLOCK: [2026-10-11 Sun 10:00]\n:END:"))
    }

    @Test
    fun transcludedContentShowsAsWrittenThereAndTargetsGo() {
        val content = "<<cap>>Le cap.\n#+transclude: [[reps]]\n#+transclude: [[id:X][Absente]]"
        assertEquals(
            listOf("Le cap.", "Lun  ·  20", "↪ Absente"),
            NotePreview.lines(content) { if (it.link == "reps") "| Lun | 20 |" else null },
        )
    }
}
