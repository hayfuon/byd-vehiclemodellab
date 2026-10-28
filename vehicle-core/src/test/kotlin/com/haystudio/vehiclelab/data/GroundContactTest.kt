package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class GroundContactTest {
    @Test fun movingDoorExcludesItsMetalSkinAndClipDescendantsFromSelfOcclusion() {
        val door="Door_RightFront_Inside"
        assertTrue(CourtesyLightMount.belongsToDoor("Carpaint_Door_RightFront",emptyList(),door))
        assertTrue(CourtesyLightMount.belongsToDoor("Chrome",listOf("Door_RightFront_Clip"),door))
        assertFalse(CourtesyLightMount.belongsToDoor("Carpaint_Door_RightBack",listOf("Door_RightBack_Clip"),door))
        assertFalse(CourtesyLightMount.belongsToDoor("Carpaint_Body",listOf("Body"),door))
    }
    @Test fun doorCourtesyLightFadesBeforeTheLatchRatherThanLeavingSliversInsideTheSill() {
        assertEquals(0f,CourtesyLightMount.brightness(.32f),0f)
        assertEquals(1f,CourtesyLightMount.brightness(.70f),.0001f)
        val gains=(0..100).map{CourtesyLightMount.brightness(it/100f)}
        assertTrue(gains.zipWithNext().all{(a,b)->b>=a && b-a<.06f})
        assertTrue(CourtesyLightMount.brightness(.18f)<.4f)
    }

    @Test fun doorLightClearsMetalSkirtBelowInteriorPanel() {
        val interior=floatArrayOf(-1f,.4f,.8f,0f,1.1f,.9f)
        val metal=floatArrayOf(-1f,.34f,.79f,0f,1.2f,.95f)
        val p=CourtesyLightMount.belowDoor(interior,listOf(interior,metal))
        assertEquals(.32f,p[1],.00001f)
        assertTrue(p[1]<metal[1]);assertTrue(p[1]<interior[1])
        assertEquals(-.5f,p[0],.00001f);assertEquals(.85f,p[2],.00001f)
    }
    @Test fun actualTreadDeterminesGroundInsteadOfUnrelatedModelBounds() {
        val matrix=GwmNodeTree.IDENTITY.copyOf().apply {this[5]=2f;this[13]=.5f}
        assertEquals(-.2f,TireGroundContact.height(listOf(floatArrayOf(0f,-.35f,0f,0f,.35f,0f)),matrix)!!,.00001f)
        assertNull(TireGroundContact.height(emptyList(),matrix))
    }
}
