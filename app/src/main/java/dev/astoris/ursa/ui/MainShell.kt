package dev.astoris.ursa.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldPredictiveBackHandler
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.data.model.Monitor
import dev.astoris.ursa.ui.browse.BrowseScreen
import dev.astoris.ursa.ui.home.HomeScreen
import dev.astoris.ursa.ui.incidents.IncidentsScreen
import dev.astoris.ursa.ui.monitors.MonitorDetailScreen
import dev.astoris.ursa.ui.monitors.MonitorListScreen
import dev.astoris.ursa.ui.push.PushScreen
import dev.astoris.ursa.ui.settings.SettingsScreen
import dev.astoris.ursa.ui.theme.UrsaMotion

/**
 * The signed-in shell. Material's navigation suite selects a bottom bar or rail from
 * the live window posture, while the monitor destination owns its list-detail state.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainShell(vm: UrsaViewModel, selected: Monitor? = null) {
    val tab by vm.tab.collectAsStateWithLifecycle()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            NavItem(tab, MainTab.HOME, R.drawable.ic_nav_home, R.string.nav_home, vm)
            NavItem(tab, MainTab.MONITORS, R.drawable.ic_nav_monitors, R.string.nav_monitors, vm)
            NavItem(tab, MainTab.INCIDENTS, R.drawable.ic_nav_incidents, R.string.nav_incidents, vm)
            NavItem(tab, MainTab.BROWSE, R.drawable.ic_nav_browse, R.string.nav_browse, vm)
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(UrsaMotion.standard()) togetherWith fadeOut(UrsaMotion.fast())
                },
                label = "main destination",
            ) { destination ->
                when (destination) {
                    MainTab.HOME -> HomeScreen(vm)
                    MainTab.MONITORS -> MonitorListDetailPane(vm, selected)
                    MainTab.INCIDENTS -> IncidentsScreen(vm)
                    MainTab.BROWSE -> BrowseScreen(vm)
                    MainTab.NOTIFICATIONS -> PushScreen(vm)
                    MainTab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun MonitorListDetailPane(
    vm: UrsaViewModel,
    selected: Monitor?,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Int>()
    val selectedId = selected?.id
    val currentDestination = navigator.currentDestination
    val currentPane = currentDestination?.pane
    val currentMonitorId = currentDestination?.contentKey

    LaunchedEffect(selectedId) {
        when {
            selectedId != null && (
                currentPane != ListDetailPaneScaffoldRole.Detail || currentMonitorId != selectedId
            ) -> navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, selectedId)

            selectedId == null && currentPane == ListDetailPaneScaffoldRole.Detail -> {
                navigator.navigateBack(BackNavigationBehavior.PopUntilCurrentDestinationChange)
            }
        }
    }
    LaunchedEffect(currentPane, currentMonitorId) {
        if (currentPane == ListDetailPaneScaffoldRole.List && selectedId != null) {
            vm.back()
        }
    }

    ThreePaneScaffoldPredictiveBackHandler(
        navigator = navigator,
        backBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
    )
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        listPane = {
            AnimatedPane { MonitorListScreen(vm) }
        },
        detailPane = {
            AnimatedPane {
                val detailId = navigator.currentDestination?.contentKey
                val detail = selected?.takeIf { it.id == detailId }
                if (detail == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.tablet_choose_monitor),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    MonitorDetailScreen(
                        vm = vm,
                        monitor = detail,
                        showBack = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] ==
                            PaneAdaptedValue.Hidden,
                        handleSystemBack = false,
                    )
                }
            }
        },
    )
}

private fun NavigationSuiteScope.NavItem(
    current: MainTab,
    target: MainTab,
    iconRes: Int,
    labelRes: Int,
    vm: UrsaViewModel,
) {
    item(
        selected = current == target,
        onClick = { vm.selectTab(target) },
        icon = { Icon(painterResource(iconRes), contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
    )
}
