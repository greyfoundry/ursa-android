package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.MonitorTagAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorDraftTransferTest {

    @Test
    fun cloneRemovesIdentityAssignmentsServerSelectorsAndSecrets() {
        val source = MonitorDraft.create("websocket-upgrade").copy(
            id = 42,
            name = "Private socket",
            active = true,
            notificationIds = setOf(3),
            parentId = 9,
            tagAssignments = listOf(MonitorTagAssignment(2, 42, "prod", "#f00")),
            websocketHeaders = listOf(MonitorHeaderDraft("Authorization", "secret", "Authorization", true)),
            websocketBasicPassword = "password",
            websocketHasSavedBasicPassword = true,
            websocketBearerToken = "token",
            websocketHasSavedBearerToken = true,
            websocketOAuthClientSecret = "client-secret",
            websocketHasSavedOAuthClientSecret = true,
            websocketTlsPrivateKey = "private-key",
            websocketHasSavedTlsPrivateKey = true,
            databaseConnectionString = "postgres://secret",
            databaseHasSavedConnectionString = true,
            mqttPassword = "mqtt-secret",
            mqttHasSavedPassword = true,
            kafkaSecretAccessKey = "aws-secret",
            kafkaHasSavedSecretAccessKey = true,
            dockerContainer = "container-id",
            dockerHostId = 7,
            remoteBrowserId = 8,
            radiusSecret = "radius-secret",
            radiusHasSavedSecret = true,
            sftpPrivateKey = "sftp-key",
            sftpHasSavedPrivateKey = true,
        )

        val clone = MonitorDraftTransfer.clone(source, "Copy of Private socket")

        assertNull(clone.id)
        assertTrue(clone.transferReview)
        assertEquals("Copy of Private socket", clone.name)
        assertFalse(clone.active)
        assertTrue(clone.notificationIds.isEmpty())
        assertNull(clone.parentId)
        assertTrue(clone.tagAssignments.isEmpty())
        assertEquals(listOf("Authorization"), clone.websocketHeaders.map(MonitorHeaderDraft::name))
        assertTrue(clone.websocketHeaders.all { it.value.isEmpty() && !it.hasSavedValue })
        assertTrue(clone.websocketBasicPassword.isEmpty())
        assertFalse(clone.websocketHasSavedBasicPassword)
        assertTrue(clone.websocketBearerToken.isEmpty())
        assertFalse(clone.websocketHasSavedBearerToken)
        assertTrue(clone.websocketOAuthClientSecret.isEmpty())
        assertFalse(clone.websocketHasSavedOAuthClientSecret)
        assertTrue(clone.websocketTlsPrivateKey.isEmpty())
        assertFalse(clone.websocketHasSavedTlsPrivateKey)
        assertTrue(clone.databaseConnectionString.isEmpty())
        assertFalse(clone.databaseHasSavedConnectionString)
        assertTrue(clone.mqttPassword.isEmpty())
        assertFalse(clone.mqttHasSavedPassword)
        assertTrue(clone.kafkaSecretAccessKey.isEmpty())
        assertFalse(clone.kafkaHasSavedSecretAccessKey)
        assertTrue(clone.dockerContainer.isEmpty())
        assertNull(clone.dockerHostId)
        assertNull(clone.remoteBrowserId)
        assertTrue(clone.radiusSecret.isEmpty())
        assertFalse(clone.radiusHasSavedSecret)
        assertTrue(clone.sftpPrivateKey.isEmpty())
        assertFalse(clone.sftpHasSavedPrivateKey)
    }

    @Test
    fun presetsAcceptOnlyCredentialFreeTypes() {
        val smtp = MonitorDraft.create("smtp").copy(name = "Mail", endpoint = "mail.example", active = true)
        val http = MonitorDraft.create("http").copy(name = "Site", endpoint = "https://example.com")

        val preset = MonitorDraftTransfer.preset(smtp)

        assertEquals("smtp", preset?.type)
        assertFalse(preset!!.active)
        assertEquals("http", MonitorDraftTransfer.preset(http)?.type)
        assertNull(MonitorDraftTransfer.preset(MonitorDraft.create("mqtt")))
        assertNull(MonitorDraftTransfer.preset(MonitorDraft.create("docker")))
    }
}
