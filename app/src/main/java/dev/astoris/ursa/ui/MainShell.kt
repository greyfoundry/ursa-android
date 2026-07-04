package dev.astoris.ursa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.astoris.ursa.R
import dev.astoris.ursa.ui.monitors.MonitorListScreen
import dev.astoris.ursa.ui.push.PushScreen
import dev.astoris.ursa.ui.settings.SettingsScreen

/**
 * The signed-in shell: a bottom navigation bar over the three primary destinations,
 * matching the app's design (Monitors, Notifications, Settings). Each destination
 * screen supplies its own top bar.
 */
@Composable
fun MainShell(vm: UrsaViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavItem(tab, MainTab.MONITORS, R.drawable.ic_nav_monitors, R.string.nav_monitors, vm)
                NavItem(tab, MainTab.NOTIFICATIONS, R.drawable.ic_nav_notifications, R.string.nav_notifications, vm)
                NavItem(tab, MainTab.SETTINGS, R.drawable.ic_nav_settings, R.string.nav_settings, vm)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                MainTab.MONITORS -> MonitorListScreen(vm)
                MainTab.NOTIFICATIONS -> PushScreen(vm)
                MainTab.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    current: MainTab,
    target: MainTab,
    iconRes: Int,
    labelRes: Int,
    vm: UrsaViewModel,
) {
    NavigationBarItem(
        selected = current == target,
        onClick = { vm.selectTab(target) },
        icon = { Icon(painterResource(iconRes), contentDescription = null) },
        label = { Text(stringResource(labelRes)) },
    )
}
