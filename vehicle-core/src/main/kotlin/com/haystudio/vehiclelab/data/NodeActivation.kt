package com.haystudio.vehiclelab.data

object NodeActivation {
    fun isHidden(
        ancestors: Set<String>,
        initiallyHidden: Set<String>,
        activated: Set<String>,
        deactivated: Set<String>
    ): Boolean = ancestors.any { name ->
        name in deactivated || (name in initiallyHidden && name !in activated)
    }
}
