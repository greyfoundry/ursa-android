package dev.astoris.ursa.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Receives an ephemeral session handoff from the same signed app on a paired phone. */
class WearSessionListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearPairingPayload.CLEAR_MESSAGE_PATH -> WearPrefs.clearPairedSession(this)
            WearPairingPayload.MESSAGE_PATH -> {
                val payload = WearPairingPayload.parseVersion1(messageEvent.data) ?: return
                WearPrefs.setPairedSession(this, payload)
            }
            WearPairingPayload.V2_MESSAGE_PATH -> {
                val payload = WearPairingPayload.parseVersion2(messageEvent.data) ?: return
                WearPrefs.setPairedSession(this, payload)
            }
            else -> return
        }
        WearSurfaceUpdates.request(this)
    }
}
