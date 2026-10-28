package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class PrefabSceneTest {
    @Test fun instanceOverridesReplaceTransformAndMaterialsWithoutChangingSharedModel() {
        val model = """{"Class":"Model","Pos":[0,0,0],"Rot":[0,0,0],"Scale":[1,1,1],"MeshPath":"scene.gwm","MeshMaterialArray":["default.mat"]}"""
        val prefab = """{"Child":[{"Child":[{"ID":"ground","${'$'}include":"ground.gmdl"}]}],"pref_data":{"ground":[
          {"op":"replace","path":"/Pos/1","value":0.406},
          {"op":"replace","path":"/Scale/0","value":1.175},
          {"op":"replace","path":"/MeshMaterialArray/0","value":"night.mat"}
        ]}}"""
        val instance = PrefabScene.resolve(JSONObject(prefab)) { JSONObject(model) }.single()
        assertEquals(0.406, instance.getJSONArray("Pos").getDouble(1), 0.00001)
        assertEquals(1.175, instance.getJSONArray("Scale").getDouble(0), 0.00001)
        assertEquals("night.mat", instance.getJSONArray("MeshMaterialArray").getString(0))
        assertEquals("default.mat", JSONObject(model).getJSONArray("MeshMaterialArray").getString(0))
    }
}
