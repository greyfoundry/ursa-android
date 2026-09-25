package dev.astoris.ursa.ui.navigation

import androidx.navigation3.runtime.NavKey
import dev.astoris.ursa.ui.AppRoute
import dev.astoris.ursa.ui.MainTab
import kotlinx.serialization.Serializable

/** Non-secret keys for the current app gates and authenticated screen shell. */
@Serializable
sealed interface UrsaRoute : NavKey {
    @Serializable
    data object Locked : UrsaRoute

    @Serializable
    data object Startup : UrsaRoute

    @Serializable
    data object Login : UrsaRoute

    @Serializable
    data class StatusPages(val pageId: String?) : UrsaRoute

    @Serializable
    data object ConnectionManager : UrsaRoute

    @Serializable
    data class ConnectionSetup(val editing: Boolean) : UrsaRoute

    @Serializable
    data class Main(
        val tab: MainSection,
        val selectedMonitorId: Int? = null,
    ) : UrsaRoute

    @Serializable
    data class MonitorDetail(val monitorId: Int) : UrsaRoute

    @Serializable
    data class MonitorEditor(val monitorId: Int?) : UrsaRoute

    @Serializable
    data object Kiosk : UrsaRoute
}

@Serializable
enum class MainSection {
    MONITORS,
    NOTIFICATIONS,
    SETTINGS,
}

data class LegacyRouteState(
    val locked: Boolean,
    val startupReady: Boolean,
    val statusPageMode: Boolean,
    val selectedStatusPageId: String?,
    val connectionManagerMode: Boolean,
    val addingConnection: Boolean,
    val editingConnection: Boolean,
    val hasSession: Boolean,
    val monitorEditorOpen: Boolean,
    val monitorEditorId: Int?,
    val kioskMode: Boolean,
    val selectedMonitorId: Int?,
    val expanded: Boolean,
    val mainTab: MainTab,
)

/** Mirrors the released top-level routing precedence while screens migrate incrementally. */
fun LegacyRouteState.toRoute(): UrsaRoute = when {
    locked -> UrsaRoute.Locked
    !startupReady -> UrsaRoute.Startup
    statusPageMode -> UrsaRoute.StatusPages(selectedStatusPageId)
    connectionManagerMode && addingConnection -> UrsaRoute.ConnectionSetup(editingConnection)
    connectionManagerMode -> UrsaRoute.ConnectionManager
    !hasSession -> UrsaRoute.Login
    monitorEditorOpen -> UrsaRoute.MonitorEditor(monitorEditorId)
    kioskMode -> UrsaRoute.Kiosk
    selectedMonitorId != null && !expanded -> UrsaRoute.MonitorDetail(selectedMonitorId)
    else -> UrsaRoute.Main(
        tab = mainTab.toMainSection(),
        selectedMonitorId = selectedMonitorId.takeIf {
            expanded && mainTab == MainTab.MONITORS
        },
    )
}

/**
 * Builds the released parent/child history for Navigation 3 without making the
 * navigation layer a second owner of product state.
 */
fun LegacyRouteState.toNavigationStack(): List<UrsaRoute> {
    val active = toRoute()
    val signedInRoot = if (hasSession) {
        UrsaRoute.Main(mainTab.toMainSection())
    } else {
        UrsaRoute.Login
    }
    return when (active) {
        UrsaRoute.Locked,
        UrsaRoute.Startup,
        UrsaRoute.Login,
        is UrsaRoute.Main,
        -> listOf(active)

        is UrsaRoute.StatusPages -> listOf(signedInRoot, active)
        UrsaRoute.ConnectionManager -> listOf(signedInRoot, active)
        is UrsaRoute.ConnectionSetup -> listOf(signedInRoot, UrsaRoute.ConnectionManager, active)
        is UrsaRoute.MonitorDetail -> listOf(UrsaRoute.Main(MainSection.MONITORS), active)
        is UrsaRoute.MonitorEditor -> listOf(UrsaRoute.Main(MainSection.MONITORS), active)
        UrsaRoute.Kiosk -> listOf(UrsaRoute.Main(MainSection.MONITORS), active)
    }
}

fun MainTab.toMainSection(): MainSection = when (this) {
    MainTab.MONITORS -> MainSection.MONITORS
    MainTab.NOTIFICATIONS -> MainSection.NOTIFICATIONS
    MainTab.SETTINGS -> MainSection.SETTINGS
}

fun MainSection.toMainTab(): MainTab = when (this) {
    MainSection.MONITORS -> MainTab.MONITORS
    MainSection.NOTIFICATIONS -> MainTab.NOTIFICATIONS
    MainSection.SETTINGS -> MainTab.SETTINGS
}

/** Legacy URSA deep links are already strict, scoped, non-secret typed keys. */
fun AppRoute.toNavKey(): NavKey = this
