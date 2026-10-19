package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransclusionTest {
    private fun note(title: String, content: String, file: String = "f", index: Int = 0, id: String? = null) =
        OrgNote(file, "$file.org", index, 1, null, false, null, title, emptyList(), null, null, null, listOfNotNull(id?.let { "ID" to it }), content, "* $title\n$content")

    @Test
    fun theLineIsFound() {
        val blocks = Blocks.parse("Avant\n#+transclude: [[id:A1][Séance]] :lines 2-3\nAprès")
        val t = blocks[1] as Blocks.Transclude
        assertEquals("id:A1", t.link)
        assertEquals(":lines 2-3", t.options)
        assertEquals(3, blocks.size)
    }

    @Test
    fun aNoteANamedElementOrSomeLinesShow() {
        val seance = note("Séance A", "Échauffement\n#+NAME: series\n| Pompes | 12 |\n#+TBLFM: \$2=12\nFin", id = "A1")
        val notes = listOf(seance)
        assertEquals(seance.content, Transclusion.resolve(notes, "id:A1", "")?.text)
        assertEquals(seance.content, Transclusion.resolve(notes, "*Séance A", "")?.text)
        assertEquals("| Pompes | 12 |\n#+TBLFM: \$2=12", Transclusion.resolve(notes, "series", "")?.text)
        assertEquals("#+NAME: series\n| Pompes | 12 |", Transclusion.resolve(notes, "id:A1", ":lines 2-3")?.text)
        assertEquals("Fin", Transclusion.resolve(notes, "id:A1", ":lines 5-")?.text)
        assertNull(Transclusion.resolve(notes, "id:nope", ""))
        val text = note("Notes", "Intro\n\n<<obj>>Objectif : 50.\nSuite.\n\nAutre.\n- un\n- <<deux>>deux\n  - sous\n- trois", index = 1)
        assertEquals("Objectif : 50.\nSuite.", Transclusion.resolve(listOf(text), "obj", "")?.text)
        assertEquals("- deux\n  - sous", Transclusion.resolve(listOf(text), "deux", "")?.text)
        // A table right under the paragraph is not part of it
        val table = note("T", "<<cap>>Le cap.\n#+NAME: t\n| a |", index = 2)
        assertEquals("Le cap.", Transclusion.resolve(listOf(table), "cap", "")?.text)
        assertNull(Transclusion.resolve(notes, "https://orgmode.org", ""))
    }
}
