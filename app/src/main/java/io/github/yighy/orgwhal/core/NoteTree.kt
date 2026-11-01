package io.github.yighy.orgwhal.core

/** Outline structure of the notes of one file, which are in document order. */
object NoteTree {

    fun hasChildren(notes: List<OrgNote>, index: Int): Boolean =
        (notes.getOrNull(index + 1)?.level ?: 0) > notes[index].level

    /** The direct sub-notes of the note at [index], in order (not theirs). */
    fun children(notes: List<OrgNote>, index: Int): List<OrgNote> {
        val level = notes[index].level
        val result = mutableListOf<OrgNote>()
        // The levels open since the note: one is a child when nothing between is its parent
        val open = ArrayDeque<Int>()
        for (i in index + 1 until notes.size) {
            val l = notes[i].level
            if (l <= level) break
            while (open.isNotEmpty() && open.last() >= l) open.removeLast()
            if (open.isEmpty()) result += notes[i]
            open.addLast(l)
        }
        return result
    }

    /** Ancestors of the note at [index], outermost first. */
    fun ancestors(notes: List<OrgNote>, index: Int): List<OrgNote> {
        val result = mutableListOf<OrgNote>()
        var level = notes[index].level
        for (i in index - 1 downTo 0) {
            if (notes[i].level < level) {
                result.add(0, notes[i])
                level = notes[i].level
            }
        }
        return result
    }

    /** For each note, the levels of its ancestors, outermost first (one pass with a stack). */
    fun ancestorLevels(notes: List<OrgNote>): List<List<Int>> {
        val stack = ArrayDeque<Int>()
        return notes.map { note ->
            while (stack.isNotEmpty() && stack.last() >= note.level) stack.removeLast()
            val levels = stack.toList()
            stack.addLast(note.level)
            levels
        }
    }

    /** For each note, the tags of its ancestors (Org tag inheritance). */
    fun inheritedTags(notes: List<OrgNote>): List<List<String>> {
        val stack = ArrayDeque<OrgNote>()
        return notes.map { note ->
            while (stack.isNotEmpty() && stack.last().level >= note.level) stack.removeLast()
            val tags = stack.flatMap { it.tags }.distinct()
            stack.addLast(note)
            tags
        }
    }

    /**
     * For each note, its title after its ancestors' (without statistics cookies), numbered among
     * notes of the same path: its place in the outline, kept when its content or state changes.
     */
    fun titlePaths(notes: List<OrgNote>): List<String> {
        val stack = ArrayDeque<OrgNote>()
        val seen = HashMap<String, Int>()
        return notes.map { note ->
            while (stack.isNotEmpty() && stack.last().level >= note.level) stack.removeLast()
            stack.addLast(note)
            val path = stack.joinToString("\u0001") { it.title.replace(Cookies.COOKIE, "").trim() }
            "${seen.merge(path, 1, Int::plus)}\u0001$path"
        }
    }

    /** For each note, its ancestors' titles, the outermost first. */
    fun ancestorTitles(notes: List<OrgNote>): List<List<String>> {
        val stack = ArrayDeque<OrgNote>()
        return notes.map { note ->
            while (stack.isNotEmpty() && stack.last().level >= note.level) stack.removeLast()
            stack.map { it.title }.also { stack.addLast(note) }
        }
    }

    /**
     * Where a short jump down goes in an outline showing the notes [shown], scrolled from [top] to
     * [bottom] (the last note seen whole): the last shown sub-note of the innermost unfolded note
     * around [top] whose sub-notes go on below [bottom], going out a level when they end on the
     * screen already. Null for the end of the notebook, there being no such note or it ending there.
     */
    fun jumpEnd(notes: List<OrgNote>, shown: Set<Int>, top: Int, bottom: Int): Int? {
        if (top !in notes.indices) return null
        val last = shown.maxOrNull() ?: return null
        val around = buildList {
            if (hasChildren(notes, top) && top + 1 in shown) add(top)
            addAll(ancestors(notes, top).map { it.index }.asReversed())
        }
        for (note in around) {
            var end = note
            while (end + 1 < notes.size && notes[end + 1].level > notes[note].level) end++
            val shownEnd = (end downTo note).first { it in shown }
            if (shownEnd >= last) return null
            if (shownEnd > bottom) return shownEnd
        }
        return null
    }

