package com.haystudio.vehiclelab.data

data class FrontLampState(val enabled: Boolean, val position: Boolean) {
    
    val sideEmission: Float get() = if (!enabled) 0f else if (position) .42f else 1f
    fun emission(ancestors: Set<String>): Float = when {
        ancestors.any { it in sideNodes } -> sideEmission
        position && ancestors.any { it in positionNodes } -> .42f
        else -> 0f
    }
    companion object {
        val sideNodes = setOf("Light_09", "Light_10")
        val positionNodes = setOf("Light_11", "Light_12", "Light_13")
        val allNodes = sideNodes + positionNodes
        fun resolve(lights: VehicleLights): FrontLampState {
            
            val position = listOf(VehicleLamp.POSITION, VehicleLamp.LOW,
                VehicleLamp.AROUND, VehicleLamp.ADJUSTMENT).any { it in lights }
            return FrontLampState(position || VehicleLamp.DRL in lights, position)
        }
    }
}
