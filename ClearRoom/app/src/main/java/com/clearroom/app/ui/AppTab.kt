package com.clearroom.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.clearroom.app.R

/** The five bottom-navigation tabs, in the order the doc's "Screens" table lists them. */
enum class AppTab(@param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int) {
    ROOM(R.string.tab_room, R.drawable.ic_tab_room),
    MEASURE(R.string.tab_measure, R.drawable.ic_tab_measure),
    RESULTS(R.string.tab_results, R.drawable.ic_tab_results),
    FIXES(R.string.tab_fixes, R.drawable.ic_tab_fixes),
    LOG(R.string.tab_log, R.drawable.ic_tab_log),
}
