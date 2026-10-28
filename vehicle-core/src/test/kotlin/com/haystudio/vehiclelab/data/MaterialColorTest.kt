package com.haystudio.vehiclelab.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MaterialColorTest {
    @Test fun explicitZeroEmissionKeepsLampMaterialUnlit() {
        val spec = MaterialLibrary.parse("M_Light_White.mat", JSONObject("""{
          "brdfType":"pbrMetallicRoughness",
          "brdf":{"baseColor":[1,1,1]},
          "emissiveColor":[0.8,0.7,0.6],"emissiveIntensity":0
        }"""))
        assertEquals(0f, spec.emissiveInt, 0f)
        assertArrayEquals(floatArrayOf(0.8f,0.7f,0.6f), spec.emissive, 0f)
    }

    private fun material(linear: Boolean) = DefaultMaterials.OTHER.copy(
        base = floatArrayOf(0.5f, 0.25f, 1f),
        emissive = floatArrayOf(0.5f, 0.25f, 0f), emissiveInt = 0.5f,
        opacity = 0.7f, useLinearColor = linear)

    @Test fun authoredColorIsLinearizedBeforeUploadingEngineMaterial() {
        val block = MaterialLibrary.pbrBlock(material(false))
        assertEquals(0.21763764f, block[0], 0.000001f)
        assertEquals(0.04736614f, block[1], 0.000001f)
        assertEquals(0.21763764f, block[4], 0.000001f)
        assertEquals(0.7f, block[3], 0f)
        assertEquals(0.5f, block[7], 0f)
    }

    @Test fun explicitlyLinearColorIsNotConvertedTwice() {
        val block = MaterialLibrary.pbrBlock(material(true))
        assertEquals(0.5f, block[0], 0f)
        assertEquals(0.25f, block[1], 0f)
        assertEquals(0.5f, block[4], 0f)
    }
}
