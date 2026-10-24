package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NoteTreeTest {

    private val doc = OrgDocument.parse(
        "#+TITLE: My notebook\n#+STARTUP: overview\nIntro text.\n\n* A\n** A1\n*** A1a\n** A2\n* B\n",
    )
    private val notes = OrgNote.fromDocument(doc, "uri", "file.org")

    @Test
    fun treeHelpers() {
        assertTrue(NoteTree.hasChildren(notes, 0))
        assertTrue(NoteTree.hasChildren(notes, 1))
        assertFalse(NoteTree.hasChildren(notes, 3))
        assertFalse(NoteTree.hasChildren(notes, 4))
        assertEquals(listOf("A", "A1"), NoteTree.ancestors(notes, 2).map { it.title })
        assertEquals(listOf("A"), NoteTree.ancestors(notes, 3).map { it.title })
        assertEquals(listOf("A", "B"), NoteTree.visible(notes) { it.title == "A" }.map { it.title })
        assertEquals(listOf("A", "A1", "A2", "B"), NoteTree.visible(notes) { it.title == "A1" }.map { it.title })
        assertEquals(listOf(emptyList(), listOf(1), listOf(1, 2), listOf(1), emptyList()), NoteTree.ancestorLevels(notes))

        // Level jump: "*" then "***"
        val jump = OrgNote.fromDocument(OrgDocument.parse("* A\n*** A1\n** A2\n"), "u", "f")
        assertEquals(listOf(emptyList(), listOf(1), listOf(1)), NoteTree.ancestorLevels(jump))
    }

    @Test
    fun directChildren() {
        assertEquals(listOf("A1", "A2"), NoteTree.children(notes, 0).map { it.title })
        assertEquals(listOf("A1a"), NoteTree.children(notes, 1).map { it.title })
        assertEquals(emptyList<String>(), NoteTree.children(notes, 4).map { it.title })
        // A level skipped (*** under *) is still a child
        val skipped = OrgNote.fromDocument(OrgDocument.parse("* P\n*** Deep\n** Next\n"), "u", "f")
        assertEquals(listOf("Deep", "Next"), NoteTree.children(skipped, 0).map { it.title })
    }

    @Test
    fun titlePaths() {
        val tasks = OrgNote.fromDocument(
            OrgDocument.parse("* Project [1/2]\n** DONE One\n** TODO Two\n*** Detail\n* Project\n* Notes\n"),
            "u",
            "f",
        )
        // Cookies left out; the same path twice is numbered
        val s = "\u0001"
        assertEquals(
            listOf("1${s}Project", "1${s}Project${s}One", "1${s}Project${s}Two", "1${s}Project${s}Two${s}Detail", "2${s}Project", "1${s}Notes"),
            NoteTree.titlePaths(tasks),
        )
    }

    @Test
    fun notebookInfo() {
        assertEquals("My notebook", Notebooks.title(doc, "file.org"))
        assertEquals("other", Notebooks.title(OrgDocument.parse("* x\n"), "other.org"))
        assertEquals("Intro text.", Notebooks.prefaceText(doc))
    }

    @Test
    fun retitlesOnlyTheTitleLine() {
        assertEquals("#+TITLE: New\nText", Notebooks.retitle("#+TITLE: Old\nText", "New"))
        assertEquals("#+title:   New\n", Notebooks.retitle("#+title:   Old  \n", "New"))
        assertNull(Notebooks.retitle("Just text", "New"))
    }

    @Test
    fun timestamps() {
        assertEquals("<2026-10-05 Mon 09:30>", OrgTime.build(LocalDate.of(2026, 10, 5), LocalTime.of(9, 30)))
        assertEquals("<2026-10-06 Tue +1w>", OrgTime.build(LocalDate.of(2026, 10, 6), null, "<2026-10-05 Mon 08:00 +1w>"))

        val parts = OrgTime.parts("<2026-10-05 Mon 09:30 +1d>")!!
        assertEquals(LocalDate.of(2026, 10, 5), parts.date)
        assertEquals(LocalTime.of(9, 30), parts.time)
        assertEquals("+1d", parts.repeater)
        assertNull(OrgTime.parts("not a date"))
    }

    @Test
    fun endTimes() {
        val day = LocalDate.of(2026, 10, 5)
        val range = OrgTime.build(day, LocalTime.of(9, 0), endTime = LocalTime.of(10, 30))
        assertEquals("<2026-10-05 Mon 09:00-10:30>", range)
        assertEquals(LocalTime.of(10, 30), OrgTime.parts(range)!!.endTime)
        // Kept when the date moves, dropped without a time
        assertEquals("<2026-10-06 Tue 09:00-10:30 +1w>", OrgTime.build(day.plusDays(1), LocalTime.of(9, 0), "<2026-10-05 Mon 09:00-10:30 +1w>"))
        assertEquals("<2026-10-06 Tue>", OrgTime.build(day.plusDays(1), null, range))
    }

    @Test
    fun repeaters() {
        val day = LocalDate.of(2026, 10, 5)
        assertEquals("<2026-10-05 Mon .+2w>", OrgTime.build(day, null, "<2026-10-05 Mon +1d>", ".+2w"))
        assertEquals("<2026-10-05 Mon>", OrgTime.build(day, null, "<2026-10-05 Mon +1d>", null))
        assertEquals("<2026-10-05 Mon ++1m>", OrgTime.build(day, null, repeater = "++1m"))

        assertEquals(OrgTime.Repeat(".+", 2, 'd', "/4d"), OrgTime.Repeat.parse(".+2d/4d"))
        assertEquals("++3w", OrgTime.Repeat("++", 3, 'w').toString())
        assertNull(OrgTime.Repeat.parse("every week"))

        assertEquals("every week (+)", OrgTime.Repeat.parse("+1w")!!.describe())
        assertEquals("every 3 months (++)", OrgTime.Repeat.parse("++3m")!!.describe())
        assertEquals("every 2 days from done (.+)", OrgTime.Repeat.parse(".+2d/4d")!!.describe())
    }

    @Test
    fun jumpEnd() {
        // A, A1, A1a, A2, B
        val all = notes.indices.toSet()
        // In A1's sub-notes: to their end, then A's, then the notebook's
        assertEquals(2, NoteTree.jumpEnd(notes, all, top = 1, bottom = 1))
        assertEquals(3, NoteTree.jumpEnd(notes, all, top = 1, bottom = 2))
        assertNull(NoteTree.jumpEnd(notes, all, top = 2, bottom = 3))
        assertEquals(3, NoteTree.jumpEnd(notes, all, top = 0, bottom = 0))
        assertNull(NoteTree.jumpEnd(notes, all, top = 4, bottom = 4))
        // A1 folded: A's end
        assertEquals(3, NoteTree.jumpEnd(notes, setOf(0, 1, 3, 4), top = 1, bottom = 1))
        // A folded: nothing open
        assertNull(NoteTree.jumpEnd(notes, setOf(0, 4), top = 0, bottom = 0))
    }
}
