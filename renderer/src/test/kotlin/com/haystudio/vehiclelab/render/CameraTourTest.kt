package com.haystudio.vehiclelab.render

import org.junit.Assert.*
import org.junit.Test

class CameraTourTest {
    @Test fun retargetKeepsMotionInsteadOfStoppingAtEverySelection() {
        val tour=CameraTour();val end=pose(0f,-90f)
        tour.begin(pose(12f,-90f))
        repeat(35){tour.advance(end,.01f)}
        val before=tour.advance(end,.001f)
        val current=tour.advance(end,.001f)
        val velocity=(current.eye[0]-before.eye[0])/.001f
        tour.begin(current)
        assertArrayEquals(current.eye,tour.advance(pose(-3f,-90f),0f).eye,0f)
        val next=tour.advance(pose(-3f,-90f),.001f)
        assertEquals("接替不能把运动速度重置为零",velocity,(next.eye[0]-current.eye[0])/.001f,kotlin.math.abs(velocity)*.08f)
    }
    private fun pose(x:Float,yaw:Float)=CameraView(floatArrayOf(x,1.12f,0f),yaw,-4f,65f,.025f,0f,0f)
    @Test fun switchKeepsTheCurrentFrameAndArrivesAtTheExactDestination() {
        val tour=CameraTour();val from=pose(12f,170f);val to=pose(.7f,-90f)
        tour.begin(from)
        assertEquals(12f,tour.advance(to,0f).eye[0],0f)
        var previous=12f
        repeat(80){val p=tour.advance(to,1f/60);assertTrue(p.eye[0]<=previous+.00001f);previous=p.eye[0]}
        assertEquals(.7f,previous,.00001f)
    }
    @Test fun interruptionStartsAtDisplayedPoseAndYawTakesTheShortArc() {
        val tour=CameraTour();tour.begin(pose(12f,179f))
        val middle=tour.advance(pose(.7f,-179f),.1f)
        assertTrue(kotlin.math.abs(middle.yaw-179f)<2f)
        tour.begin(middle)
        val start=tour.advance(pose(12f,20f),0f)
        assertArrayEquals(middle.eye,start.eye,0f)
        assertEquals(middle.yaw,start.yaw,0f)
    }

    @Test fun gestureInterruptionRebasesFromTheDisplayedFrameWithoutCancellingTheTour() {
        val tour=CameraTour();tour.begin(pose(12f,170f))
        val middle=tour.advance(pose(.7f,-90f),.18f)
        tour.rebase(middle)
        assertTrue(tour.isActive)
        assertArrayEquals(middle.eye,tour.advance(pose(.7f,-90f),0f).eye,0f)
    }

    @Test fun transitionInterpolatesTheOriginalEyeAndAimPointsWithTheHistoricalArc() {
        val from=CameraView.looking(floatArrayOf(-8.8f,2.44f,8.5f),floatArrayOf(0f,.7f,0f),23f,.1f,0f,0f)
        val to=CameraView.looking(floatArrayOf(.70f,1.12f,0f),floatArrayOf(.1f,1.2f,.9f),65f,.025f,0f,0f)
        val fromAim=from.aim();val toAim=to.aim()
        val tour=CameraTour();tour.begin(from)
        var actual=from
        repeat(5) { actual=tour.advance(to,.085f) }
        val t=(5f*.085f/CameraTour.DURATION_SECONDS).coerceIn(0f,1f)
        val a=t*t*t*(t*(t*6f-15f)+10f)
        val eye=FloatArray(3) { from.eye[it]+(to.eye[it]-from.eye[it])*a }
        val aim=FloatArray(3) { fromAim[it]+(toAim[it]-fromAim[it])*a }
        assertTrue(kotlin.math.abs(actual.eye[0]-eye[0])+kotlin.math.abs(actual.eye[2]-eye[2])>.1f)
        assertArrayEquals(aim,actual.aim(),.01f)
        assertEquals(from.fov+(to.fov-from.fov)*a,actual.fov,.01f)
    }
    @Test fun enteringCabinKeepsTheCarInViewBeforeReachingTheBody() {
        val from=CameraView.looking(floatArrayOf(-8.8f,2.44f,8.5f),floatArrayOf(0f,.7f,0f),23f,.1f,0f,0f)
        val tour=CameraTour();tour.begin(from)
        val target=pose(.7f,-90f)
        var middle=from
        repeat(25){middle=tour.advance(target,1f/60f)}
        val ray=middle.aim().zip(middle.eye){a,b->a-b}
        val toCar=middle.eye.mapIndexed{i,v->(if(i==1).7f else 0f)-v}
        assertTrue(ray.indices.sumOf{(ray[it]*toCar[it]).toDouble()}>0)
        var last=middle
        repeat(40) {
            val next=tour.advance(target,1f/60f)
            val yawStep=((next.yaw-last.yaw+540f)%360f)-180f
            assertTrue("转向不能骤然翻转",kotlin.math.abs(yawStep)<30f)
            last=next
        }
    }

