package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TemplatesTest {

    private val now = LocalDateTime.of(2026, 10, 18, 9, 30)

    @Test
    fun placeholdersAreFilled() {
        assertEquals("Séance [2026-10-18 Sun] at [2026-10-18 Sun 09:30]", Templates.fill("Séance %u at %U%?", now))
        assertEquals("<2026-10-18 Sun> <2026-10-18 Sun 09:30>", Templates.fill("%t %T", now))
    }
}
