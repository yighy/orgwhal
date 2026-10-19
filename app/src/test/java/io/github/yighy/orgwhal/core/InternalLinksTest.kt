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
    fun aLinkBeingTypedIsFound() {
        // "[" closes itself: "[[" gives "[[]]", the cursor in the middle
        assertEquals(InternalLinks.Query(5, 13, "Jour"), InternalLinks.queryAt("Voir [[Jour]] la", 11))
        assertEquals(InternalLinks.Query(0, 4, ""), InternalLinks.queryAt("[[]]", 2))
        assertEquals(InternalLinks.Query(0, 4, "Ab"), InternalLinks.queryAt("[[Ab", 4))
        assertNull(InternalLinks.queryAt("[[id:ab]]", 7))
        assertNull(InternalLinks.queryAt("[[Ab]] cd", 9))
        assertNull(InternalLinks.queryAt("[[Ab][cd]]", 8))
    }

    @Test
    fun aNoteIsLinkedByItsTitleWhenThatLeadsToIt() {
        val a = note("Journal [1/2]").copy(fileUri = "a", index = 0)
        val b = note("Courses").copy(fileUri = "a", index = 1)
        val target = note("Plan", "Voir <<Courses>>").copy(fileUri = "b", index = 0)
        val twin = note("Plan").copy(fileUri = "b", index = 1)
        val dated = note("Séance [2026-10-13 Tue]").copy(fileUri = "a", index = 2)
        val notes = listOf(a, b, target, twin, dated)
        assertEquals("[[Journal]]", InternalLinks.linkTo(notes, a, "a"))
        assertEquals("[[*Courses]]", InternalLinks.linkTo(notes, b, "a"))
        assertNull(InternalLinks.linkTo(notes, twin, "b"))
        // In another notebook: Emacs would not find it by its title
        assertNull(InternalLinks.linkTo(notes, target, "a"))
        assertNull(InternalLinks.linkTo(notes, dated, "a"))
    }

    @Test
    fun targetsAndNamesAreSuggested() {
        val n = note("Budget", "<<départ>> et <<<radio>>>\n#+NAME: dépenses\n| a |")
        assertEquals(listOf("départ", "dépenses"), InternalLinks.names(listOf(n), "dé").map { it.name })
        assertEquals(listOf(true, false), InternalLinks.names(listOf(n), "").map { it.target })
    }

    @Test
    fun aTargetBeingWrittenIsOfferedTheLinksWaitingForOne() {
        assertEquals(InternalLinks.Query(5, 12, "éch"), InternalLinks.targetQueryAt("Voir <<éch>> ici", 10))
        assertEquals(InternalLinks.Query(0, 4, ""), InternalLinks.targetQueryAt("<<>>", 2))
        assertNull(InternalLinks.targetQueryAt("<<<radio>>>", 5))
        assertNull(InternalLinks.targetQueryAt("<<fait>> puis", 13))
        val a = note("Séance", "#+transclude: [[échauffement]]\nVoir [[Plan]], [[*Absente]], [[https://x.org][site]]").copy(fileUri = "a", index = 0)
        val b = note("Plan").copy(fileUri = "a", index = 1)
        assertEquals(listOf("échauffement"), InternalLinks.waiting(listOf(a, b)).map { it.name })
    }

    @Test
    fun aRenamedTargetOrNameIsFollowed() {
        assertEquals("objectif" to "but", InternalLinks.renamedName("<<objectif>>Viser 50.", "<<but>>Viser 50."))
        assertEquals("reps" to "séries", InternalLinks.renamedName("#+NAME: reps\n| a |", "#+NAME: séries\n| a |"))
        // Nothing renamed, or two at once
        assertNull(InternalLinks.renamedName("<<a>> x", "<<a>> y"))
        assertNull(InternalLinks.renamedName("<<a>> <<b>>", "<<c>> <<d>>"))
        assertEquals(
            "[[but]], [[but][le but]], [[objectifs]]\n#+transclude: [[but]] :lines 1-2\n#+TBLFM: \$2=remote(but, @2\$1)",
            InternalLinks.renameNameLinks(
                "[[objectif]], [[objectif][le but]], [[objectifs]]\n#+transclude: [[objectif]] :lines 1-2\n#+TBLFM: \$2=remote(objectif, @2\$1)",
                "objectif",
                "but",
            ),
        )
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
