package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class RoofCurtainPlacementTest {
    @Test fun curvedPanelsRemainSeparatedThroughTheWholeTravel() {
        val front=floatArrayOf(-.07f,1.419f,-.43f,.47f,1.443f,.43f)
        val rear=floatArrayOf(.462f,1.434f,-.43f,.89f,1.443f,.43f)
        val offset=RoofCurtain.frontLayerOffset(front,rear)
        assertTrue(front[4]+offset<=rear[1]-.0029f)
        for(i in 0..240) {
            val a=RoofCurtain.retraction(front,.89f,1f,i/240f)
            val b=RoofCurtain.retraction(rear,.89f,1f,i/240f)
            val frontEdge=front[3]*a[0]+a[12]
            val rearEdge=rear[0]*b[0]+b[12]
            assertTrue("进度 $i 接缝不能分离",frontEdge>=rearEdge-.0002f)
        }
    }
    @Test fun differentClipDurationsUseOneContinuousProgress() {
        fun clip(duration:Float)=NodeClip("Sunshade_Clip","panel",duration,arrayOfNulls(3),arrayOfNulls(3),arrayOfNulls(3))
        val front=clip(1.033f);val rear=clip(.533f)
        assertEquals(.4f,RoofCurtain.sharedProgress(mapOf(front to .4f,rear to .8f)),0f)
        assertEquals(.6f,RoofCurtain.sharedProgress(mapOf(front to .6f,rear to 1f)),0f)
    }
    @Test fun closedPanelsCoverOpeningAndKeepAnOverlappingSeam() {
        val fit=RoofCurtain.fitBounds(floatArrayOf(-.2318f,1.419f,-.3555f,.5643f,1.443f,.3555f),
            floatArrayOf(-.305f,1.42f,-.418f,.632f,1.482f,.418f))
        assertTrue(fit[0]<-.305f && fit[3]>.632f)
        assertTrue(fit[2]<-.418f && fit[5]>.418f)
    }
    @Test fun retractingPanelsNeverDropOrShrinkVerticallyAndStayInsideThePocket() {
        val bounds=floatArrayOf(-.32f,1.42f,-.43f,.23f,1.445f,.43f)
        var last=-Float.MAX_VALUE
        for(i in 0..240) {
            val pose=RoofCurtain.retraction(bounds,.65f,.99f,i/240f)
            val left=bounds[0]*pose[0]+pose[12]
            val right=bounds[3]*pose[0]+pose[12]
            assertTrue(left>=last-.00001f);last=left
            assertTrue(right<=.671f)
            assertEquals(1f,pose[5],0f);assertEquals(1f,pose[10],0f)
            assertEquals(0f,pose[13],0f)
            assertTrue(pose.all{it.isFinite()})
        }
        assertTrue(last>=.65f)
    }
}
