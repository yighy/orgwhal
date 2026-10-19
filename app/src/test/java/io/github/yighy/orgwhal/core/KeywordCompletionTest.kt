package io.github.yighy.orgwhal.core

import io.github.yighy.orgwhal.core.KeywordCompletion.Choice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordCompletionTest {
    @Test
    fun aKeywordIsTypedAtTheStartOfALine() {
        assertEquals(KeywordCompletion.Query(6, 10, "", "tr"), KeywordCompletion.queryAt("Avant\n#+tr", 10))
        assertEquals(KeywordCompletion.Query(2, 3, "  ", ""), KeywordCompletion.queryAt("  #", 3))
        // A comment, a keyword already written, or a "#" within a line
        assertNull(KeywordCompletion.queryAt("# note", 6))
        assertNull(KeywordCompletion.queryAt("#+NAME: t", 4))
        assertNull(KeywordCompletion.queryAt("Voir #", 6))
    }

    @Test
    fun choicesFollowWhatIsTyped() {
        assertEquals(listOf(Choice.Transclude), KeywordCompletion.choices(KeywordCompletion.queryAt("#+tr", 4)!!))
        assertEquals(listOf(Choice.Source), KeywordCompletion.choices(KeywordCompletion.queryAt("#src", 4)!!))
        assertEquals(Choice.entries.size, KeywordCompletion.choices(KeywordCompletion.queryAt("#", 1)!!).size)
    }

    @Test
    fun aBlockIsWrittenWholeTheCursorInside() {
        assertEquals("#+BEGIN_QUOTE\n  \n  #+END_QUOTE" to 16, Choice.Quote.text("  "))
        assertEquals("#+transclude: [[]]" to 16, Choice.Transclude.text(""))
    }
}
