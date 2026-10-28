package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class CabinAirflowTest {
    private val empty = WeatherCollision(emptyList())

    @Test fun lowFanContinuouslySuppliesEveryOutletWithoutRandomEmptyVents() {
        val air = CabinAirflow(42)
        repeat(120) {
            air.step(1f / 60f, AirflowMode.COOL, .15f, empty)
            assertEquals("central, side and rear outlets must all keep flowing",6,
                air.particles.map { "${if(it.origin.x>0f) "rear" else if(it.origin.y>.85f) "side" else "central"}-${it.origin.z>0f}" }.toSet().size)
            assertTrue("front jets must originate above the wireless charging tray",air.particles.filter {it.origin.x<0f && it.origin.y<.85f}.all {it.origin.y in .73f.. .80f && it.origin.x in -.59f..-.52f})
        }
    }

    @Test fun airflowCoversEachFrontApertureInsteadOfOnlyItsCenter() {
        val air=CabinAirflow(9)
        repeat(120){air.step(1f/60f,AirflowMode.NORMAL,.7f,empty)}
        for(left in listOf(true,false)) {
            val origins=air.particles.filter {it.origin.x<0f && it.origin.y in .73f.. .80f && (it.origin.z>0f)==left}.map {it.origin.z}
            assertTrue("front outlet must exist",origins.isNotEmpty())
            assertTrue("each front opening should span the actual 6cm opening",origins.max()-origins.min() in .055f.. .07f)
        }
    }

    @Test fun streamsHaveDifferentLengthsButMoveWithoutFrameRandomJitter() {
        val air=CabinAirflow(7)
        repeat(180){air.step(1f/60f,AirflowMode.COOL,.7f,empty)}
        val ends=air.particles.filter {it.origin.x<0f && it.origin.z>0f}.groupBy {it.origin}.values.map {it.last()}
        val lengths=ends.map {p->(p.position-p.origin).let {kotlin.math.sqrt(it.dot(it))}}
        assertTrue("streams should not end on one mechanical plane",lengths.max()-lengths.min()>.025f)
        val positions=air.particles.map {it.position}
        air.step(1f/60f,AirflowMode.COOL,.7f,empty)
        assertEquals(positions.size,air.particles.size)
        assertTrue(positions.zip(air.particles).all {(a,b)->(a-b.position).let {it.dot(it)}<.000016f})
    }

    @Test fun islandOutletsStayInsideTheirActualOpeningsAndBlowMostlyHorizontally() {
        val air=CabinAirflow(11)
        repeat(120){air.step(1f/60f,AirflowMode.COOL,.8f,empty)}
        val rear=air.particles.filter {it.origin.x>0f}
        assertTrue(rear.isNotEmpty())
        assertTrue(rear.all {it.origin.z in .022f.. .080f || it.origin.z in -.070f..-.011f})
        val central=air.particles.filter {it.origin.x<0f && it.origin.y<.85f}
        assertTrue(central.isNotEmpty())
        assertTrue(central.all {(it.position.y-it.origin.y)<(it.position.x-it.origin.x)*.35f})
    }

    @Test fun invalidFanStopsFlowInsteadOfProducingNanPositions() {
        val air = CabinAirflow()
        repeat(60) { air.step(1f / 60f, AirflowMode.COOL, 1f, empty) }
        air.step(1f / 60f, AirflowMode.COOL, Float.NaN, empty)
        assertTrue(air.particles.isEmpty())
    }

    @Test fun footAirStartsAtDashUndersideAndStaysAboveTheFootwellFloor() {
        val air=CabinAirflow(3)
        repeat(120){air.step(1f/60f,AirflowMode.COOL,.8f,empty,setOf(AirflowDirection.FEET))}
        assertTrue(air.particles.isNotEmpty())
        assertTrue("foot air must not originate in mid air below the dash",air.particles.all {it.origin.y in .60f.. .65f && it.origin.x<-.70f})
        assertTrue(air.particles.all {it.position.y<it.origin.y && it.position.y>.27f})
        assertTrue(air.particles.all {it.position.x>it.origin.x})
    }

    @Test fun airflowStopsAtSolidCabinSurfaces() {
        val wall = WeatherCollision(listOf(WeatherCollision.Surface(1,
            floatArrayOf(-.43f,0f,-2f,-.43f,2f,-2f,-.43f,2f,2f,-.43f,0f,2f),intArrayOf(0,1,2,0,2,3))))
        val air=CabinAirflow()
        repeat(60) { air.step(1f/60f,AirflowMode.COOL,1f,wall) }
        assertTrue(air.particles.filter {it.origin.x<0f}.all { it.position.x<-.43f })
    }

    @Test fun outletDirectionsCanRunTogetherAndStopIndependently() {
        val air=CabinAirflow()
        air.step(.1f,AirflowMode.NORMAL,.7f,empty,AirflowDirection.entries.toSet())
        assertEquals(AirflowDirection.entries.toSet(),air.particles.map {it.direction}.toSet())
        assertTrue(air.particles.filter {it.direction==AirflowDirection.DEFROST}.all {it.position.y>it.origin.y})
        assertTrue(air.particles.filter {it.direction==AirflowDirection.FEET}.all {it.position.y<it.origin.y})
        air.step(.1f,AirflowMode.NORMAL,.7f,empty,setOf(AirflowDirection.FEET))
        assertEquals(setOf(AirflowDirection.FEET),air.particles.map {it.direction}.toSet())
        air.step(.1f,AirflowMode.NORMAL,.7f,empty,emptySet())
        assertTrue(air.particles.isEmpty())
    }

    @Test fun defrostCoversWindshieldAndBothSideWindows() {
        val air=CabinAirflow(17)
        repeat(120){air.step(1f/60f,AirflowMode.COOL,.8f,empty,setOf(AirflowDirection.DEFROST))}
        val defrost=air.particles.filter {it.direction==AirflowDirection.DEFROST}
        assertTrue(defrost.isNotEmpty())
        assertTrue("central and side defrost outlets must all flow",defrost.map { it.origin.z }.distinct().size>=5)
        assertTrue("defrost plume should spread beyond the physical slot",defrost.any {it.position.z-it.origin.z>.035f} && defrost.any {it.position.z-it.origin.z<-.035f})
        assertTrue(defrost.all {it.position.y>it.origin.y})
    }

    @Test fun higherFanMovesFlowFasterWithoutExtendingAcrossTheCabin() {
        val low=CabinAirflow(1);val high=CabinAirflow(1)
        repeat(120) {
            low.step(1f/60f,AirflowMode.NORMAL,.1f,empty)
            high.step(1f/60f,AirflowMode.NORMAL,1f,empty)
        }
        assertTrue(high.travel>low.travel*1.8f)
        assertTrue(high.particles.all {(it.position-it.origin).let {d->d.dot(d)}<.16f})
        val before=low.travel
        low.step(1f/60f,AirflowMode.NORMAL,1f,empty)
        assertTrue("fan input must converge instead of jumping",low.travel-before<.015f)
    }
}
