package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowTravelTest {
    @Test fun windowProgressSkipsStationaryLeadAndTail() {
        val track=AnimTrack(floatArrayOf(0f,.2f,.8f,1f),floatArrayOf(0f,0f,100f,100f))
        val clip=NodeClip("Window_Test","glass",1f,arrayOf(null,track,null),arrayOf(null,null,null),arrayOf(null,null,null))
        for(p in listOf(0f,.01f,.04f,.5f,.98f,1f)) {
            assertEquals(p*100f,clip.localAtTravel(p)[13],.04f)
        }
    }
}