    @Test fun opposingViewTransitionUsesTheOriginalSmallAvoidanceArc() {
        val from=CameraView.looking(floatArrayOf(-8.8f,2.44f,8.5f),floatArrayOf(0f,.7f,0f),23f,.1f,0f,0f)
        val target=pose(.7f,-90f)
        val tour=CameraTour();tour.begin(from)
        var middle=from
        repeat(5){middle=tour.advance(target,.085f)}
        val directX=(from.eye[0]+target.eye[0])*.5f
        val directZ=(from.eye[2]+target.eye[2])*.5f
        assertTrue("外部运镜应保留避车弧线",kotlin.math.abs(middle.eye[0]-directX)+kotlin.math.abs(middle.eye[2]-directZ)>.1f)
        repeat(7){tour.advance(target,.1f)}
        assertArrayEquals(target.eye,tour.advance(target,0f).eye,.0001f)
    }

    @Test fun exteriorAndCabinPresetChangesUseOnlyTheHistoricalBoundedArc() {
        val outsideEyes=listOf(
            floatArrayOf(-8.8f,2.44f,8.5f),floatArrayOf(-8.8f,2.44f,-8.5f),
            floatArrayOf(-12.5f,2f,0f),floatArrayOf(12.5f,2f,0f),
            floatArrayOf(-8f,2f,8f),floatArrayOf(8f,2f,-8f),
            floatArrayOf(0f,5f,12.5f)
        )
        val cabinEyes=listOf(
            floatArrayOf(-.08f,1.13f,.37f),floatArrayOf(-.08f,1.13f,-.37f),
            floatArrayOf(.94f,1.16f,.38f),floatArrayOf(.94f,1.16f,-.38f),
            floatArrayOf(.70f,1.12f,0f),floatArrayOf(.34f,1.02f,0f)
        )
        for(outsideEye in outsideEyes) for(cabinEye in cabinEyes) {
            val outside=CameraView.looking(outsideEye,floatArrayOf(0f,.7f,0f),23f,.1f,0f,0f)
            val cabin=CameraView.looking(cabinEye,floatArrayOf(0f,.7f,0f),65f,.025f,0f,0f)
            for((start,target) in listOf(outside to cabin,cabin to outside)) {
                val tour=CameraTour();tour.begin(start,cabin=true)
                var maxDeviation=0f
                repeat(240) {
                    val p=tour.advance(target,1f/120f)
                    val a=tour.progress
                    val expected=FloatArray(3){start.eye[it]+(target.eye[it]-start.eye[it])*a}
                    maxDeviation=maxOf(maxDeviation,kotlin.math.sqrt(p.eye.indices.sumOf{(p.eye[it]-expected[it]).toDouble().let{v->v*v}}).toFloat())
                }
                assertTrue("避车弧偏移不应超过历史值",maxDeviation<=.601f)
                assertArrayEquals(target.eye,tour.advance(target,0f).eye,.0001f)
            }
        }
    }

