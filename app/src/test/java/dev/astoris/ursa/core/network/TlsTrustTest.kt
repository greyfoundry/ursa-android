package dev.astoris.ursa.core.network

import org.junit.Assert.fail
import org.junit.Test
import java.security.cert.CertificateException

class TlsTrustTest {

    @Test fun firstCertificateIsPinnedAndAcceptedAgain() {
        val pin = SessionCertificatePin()
        val fingerprint = byteArrayOf(1, 2, 3, 4)

        pin.verifyOrPin(fingerprint)
        fingerprint.fill(9)
        pin.verifyOrPin(byteArrayOf(1, 2, 3, 4))
    }

    @Test fun changedCertificateIsRejected() {
        val pin = SessionCertificatePin()
        pin.verifyOrPin(byteArrayOf(1, 2, 3, 4))

        try {
            pin.verifyOrPin(byteArrayOf(4, 3, 2, 1))
            fail("A changed certificate must be rejected")
        } catch (_: CertificateException) {
            // Expected.
        }
    }
}
