package com.tendril.app.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.tendril.app.generated.resources.Res
import com.tendril.app.generated.resources.nav_calendar
import com.tendril.app.generated.resources.nav_pages
import com.tendril.app.generated.resources.nav_road_map
import com.tendril.app.generated.resources.nav_settings
import com.tendril.app.generated.resources.nav_tasks_habits
import org.jetbrains.compose.resources.StringResource

/** The five tabs of the Workbench nav shell (spec §1 / §2.2). Order here is the tab order. */
enum class WorkbenchDestination(
    val route: String,
    val labelRes: StringResource,
    val icon: ImageVector,
) {
    PAGES("pages", Res.string.nav_pages, Icons.Outlined.Description),
    CALENDAR("calendar", Res.string.nav_calendar, Icons.Outlined.CalendarMonth),
    TASKS_HABITS("tasks_habits", Res.string.nav_tasks_habits, Icons.Outlined.CheckCircle),
    ROAD_MAP("road_map", Res.string.nav_road_map, Icons.Outlined.AccountTree),
    SETTINGS("settings", Res.string.nav_settings, Icons.Outlined.Settings),
}
