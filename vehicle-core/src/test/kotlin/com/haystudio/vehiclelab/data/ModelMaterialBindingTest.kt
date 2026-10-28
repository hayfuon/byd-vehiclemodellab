package com.haystudio.vehiclelab.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelMaterialBindingTest {
    @Test fun namedBindingsOverrideArrayByComponentName() {
        val model = JSONObject("""{"MeshMaterialArray":["white.mat"],"MeshNameMaterialMap":{"SM_ShadowPlane":"black.mat"}}""")
        assertEquals("black.mat", ModelMaterialBinding.path(model, "SM_ShadowPlane", 0))
    }

    @Test fun unmatchedNameDoesNotFallBackToIndexedMaterial() {
        val model = JSONObject("""{"MeshMaterialArray":["white.mat"],"MeshNameMaterialMap":{"other":"black.mat"}}""")
        assertNull(ModelMaterialBinding.path(model, "SM_ShadowPlane", 0))
    }

    @Test fun emptyNameMapUsesIndexedMaterials() {
        val model = JSONObject("""{"MeshMaterialArray":["zero.mat","one.mat"],"MeshNameMaterialMap":{}}""")
        assertEquals("one.mat", ModelMaterialBinding.path(model, "part", 1))
    }

    @Test fun prefabOverridesReachNamedMaterialAndTransform() {
        val prefab = JSONObject("""{"Child":[{"ID":"shadow","${'$'}include":"shadow.gmdl"}],"pref_data":{"shadow":[
          {"op":"replace","path":"/Pos/0","value":0.25},
          {"op":"replace","path":"/Scale/2","value":1.015},
          {"op":"replace","path":"/MeshNameMaterialMap/SM_ShadowPlane","value":"desktop.mat"}]}}""")
        val model = PrefabScene.resolve(prefab) { JSONObject("""{"Pos":[0,0,0],"Scale":[1,1,1],"MeshNameMaterialMap":{"SM_ShadowPlane":"default.mat"}}""") }.single()
        assertEquals("desktop.mat", ModelMaterialBinding.path(model, "SM_ShadowPlane", 0))
        assertEquals(0.25, model.getJSONArray("Pos").getDouble(0), 0.000001)
        assertEquals(1.015, model.getJSONArray("Scale").getDouble(2), 0.000001)
    }
}
