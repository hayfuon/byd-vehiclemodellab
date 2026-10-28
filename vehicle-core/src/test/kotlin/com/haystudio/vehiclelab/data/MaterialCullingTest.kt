package com.haystudio.vehiclelab.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialCullingTest {
    private fun parse(json: String) = MaterialLibrary.parse("M_Test.mat",
        JSONObject(json).put("brdfType", "pbrMetallicRoughness")
            .put("brdf", JSONObject("""{"baseColor":[1,1,1]}""")))

    @Test fun engineMaterialCullsBackFacesUnlessDoubleSided() {
        assertEquals(FaceCulling.BACK, parse("""{"doubleSided":false}""").faceCulling)
        assertEquals(FaceCulling.NONE, parse("""{"doubleSided":true}""").faceCulling)
    }

    @Test fun customRenderStateOverridesTopLevelDoubleSided() {
        assertEquals(FaceCulling.NONE, parse("""{"doubleSided":false,
          "Material":{"RenderStateSettings":{"FaceCullingMode":"None"}}}""").faceCulling)
        assertEquals(FaceCulling.FRONT, parse("""{"doubleSided":true,
          "Material":{"RenderStateSettings":{"FaceCullingMode":"Front"}}}""").faceCulling)
        assertEquals(FaceCulling.BACK, parse("""{"doubleSided":true,
          "Material":{"RenderStateSettings":{"FaceCullingMode":"Back"}}}""").faceCulling)
    }
}
