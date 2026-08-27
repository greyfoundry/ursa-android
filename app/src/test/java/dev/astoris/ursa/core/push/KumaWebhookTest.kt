package dev.astoris.ursa.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KumaWebhookTest {

    @Test
    fun ntfyDistributorGetsRawUploadFlagWithoutDroppingQuery() {
        assertEquals(
            "https://push.example/topic?auth=x&up=1",
            KumaWebhook.deliveryUrl(" https://push.example/topic?auth=x ", "io.heckel.ntfy"),
        )
        assertEquals(
            "https://push.example/topic?up=1",
            KumaWebhook.deliveryUrl("https://push.example/topic?up=1", "ntfy"),
        )
    }

    @Test
    fun otherDistributorsKeepValidatedEndpointUnchanged() {
        assertEquals(
            "https://push.example/endpoint",
            KumaWebhook.deliveryUrl("https://push.example/endpoint", "org.example.distributor"),
        )
    }

    @Test
    fun rejectsNonHttpCredentialAndFragmentEndpoints() {
        assertNull(KumaWebhook.deliveryUrl("ftp://push.example/topic", "ntfy"))
        assertNull(KumaWebhook.deliveryUrl("https://user:pass@push.example/topic", "ntfy"))
        assertNull(KumaWebhook.deliveryUrl("https://push.example/topic#secret", "ntfy"))
    }
}