    @Test fun everyPresetUsesTheSameSmoothTransitionDuration() {
        val tour = CameraTour()
        val from = pose(12f, 170f)
        val to = pose(.7f, -90f)
        tour.begin(from)
        var atOneSecond = from
        repeat(8) { atOneSecond = tour.advance(to, .1f) }
        assertTrue(tour.isActive)
        assertTrue(atOneSecond.eye[0] > to.eye[0])
        val arrived = tour.advance(to, .05f)
        assertFalse(tour.isActive)
        assertArrayEquals(to.eye, arrived.eye, .0001f)
    }
    @Test fun exteriorOppositeViewsStayOutsideTheCarAndArriveWithoutDiscontinuity() {
        val tour=CameraTour()
        val from=CameraView.looking(floatArrayOf(-4f,1.4f,0f),floatArrayOf(0f,.75f,0f),42f,.1f,0f,0f)
        val target=CameraView.looking(floatArrayOf(4f,1.4f,0f),floatArrayOf(0f,.75f,0f),42f,.1f,0f,0f)
        tour.begin(from,exterior=true)
        assertArrayEquals(from.eye,tour.advance(target,0f).eye,.00001f)
        var previous=from
        repeat(200) {
            val v=tour.advance(target,1f/120f)
            val x=v.eye[0]/2.9f;val y=(v.eye[1]-.75f)/1.35f;val z=v.eye[2]/1.4f
            assertTrue("镜头不得进入车身包围体",x*x+y*y+z*z>1f)
            assertTrue("镜头须连续",v.eye.indices.sumOf { val d=(v.eye[it]-previous.eye[it]).toDouble();d*d }<.04)
            previous=v
        }
        assertArrayEquals(target.eye,tour.advance(target,.1f).eye,.00001f)
    }
    @Test fun frontToRearKeepsAConstantOrbitRadiusAndOneContinuousAngularSweep() {
        val start=CameraView.looking(floatArrayOf(-12.5f,2f,0f),floatArrayOf(0f,.75f,0f),42f,.1f,0f,0f)
        val end=CameraView.looking(floatArrayOf(12.5f,2f,0f),floatArrayOf(0f,.75f,0f),42f,.1f,0f,0f)
        val expected=kotlin.math.sqrt(12.5f*12.5f+1.25f*1.25f)
        val tour=CameraTour();tour.begin(start,exterior=true)
        var previousAngle=kotlin.math.atan2(start.eye[0],start.eye[2]);var direction=0f
        repeat(180) {
            val p=tour.advance(end,1f/120f)
            val r=kotlin.math.sqrt(p.eye[0]*p.eye[0]+(p.eye[1]-.75f)*(p.eye[1]-.75f)+p.eye[2]*p.eye[2])
            assertEquals("绕车时不能先缩近再拉远",expected,r,.0001f)
            val angle=kotlin.math.atan2(p.eye[0],p.eye[2])
            val delta=kotlin.math.atan2(kotlin.math.sin(angle-previousAngle),kotlin.math.cos(angle-previousAngle))
            if(kotlin.math.abs(delta)>.00001f) {
                if(direction==0f)direction=kotlin.math.sign(delta)
                assertTrue("中途不能换向或折返",delta*direction>=0f)
            }
            previousAngle=angle
        }
        assertArrayEquals(end.eye,tour.advance(end,0f).eye,.0001f)
    }
    @Test fun cabinEntryAndExitHaveTheSameDurationAndBoundedAngularSpeed() {
        val outside=CameraView.looking(floatArrayOf(-8.8f,2.44f,8.5f),floatArrayOf(0f,.7f,0f),23f,.1f,0f,0f)
        val inside=pose(.7f,-90f)
        for((start,end) in listOf(outside to inside,inside to outside)) {
            val tour=CameraTour();tour.begin(start,cabin=true)
            var previous=start
            repeat(89) {
                val next=tour.advance(end,1f/60f)
                val delta=((next.yaw-previous.yaw+540f)%360f)-180f
                assertTrue("座舱转换朝向不得急转",kotlin.math.abs(delta)<5f)
                previous=next
            }
            assertTrue("进出舱均须保留完整过渡",tour.isActive)
            tour.advance(end,1f/60f);val arrived=tour.advance(end,1f/60f)
            assertFalse(tour.isActive)
            assertArrayEquals(end.eye,arrived.eye,.0001f)
        }
    }

