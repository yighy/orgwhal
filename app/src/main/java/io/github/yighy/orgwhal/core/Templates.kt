package io.github.yighy.orgwhal.core

import java.time.LocalDateTime

/**
 * Template notes, kept in the Org files themselves: a note tagged :template: is offered when
 * making a new one, which starts as a copy of it. As in Emacs' capture templates, %u writes
 * today's inactive date, %U with the time, %t and %T the active ones; %? (the cursor there) goes.
 */
object Templates {

    const val TAG = "template"

    /** Properties that belong to the template note itself, not to the notes made from it. */
    private val OWN_PROPERTIES = setOf("ID", "CUSTOM_ID", SearchQuery.CREATED_PROPERTY)

    private val LOGBOOK = Regex("""^[ \t]*:LOGBOOK:[ \t]*\n(?:.*\n)*?[ \t]*:END:[ \t]*(?:\n|$)""", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

    fun isTemplate(note: OrgNote): Boolean = note.tags.any { it.equals(TAG, ignoreCase = true) }

    /** [text] with its placeholders filled for [now]. */
    fun fill(text: String, now: LocalDateTime): String = text
        .replace("%U", OrgTime.inactive(now))
        .replace("%u", OrgTime.inactiveDate(now.toLocalDate()))
        .replace("%T", OrgTime.build(now.toLocalDate(), now.toLocalTime().withSecond(0).withNano(0)))
        .replace("%t", OrgTime.build(now.toLocalDate(), null))
        .replace("%?", "")

    /** A new note made from [template] at [now]: its title, state, tags (not :template:) and content filled, its clocking left out. */
    fun newNote(template: OrgNote, now: LocalDateTime): NoteEdit = NoteEdit(
        title = fill(template.title, now).trim(),
        state = template.state,
        tags = template.tags.filterNot { it.equals(TAG, ignoreCase = true) },
        properties = template.properties.filterNot { (name, _) -> name.uppercase() in OWN_PROPERTIES }.map { (n, v) -> n to fill(v, now) },
        content = fill(LOGBOOK.replace(template.content, ""), now).trim('\n'),
        priority = template.priority,
        scheduled = template.scheduled?.let { fill(it, now) },
        deadline = template.deadline?.let { fill(it, now) },
    )
}
