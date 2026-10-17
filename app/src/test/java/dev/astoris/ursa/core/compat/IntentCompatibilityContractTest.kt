package dev.astoris.ursa.core.compat

import dev.astoris.ursa.core.push.MonitorActionReceiver
import dev.astoris.ursa.core.push.OverallStatusService
import dev.astoris.ursa.core.push.PushAlertActionReceiver
import dev.astoris.ursa.core.push.PushEventPolicy
import dev.astoris.ursa.core.push.PushSeverity
import dev.astoris.ursa.core.push.PushSeverityPolicy
import dev.astoris.ursa.core.push.UrsaPushService
import dev.astoris.ursa.core.work.CertExpiryWorker
import dev.astoris.ursa.ui.AppDeepLink
import dev.astoris.ursa.ui.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class IntentCompatibilityContractTest {

    @Test fun released_deep_links_actions_and_channels_remain_stable() {
        val contract = fixture("intent_contracts_v1.tsv")
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .associate { line ->
                val columns = line.split('\t')
                require(columns.size == 2) { "Invalid intent contract row: $line" }
                columns[0] to columns[1]
            }

        assertEquals(AppRoute.Push, AppDeepLink.parse(contract.getValue("deep_link_push")))
        assertEquals(AppRoute.Settings, AppDeepLink.parse(contract.getValue("deep_link_settings")))
        assertEquals(
            AppRoute.StatusPage("public-demo"),
            AppDeepLink.parse(contract.getValue("deep_link_status_page")),
        )
        assertEquals(contract.getValue("notification_pause"), MonitorActionReceiver.ACTION_PAUSE)
        assertEquals(contract.getValue("notification_resume"), MonitorActionReceiver.ACTION_RESUME)
        assertEquals(
            contract.getValue("notification_acknowledge"),
            PushAlertActionReceiver.ACTION_ACKNOWLEDGE,
        )
        assertEquals(contract.getValue("notification_snooze"), PushAlertActionReceiver.ACTION_SNOOZE)
        assertEquals(
            contract.getValue("channel_monitor_critical"),
            PushSeverityPolicy.route(PushSeverity.CRITICAL).channelId,
        )
        assertEquals(
            contract.getValue("channel_monitor_standard"),
            PushSeverityPolicy.route(PushSeverity.STANDARD).channelId,
        )
        assertEquals(
            contract.getValue("channel_monitor_silent"),
            PushSeverityPolicy.route(PushSeverity.SILENT).channelId,
        )
        assertEquals(contract.getValue("channel_monitor_recovery"), PushEventPolicy.RECOVERY_ROUTE.channelId)
        assertEquals(contract.getValue("channel_monitor_maintenance"), PushEventPolicy.MAINTENANCE_ROUTE.channelId)
        assertEquals(contract.getValue("channel_updates"), PushEventPolicy.UPDATE_ROUTE.channelId)
        assertEquals(contract.getValue("channel_overall_status"), OverallStatusService.CHANNEL_ID)
        assertEquals(contract.getValue("channel_certificate_expiry"), CertExpiryWorker.CHANNEL_ID)
        assertEquals(contract.getValue("channel_monitor_critical"), UrsaPushService.CHANNEL_ID)
    }

    @Test fun generated_scoped_routes_keep_their_released_shapes() {
        val url = "https://kuma.example.test/base"
        val scope = AppDeepLink.serverScope(url)

        assertEquals("ursa://connection/$scope", AppDeepLink.connection(url))
        assertEquals("ursa://monitor/$scope/7", AppDeepLink.monitor(url, 7))
        assertEquals("ursa://incident/$scope/7", AppDeepLink.incident(url, 7))
    }

    private fun fixture(name: String): String = requireNotNull(
        javaClass.getResource("/fixtures/$name"),
    ) { "Missing compatibility fixture: $name" }.readText()
}
