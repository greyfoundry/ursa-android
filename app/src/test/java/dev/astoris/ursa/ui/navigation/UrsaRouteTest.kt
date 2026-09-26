package dev.astoris.ursa.ui.navigation

import androidx.navigation3.runtime.NavKey
import dev.astoris.ursa.ui.AppRoute
import dev.astoris.ursa.ui.MainTab
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UrsaRouteTest {
    private fun state(
        locked: Boolean = false,
        startupReady: Boolean = true,
        statusPageMode: Boolean = false,
        selectedStatusPageId: String? = null,
        connectionManagerMode: Boolean = false,
        addingConnection: Boolean = false,
        editingConnection: Boolean = false,
        hasSession: Boolean = true,
        monitorEditorOpen: Boolean = false,
        monitorEditorId: Int? = null,
        kioskMode: Boolean = false,
        selectedMonitorId: Int? = null,
        mainTab: MainTab = MainTab.MONITORS,
    ) = LegacyRouteState(
        locked,
        startupReady,
        statusPageMode,
        selectedStatusPageId,
        connectionManagerMode,
        addingConnection,
        editingConnection,
        hasSession,
        monitorEditorOpen,
        monitorEditorId,
        kioskMode,
        selectedMonitorId,
        mainTab,
    )

    @Test fun rootGatesPreserveReleasedPrecedence() {
        assertEquals(UrsaRoute.Locked, state(locked = true, startupReady = false).toRoute())
        assertEquals(UrsaRoute.Startup, state(startupReady = false, statusPageMode = true).toRoute())
        assertEquals(
            UrsaRoute.StatusPages(null),
            state(statusPageMode = true, connectionManagerMode = true).toRoute(),
        )
        assertEquals(
            UrsaRoute.ConnectionSetup(editing = true),
            state(connectionManagerMode = true, addingConnection = true, editingConnection = true).toRoute(),
        )
        assertEquals(
            UrsaRoute.ConnectionManager,
            state(connectionManagerMode = true, hasSession = false).toRoute(),
        )
        assertEquals(UrsaRoute.Login, state(hasSession = false, monitorEditorOpen = true).toRoute())
    }

    @Test fun childRoutesRetainTheirReleasedParentHistory() {
        assertEquals(
            listOf(
                UrsaRoute.Main(MainSection.SETTINGS),
                UrsaRoute.ConnectionManager,
                UrsaRoute.ConnectionSetup(editing = true),
            ),
            state(
                connectionManagerMode = true,
                addingConnection = true,
                editingConnection = true,
                mainTab = MainTab.SETTINGS,
            ).toNavigationStack(),
        )
        assertEquals(
            listOf(UrsaRoute.Login, UrsaRoute.StatusPages("public-demo")),
            state(
                statusPageMode = true,
                selectedStatusPageId = "public-demo",
                hasSession = false,
            ).toNavigationStack(),
        )
        assertEquals(
            listOf(UrsaRoute.Main(MainSection.MONITORS, selectedMonitorId = 42)),
            state(selectedMonitorId = 42).toNavigationStack(),
        )
    }

    @Test fun authenticatedRoutesPreserveEditorKioskAndAdaptiveSelectionOrder() {
        assertEquals(
            UrsaRoute.MonitorEditor(42),
            state(monitorEditorOpen = true, monitorEditorId = 42, kioskMode = true).toRoute(),
        )
        assertEquals(UrsaRoute.Kiosk, state(kioskMode = true, selectedMonitorId = 42).toRoute())
        assertEquals(
            UrsaRoute.Main(MainSection.MONITORS, selectedMonitorId = 42),
            state(selectedMonitorId = 42).toRoute(),
        )
    }

    @Test fun currentTabsMapToTypedDestinations() {
        assertEquals(
            UrsaRoute.Main(MainSection.HOME),
            state(mainTab = MainTab.HOME, selectedMonitorId = 42).toRoute(),
        )
        assertEquals(UrsaRoute.Main(MainSection.MONITORS), state().toRoute())
        assertEquals(
            UrsaRoute.Main(MainSection.NOTIFICATIONS),
            state(mainTab = MainTab.NOTIFICATIONS, selectedMonitorId = 42).toRoute(),
        )
        assertEquals(
            UrsaRoute.Main(MainSection.SETTINGS),
            state(mainTab = MainTab.SETTINGS).toRoute(),
        )
        assertEquals(
            UrsaRoute.Main(MainSection.INCIDENTS),
            state(mainTab = MainTab.INCIDENTS).toRoute(),
        )
    }

    @Test fun everyLegacyDeepLinkIsAlreadyANavigationKey() {
        val routes = listOf(
            AppRoute.Push,
            AppRoute.Settings,
            AppRoute.Connection("0123456789abcdef"),
            AppRoute.Monitor("0123456789abcdef", 42),
            AppRoute.Incident("0123456789abcdef", 42),
            AppRoute.StatusPage("public-demo"),
        )

        routes.forEach { route -> assertSame(route, route.toNavKey() as AppRoute) }
        routes.forEach { route -> assertSame(route, route as NavKey) }
    }

    @Test fun navigationStackRoundTripsForProcessRestoration() {
        val stack = listOf<UrsaRoute>(
            UrsaRoute.Main(MainSection.HOME),
            UrsaRoute.Main(MainSection.MONITORS, selectedMonitorId = 42),
            UrsaRoute.Main(MainSection.INCIDENTS),
            UrsaRoute.MonitorDetail(42),
        )

        assertEquals(stack, Json.decodeFromString<List<UrsaRoute>>(Json.encodeToString(stack)))
    }
}
