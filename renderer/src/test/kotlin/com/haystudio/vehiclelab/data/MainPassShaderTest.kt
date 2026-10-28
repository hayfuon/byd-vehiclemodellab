package com.haystudio.vehiclelab.data

import com.haystudio.vehiclelab.render.OfficialProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPassShaderTest {
    @Test fun customPaintHonorsToneMappingAndDefersHdrMappingToPresentation() {
        val source="void main() { outrgb = ACESFilm2BYD(outrgb); OutputFragColor(vec4(outrgb,opacity)); }"
        val result=OfficialProgram.adaptToneMapping(source)
        assertTrue(result.contains("if (!HdrEnabled() && NeedDoToneMapping()) outrgb = ACESFilm2BYD(outrgb);"))
        assertTrue(result.contains("OutputFragColor(vec4(outrgb,opacity));"))
    }

    @Test fun mainPassDoesNotClipRoofPaintOrLampGlassAtGroundPlane() {
        val normal = DefaultMaterials.OTHER.copy(
            vsOptions = setOf("CAR_CL"), fsOptions = setOf("CAR_CL", "USE_BASE_MAP"))
        val clipped = normal.copy(
            vsOptions = normal.vsOptions + "DISCARD_ABOVE",
            fsOptions = normal.fsOptions + "DISCARD_BELOW")
        assertEquals(OfficialProgram.cacheKey(normal, false), OfficialProgram.cacheKey(clipped, false))
    }
}
