package com.haystudio.vehiclelab.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MaterialNormalTest {
    
    private val ground = JSONObject("""{
      "brdfType":"pbrMetallicRoughness",
      "normalTexture":{"source":"ground_n.ktx","isLinear":true,
        "scale":[1,1],"translation":[0.25,0.5],"bumpScale":0.1,
        "UFunction":{"UVFunctionType":5,"UVFunctionParam":[10.8,0,0,0]},
        "VFunction":{"UVFunctionType":5,"UVFunctionParam":[10.8,0,0,0]}},
      "brdf":{"baseColor":[0.56863,0.56863,0.56863],"metallic":0.8,"roughness":0.2}
    }""")

    @Test fun groundRetainsAuthoredNormalTextureAndIndependentTiling() {
        val spec = MaterialLibrary.parse("m_env_ground_night.mat", ground)
        assertEquals("ground_n.ktx", spec.normalTex)
        assertArrayEquals(floatArrayOf(10.8f,10.8f), spec.normalUvScale, 0.00001f)
        assertArrayEquals(floatArrayOf(0.25f,0.5f), spec.normalUvOffset, 0f)
        assertEquals(0.1f, spec.bumpScale, 0f)
    }

    @Test fun bothEngineMaterialBlocksRetainNormalStrength() {
        val spec = MaterialLibrary.parse("m_env_ground_night.mat", ground)
        for (block in listOf(MaterialLibrary.pbrBlock(spec), MaterialLibrary.phongBlock(spec))) {
            assertEquals(0.1f, block[10], 0f)
            assertEquals(0.1f, block[11], 0f)
        }
    }
}
