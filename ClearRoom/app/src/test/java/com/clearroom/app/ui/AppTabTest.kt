package com.clearroom.app.ui

import com.clearroom.app.R
import kotlin.test.Test
import kotlin.test.assertEquals

class AppTabTest {

    @Test
    fun `tabs follow the doc's order - Room, Measure, Results, Fixes, Log`() {
        assertEquals(
            listOf(R.string.tab_room, R.string.tab_measure, R.string.tab_results, R.string.tab_fixes, R.string.tab_log),
            AppTab.entries.map { it.labelRes },
        )
    }

    @Test
    fun `each tab has its own icon`() {
        val icons = AppTab.entries.map { it.iconRes }
        assertEquals(icons.size, icons.distinct().size, "tabs share an icon")
    }
}
