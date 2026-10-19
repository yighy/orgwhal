package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalLinksTest {
    @Test
    fun aMissingLinkGivesItsTitle() {
        assertEquals("Acheter du bois", InternalLinks.titleFor("*Acheter du bois"))
        assertEquals("Journal", InternalLinks.titleFor("Journal"))
        assertNull(InternalLinks.titleFor("#custom"))
    }

    @Test
    fun theLinkOfAMadeNoteLeadsToItsId() {
        assertEquals(
            "Voir [[id:X1][Idée vide]], [[id:X1][mon idée]], pas [[Autre]]",
            InternalLinks.toIdLinks("Voir [[Idée vide]], [[Idée vide][mon idée]], pas [[Autre]]", "Idée vide", "X1", "Idée vide"),
        )
    }

    @Test
    fun titleLinksFollowARename() {
        val text = "[[Old]] [[*old]] [[Old][Old]] [[Old][mine]] [[Older]] [[id:X][Old]]"
        assertEquals(
            "[[New]] [[*New]] [[New]] [[New][mine]] [[Older]] [[id:X][Old]]",
            InternalLinks.renameLinks(text, "Old", "New", plain = true, starred = true),
        )
        assertEquals(
            "[[Old]] [[*New]] [[Old][Old]] [[Old][mine]] [[Older]] [[id:X][Old]]",
            InternalLinks.renameLinks(text, "Old", "New", plain = false, starred = true),
        )
    }


    private fun note(title: String, content: String = "", properties: List<Pair<String, String>> = emptyList()) =
        OrgNote("f", "f.org", 0, 1, null, false, null, title, emptyList(), null, null, null, properties, content, "* $title\n$content")

    private val notes = listOf(
        note("Budget [2/3]", "#+NAME: depenses\n| a | 1 |"),
        note("Voyage", "See <<hotel>> here"),
        note("Other", properties = listOf("CUSTOM_ID" to "other")),
    )

    @Test
    fun findsTargetsNamesTitlesAndCustomIds() {
        assertEquals("Voyage", InternalLinks.find(notes, "hotel")?.title)
        assertEquals("Budget [2/3]", InternalLinks.find(notes, "depenses")?.title)
        assertEquals("Budget [2/3]", InternalLinks.find(notes, "*Budget")?.title)
        assertEquals("Other", InternalLinks.find(notes, "#other")?.title)
        assertNull(InternalLinks.find(notes, "nothing"))
    }

    @Test
    fun schemesAreNotInternal() {
        assertTrue(InternalLinks.isInternal("depenses"))
        assertTrue(InternalLinks.isInternal("*A title"))
        assertFalse(InternalLinks.isInternal("https://example.org"))
        assertFalse(InternalLinks.isInternal("id:123"))
    }
}
