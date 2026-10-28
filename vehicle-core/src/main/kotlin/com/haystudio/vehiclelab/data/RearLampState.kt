package com.haystudio.vehiclelab.data

data class RearLampState(val nodes: Set<String>, val plate: Boolean, val braking: Boolean) {
    fun emission(ancestors: Set<String>): Float = when {
        ancestors.none { it in nodes } -> 0f
        braking && ancestors.any { it == "Light_16" || it == "Light_17" } -> 8.5f
        "Light_19" in ancestors -> 1.55f
        braking -> 1.35f
        else -> 1f
    }
    companion object {
        val runningNodes = setOf("Light_18_04", "Light_18_05", "Light_18_06")
        val brakingNodes = setOf("Light_16", "Light_17", "Light_18_01", "Light_18_02", "Light_18_03", "Light_19")
        val allNodes = runningNodes + brakingNodes
        fun resolve(lights: VehicleLights): RearLampState {
            val driving = FrontLampState.resolve(lights).position
            val braking = VehicleLamp.BRAKE in lights
            val high = if (VehicleLamp.REAR in lights) setOf("Light_19") else emptySet()
            return RearLampState((if (braking) brakingNodes else if (driving) runningNodes else emptySet()) + high, driving, braking)
        }
    }
}
