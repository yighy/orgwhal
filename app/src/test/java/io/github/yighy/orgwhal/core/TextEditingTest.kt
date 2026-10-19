package io.github.yighy.orgwhal.core

import io.github.yighy.orgwhal.core.TextEditing.Edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextEditingTest {

    /** Text with "|" as the cursor (or two "|" around a selection). */
    private fun e(marked: String): Edit {
        val a = marked.indexOf('|')
        val b = marked.indexOf('|', a + 1)
        val text = marked.replace("|", "")
        return if (b < 0) Edit(text, a) else Edit(text, a, b - 1)
    }

    private fun typed(before: String, char: Char): Edit? {
        val old = e(before)
        val new = Edit(old.text.substring(0, old.start) + char + old.text.substring(old.start), old.start + 1)
        return TextEditing.afterTyping(old, new)
    }

    private fun backspace(before: String): Edit? {
        val old = e(before)
        return TextEditing.afterTyping(old, Edit(old.text.removeRange(old.start - 1, old.start), old.start - 1))
    }

    @Test
    fun listsGoOn() {
        assertEquals(e("- [X] milk\n- [ ] |"), typed("- [X] milk|", '\n'))
        assertEquals(e("  + bread\n  + |"), typed("  + bread|", '\n'))
        assertEquals(e("1. one\n2. |"), typed("1. one|", '\n'))
        // An empty item ends the list
        assertEquals(e("- a\n|"), typed("- a\n- [ ] |", '\n'))
        // Not a list: nothing to do
        assertNull(typed("plain|", '\n'))
        assertNull(typed("-5 degrees|", '\n'))
    }

    @Test
    fun aDrawerCloses() {
        assertEquals(e(":NOTES:\n|\n:END:"), typed(":NOTES:|", '\n'))
        assertEquals(e("Avant\n  :LOGBOOK:\n  |\n  :END:\nAprès"), typed("Avant\n  :LOGBOOK:|\nAprès", '\n'))
        // Already closed, :END: itself, properties
        assertNull(typed(":NOTES:|\nnote\n:END:", '\n'))
        assertNull(typed(":END:|", '\n'))
        assertNull(typed(":PROPERTIES:|", '\n'))
        // Closed further down only after another drawer: this one is still open
        assertEquals(e(":A:\n|\n:END:\n:B:\n:END:"), typed(":A:|\n:B:\n:END:", '\n'))
    }

    @Test
    fun bracketsClose() {
        assertEquals(e("see [|]"), typed("see |", '['))
        assertEquals(e("f(|)"), typed("f|", '('))
        // Typing the closing one steps over it
        assertEquals(e("[x]|"), typed("[x|]", ']'))
        // Deleting an opening one with its empty pair
        assertEquals(e("a |"), backspace("a [|]"))
        assertNull(backspace("a [x|]"))
    }

    @Test
    fun toolbarLineEdits() {
        assertEquals(e("- milk|"), TextEditing.toggleList(e("milk|")))
        assertEquals(e("milk|"), TextEditing.toggleList(e("- milk|")))
        assertEquals(e("- [ ] milk|"), TextEditing.toggleCheckbox(e("milk|")))
        // On an empty line, the cursor goes after what is added
        assertEquals(e("a\n- [ ] |"), TextEditing.toggleCheckbox(e("a\n|")))
        assertEquals(e("- |milk"), TextEditing.toggleList(e("|milk")))
        assertEquals(e("- [ ] milk|"), TextEditing.toggleCheckbox(e("- milk|")))
        assertEquals(e("- milk|"), TextEditing.toggleCheckbox(e("- [X] milk|")))
        assertEquals(e("|- a\n- b|\n- c"), TextEditing.toggleList(e("|a\nb|\n- c")))
        assertEquals(e("  - |a"), TextEditing.indent(e("- |a")))
        assertEquals(e("- |a"), TextEditing.outdent(e("  - |a")))
        assertEquals(e("see id:|"), TextEditing.insert(e("see |"), "id:"))
    }

    @Test
    fun emphasis() {
        // A selection, without its edge spaces
        assertEquals(e("a *|big|* deal"), TextEditing.toggleEmphasis(e("a| big |deal"), '*'))
        // The word at the cursor; at its end, the cursor goes on after it
        assertEquals(e("a /bi|g/ deal"), TextEditing.toggleEmphasis(e("a bi|g deal"), '/'))
        assertEquals(e("a _big_| deal"), TextEditing.toggleEmphasis(e("a big| deal"), '_'))
        // Nowhere to put it: an empty pair, removed when toggled again
        assertEquals(e("a ~|~"), TextEditing.toggleEmphasis(e("a |"), '~'))
        assertEquals(e("a |"), TextEditing.toggleEmphasis(e("a ~|~"), '~'))
        // Already there: the markers go
        assertEquals(e("a bi|g deal"), TextEditing.toggleEmphasis(e("a *bi|g* deal"), '*'))
        assertEquals(e("a |big| deal"), TextEditing.toggleEmphasis(e("a *|big|* deal"), '*'))
        assertEquals(e("a |big| deal"), TextEditing.toggleEmphasis(e("a |*big*| deal"), '*'))
        // Another kind of emphasis is kept, the new one goes inside it
        assertEquals(e("a */bi|g/* deal"), TextEditing.toggleEmphasis(e("a *bi|g* deal"), '/'))
    }

    @Test
    fun timestampAtCursor() {
        assertEquals(4..19, TextEditing.timestampAt(e("see <2026-10-|06 Tue> or")))
        assertEquals(4..19, TextEditing.timestampAt(e("see <2026-10-06 Tue>| or")))
        assertEquals(4..19, TextEditing.timestampAt(e("see [2026-10-06 |Tue] or")))
        assertNull(TextEditing.timestampAt(e("see| <2026-10-06 Tue> or")))
        assertNull(TextEditing.timestampAt(e("a [[link|]]")))
    }
}
