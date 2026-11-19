package dev.astoris.ursa.core.network

import dev.astoris.ursa.data.model.Heartbeat
import dev.astoris.ursa.data.model.Monitor

/** Status rules for monitor lists and beat histories. Kept free of the socket so that tests can reach them. */
internal object MonitorStatusMerge {
    /**
     * Sets the status and ping from the newest beat. The newest row of a heartbeatList is the
     * monitor's current state; without this the monitor stays PENDING until its next live
     * heartbeat (and forever in an offline snapshot). No beats: the monitor does not change.
     */
    fun withNewestBeat(monitor: Monitor, beats: List<Heartbeat>): Monitor {
        val last = beats.lastOrNull() ?: return monitor
        return monitor.copy(status = last.status, ping = last.ping ?: monitor.ping)
    }

    /**
     * A monitor list from the server has no status (the parsed Monitor defaults to PENDING).
     * Keep the status the client already knows. The newest beat of this session wins
     * (heartbeatList can arrive before the monitor list, and live beats are added to the
     * history); else keep the previous monitor state, for example from the cached snapshot.
     */
    fun withKnownStatus(
        fresh: Map<Int, Monitor>,
        previous: Map<Int, Monitor>,
        history: Map<Int, List<Heartbeat>>,
    ): Map<Int, Monitor> = fresh.mapValues { (id, monitor) ->
        val known = previous[id]
        val kept = if (known != null) {
            monitor.copy(
                status = known.status,
                ping = known.ping,
                avgPing = known.avgPing,
                uptime24h = known.uptime24h,
            )
        } else {
            monitor
        }
        withNewestBeat(kept, history[id].orEmpty())
    }
}
