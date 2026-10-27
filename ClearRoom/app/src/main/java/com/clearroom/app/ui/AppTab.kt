package com.clearroom.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.clearroom.app.R

/**
 * The five bottom-navigation tabs, in the order the doc's "Screens" table lists them. [titleRes]
 * heads the tab's screen ("Best changes" for Fixes); [labelRes] names it in the navigation bar.
 */
enum class AppTab(@param:StringRes val labelRes: Int, @param:DrawableRes val iconRes: Int, @param:StringRes val titleRes: Int) {
    ROOM(R.string.tab_room, R.drawable.ic_tab_room, R.string.title_room),
    MEASURE(R.string.tab_measure, R.drawable.ic_tab_measure, R.string.title_measure),
    RESULTS(R.string.tab_results, R.drawable.ic_tab_results, R.string.title_results),
    FIXES(R.string.tab_fixes, R.drawable.ic_tab_fixes, R.string.title_fixes),
    LOG(R.string.tab_log, R.drawable.ic_tab_log, R.string.title_log),
}
