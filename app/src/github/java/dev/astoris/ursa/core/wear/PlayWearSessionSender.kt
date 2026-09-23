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
        val capabilityClient = Wearable.getCapabilityClient(context)
        val version2 = capabilityClient
            .getCapability(WearSessionTransfer.V2_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .awaitResult()
        val version1 = capabilityClient
            .getCapability(WearSessionTransfer.V1_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .awaitResult()
        if (version1.isFailure && version2.isFailure) {
            return WearSessionSendResult.Failure(WearSessionSendError.TRANSFER_FAILED)
        }
        if (version2.isFailure && !transfer.canUseLegacyProtocol) {
            return WearSessionSendResult.Failure(WearSessionSendError.TRANSFER_FAILED)
        }
        val version1Nodes = version1.getOrNull()?.nodes.orEmpty().associateBy { it.id }
        val version2Nodes = version2.getOrNull()?.nodes.orEmpty().associateBy { it.id }
        val targets = WearProtocolRouting.plan(
            version1Nodes = version1Nodes.keys,
            version2Nodes = version2Nodes.keys,
            allowLegacy = transfer.canUseLegacyProtocol,
        )
        if (targets.version2.isEmpty() && targets.legacy.isEmpty() && targets.updateRequired.isEmpty()) {
            return WearSessionSendResult.Failure(WearSessionSendError.NO_REACHABLE_WATCH)
        }
        val messageClient = Wearable.getMessageClient(context)
        val deliveredV2 = targets.version2.count { nodeId ->
            messageClient.sendMessage(
                nodeId,
                WearSessionTransfer.V2_MESSAGE_PATH,
                transfer.encode(),
            ).awaitResult().isSuccess
        }
        val deliveredLegacy = targets.legacy.count { nodeId ->
            messageClient.sendMessage(
                nodeId,
                WearSessionTransfer.V1_MESSAGE_PATH,
                transfer.encodeLegacy(),
            ).awaitResult().isSuccess
        }
        val delivered = deliveredV2 + deliveredLegacy
        return if (delivered > 0) {
            WearSessionSendResult.Success(delivered)
        } else if (targets.version2.isEmpty() && targets.updateRequired.isNotEmpty()) {
            WearSessionSendResult.Failure(WearSessionSendError.WATCH_UPDATE_REQUIRED)
        } else {
            WearSessionSendResult.Failure(WearSessionSendError.TRANSFER_FAILED)
        }
    }

    override suspend fun clear(context: Context): WearSessionSendResult {
        val capability = Wearable.getCapabilityClient(context)
            .getCapability(WearSessionTransfer.V1_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .awaitResult().getOrNull()
            ?: return WearSessionSendResult.Failure(WearSessionSendError.TRANSFER_FAILED)
        if (capability.nodes.isEmpty()) {
            return WearSessionSendResult.Failure(WearSessionSendError.NO_REACHABLE_WATCH)
        }
        val messageClient = Wearable.getMessageClient(context)
        val delivered = capability.nodes.count { node ->
            messageClient.sendMessage(
                node.id,
                WearSessionTransfer.CLEAR_MESSAGE_PATH,
                byteArrayOf(),
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
