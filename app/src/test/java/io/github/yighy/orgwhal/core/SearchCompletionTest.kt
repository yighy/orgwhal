package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCompletionTest {

    private val vocabulary = SearchCompletion.Vocabulary(
        notebooks = listOf("work", "My notes"),
        tags = listOf("home", "health", "work"),
        keywords = listOf("TODO", "NEXT", "DONE"),
        columns = listOf("Quantité", "Unité", "en stock"),
    )

    private fun texts(text: String, cursor: Int = text.length) =
        SearchCompletion.queryAt(text, cursor)?.let { SearchCompletion.choices(it, vocabulary) }.orEmpty().map { it.text }

    @Test
    fun prefixes() {
        assertEquals(listOf("t.", "tn."), texts("t"))
        assertEquals(listOf("c.", "cr.", "col."), texts("i.todo c"))
        assertEquals(listOf(".t.", ".tn."), texts(".t"))
        // All of them in an empty field, none after a space
        assertEquals(15, texts("").size)
        assertTrue(texts("i.todo ").isEmpty())
        assertTrue(texts("milk").isEmpty())
        // In a notebook, no notebook to choose
        val inNotebook = SearchCompletion.queryAt("", 0)!!
        assertEquals(14, SearchCompletion.choices(inNotebook, vocabulary.copy(notebooks = emptyList())).size)
    }

    @Test
    fun values() {
        assertEquals(listOf("t.home", "t.health"), texts("t.h"))
        assertEquals(listOf("i.NEXT"), texts("i.n"))
        assertEquals(listOf("b.\"My notes\""), texts("b.m"))
        assertEquals(listOf("o.scheduled"), texts("o.sc"))
        // Written in full: nothing left to propose
        assertTrue(texts("t.home").isEmpty())
        assertEquals(listOf("s.le.", "s.today", "s.tomorrow"), texts("s.le") + texts("s.to"))
        assertEquals(listOf("d.le.3d"), texts("d.le.3"))
    }

    @Test
    fun columns() {
        // Accents and case do not matter
        assertEquals(listOf("col.Quantité."), texts("col.quanti"))
        assertEquals(listOf("col.\"en stock\"."), texts("col.en"))
        assertEquals(listOf("col.Quantité.lt.", "col.Quantité.le."), texts("col.Quantité.l"))
        assertEquals(listOf("col.\"en stock\".eq."), texts("col.\"en stock\".e"))
        assertTrue(texts("col.Quantité.le.").isEmpty())
        // Inside quotes
        assertNull(SearchCompletion.queryAt("col.\"en s", 9))
    }

    @Test
    fun applying() {
        val text = "i.todo t.h o.p"
        val query = SearchCompletion.queryAt(text, 10)!!
        val choice = SearchCompletion.choices(query, vocabulary).first()
        assertEquals("i.todo t.home o.p" to 14, SearchCompletion.apply(text, query, choice))

        val prefix = SearchCompletion.queryAt("co", 2)!!
        val col = SearchCompletion.choices(prefix, vocabulary).single()
        assertEquals("col." to 4, SearchCompletion.apply("co", prefix, col))
        val end = SearchCompletion.queryAt("t.wo", 4)!!
        assertEquals("t.work " to 7, SearchCompletion.apply("t.wo", end, SearchCompletion.choices(end, vocabulary).single()))
    }

    @Test
    fun fieldNames() {
        val content = "| Quantité | Unité |\n|---+---|\n| 1 | pots |\n\n| Stock | 0 |\n| 12 | x |\n"
        assertEquals(listOf("Quantité", "Unité", "Stock"), SearchQuery.fieldNames(content))
    }
}
