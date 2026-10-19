package io.github.yighy.orgwhal.core

/**
 * Typing help for note text: lists that go on at a new line, brackets that close themselves,
 * and the line edits of the toolbar over the keyboard (list, checkbox, indent, insert).
 */
object TextEditing {

    /** Text with its selection ([start] to [end]; the cursor when equal). */
    data class Edit(val text: String, val start: Int, val end: Int = start) {
        val collapsed: Boolean get() = start == end
    }

    private val PAIRS = mapOf('[' to ']', '(' to ')', '{' to '}')

    /** List item at the start of a line: indentation, bullet or number, spaces, checkbox. */
    private val ITEM = Regex("""^([ \t]*)(?:([-+*])|(\d+)([.)]))([ \t]+)(\[[ xX-]][ \t]+)?""")

    /**
     * Adjusts what typing did ([old] became [new]), or returns null to keep it as is:
     * - a new line after a list item starts another one ("- [ ] " again, "2." after "1.");
     *   on an empty item it ends the list instead (with [lists] on)
     * - "[", "(" or "{" gets its closing pair, the cursor between them
     * - typing a closing one just before the same one steps over it
     * - deleting an opening one with its empty pair right after deletes both
     * - "<<" (a target) gets its ">>", "<<<" (a radio target) its ">>>"; ">" steps over theirs,
     *   and deleting a "<" of an empty one deletes them
     */
    fun afterTyping(old: Edit, new: Edit, lists: Boolean = true): Edit? {
        if (!old.collapsed || !new.collapsed) return null
        val p = old.start
        val o = old.text
        val n = new.text
        val typedOne = n.length == o.length + 1 && new.start == p + 1 &&
            n.regionMatches(0, o, 0, p) && n.regionMatches(p + 1, o, p, o.length - p)
        if (typedOne) {
            val c = n[p]
            return when {
                c == '\n' -> if (lists) closeDrawer(o, p) ?: continueList(o, p) else null
                c in PAIRS.values && o.getOrNull(p) == c -> Edit(o, p + 1)
                c == '<' && o.regionMatches(p - 1, "<", 0, 1) && !o.regionMatches(p - 2, "<", 0, 1) && o.getOrNull(p) != '>' ->
                    Edit(n.substring(0, p + 1) + ">>" + n.substring(p + 1), p + 1)
                c == '<' && o.regionMatches(p - 2, "<<", 0, 2) && o.startsWith(">>", p) && !o.startsWith(">>>", p) ->
                    Edit(n.substring(0, p + 1) + ">" + n.substring(p + 1), p + 1)
                c == '>' && o.getOrNull(p) == '>' && inTarget(o, p) -> Edit(o, p + 1)
                c in PAIRS -> Edit(n.substring(0, p + 1) + PAIRS.getValue(c) + n.substring(p + 1), p + 1)
                else -> null
            }
        }
        val deletedOne = p >= 1 && n.length == o.length - 1 && new.start == p - 1 &&
            n.regionMatches(0, o, 0, p - 1) && n.regionMatches(p - 1, o, p, o.length - p)
        if (deletedOne && PAIRS[o[p - 1]]?.let { it == o.getOrNull(p) } == true) {
            return Edit(n.removeRange(p - 1, p), p - 1)
        }
        // An empty target: "<<|>>" losing a "<" loses its ">>" too ("<<<|>>>" its last ">")
        if (deletedOne && o[p - 1] == '<' && o.regionMatches(p - 2, "<", 0, 1)) {
            val closing = o.substring(p).takeWhile { it == '>' }.length
            val opening = o.substring(0, p).takeLastWhile { it == '<' }.length
            if (closing == opening && (opening == 2 || opening == 3)) {
                // "<|" left of a target, "<<|>>" of a radio one
                return Edit(n.removeRange(p - 1, p - 1 + if (opening == 2) 2 else 1), p - 1)
            }
        }
        return null
    }

    /** Whether [p] is within a target being written: "<<" before it on its line, not yet closed. */
    private fun inTarget(text: String, p: Int): Boolean {
        val lineStart = text.lastIndexOf('\n', p - 1) + 1
        val before = text.substring(lineStart, p)
        val open = before.lastIndexOf("<<")
        // Its name, and the ">" already stepped over
        return open >= 0 && '>' !in before.substring(open + 2).trimEnd('>')
    }

    private val DRAWER = Regex("""^([ \t]*):([A-Za-z][\w-]*):[ \t]*$""")

