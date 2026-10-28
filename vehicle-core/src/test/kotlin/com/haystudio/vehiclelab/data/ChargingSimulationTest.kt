package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ChargingSimulationTest {
    @Test fun groundApertureCannotFullyReleaseBeforeTheOuterCoverOpens() {
        assertEquals(0f, ChargingPortPresentation.groundAperture(.1f), .0001f)
        assertTrue(ChargingPortPresentation.groundAperture(.5f) < .5f)
        assertTrue(ChargingPortPresentation.groundAperture(.9f) < 1f)
        assertEquals(1f, ChargingPortPresentation.groundAperture(1f), .0001f)
    }
    @Test fun emitterBaseMaterialIsIndependentOfLampState() {
        val unlit = FloatArray(48) { .9f }
        val lit = FloatArray(48) { .1f }
        ChargingPortPresentation.stabilizeLensMaterial(unlit, phong = false)
        ChargingPortPresentation.stabilizeLensMaterial(lit, phong = false)
        assertArrayEquals(unlit.copyOfRange(0, 3), lit.copyOfRange(0, 3), 0f)
        assertEquals(.08f, unlit[12], 0f)
        assertEquals(.68f, unlit[13], 0f)
        assertEquals("unlit charge lens should be a light gray in linear space", .65f.pow(2.2f), unlit[0], .0001f)
    }

    @Test fun phongEmitterBaseMaterialIsIndependentOfLampState() {
        val unlit = FloatArray(24) { .9f }
        val lit = FloatArray(24) { .1f }
        ChargingPortPresentation.stabilizeLensMaterial(unlit, phong = true)
        ChargingPortPresentation.stabilizeLensMaterial(lit, phong = true)
        assertArrayEquals(unlit.copyOfRange(0, 3), lit.copyOfRange(0, 3), 0f)
        assertEquals(.08f, unlit[12], 0f)
        assertEquals(.68f, unlit[13], 0f)
        assertEquals("unlit charge lens should be a light gray in linear space", .65f.pow(2.2f), unlit[0], .0001f)
    }

    @Test fun emitterAssociationIncludesAdjacentLampSurfaceButExcludesUnrelatedBodyPanels() {
        val lamp = floatArrayOf(1.68f,.94f,-.93f, 1.73f,.98f,-.90f)
        val adjacent = floatArrayOf(1.73f,.94f,-.93f, 1.77f,.98f,-.90f)
        val body = floatArrayOf(1.92f,.94f,-.93f, 1.98f,.98f,-.90f)
        assertTrue(ChargingPortPresentation.matchesEmitterSurface(false, adjacent, lamp))
        assertTrue(ChargingPortPresentation.matchesEmitterSurface(true, body, lamp))
        assertFalse(ChargingPortPresentation.matchesEmitterSurface(false, body, lamp))
    }

    @Test fun chargingLampFadesOnlyAfterCoverClearsTheFirstTenPercent() {
        assertEquals(0f, ChargingPortPresentation.coverGain(0f), 0f)
        assertEquals(0f, ChargingPortPresentation.coverGain(.1f), 0f)
        assertTrue(ChargingPortPresentation.coverGain(.2f) in 0f..1f)
        assertEquals(1f, ChargingPortPresentation.coverGain(.35f), .0001f)
    }

    @Test fun disconnectedOpenPortKeepsAConstantWhiteSocketLamp() {
        val open = ChargingPortPresentation.emitter(true, ChargingState.DISCONNECTED, 0f)
        val later = ChargingPortPresentation.emitter(true, ChargingState.DISCONNECTED, 9f)
        assertEquals(.72f, open.intensity, .0001f)
        assertEquals(open.intensity, later.intensity, 0f)
        assertEquals(open.color.toList(), later.color.toList())
    }

    @Test fun chargingCableShadowUsesCurrentCablePathProjectedToGround() {
        val path = listOf(floatArrayOf(1f, 2f, 3f), floatArrayOf(4f, 5f, 6f))
        val projected = ChargingCableGeometry.groundShadowPath(path, -.4f)
        assertEquals(2, projected.size)
        assertArrayEquals(floatArrayOf(1f, -.4f, 3f), projected[0], 0f)
        assertArrayEquals(floatArrayOf(4f, -.4f, 6f), projected[1], 0f)
    }
    @Test fun bothChargingChoicesUseTheRightRearChargingCover() {
        assertEquals("Right", ChargingPort.FAST.clipSide)
        assertEquals("Right", ChargingPort.SLOW.clipSide)
        assertEquals(ChargingPort.FAST,
            ChargingSimulation.openPortAndConnect(ChargingState.DISCONNECTED, ChargingPort.FAST).port)
    }

    @Test fun eachConnectorUsesItsOwnStaticSocketOnTheRightRearPort() {
        
        assertEquals(1.73043f, ChargingPort.FAST.socketLocal[0], .00001f)
        assertEquals(.89142f, ChargingPort.FAST.socketLocal[1], .00001f)
        assertEquals(-.86694f, ChargingPort.FAST.socketLocal[2], .00001f)
        assertEquals(1.66642f, ChargingPort.SLOW.socketLocal[0], .00001f)
        assertEquals(.88545f, ChargingPort.SLOW.socketLocal[1], .00001f)
        assertEquals(-.87263f, ChargingPort.SLOW.socketLocal[2], .00001f)
        assertTrue(ChargingPort.FAST.socketLocal[0] > ChargingPort.SLOW.socketLocal[0])
        assertEquals(-.9766881f, ChargingPort.FAST.outwardLocal[2], .000001f)
        assertEquals(.2039855f, ChargingPort.SLOW.outwardLocal[1], .000001f)
    }

    @Test fun fastAndSlowConnectorsRetainDistinctRealSocketSizes() {
        assertTrue(ChargingPort.FAST.plugScale > ChargingPort.SLOW.plugScale)
        assertTrue("fast charging lead must be visibly thicker than the slow lead",
            ChargingPort.FAST.cableRadius > ChargingPort.SLOW.cableRadius)
    }

    @Test fun openChargeCoverShowsWhiteStatusWhileConnectionStatesUseTheirOwnColors() {
        val open = ChargingPortPresentation.emitter(true, ChargingState.DISCONNECTED, 0f)
        val connected = ChargingPortPresentation.emitter(true, ChargingState.CONNECTED, 0f)
        val charging = ChargingPortPresentation.emitter(true, ChargingState.CHARGING, 0f)
        val complete = ChargingPortPresentation.emitter(true, ChargingState.COMPLETE, 0f)

        assertTrue(open.color[0] > .8f && open.color[1] > .8f && open.color[2] > .8f)
        assertTrue(connected.color[0] > connected.color[1])
        assertTrue(charging.color[1] > charging.color[0])
        assertTrue(complete.color[1] > complete.color[0])
    }

    @Test fun connectedAndChargingEmittersGoFullyDarkBetweenBreathsButCompleteStaysSteady() {
        val connectedA = ChargingPortPresentation.emitter(true, ChargingState.CONNECTED, 0f)
        val connectedB = ChargingPortPresentation.emitter(true, ChargingState.CONNECTED, .34f)
        val chargingA = ChargingPortPresentation.emitter(true, ChargingState.CHARGING, 0f)
        val chargingB = ChargingPortPresentation.emitter(true, ChargingState.CHARGING, .34f)
        val completeA = ChargingPortPresentation.emitter(true, ChargingState.COMPLETE, 0f)
        val completeB = ChargingPortPresentation.emitter(true, ChargingState.COMPLETE, .34f)

        assertEquals(0f, connectedA.intensity, 0f)
        assertEquals(0f, chargingA.intensity, 0f)
        assertTrue(connectedB.intensity > .01f)
        assertTrue(chargingB.intensity > .01f)
        assertEquals(completeA.intensity, completeB.intensity, 0f)
    }

    @Test fun connectorWaitsForCoverThenFadesAndInsertsAndDisconnectsInReverseOrder() {
        var motion = ChargingConnectionMotion.State()
        repeat(16) { motion = ChargingConnectionMotion.advance(motion, true, outerCoverReady = true, dt = .1f) }
        assertTrue(motion.cap > .98f)
        assertTrue(motion.plug > .7f)
        assertTrue(ChargingConnectionMotion.opacity(motion.plug) > .99f)
        assertTrue(ChargingConnectionMotion.insertion(motion.plug) > .5f)

        val retracting = ChargingConnectionMotion.advance(motion, false, outerCoverReady = true, dt = .1f)
        assertTrue(retracting.plug < motion.plug)
        assertEquals(motion.cap, retracting.cap, .0001f)
    }

    @Test fun connectionMotionReachesExactEndpointsAndRetractsBeforeCapCloses() {
        var m = ChargingConnectionMotion.State()
        repeat(120) { m = ChargingConnectionMotion.advance(m, true, true, 1f / 60f) }
        assertEquals(1f, m.cap, 0f)
        assertEquals(1f, m.plug, 0f)
        var sawRetraction = false
        repeat(120) {
            val previous = m
            m = ChargingConnectionMotion.advance(m, false, true, 1f / 60f)
            if (m.plug > 0f) {
                sawRetraction = true
                assertEquals(1f, m.cap, 0f)
                assertTrue(m.plug <= previous.plug)
            }
        }
        assertTrue(sawRetraction)
        assertEquals(0f, m.cap, 0f)
        assertEquals(0f, m.plug, 0f)
    }

    @Test fun connectionWaitsForOuterCoverAndCanReverseWithoutJumping() {
        var m = ChargingConnectionMotion.State()
        repeat(90) { m = ChargingConnectionMotion.advance(m, true, false, 1f / 60f) }
        assertEquals(ChargingConnectionMotion.State(), m)
        repeat(40) { m = ChargingConnectionMotion.advance(m, true, true, 1f / 60f) }
        val reverse = ChargingConnectionMotion.advance(m, false, true, 1f / 60f)
        assertTrue(reverse.plug < m.plug)
        assertTrue(m.plug - reverse.plug < .05f)
    }

    @Test fun outerCoverOnlyClosesAfterTheConnectorAndInnerCapAreFullyRetracted() {
        assertFalse(ChargingConnectionMotion.canCloseOuterCover(ChargingConnectionMotion.State(.2f, 0f)))
        assertFalse(ChargingConnectionMotion.canCloseOuterCover(ChargingConnectionMotion.State(0f, .2f)))
        assertTrue(ChargingConnectionMotion.canCloseOuterCover(ChargingConnectionMotion.State()))
    }

    @Test fun innerSocketCapFadesInPlaceWithoutChangingTheExternalDoorState() {
        assertEquals(1f, ChargingConnectionMotion.coverVisibility(0f), 0f)
        assertEquals(0f, ChargingConnectionMotion.coverVisibility(1f), 0f)
        assertTrue(ChargingConnectionMotion.coverVisibility(.25f) >
            ChargingConnectionMotion.coverVisibility(.75f))
    }

    @Test fun portReceiverLightIsAbsentAtNoonAndWeakerAtDawnThanAtNight() {
        assertEquals(0f, ChargingPortPresentation.receiverGain(DayPeriod.NOON), 0f)
        assertTrue(ChargingPortPresentation.receiverGain(DayPeriod.MORNING) > 0f)
        assertTrue(ChargingPortPresentation.receiverGain(DayPeriod.MORNING) <
            ChargingPortPresentation.receiverGain(DayPeriod.NIGHT) * .25f)
    }

    @Test fun breathingHasAnOffDwellAndNoAmbientIlluminationAtNoonOrDuringOffPhase() {
        for ((state, rate) in listOf(ChargingState.CONNECTED to .60f, ChargingState.CHARGING to .46f)) {
            assertEquals(0f, ChargingPortPresentation.emitter(true, state, .05f / rate).intensity, 0f)
            assertEquals(0f, ChargingPortPresentation.emitter(true, state, .95f / rate).intensity, 0f)
            assertEquals(1f, ChargingPortPresentation.emitter(true, state, .5f / rate).intensity, .0001f)
        }
    }

    @Test fun chargePortEmitterIsLitForEveryOpenRightPortState() {
        assertFalse(ChargingPortPresentation.emittersLit(false, ChargingState.CONNECTED))
        assertTrue(ChargingPortPresentation.emittersLit(true, ChargingState.DISCONNECTED))
        assertTrue(ChargingPortPresentation.emittersLit(true, ChargingState.CONNECTED))
        assertTrue(ChargingPortPresentation.emittersLit(true, ChargingState.CHARGING))
        assertTrue(ChargingPortPresentation.emittersLit(true, ChargingState.COMPLETE))
    }

    @Test fun onlyTheOriginalRightChargeLightReceivesEmitterState() {
        assertTrue(ChargingPortPresentation.outerEmitterLit("Light_EV_DMI.001", true, ChargingState.CONNECTED))
        assertFalse(ChargingPortPresentation.outerEmitterLit("Light_EV_DMI", true, ChargingState.CONNECTED))
    }

    @Test fun connectorRequiresTheRightChargingCoverToBeOpen() {
        assertEquals(ChargingState.DISCONNECTED,
            ChargingSimulation.connect(ChargingState.DISCONNECTED, portOpen = false))
        assertEquals(ChargingState.CONNECTED,
            ChargingSimulation.connect(ChargingState.DISCONNECTED, portOpen = true))
    }

    @Test fun panelConnectionRequestOpensThePortAndConnectsInOneAction() {
        val result = ChargingSimulation.openPortAndConnect(ChargingState.DISCONNECTED)

        assertTrue(result.portOpen)
        assertEquals(ChargingState.CONNECTED, result.state)
    }

    @Test fun chargingRequiresAConnectedConnectorAndCanStopWithoutDisconnecting() {
        assertEquals(ChargingState.DISCONNECTED,
            ChargingSimulation.start(ChargingState.DISCONNECTED))
        assertEquals(ChargingState.CHARGING,
            ChargingSimulation.start(ChargingState.CONNECTED))
        assertEquals(ChargingState.CONNECTED,
            ChargingSimulation.stop(ChargingState.CHARGING))
        assertEquals(ChargingState.CHARGING,
            ChargingSimulation.start(ChargingState.CHARGING))
    }

    @Test fun connectedPortCannotBeClosedUntilDisconnected() {
        assertFalse(ChargingSimulation.canClosePort(ChargingState.CONNECTED))
        assertFalse(ChargingSimulation.canClosePort(ChargingState.CHARGING))
        assertTrue(ChargingSimulation.canClosePort(ChargingState.DISCONNECTED))
        assertEquals(ChargingState.DISCONNECTED,
            ChargingSimulation.disconnect(ChargingState.CONNECTED))
    }

    @Test fun completingChargeRequiresAnActiveChargeAndCanBeDisconnected() {
        assertEquals(ChargingState.CONNECTED,
            ChargingSimulation.complete(ChargingState.CONNECTED))
        assertEquals(ChargingState.COMPLETE,
            ChargingSimulation.complete(ChargingState.CHARGING))
        assertEquals(ChargingState.DISCONNECTED,
            ChargingSimulation.disconnect(ChargingState.COMPLETE))
    }

    @Test fun chargingProgressAdvancesOnlyWhileChargingAndCompletesAtFullCapacity() {
        val connected = ChargingSnapshot(ChargingState.CONNECTED, 99.5f)
        assertEquals(connected, ChargingSimulation.advance(connected, seconds = 60f, ratePercentPerMinute = 1f))

        val charging = connected.copy(state = ChargingState.CHARGING)
        val complete = ChargingSimulation.advance(charging, seconds = 60f, ratePercentPerMinute = 1f)
        assertEquals(ChargingState.COMPLETE, complete.state)
        assertEquals(100f, complete.stateOfChargePercent)
    }

    @Test fun progressNeverMovesBackwardsOrExceedsFullCapacity() {
        val almostFull = ChargingSnapshot(ChargingState.CHARGING, 99.9f)
        val result = ChargingSimulation.advance(almostFull, seconds = 10_000f, ratePercentPerMinute = 50f)
        assertEquals(ChargingState.COMPLETE, result.state)
        assertEquals(100f, result.stateOfChargePercent)
    }

    @Test fun invalidElapsedTimeAndRateDoNotChangeTheSnapshot() {
        val charging = ChargingSnapshot(ChargingState.CHARGING, 52f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { seconds ->
            assertEquals(charging, ChargingSimulation.advance(charging, seconds))
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f).forEach { rate ->
            assertEquals(charging, ChargingSimulation.advance(charging, 1f, rate))
        }
    }
}
