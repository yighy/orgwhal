package io.github.yighy.orgwhal.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FitColumnsTest {

    @Test
    fun aTableThatFitsKeepsItsWidths() {
        assertArrayEquals(intArrayOf(100, 50, 200), fitColumns(intArrayOf(100, 50, 200), 400, 72))
    }

    @Test
    fun theWidestColumnsShareWhatIsLeft() {
        // 50 kept, the two others sharing the 350 left
        assertArrayEquals(intArrayOf(175, 50, 175), fitColumns(intArrayOf(300, 50, 600), 400, 72))
        // 150 fits within its share, only the widest narrows
        assertArrayEquals(intArrayOf(150, 50, 200), fitColumns(intArrayOf(150, 50, 600), 400, 72))
    }

    @Test
    fun notBelowTheFloor() {
        assertArrayEquals(intArrayOf(72, 72, 72), fitColumns(intArrayOf(300, 300, 300), 120, 72))
    }
}