    /**
     * New line at [p] at the end of a drawer's first line (":NOTES:"): its ":END:" under the new
     * one, the cursor between them, unless something closes it already (an :END: before the next
     * drawer). Not for :END: itself nor :PROPERTIES: (properties have their own place).
     */
    private fun closeDrawer(text: String, p: Int): Edit? {
        val lineStart = text.lastIndexOf('\n', p - 1) + 1
        val lineEnd = text.indexOf('\n', p).let { if (it < 0) text.length else it }
        if (text.substring(p, lineEnd).isNotBlank()) return null
        val m = DRAWER.matchEntire(text.substring(lineStart, p)) ?: return null
        val name = m.groupValues[2]
        if (name.equals("END", ignoreCase = true) || name.equals("PROPERTIES", ignoreCase = true)) return null
        val after = text.substring(lineEnd).split('\n').drop(1)
        for (line in after) {
            val other = DRAWER.matchEntire(line) ?: continue
            if (other.groupValues[2].equals("END", ignoreCase = true)) return null
            break
        }
        val indent = m.groupValues[1]
        return Edit(text.substring(0, p) + "\n" + indent + "\n" + indent + ":END:" + text.substring(p), p + 1 + indent.length)
    }

    /** New line at [p] after a list item: another item, or the end of the list if it was empty. */
    private fun continueList(text: String, p: Int): Edit? {
        val lineStart = text.lastIndexOf('\n', p - 1) + 1
        val line = text.substring(lineStart, p)
        val m = ITEM.find(line) ?: return null
        if (line.substring(m.range.last + 1).isBlank()) {
            // An empty item: the list ends, the bullet goes
            return Edit(text.removeRange(lineStart, p), lineStart)
        }
        val (indent, bullet, number, dot, spaces, box) = m.destructured
        val next = indent + (bullet.ifEmpty { "${number.toInt() + 1}$dot" }) + spaces + (if (box.isNotEmpty()) "[ ] " else "")
        val insert = "\n" + next
        return Edit(text.substring(0, p) + insert + text.substring(p), p + insert.length)
    }

    /** Adds "- " to the lines, or takes the bullets away if they all have one. */
    fun toggleList(edit: Edit): Edit {
        val lines = targetLines(edit)
        val allItems = lines.all { ITEM.containsMatchIn(it) }
        return mapLines(edit) { line ->
            val m = ITEM.find(line)
            when {
                allItems && m != null -> line.removeRange(m.groupValues[1].length, m.value.length)
                !allItems && m == null && (line.isNotBlank() || lines.size == 1) -> insertAfterIndent(line, "- ")
                else -> line
            }
        }
    }

    /** Adds a checkbox to the lines (with a bullet if needed), or takes them away if they all have one. */
    fun toggleCheckbox(edit: Edit): Edit {
        val lines = targetLines(edit)
        val allBoxes = lines.all { ITEM.find(it)?.groupValues?.get(6)?.isNotEmpty() == true }
        return mapLines(edit) { line ->
            val m = ITEM.find(line)
            when {
                allBoxes && m != null -> line.removeRange(m.value.length - m.groupValues[6].length, m.value.length)
                allBoxes -> line
                m == null -> if (line.isNotBlank() || lines.size == 1) insertAfterIndent(line, "- [ ] ") else line
                m.groupValues[6].isEmpty() -> line.substring(0, m.value.length) + "[ ] " + line.substring(m.value.length)
                else -> line
            }
        }
    }

    /** Two more spaces before the lines. */
    fun indent(edit: Edit): Edit {
        val single = targetLines(edit).size == 1
        return mapLines(edit) { line -> if (line.isNotBlank() || single) "  $line" else line }
    }

    /** Two spaces (or a tab) less before the lines. */
    fun outdent(edit: Edit): Edit = mapLines(edit) { line ->
        when {
            line.startsWith("  ") -> line.substring(2)
            line.startsWith("\t") || line.startsWith(" ") -> line.substring(1)
            else -> line
        }
    }

