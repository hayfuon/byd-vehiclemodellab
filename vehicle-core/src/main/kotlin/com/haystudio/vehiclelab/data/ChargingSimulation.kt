package com.haystudio.vehiclelab.data

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.pow

enum class ChargingState {
    DISCONNECTED,
    CONNECTED,
    CHARGING,
    COMPLETE
}

object ChargingPortPresentation {
    const val OUTER_EMITTER_NODE = "Light_EV_DMI.001"

    fun stabilizeLensMaterial(block: FloatArray, phong: Boolean) {
        if (block.size < 16) return
        val base = .65f.pow(2.2f) 
        block[0] = base
        block[1] = base
        block[2] = base
        block[12] = .08f
        block[13] = .68f
    }

    fun matchesEmitterSurface(
        includeSurroundingReceiver: Boolean,
        candidateBounds: FloatArray,
        emitterBounds: FloatArray
    ): Boolean {
        if (candidateBounds.size < 6 || emitterBounds.size < 6 ||
            candidateBounds.take(6).any { !it.isFinite() } ||
            emitterBounds.take(6).any { !it.isFinite() }
        ) return false

        var maxGap = 0f
        for (axis in 0..2) {
            val gap = when {
                candidateBounds[axis] > emitterBounds[axis + 3] -> candidateBounds[axis] - emitterBounds[axis + 3]
                emitterBounds[axis] > candidateBounds[axis + 3] -> emitterBounds[axis] - candidateBounds[axis + 3]
                else -> 0f
            }
            maxGap = maxOf(maxGap, gap)
        }
        return maxGap <= if (includeSurroundingReceiver) .22f else .045f
    }

    data class Emitter(val color: FloatArray, val intensity: Float)

    fun coverGain(openProgress: Float): Float {
        val t = ((openProgress.coerceIn(0f, 1f) - .10f) / .25f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 灯口先供本地照明，外盖继续打开时才逐步释放向外的光通量和角域。 */
    fun groundAperture(openProgress: Float): Float {
        val t = ((openProgress.coerceIn(0f, 1f) - .1f) / .9f).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun receiverGain(period: DayPeriod): Float = when (period) {
        DayPeriod.NIGHT -> 1f
        DayPeriod.MORNING -> .18f
        DayPeriod.NOON -> 0f
    }

    fun emittersLit(rightCoverOpen: Boolean, state: ChargingState): Boolean =
        rightCoverOpen

    fun outerEmitterLit(node: String, rightCoverOpen: Boolean, state: ChargingState): Boolean =
        node.equals(OUTER_EMITTER_NODE, ignoreCase = true) && emittersLit(rightCoverOpen, state)

    fun emitter(rightCoverOpen: Boolean, state: ChargingState, timeSeconds: Float): Emitter {
        if (!rightCoverOpen) return Emitter(floatArrayOf(0f, 0f, 0f), 0f)
        fun pulse(rate: Float): Float {

            val phase = (timeSeconds.coerceFinite() * rate).mod(1f)
            val travel = ((phase - .12f) / .76f).coerceIn(0f, 1f)
            return if (phase <= .12f || phase >= .88f) 0f else sin(travel * PI.toFloat())
        }
        return when (state) {
            ChargingState.DISCONNECTED -> Emitter(floatArrayOf(1f, .96f, .88f), .72f)
            ChargingState.CONNECTED -> Emitter(floatArrayOf(1f, .27f, .035f), pulse(.60f))
            ChargingState.CHARGING -> Emitter(floatArrayOf(.06f, 1f, .28f), pulse(.46f))
            ChargingState.COMPLETE -> Emitter(floatArrayOf(.06f, 1f, .28f), 1f)
        }
    }

    private fun Float.coerceFinite(): Float = if (isFinite()) this else 0f
}

object ChargingConnectionMotion {
    data class State(val cap: Float = 0f, val plug: Float = 0f)

    fun advance(current: State, wantsConnection: Boolean, outerCoverReady: Boolean, dt: Float): State {
        val safeDt = if (dt.isFinite()) dt.coerceAtLeast(0f) else 0f
        fun move(value: Float, target: Float, seconds: Float): Float {
            val step = (safeDt / seconds).coerceIn(0f, 1f)
            return if (value < target) (value + step).coerceAtMost(target)
            else (value - step).coerceAtLeast(target)
        }
        
        val plugTarget = if (wantsConnection && outerCoverReady && current.cap >= .985f) 1f else 0f
        val plug = move(current.plug, plugTarget, .42f)
        val capTarget = when {
            wantsConnection && outerCoverReady -> 1f
            plug <= .001f -> 0f
            else -> current.cap
        }
        return State(move(current.cap, capTarget, .24f), plug)
    }

    fun opacity(progress: Float): Float = smooth((progress - .03f) / .23f)
    fun insertion(progress: Float): Float = smooth((progress - .25f) / .69f)

    fun coverVisibility(progress: Float): Float = 1f - smooth(progress)

    fun canCloseOuterCover(state: State): Boolean = state.cap <= .001f && state.plug <= .001f

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

data class ChargingSnapshot(
    val state: ChargingState = ChargingState.DISCONNECTED,
    val stateOfChargePercent: Float = 35f
)

data class ChargingConnection(
    val state: ChargingState,
    val portOpen: Boolean,
    val port: ChargingPort = ChargingPort.SLOW
)

object ChargingSimulation {
    fun openPortAndConnect(
        state: ChargingState,
        port: ChargingPort = ChargingPort.SLOW
    ): ChargingConnection =
        ChargingConnection(
            if (state == ChargingState.DISCONNECTED) ChargingState.CONNECTED else state,
            portOpen = true,
            port = port
        )

    fun connect(state: ChargingState, portOpen: Boolean): ChargingState =
        if (state == ChargingState.DISCONNECTED && portOpen) ChargingState.CONNECTED else state

    fun start(state: ChargingState): ChargingState = when (state) {
        ChargingState.CONNECTED, ChargingState.CHARGING -> ChargingState.CHARGING
        else -> state
    }

    fun stop(state: ChargingState): ChargingState =
        if (state == ChargingState.CHARGING) ChargingState.CONNECTED else state

    fun complete(state: ChargingState): ChargingState =
        if (state == ChargingState.CHARGING) ChargingState.COMPLETE else state

    fun disconnect(state: ChargingState): ChargingState =
        if (state == ChargingState.DISCONNECTED) state else ChargingState.DISCONNECTED

    fun canClosePort(state: ChargingState): Boolean = state == ChargingState.DISCONNECTED

    fun advance(
        snapshot: ChargingSnapshot,
        seconds: Float,
        ratePercentPerMinute: Float = 24f
    ): ChargingSnapshot {
        if (snapshot.state != ChargingState.CHARGING || !seconds.isFinite() || seconds <= 0f ||
            !ratePercentPerMinute.isFinite() || ratePercentPerMinute <= 0f
        ) return snapshot

        val level = (snapshot.stateOfChargePercent.coerceIn(0f, 100f) +
            seconds * ratePercentPerMinute / 60f).coerceAtMost(100f)
        return ChargingSnapshot(
            if (level >= 100f) ChargingState.COMPLETE else ChargingState.CHARGING,
            level
        )
    }
}
