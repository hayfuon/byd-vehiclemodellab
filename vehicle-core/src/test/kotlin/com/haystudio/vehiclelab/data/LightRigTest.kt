package com.haystudio.vehiclelab.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LightRigTest {
    @Test fun defaultLightColorMatchesEngineLinearRadiance() {
        val light = LightRig.parse(JSONObject("""{"Class":"Light","Rot":[0,0,0],
          "Color":[0.5,0.25,1],"Intensity":3.5}"""))!!.lights.single()
        assertArrayEquals(floatArrayOf(0.76173174f, 0.1657815f, 3.5f), light.radiance(), 0.000001f)
    }

    @Test fun explicitlyLinearLightColorIsNotConvertedTwice() {
        val light = LightRig.parse(JSONObject("""{"Class":"Light","Rot":[0,0,0],
          "Color":[0.5,0.25,1],"Intensity":2,"UseLinearColor":true}"""))!!.lights.single()
        assertArrayEquals(floatArrayOf(1f, 0.5f, 2f), light.radiance(), 0f)
    }

    @Test fun nightSpotIncludesParentTranslation() {
        
        val rig = LightRig.parse(JSONObject("""{"Class":"Prefab","Child":[{
          "Class":"EmptyNode","Pos":[-0.95667863,0.77858531,0.64495611],"Child":[{
          "Class":"Light","Name":"Spot1","LightType":"Spot",
          "Pos":[-7.92600012,4.49085665,6.10079908],"Rot":[1.39210951,-0.68383747,0]
        }]}]}"""))!!
        assertArrayEquals(floatArrayOf(-8.882679f,5.269442f,6.745755f), rig.lights.single().pos, 0.00001f)
    }

    @Test fun parentRotationAndScaleTransformPositionAndDirection() {
        val light = LightRig.parse(JSONObject("""{"Class":"Prefab","Pos":[1,2,3],
          "Rot":[0,1.57079632679,0],"Scale":[2,2,2],"Child":[{
          "Class":"Light","Pos":[0,0,1],"Rot":[0,0,0]
        }]}"""))!!.lights.single()
        assertArrayEquals(floatArrayOf(3f,2f,3f), light.pos, 0.00001f)
        assertArrayEquals(floatArrayOf(1f,0f,0f), light.forward, 0.00001f)
    }

    @Test fun inactiveParentsDisableChildLights() {
        assertNull(LightRig.parse(JSONObject("""{"Active":false,"Child":[{
          "Class":"Light","Pos":[0,0,0],"Rot":[0,0,0]}]}""")))
    }
    @Test fun explicitlyRequestedShadowsChooseAnExistingLightWithoutAddingRadiance() {
        val rig=LightRig.parse(JSONObject("""{"Child":[
          {"Class":"Light","LightType":"Directional","Rot":[0,0,0],"Intensity":1},
          {"Class":"Light","LightType":"Directional","Rot":[0,0,0],"Intensity":4}
        ]}"""))!!
        assertEquals(-1,rig.shadowCasterIndex(false))
        assertEquals(1,rig.shadowCasterIndex(true))
        assertEquals(2,rig.lights.size)
        assertEquals(4f,rig.lights[1].intensity,0f)
    }
}
