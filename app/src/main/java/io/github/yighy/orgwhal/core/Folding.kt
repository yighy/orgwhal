package io.github.yighy.orgwhal.core

/**
 * The notes folded when a notebook is shown for the first time, as Org shows a file it opens:
 * after the file's #+STARTUP (overview, showNlevels; content, showall, showeverything leave all
 * notes open), then each note's VISIBILITY property (folded, children, content, all).
 */
object Folding {

    private val STARTUP = Regex("""^[ \t]*#\+STARTUP:(.*)$""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
    private val LEVELS = Regex("""show(\d+)levels""", RegexOption.IGNORE_CASE)

    /** Indices of [notes] (of a notebook whose text before the first note is [preface]) to fold. */
    fun initial(preface: String, notes: List<OrgNote>): Set<Int> {
        val words = STARTUP.findAll(preface).flatMap { it.groupValues[1].trim().split(Regex("\\s+")) }.map { it.lowercase() }.toList()
        // The last word about folding wins, as in Org
        val levels = words.lastNotNullOrNull { word ->
            when {
                word == "overview" || word == "fold" -> 1
                word == "content" || word == "showall" || word == "showeverything" || word == "nofold" -> 0
                else -> LEVELS.matchEntire(word)?.groupValues?.get(1)?.toInt()
            }
        } ?: 0
        val folded = notes.indices.filterTo(LinkedHashSet()) { i ->
            levels > 0 && notes[i].level >= levels && NoteTree.hasChildren(notes, i)
        }
        notes.forEachIndexed { i, note ->
            val subtree = (i + 1 until notes.size).takeWhile { notes[it].level > note.level }
            when (note.property("VISIBILITY")?.trim()?.lowercase()) {
                "folded" -> if (NoteTree.hasChildren(notes, i)) folded += i
                "children" -> {
                    folded -= i
                    subtree.filter { NoteTree.ancestors(notes, it).lastOrNull() == note && NoteTree.hasChildren(notes, it) }
                        .forEach { folded += it }
                }
                "content", "all", "showall" -> {
                    folded -= i
                    folded -= subtree.toSet()
                }
            }
        }
        // Archived trees (:ARCHIVE:) stay closed, as Org hides them whatever the rest
        notes.forEachIndexed { i, note -> if (Archive.TAG in note.tags && NoteTree.hasChildren(notes, i)) folded += i }
        return folded
    }

    private inline fun <T, R : Any> List<T>.lastNotNullOrNull(transform: (T) -> R?): R? {
        for (i in indices.reversed()) transform(this[i])?.let { return it }
        return null
    }
}