    /**
     * Toggles Org emphasis with [marker] (`*`, `/`, `_`, `+`, `=` or `~`):
     * - an emphasis of that kind around the selection or the cursor loses its markers
     * - else the selection (without the spaces at its edges) or the word at the cursor gets them
     * - else an empty pair goes in, the cursor between; toggled again at once, it goes away
     */
    fun toggleEmphasis(edit: Edit, marker: Char): Edit {
        val text = edit.text
        Markup.find(text)
            .firstOrNull { text[it.range.first] == marker && edit.start >= it.range.first && edit.end <= it.range.last + 1 }
            ?.let { return unwrap(edit, it.range.first, it.range.last) }
        if (edit.collapsed && edit.start >= 1 && text.getOrNull(edit.start) == marker && text[edit.start - 1] == marker) {
            return Edit(text.removeRange(edit.start - 1, edit.start + 1), edit.start - 1)
        }
        var start = edit.start
        var end = edit.end
        if (edit.collapsed) {
            while (start > 0 && text[start - 1].isLetterOrDigit()) start--
            while (end < text.length && text[end].isLetterOrDigit()) end++
            if (start == end) return Edit(text.replaceRange(start, start, "$marker$marker"), start + 1)
        } else {
            while (start < end && text[start].isWhitespace()) start++
            while (end > start && text[end - 1].isWhitespace()) end--
            if (start == end) return Edit(text.replaceRange(edit.start, edit.end, "$marker$marker"), edit.start + 1)
        }
        val wrapped = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
        return when {
            !edit.collapsed -> Edit(wrapped, start + 1, end + 1)
            // At the end of the word, the cursor goes on after it
            edit.start == end -> Edit(wrapped, end + 2)
            else -> Edit(wrapped, edit.start + 1)
        }
    }

    /** Removes the markers at [first] and [last]; the selection follows the text. */
    private fun unwrap(edit: Edit, first: Int, last: Int): Edit {
        fun move(pos: Int) = pos - (if (pos > first) 1 else 0) - (if (pos > last) 1 else 0)
        val text = edit.text.removeRange(last, last + 1).removeRange(first, first + 1)
        return Edit(text, move(edit.start), move(edit.end))
    }

    private val TIMESTAMP = Regex("""<\d{4}-\d{2}-\d{2}[^>\n]*>|\[\d{4}-\d{2}-\d{2}[^]\n]*]""")

    /** Where the Org timestamp around the cursor or the selection is (just after it counts too). */
    fun timestampAt(edit: Edit): IntRange? =
        TIMESTAMP.findAll(edit.text).firstOrNull { edit.start >= it.range.first && edit.end <= it.range.last + 1 }?.range

    /** Puts [insert] in place of the selection, the cursor after it. */
    fun insert(edit: Edit, insert: String): Edit =
        Edit(edit.text.replaceRange(edit.start, edit.end, insert), edit.start + insert.length)

    private fun insertAfterIndent(line: String, prefix: String): String {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        return indent + prefix + line.substring(indent.length)
    }

    private fun lineBounds(edit: Edit): Pair<Int, Int> {
        val first = edit.text.lastIndexOf('\n', edit.start - 1) + 1
        val end = edit.text.indexOf('\n', edit.end).let { if (it < 0) edit.text.length else it }
        return first to end
    }

    /** Lines the selection touches (only non-blank ones, unless it is a single line). */
    private fun targetLines(edit: Edit): List<String> {
        val (first, end) = lineBounds(edit)
        val lines = edit.text.substring(first, end).split('\n')
        return if (lines.size == 1) lines else lines.filter { it.isNotBlank() }.ifEmpty { lines }
    }

    /**
     * Changes each line the selection touches; the selection follows the text (a position in
     * a changed part goes to its end).
     */
    private fun mapLines(edit: Edit, change: (String) -> String): Edit {
        val text = edit.text
        val (first, end) = lineBounds(edit)
        val out = StringBuilder(text.substring(0, first))
        var start = edit.start
        var stop = edit.end
        var oldLineStart = first
        text.substring(first, end).split('\n').forEachIndexed { i, line ->
            if (i > 0) out.append('\n')
            val newLineStart = out.length
            val new = change(line)
            out.append(new)
            val prefix = line.commonPrefixWith(new).length
            val suffix = minOf(line.commonSuffixWith(new).length, line.length - prefix, new.length - prefix)
            // At the edge of what changed, the cursor and a selection's end go after it (text
            // inserted at the cursor), a selection's start stays before it
            fun move(pos: Int, after: Boolean): Int {
                val o = pos - oldLineStart
                if (o < 0 || o > line.length) return pos
                val inNew = when {
                    o < prefix || (o == prefix && !after) -> o
                    o >= line.length - suffix -> o + new.length - line.length
                    else -> new.length - suffix
                }
                return newLineStart + inNew
            }
            val range = oldLineStart..oldLineStart + line.length
            if (edit.start in range) start = move(edit.start, after = edit.collapsed)
            if (edit.end in range) stop = move(edit.end, after = true)
            oldLineStart += line.length + 1
        }
        val shift = out.length - end
        out.append(text.substring(end))
        if (edit.start > end) start += shift
        if (edit.end > end) stop += shift
        return Edit(out.toString(), start, stop)
    }
}
