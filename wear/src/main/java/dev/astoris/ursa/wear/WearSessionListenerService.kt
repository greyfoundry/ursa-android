package dev.astoris.ursa.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/** Receives an ephemeral session handoff from the same signed app on a paired phone. */
class WearSessionListenerService : WearableListenerService() {
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearPairingPayload.MESSAGE_PATH) return
        val payload = WearPairingPayload.parse(messageEvent.data) ?: return
        WearPrefs.setPairedSession(this, payload)
        WearSurfaceUpdates.request(this)
    }
}
