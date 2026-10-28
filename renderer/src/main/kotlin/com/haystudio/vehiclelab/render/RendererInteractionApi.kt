package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.VehicleInteractionApi
import com.haystudio.vehiclelab.data.VehicleInteractionPatch
import com.haystudio.vehiclelab.data.VehicleInteractionState

class RendererInteractionApi(private val renderer: CarRenderer) : VehicleInteractionApi {
    override fun snapshot(): VehicleInteractionState = VehicleInteractionState(
        doorsOpen = renderer.doorsOpen,
        windowsOpen = renderer.windowsOpen,
        sunroofOpen = renderer.sunroofOpen,
        sunroofVentOpen = renderer.sunroofVentOpen,
        curtainOpen = renderer.curtainOpen,
        trunkOpen = renderer.trunkOpen,
        hoodOpen = renderer.hoodOpen,
        mirrorsExpanded = renderer.mirrorsExpanded,
        leftFuelCoverOpen = renderer.leftChargerOpen,
        rightChargingCoverOpen = renderer.rightChargerOpen,
        handlesExtended = renderer.handlesExtended,
        drivingSimulation = renderer.drivingSimulation,
        wheelsRolling = renderer.wheelsRolling,
        wheelsSteered = renderer.wheelsSteered,
        readingLamps = renderer.readingLamps,
        luggageLampEnabled = renderer.luggageLampEnabled,
        interiorAmbientEnabled = renderer.interiorAmbientEnabled,
        interiorAmbientBrightness = renderer.interiorAmbientBrightness,
        vehicleSpeedKmh = renderer.vehicleSpeedKmh,
        steeringAngleDegrees = renderer.steeringAngleDegrees ?: 0f,
        selectedChargingPort = renderer.selectedChargingPort,
        drivingControls = renderer.drivingControls,
        lights = renderer.manualLights
    ).normalized()

    override fun apply(patch: VehicleInteractionPatch): VehicleInteractionState {
        val next = snapshot().applyPatch(patch)
        if(patch.doorsOpen!=null)renderer.doorsOpen = next.doorsOpen
        if(patch.windowsOpen!=null)renderer.windowsOpen = next.windowsOpen
        if(patch.sunroofOpen!=null)renderer.sunroofOpen = next.sunroofOpen
        if(patch.sunroofVentOpen!=null)renderer.sunroofVentOpen = next.sunroofVentOpen
        if(patch.curtainOpen!=null)renderer.curtainOpen = next.curtainOpen
        if(patch.trunkOpen!=null)renderer.trunkOpen = next.trunkOpen
        if(patch.hoodOpen!=null)renderer.hoodOpen = next.hoodOpen
        if(patch.mirrorsExpanded!=null)renderer.mirrorsExpanded = next.mirrorsExpanded
        if(patch.leftFuelCoverOpen!=null)renderer.leftChargerOpen = next.leftFuelCoverOpen
        if(patch.rightChargingCoverOpen!=null)renderer.rightChargerOpen = next.rightChargingCoverOpen
        if(patch.handlesExtended!=null)renderer.handlesExtended = next.handlesExtended
        if(patch.drivingSimulation!=null)renderer.drivingSimulation = next.drivingSimulation
        if(patch.wheelsRolling!=null)renderer.wheelsRolling = next.wheelsRolling
        if(patch.wheelsSteered!=null)renderer.wheelsSteered = next.wheelsSteered
        if(patch.readingLamps!=null)renderer.readingLamps = next.readingLamps
        if(patch.luggageLampEnabled!=null)renderer.luggageLampEnabled = next.luggageLampEnabled
        if(patch.interiorAmbientEnabled!=null)renderer.interiorAmbientEnabled = next.interiorAmbientEnabled
        if(patch.interiorAmbientBrightness!=null)renderer.interiorAmbientBrightness = next.interiorAmbientBrightness
        if(patch.drivingControls!=null)renderer.applyDrivingControls(next.drivingControls)
        if(patch.vehicleSpeedKmh!=null || patch.clearVehicleSpeed)renderer.vehicleSpeedKmh = next.vehicleSpeedKmh
        if(patch.steeringAngleDegrees!=null)renderer.steeringAngleDegrees = next.steeringAngleDegrees
        if(patch.selectedChargingPort!=null)renderer.selectedChargingPort = next.selectedChargingPort
        if(patch.lights!=null)renderer.setVehicleLights(next.lights)
        return next
    }
}