    @Test fun cabinCameraSpeedScalesWithTravelAndFieldOfViewChange() {
        val outside=CameraView.looking(floatArrayOf(-8.8f,2.44f,8.5f),floatArrayOf(0f,.7f,0f),23f,.1f,0f,0f)
        val cabin=pose(.7f,-90f)
        val nearby=pose(.76f,-88f)
        fun duration(from:CameraView,to:CameraView):Float {
            val tour=CameraTour();tour.begin(from,cabin=true)
            var elapsed=0f
            while(tour.isActive && elapsed<3f) {tour.advance(to,1f/120f);elapsed+=1f/120f}
            assertFalse("过渡应能在上限内完成",tour.isActive)
            return elapsed
        }
        val entry=duration(outside,cabin)
        val exit=duration(cabin,outside)
        val nearbyChange=duration(cabin,nearby)
        assertEquals("进舱和出舱速度映射一致",entry,exit,.02f)
        assertTrue("相邻小幅机位切换不应沿用整段进出舱时长",entry>nearbyChange+.25f)
        assertTrue("大幅进出舱保持在优雅时长内",entry<=CameraTour.CABIN_DURATION_SECONDS+.02f)
    }

    @Test fun aMovingRequestedTargetDoesNotReplanTheActiveTour() {
        val start=pose(12f,170f);val end=pose(.7f,-90f)
        val stable=CameraTour();val changing=CameraTour()
        stable.begin(start,cabin=true);changing.begin(start,cabin=true)
        changing.advance(end,0f)
        repeat(80) { frame ->
            val expected=stable.advance(end,1f/60f)
            val jitter=end.copy(eye=floatArrayOf(end.eye[0]+frame*.01f,1.12f,0f),yaw=end.yaw+frame*.1f)
            val actual=changing.advance(jitter,1f/60f)
            assertArrayEquals(expected.eye,actual.eye,.0001f)
            assertEquals(expected.yaw,actual.yaw,.0001f)
        }
    }

    @Test fun cabinTransitionKeepsTheInterpolatedFocusInFrontAndIsReversible() {
        val outside=CameraView.looking(floatArrayOf(-8.8f,2.44f,8.5f),floatArrayOf(0f,.7f,0f),23f,.1f,0f,0f)
        val inside=pose(.7f,-90f)
        val entry=CameraTour();entry.begin(outside,cabin=true)
        val exit=CameraTour();exit.begin(inside,cabin=true)
        val entryFrames=mutableListOf<CameraView>();val exitFrames=mutableListOf<CameraView>()
        entryFrames+=entry.advance(inside,0f);exitFrames+=exit.advance(outside,0f)
        repeat(87) {
            entryFrames+=entry.advance(inside,CameraTour.CABIN_DURATION_SECONDS/87)
            exitFrames+=exit.advance(outside,CameraTour.CABIN_DURATION_SECONDS/87)
        }
        for(i in entryFrames.indices) {
            val a=entryFrames[i];val b=exitFrames[87-i]
            assertArrayEquals("进出舱使用同一条可逆轨迹",a.eye,b.eye,.0005f)
            val progress=((a.fov-outside.fov)/(inside.fov-outside.fov)).coerceIn(0f,1f)
            val expectedAim=FloatArray(3){outside.aim()[it]+(inside.aim()[it]-outside.aim()[it])*progress}
            assertArrayEquals("过渡时车辆注视点不得滑出视线",expectedAim,a.aim(),.0001f)
            assertTrue(a.focusDistance>.05f)
        }
    }

}
