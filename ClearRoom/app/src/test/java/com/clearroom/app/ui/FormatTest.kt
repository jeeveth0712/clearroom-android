package com.clearroom.app.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {

    @Test
    fun `whole metres have no decimals`() {
        assertEquals("9", 9.0.formatMetres())
        assertEquals("60", 60.0.formatMetres())
    }

    @Test
    fun `decimals keep up to two places without trailing zeros`() {
        assertEquals("3.5", 3.5.formatMetres())
        assertEquals("3.25", 3.25.formatMetres())
        assertEquals("7.33", 7.333.formatMetres())
        assertEquals("0.1", 0.1.formatMetres())
    }

    @Test
    fun `values that round to a whole number show as whole`() {
        assertEquals("8", 7.999.formatMetres())
    }

    @Test
    fun `a decimal point is used even where the phone's language writes a comma`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("2.5", 2.5.formatMetres())
        } finally {
            Locale.setDefault(original)
        }
    }
}
