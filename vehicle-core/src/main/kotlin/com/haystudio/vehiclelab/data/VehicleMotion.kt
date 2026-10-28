package com.haystudio.vehiclelab.data

enum class VehicleMotionChannel {
    DOOR, HANDLE, WINDOW, SUNROOF, CURTAIN, TRUNK, HOOD, MIRROR, CHARGER, WHEEL_ROLL, WHEEL_STEER;

    companion object {
        fun ofClip(name: String): VehicleMotionChannel? = when {
            name.contains("Wheel_Steering", ignoreCase = true) -> WHEEL_STEER
            name.contains("Wheel_", ignoreCase = true) -> WHEEL_ROLL
            name.contains("Rearview", ignoreCase = true) -> MIRROR
            name.contains("Charger", ignoreCase = true) -> CHARGER
            name.contains("Door_Hood", ignoreCase = true) -> HOOD
            name.contains("Door_Trunk", ignoreCase = true) -> TRUNK
            name.contains("Sunshade", ignoreCase = true) -> CURTAIN
            name.contains("SunRoof", ignoreCase = true) && !name.contains("_Dash", true) -> SUNROOF
            name.contains("Window", ignoreCase = true) && !name.contains("_Dash", true) -> WINDOW
            name.contains("Handle_", ignoreCase = true) -> HANDLE
            name.contains("Door_", ignoreCase = true) -> DOOR
            else -> null
        }
    }
}

object VehicleMotionProgress {
    fun clamp(value: Float): Float = value.coerceIn(0f, 1f)

    fun advance(current: Float, target: Float, dt: Float, duration: Float, epsilon: Float = .001f): Float {
        if (kotlin.math.abs(current - target) <= epsilon) return target
        val step = (dt / duration.coerceAtLeast(.15f)).coerceAtMost(1f)
        val next = if (current < target) minOf(target, current + step) else maxOf(target, current - step)
        return if (kotlin.math.abs(next - target) <= epsilon) target else next
    }

    fun roll(value: Float): Float {
        val wrapped = value % 1f
        return if (wrapped < 0f) wrapped + 1f else wrapped
    }
    fun advanceRoll(phase: Float, signedCycles: Float): Float = roll(phase + signedCycles)
}

object VehicleMotionTransforms {
    fun roll(rest: FloatArray, phase: Float): FloatArray {
        val angle = VehicleMotionProgress.roll(phase) * (Math.PI * 2.0).toFloat()
        val c = kotlin.math.cos(angle)
        val s = kotlin.math.sin(angle)
        val rotateAroundLocalY = floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
        return GwmNodeTree.multiply(rest, rotateAroundLocalY)
    }
}
