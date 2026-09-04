package dev.astoris.ursa.core.wear

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class PlayWearSessionSender : WearSessionSender {
    override suspend fun send(
        context: Context,
        transfer: WearSessionTransfer,
    ): WearSessionSendResult {
        val capability = Wearable.getCapabilityClient(context)
            .getCapability(WearSessionTransfer.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .awaitResult().getOrNull()
            ?: return WearSessionSendResult.Failure(WearSessionSendError.TRANSFER_FAILED)
        if (capability.nodes.isEmpty()) {
            return WearSessionSendResult.Failure(WearSessionSendError.NO_REACHABLE_WATCH)
        }
        val messageClient = Wearable.getMessageClient(context)
        val delivered = capability.nodes.count { node ->
            messageClient.sendMessage(
                node.id,
                WearSessionTransfer.MESSAGE_PATH,
                transfer.encode(),
            ).awaitResult().isSuccess
        }
        return if (delivered > 0) {
            WearSessionSendResult.Success(delivered)
        } else {
            WearSessionSendResult.Failure(WearSessionSendError.TRANSFER_FAILED)
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): Result<T> = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(Result.success(value))
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resume(Result.failure(error))
    }
}
