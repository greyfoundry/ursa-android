package dev.astoris.ursa.core.network

import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionFailureTest {
    @Test
    fun deviceOfflineTakesPriority() {
        assertEquals(
            ConnectionFailureReason.DEVICE_OFFLINE,
            ConnectionFailure.classify(ConnectException("refused"), networkAvailable = false),
        )
    }

    @Test
    fun commonTransportFailuresAreActionable() {
        assertEquals(
            ConnectionFailureReason.CERTIFICATE,
            ConnectionFailure.classify(SSLHandshakeException("trust anchor")),
        )
        assertEquals(
            ConnectionFailureReason.SERVER_UNREACHABLE,
            ConnectionFailure.classify(UnknownHostException("host")),
        )
        assertEquals(
            ConnectionFailureReason.INCOMPATIBLE_RESPONSE,
            ConnectionFailure.classify(IllegalStateException("Unexpected response from server")),
        )
    }
}
