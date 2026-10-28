package com.haystudio.vehiclelab.data

data class VehicleInteractionState(
    val doorsOpen: Boolean = false,
    val windowsOpen: Boolean = false,
    val sunroofOpen: Boolean = false,
    val sunroofVentOpen: Boolean = false,
    val curtainOpen: Boolean = false,
    val trunkOpen: Boolean = false,
    val hoodOpen: Boolean = false,
    val mirrorsExpanded: Boolean = true,
    val leftFuelCoverOpen: Boolean = false,
    val rightChargingCoverOpen: Boolean = false,
    val handlesExtended: Boolean = false,
    val drivingSimulation: Boolean = false,
    val wheelsRolling: Boolean = false,
    val wheelsSteered: Boolean = false,
    val readingLamps:Set<CabinReadingLamp> = emptySet(),
    val luggageLampEnabled:Boolean = true,
    val interiorAmbientEnabled: Boolean = false,
    val interiorAmbientBrightness: Float = .5f,
    val vehicleSpeedKmh: Float? = null,
    val steeringAngleDegrees: Float = 0f,
    val selectedChargingPort: ChargingPort = ChargingPort.SLOW,
    val drivingControls: DrivingControls = DrivingControls(),
    val lights: VehicleLights = VehicleLights()
) {
    fun normalized(): VehicleInteractionState = copy(
        drivingControls = drivingControls.normalized(),
        sunroofVentOpen = sunroofVentOpen && !sunroofOpen,
        interiorAmbientBrightness = interiorAmbientBrightness.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f,
        vehicleSpeedKmh = vehicleSpeedKmh?.takeIf(Float::isFinite)?.coerceIn(-180f, 180f),
        steeringAngleDegrees = steeringAngleDegrees.takeIf(Float::isFinite)?.coerceIn(-450f, 450f) ?: 0f
    )

    fun applyPatch(patch: VehicleInteractionPatch): VehicleInteractionState = copy(
        doorsOpen = patch.doorsOpen ?: doorsOpen,
        windowsOpen = patch.windowsOpen ?: windowsOpen,
        sunroofOpen = patch.sunroofOpen ?: sunroofOpen,
        sunroofVentOpen = patch.sunroofVentOpen ?: sunroofVentOpen,
        curtainOpen = patch.curtainOpen ?: curtainOpen,
        trunkOpen = patch.trunkOpen ?: trunkOpen,
        hoodOpen = patch.hoodOpen ?: hoodOpen,
        mirrorsExpanded = patch.mirrorsExpanded ?: mirrorsExpanded,
        leftFuelCoverOpen = patch.leftFuelCoverOpen ?: leftFuelCoverOpen,
        rightChargingCoverOpen = patch.rightChargingCoverOpen ?: rightChargingCoverOpen,
        handlesExtended = patch.handlesExtended ?: handlesExtended,
        drivingSimulation = patch.drivingSimulation ?: drivingSimulation,
        wheelsRolling = patch.wheelsRolling ?: wheelsRolling,
        wheelsSteered = patch.wheelsSteered ?: wheelsSteered,
        readingLamps = patch.readingLamps ?: readingLamps,
        luggageLampEnabled = patch.luggageLampEnabled ?: luggageLampEnabled,
        interiorAmbientEnabled = patch.interiorAmbientEnabled ?: interiorAmbientEnabled,
        interiorAmbientBrightness = patch.interiorAmbientBrightness ?: interiorAmbientBrightness,
        vehicleSpeedKmh = if(patch.clearVehicleSpeed) null else patch.vehicleSpeedKmh ?: vehicleSpeedKmh,
        steeringAngleDegrees = patch.steeringAngleDegrees ?: steeringAngleDegrees,
        selectedChargingPort = patch.selectedChargingPort ?: selectedChargingPort,
        drivingControls = patch.drivingControls ?: drivingControls,
        lights = patch.lights ?: lights
    ).normalized()
}

data class VehicleInteractionPatch(
    val doorsOpen: Boolean? = null,
    val windowsOpen: Boolean? = null,
    val sunroofOpen: Boolean? = null,
    val sunroofVentOpen: Boolean? = null,
    val curtainOpen: Boolean? = null,
    val trunkOpen: Boolean? = null,
    val hoodOpen: Boolean? = null,
    val mirrorsExpanded: Boolean? = null,
    val leftFuelCoverOpen: Boolean? = null,
    val rightChargingCoverOpen: Boolean? = null,
    val handlesExtended: Boolean? = null,
    val drivingSimulation: Boolean? = null,
    val wheelsRolling: Boolean? = null,
    val wheelsSteered: Boolean? = null,
    val readingLamps:Set<CabinReadingLamp>? = null,
    val luggageLampEnabled:Boolean? = null,
    val interiorAmbientEnabled: Boolean? = null,
    val interiorAmbientBrightness: Float? = null,
    val vehicleSpeedKmh: Float? = null,
    val clearVehicleSpeed: Boolean = false,
    val steeringAngleDegrees: Float? = null,
    val selectedChargingPort: ChargingPort? = null,
    val drivingControls: DrivingControls? = null,
    val lights: VehicleLights? = null
)

interface VehicleInteractionApi {
    fun snapshot(): VehicleInteractionState
    fun apply(patch: VehicleInteractionPatch): VehicleInteractionState
}