    /** A note's row in view in an outline: where its title's line is ([top]) and where it ends. */
    data class ShownRow(val note: Int, val top: Int, val bottom: Int)

    /** A title staying at the top of an outline, drawn at [y]. */
    data class Stuck(val note: Int, val y: Int)

    /**
     * The titles that stay at the top of an outline showing [rows] (in order, from the top, all
     * positions from the top of the list), as if each note came up, stopped at its [place] and
     * stayed there while its sub-notes go by; the next one of its level then pushes it away. Only
     * unfolded notes with sub-notes stay, and notes of at least [longNote], whose content goes on
     * under their title; the [max] nearest. Each takes [height].
     */
    fun stuck(
        notes: List<OrgNote>,
        rows: List<ShownRow>,
        isFolded: (OrgNote) -> Boolean,
        place: (Int) -> Int,
        height: Int,
        max: Int,
        longNote: Int = Int.MAX_VALUE,
    ): List<Stuck> {
        val first = rows.indexOfFirst { it.bottom > place(0) }
        if (first < 0) return emptyList()
        // Those above what shows are gone by; then each row as it comes to its place
        var stack = ancestors(notes, rows[first].note).map { it.index }
        for (row in rows.drop(first)) {
            val note = notes[row.note]
            val kept = stack.filter { notes[it].level < note.level }
            if (row.top >= place(minOf(kept.size, max - 1))) break
            val stays = hasChildren(notes, row.note) && !isFolded(note) || row.bottom - row.top >= longNote
            stack = if (stays) kept + row.note else kept
        }
        var pushed = 0
        return stack.takeLast(max).mapIndexed { i, index ->
            // By the first row past its sub-notes, or the end of the list; those under it go with it
            val next = rows.firstOrNull { it.note > index && notes[it.note].level <= notes[index].level }?.top
                ?: rows.lastOrNull()?.bottom?.takeIf { rows.last().note == notes.lastIndex }
            next?.let { pushed = minOf(pushed, it - height - place(i)) }
            Stuck(index, place(i) + pushed)
        }
    }

    /** Notes not hidden under a folded ancestor. */
    fun visible(notes: List<OrgNote>, isFolded: (OrgNote) -> Boolean): List<OrgNote> {
        val result = mutableListOf<OrgNote>()
        var hideBelow = Int.MAX_VALUE
        for (note in notes) {
            if (note.level > hideBelow) continue
            hideBelow = Int.MAX_VALUE
            result += note
            if (isFolded(note)) hideBelow = note.level
        }
        return result
    }
}

/** A notebook is one .org file. */
object Notebooks {
    private val TITLE = Regex("""^#\+TITLE:[ \t]*(.+)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val KEYWORD_LINE = Regex("""^[ \t]*#\+\S+:.*$""")

    /** [preface] with its #+TITLE set to [title], or null when it has no #+TITLE line. */
    fun retitle(preface: String, title: String): String? {
        val value = TITLE.find(preface)?.groups?.get(1) ?: return null
        return preface.replaceRange(value.range, title.trim())
    }

    /** #+TITLE of the file, or its name without extension. */
    fun title(doc: OrgDocument, fileName: String): String =
        TITLE.find(doc.preface)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.removeSuffix(".org").removeSuffix(".ORG")

    /** Preface text without #+KEYWORD lines. */
    fun prefaceText(doc: OrgDocument): String = prefaceText(doc.preface)

    /** [preface] without its #+ lines, as shown. */
    fun prefaceText(preface: String): String =
        preface.lines().filterNot { KEYWORD_LINE.matches(it) }.joinToString("\n").trim()
}
