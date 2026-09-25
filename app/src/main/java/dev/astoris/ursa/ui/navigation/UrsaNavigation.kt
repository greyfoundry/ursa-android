package dev.astoris.ursa.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.data.model.ServerConnection
import dev.astoris.ursa.ui.MonitorEditorUiState
import dev.astoris.ursa.ui.StartupLoadingScreen
import dev.astoris.ursa.ui.UrsaViewModel
import dev.astoris.ursa.ui.connections.ConnectionManagerScreen
import dev.astoris.ursa.ui.connections.LoginScreen
import dev.astoris.ursa.ui.lock.LockScreen
import dev.astoris.ursa.ui.monitors.KioskScreen
import dev.astoris.ursa.ui.monitors.MonitorDetailScreen
import dev.astoris.ursa.ui.monitors.MonitorEditorScreen
import dev.astoris.ursa.ui.statuspage.StatusPageScreen
import dev.astoris.ursa.ui.MainShell

@Composable
internal fun UrsaNavHost(
    vm: UrsaViewModel,
    routeState: LegacyRouteState,
    selected: Monitor?,
    editingConnection: ServerConnection?,
    monitorEditor: MonitorEditorUiState,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val activity = LocalContext.current.findActivity()
    val backStack = rememberNavBackStack(UrsaRoute.Startup)
    var restorationPending by remember { mutableStateOf(true) }
    val desiredStack = routeState.toNavigationStack()

    LaunchedEffect(desiredStack) {
        val active = desiredStack.last()
        if (active == UrsaRoute.Startup || active == UrsaRoute.Locked) {
            while (backStack.lastOrNull() == UrsaRoute.Startup || backStack.lastOrNull() == UrsaRoute.Locked) {
                backStack.removeLastOrNull()
            }
            backStack.add(active)
            return@LaunchedEffect
        }

        while (backStack.lastOrNull() == UrsaRoute.Startup || backStack.lastOrNull() == UrsaRoute.Locked) {
            backStack.removeLastOrNull()
        }

        if (restorationPending) {
            restorationPending = false
            when (val restored = backStack.lastOrNull()) {
                is UrsaRoute.MonitorDetail -> if (
                    active is UrsaRoute.Main && routeState.hasSession && routeState.selectedMonitorId == null
                ) {
                    vm.select(restored.monitorId)
                    return@LaunchedEffect
                }

                is UrsaRoute.Main -> if (active is UrsaRoute.Main) {
                    var restoredState = false
                    if (restored.tab != active.tab) {
                        vm.selectTab(restored.tab.toMainTab())
                        restoredState = true
                    }
                    if (
                        routeState.hasSession && routeState.selectedMonitorId == null &&
                        restored.selectedMonitorId != null
                    ) {
                        vm.select(restored.selectedMonitorId)
                        restoredState = true
                    }
                    if (restoredState) return@LaunchedEffect
                }

                is UrsaRoute.StatusPages -> if (
                    active is UrsaRoute.Main || active == UrsaRoute.Login
                ) {
                    vm.enterStatusPage()
                    restored.pageId?.let(vm::openStatusPageDeepLink)
                    return@LaunchedEffect
                }

                UrsaRoute.ConnectionManager,
                is UrsaRoute.ConnectionSetup,
                -> if (active is UrsaRoute.Main || active == UrsaRoute.Login) {
                    vm.enterConnectionManager()
                    return@LaunchedEffect
                }

                UrsaRoute.Kiosk -> if (active is UrsaRoute.Main && routeState.hasSession) {
                    vm.enterKioskMode()
                    return@LaunchedEffect
                }

                else -> Unit
            }
        }

        if (backStack.toList() != desiredStack) {
            backStack.clear()
            backStack.addAll(desiredStack)
        }
    }

    // Security/startup gates render synchronously in front of any restored
    // authenticated back stack so process restoration cannot flash private UI.
    if (routeState.locked) {
        LockScreen(vm)
    } else if (!routeState.startupReady) {
        StartupLoadingScreen()
    } else NavDisplay(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        onBack = {
            when (backStack.lastOrNull()) {
                UrsaRoute.Locked,
                UrsaRoute.Startup,
                UrsaRoute.Login,
                is UrsaRoute.Main,
                null,
                -> activity?.finish()

                is UrsaRoute.StatusPages -> {
                    backStack.removeLastOrNull()
                    vm.exitStatusPage()
                }

                UrsaRoute.ConnectionManager -> {
                    backStack.removeLastOrNull()
                    vm.exitConnectionManager()
                }

                is UrsaRoute.ConnectionSetup -> {
                    backStack.removeLastOrNull()
                    vm.cancelAddingConnection()
                }

                is UrsaRoute.MonitorDetail -> {
                    backStack.removeLastOrNull()
                    vm.back()
                }

                is UrsaRoute.MonitorEditor -> {
                    if (monitorEditor !is MonitorEditorUiState.Saving) {
                        backStack.removeLastOrNull()
                        vm.closeMonitorEditor()
                    }
                }

                UrsaRoute.Kiosk -> {
                    backStack.removeLastOrNull()
                    vm.exitKioskMode()
                }
            }
        },
        entryProvider = { key ->
            when (key) {
                UrsaRoute.Locked -> NavEntry(key) { LockScreen(vm) }
                UrsaRoute.Startup -> NavEntry(key) { StartupLoadingScreen() }
                UrsaRoute.Login -> NavEntry(key) { LoginScreen(vm) }
                is UrsaRoute.StatusPages -> NavEntry(key, contentKey = STATUS_PAGES_CONTENT_KEY) {
                    StatusPageScreen(vm, handleRootSystemBack = false)
                }
                UrsaRoute.ConnectionManager -> NavEntry(key) {
                    ConnectionManagerScreen(vm, handleSystemBack = false)
                }
                is UrsaRoute.ConnectionSetup -> NavEntry(key, contentKey = CONNECTION_SETUP_CONTENT_KEY) {
                    LoginScreen(
                        vm = vm,
                        initialConnection = editingConnection,
                        onBack = vm::cancelAddingConnection,
                        onConnected = vm::finishAddingConnection,
                        handleSystemBack = false,
                    )
                }
                is UrsaRoute.Main -> NavEntry(key, contentKey = MAIN_CONTENT_KEY) {
                    MainShell(vm, expanded, selected)
                }
                is UrsaRoute.MonitorDetail -> NavEntry(key) {
                    val monitor = selected?.takeIf { it.id == key.monitorId }
                    if (monitor == null) {
                        StartupLoadingScreen()
                    } else {
                        MonitorDetailScreen(vm, monitor, handleSystemBack = false)
                    }
                }
                is UrsaRoute.MonitorEditor -> NavEntry(key, contentKey = MONITOR_EDITOR_CONTENT_KEY) {
                    MonitorEditorScreen(vm, handleSystemBack = false)
                }
                UrsaRoute.Kiosk -> NavEntry(key) {
                    KioskScreen(vm, handleSystemBack = false)
                }
                else -> error("Unknown route: $key")
            }
        },
        modifier = modifier,
    )
}

private const val MAIN_CONTENT_KEY = "main"
private const val STATUS_PAGES_CONTENT_KEY = "status-pages"
private const val CONNECTION_SETUP_CONTENT_KEY = "connection-setup"
private const val MONITOR_EDITOR_CONTENT_KEY = "monitor-editor"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
