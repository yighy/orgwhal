package io.github.yighy.orgwhal.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InactiveDateTest {

    @Test
    fun aJournalDateIsInactiveWithoutTime() {
        assertEquals("[2026-10-13 Tue]", OrgTime.inactiveDate(LocalDate.of(2026, 10, 13)))
    }

    @Test
    fun anActiveTimestampBecomesInactive() {
        assertEquals("[2026-10-13 Tue]", OrgTime.toInactive("<2026-10-13 Tue>"))
        assertEquals("[2026-10-13 Tue]", OrgTime.toInactive("[2026-10-13 Tue]"))
    }
}
