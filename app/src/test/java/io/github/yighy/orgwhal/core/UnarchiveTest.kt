package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class UnarchiveTest {

    private val notebook = """
        * Projects
        ** Home
        *** TODO Paint the door
        SCHEDULED: <2026-11-10 Tue>
        :PROPERTIES:
        :ID:       D1
        :END:
        Blue.
        **** Buy paint
        * Other
    """.trimIndent() + "\n"

    @Test
    fun putBackAsItWas() {
        val doc = OrgDocument.parse(notebook)
        val original = Outline.extract(doc, listOf(2)).single()
        val archived = Archive.entry(doc, 2, "notes/gtd.org", LocalDateTime.of(2026, 11, 7, 17, 0))
        // Archiving adds its properties; putting back takes them away, the rest as it was
        assertEquals(original, Archive.restored(archived))

        // Where from, read back from the archived note
        val archive = OrgDocument.parse(Archive.append(null, "gtd.org", listOf(archived)))
        val note = OrgNote.fromDocument(archive, "a", "gtd.org_archive").single { it.title == "Paint the door" }
        val origin = Archive.origin(note)!!
        assertEquals("notes/gtd.org", origin.file)
        assertEquals(listOf("Projects", "Home"), origin.parents)
        assertEquals("2026-11-07 Sat 17:00", origin.time)
    }

    @Test
    fun aDrawerOnlyForArchivingGoes() {
        val raw = "* DONE Old\n:PROPERTIES:\n:ARCHIVE_TIME: 2026-11-07 Sat 17:00\n:ARCHIVE_FILE: gtd.org\n:END:\nText\n"
        assertEquals("* DONE Old\nText\n", SectionEditor.removeProperties(raw, Archive.PROPERTIES))
    }

    @Test
    fun treesArchivedWithTheTag() {
        val doc = OrgDocument.parse("* A\n* B :ARCHIVE:\n** B1\n*** B1a\n** B2\n* C\n** C1 :ARCHIVE:\n* D\n")
        val notes = OrgNote.fromDocument(doc, "u", "f.org")
        // A tagged note and all under it, nothing beside
        assertEquals(listOf(false, true, true, true, true, false, true, false), Archive.taggedTrees(notes).toList())
        // Those with sub-notes open folded, whatever the file says
        assertEquals(setOf(1), Folding.initial("#+STARTUP: showall\n", notes))
    }

    @Test
    fun findsTheNotebook() {
        val paths = listOf("gtd.org", "notes/work.org", "inbox.org")
        assertEquals(0, Archive.notebook("gtd.org", paths))
        assertEquals(1, Archive.notebook("notes/work.org", paths))
        // Emacs writes the full path
        assertEquals(1, Archive.notebook("/home/me/org/notes/work.org", paths))
        assertEquals(1, Archive.notebook("~/org/notes/work.org", paths))
        assertEquals(2, Archive.notebook("C:\\Users\\me\\org\\Inbox.org", paths))
        assertNull(Archive.notebook("gone.org", paths))
    }

    @Test
    fun findsTheParentOrTheNearest() {
        val notes = OrgNote.fromDocument(OrgDocument.parse(notebook), "u", "gtd.org")
        assertEquals("Home", Archive.parent(notes, listOf("Projects", "Home"))?.title)
        // Home renamed: under Projects
        assertEquals("Projects", Archive.parent(notes, listOf("Projects", "House"))?.title)
        assertNull(Archive.parent(notes, listOf("Gone", "Home")))
        assertNull(Archive.parent(notes, emptyList()))
    }
}
