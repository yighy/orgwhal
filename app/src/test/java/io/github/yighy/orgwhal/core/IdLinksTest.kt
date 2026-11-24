package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdLinksTest {

    @Test
    fun detectsQueryBeforeCursor() {
        assertEquals(IdLinks.Query(4, 11, "shop"), IdLinks.queryAt("See id:shop", 11))
        assertEquals("", IdLinks.queryAt("id:", 3)?.text)
        assertEquals("big list", IdLinks.queryAt("x id:big list", 13)?.text)
        assertEquals("a", IdLinks.queryAt("(id:a", 5)?.text)
    }

    @Test
    fun ignoresExistingLinksAndWords() {
        assertNull(IdLinks.queryAt("[[id:abc", 8))
        assertNull(IdLinks.queryAt("valid:abc", 9))
        assertNull(IdLinks.queryAt("id:abc\nmore", 11))
        assertNull(IdLinks.queryAt("[[id:abc][Title]] done", 22))
    }

    @Test
    fun formatsAndFindsLinks() {
        val link = IdLinks.format("u1", "Shopping [list]")
        assertEquals("[[id:u1][Shopping (list)]]", link)

        val found = IdLinks.find("See $link and [[id:u2]].")
        assertEquals(listOf("u1", "u2"), found.map { it.id })
        assertEquals(listOf("Shopping (list)", null), found.map { it.description })
    }

    @Test
    fun renamesLinksStillShowingTheOldTitle() {
        val text = "See [[id:U1][Old (draft)]], [[id:u1][my notes]] and [[id:u2][Old (draft)]]."
        assertEquals(
            "See [[id:U1][New]], [[id:u1][my notes]] and [[id:u2][Old (draft)]].",
            IdLinks.renameLinks(text, "u1", "Old [draft]", "New"),
        )
        assertEquals(text, IdLinks.renameLinks(text, "u3", "Old [draft]", "New"))
    }

    @Test
    fun checkboxes() {
        val text = "Shopping:\n- [ ] milk\n  + [X] bread\n1. [-] eggs\nnot [ ] a box"
        assertEquals(listOf(false, true, false), Checkboxes.find(text).map { it.checked })
        assertEquals("Shopping:\n- [X] milk\n  + [X] bread\n1. [-] eggs\nnot [ ] a box", Checkboxes.toggle(text, 0))
        assertEquals("Shopping:\n- [ ] milk\n  + [ ] bread\n1. [-] eggs\nnot [ ] a box", Checkboxes.toggle(text, 1))
        assertEquals(text, Checkboxes.toggle(text, 5))
        assertEquals(IdLinks.Query(4, 12, "gtd"), IdLinks.queryAt("See file:gtd", 12, "file:"))
    }

    @Test
    fun findsAllKindsOfLinks() {
        val text = "See https://www.orgzly.com. Mail mailto:me@x.org, call tel:+33123 or " +
            "[[https://example.com][the site]] (file:gtd.org) and [[id:u1][a note]] geo:48.8,2.3"
        assertEquals(
            listOf("https://www.orgzly.com", "mailto:me@x.org", "tel:+33123", "https://example.com", "file:gtd.org", "id:u1", "geo:48.8,2.3"),
            Links.find(text).map { it.target },
        )
        assertEquals("the site", Links.find(text)[3].shown)
        assertEquals("Go to the site", IdLinks.plainText("Go to [[https://example.com][the site]]"))
    }

    @Test
    fun suggestionsRankPrefixFirst() {
        fun note(title: String) = OrgNote("u", "f", 0, 1, null, false, null, title, emptyList(), null, null, null, emptyList(), "", "")
        val notes = listOf(note("Weekly shopping"), note("Shopping list"), note("Garage"))
        assertEquals(listOf("Shopping list", "Weekly shopping"), IdLinks.suggestions(notes, "shop").map { it.title })
        assertEquals(3, IdLinks.suggestions(notes, "").size)
    }

    @Test
    fun suggestionsIgnoreCaseAndAccents() {
        fun note(title: String) = OrgNote("u", "f", 0, 1, null, false, null, title, emptyList(), null, null, null, emptyList(), "", "")
        val notes = listOf(note("Séance de sport"), note("Crème brûlée"), note("Garage"))
        assertEquals(listOf("Séance de sport"), IdLinks.suggestions(notes, "SEANCE").map { it.title })
        assertEquals(listOf("Crème brûlée"), IdLinks.suggestions(notes, "creme brulee").map { it.title })
        assertEquals(listOf("Garage"), IdLinks.suggestions(notes, "garàge").map { it.title })
    }

    @Test
    fun suggestionsFindSubNotesByTheirParents() {
        fun note(title: String, level: Int) = OrgNote("u", "f", 0, level, null, false, null, title, emptyList(), null, null, null, emptyList(), "", "")
        val notes = listOf(note("Meals", 1), note("Chicken rice", 2), note("Sauce", 3), note("Rice pudding", 1))
        val paths = NoteTree.ancestorTitles(notes).map { listOf("Kitchen") + it }
        fun find(query: String) = IdLinks.suggestions(notes, query) { paths[notes.indexOf(it)] }.map { it.title }
        // The note itself first, then its sub-notes, the nearest first
        assertEquals(listOf("Meals", "Chicken rice", "Sauce"), find("meals"))
        assertEquals(listOf("Chicken rice", "Sauce"), find("meals rice"))
        // Titles before paths: Sauce is found by its parent's title
        assertEquals(listOf("Rice pudding", "Chicken rice", "Sauce"), find("rice"))
        // The notebook's name finds all its notes
        assertEquals(4, find("kitchen").size)
    }
}
