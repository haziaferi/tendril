package com.tendril.app.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B§13.4 14a — the breakpoint is one number, and this pins which side of it each width lands. */
class ShellLayoutTest {

    @Test
    fun `below 840 dp is the bar, from 840 dp the rail`() {
        assertEquals(ShellLayout.BAR, shellLayoutFor(0f))
        assertEquals(ShellLayout.BAR, shellLayoutFor(411f))   // a phone, portrait
        assertEquals(ShellLayout.BAR, shellLayoutFor(839.9f))
        assertEquals(ShellLayout.RAIL, shellLayoutFor(840f))
        assertEquals(ShellLayout.RAIL, shellLayoutFor(1200f))
    }

    @Test
    fun `a width that is not a number is the bar, never a crash`() {
        assertEquals(ShellLayout.BAR, shellLayoutFor(Float.NaN))
    }

    @Test
    fun `a sheet changes form exactly where the shell does`() {
        for (width in listOf(0f, 411f, 839.9f, 840f, 1200f, Float.NaN)) {
            val expected = if (shellLayoutFor(width) == ShellLayout.RAIL) SheetForm.SLIDE_OVER else SheetForm.BOTTOM
            assertEquals("at $width dp", expected, sheetFormFor(width))
        }
        assertEquals(SheetForm.BOTTOM, sheetFormFor(839.9f))
        assertEquals(SheetForm.SLIDE_OVER, sheetFormFor(840f))
    }
}

/** The window frame's encoding — a round trip, garbage, and the floor. */
class WindowFrameTest {

    @Test
    fun `encode and decode round-trip`() {
        val frame = WindowFrame(1280, 900, 40, 60)
        assertEquals(frame, WindowFrame.decode(frame.encode()))
        assertEquals("1280,900,40,60", frame.encode())
        assertTrue(frame.positioned)
    }

    @Test
    fun `the default is unpositioned so the OS places it`() {
        assertFalse(DEFAULT_WINDOW.positioned)
        assertEquals(DEFAULT_WINDOW, WindowFrame.decode(DEFAULT_WINDOW.encode()))
    }

    @Test
    fun `absent or unreadable input decodes to null`() {
        assertNull(WindowFrame.decode(null))
        assertNull(WindowFrame.decode(""))
        assertNull(WindowFrame.decode("garbage"))
        assertNull(WindowFrame.decode("1200,800"))
        assertNull(WindowFrame.decode("1200,800,0,0,7"))
        assertNull(WindowFrame.decode("a,b,c,d"))
        assertNull(WindowFrame.decode("0,800,0,0"))
        assertNull(WindowFrame.decode("1200,-1,0,0"))
    }

    @Test
    fun `a stored size below the minimum is raised to it`() {
        assertEquals(WindowFrame(800, 600, 10, 10), WindowFrame.decode("300,200,10,10"))
    }
}
