package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class ColorConfigTest {
    @Test fun upperPaintMustNeverUseChromeSelection() {
        val lower=CarTint(floatArrayOf(.1f,.2f,.1f),floatArrayOf(1f,.1f),.8f)
        val upper=CarTint(floatArrayOf(.01f,.01f,.01f),floatArrayOf(1f,.1f),.8f)
        val chrome=CarTint(floatArrayOf(1f,1f,1f),floatArrayOf(1f,.1f),.8f)
        val original=CarColor("双色",listOf(lower,upper,chrome))
        val alternative=CarColor("深色饰件",listOf(chrome,chrome,upper))
        val palette=CarPalette(listOf("M_Carpaint.mat","M_Carpaint_up.mat","M_Chrome_Plating_01.mat"),listOf(original,alternative),0,emptyList())
        assertSame(upper,palette.tintOf(original,"M_Carpaint_up.mat",alternative))
        assertSame(upper,palette.tintOf(original,"M_Chrome_Plating_01.mat",alternative))
        assertEquals(PaintRegion.UPPER,palette.regionOf("M_Carpaint_up.mat"))
    }
    @Test fun trimSelectionChangesSecondaryMaterialsWithoutChangingPaint() {
        val body = CarTint(floatArrayOf(.1f,.2f,.1f),floatArrayOf(1f,.1f),.8f)
        val dark = CarTint(floatArrayOf(.01f,.01f,.01f),floatArrayOf(1f,.1f),.8f)
        val bright = CarTint(floatArrayOf(.8f,.8f,.8f),floatArrayOf(1f,.1f),.8f)
        val green = CarColor("墨绿色",listOf(body,dark))
        val silver = CarColor("银色",listOf(bright,bright))
        val palette = CarPalette(listOf("Paint","Trim"),listOf(green,silver),0,emptyList())
        assertSame(body,palette.tintOf(green,"Paint",silver))
        assertSame(bright,palette.tintOf(green,"Trim",silver))
        assertSame(dark,palette.tintOf(green,"Trim"))
    }
    @Test fun timeChangeOverridesChromeRoughnessAndClearsMaterialDiffuseLighting() {
        
        val original = mapOf<String, Any>(
            "_Roughness" to 0.8f,
            "_EnvSpecInt" to 0.8f,
            "_EnvDiffInt" to 0.007f,
            "_EnvDownCol" to floatArrayOf(0.53f, 0.8f, 1.4f),
            "_EnvSideCol" to floatArrayOf(0.16f, 0.16f, 0.12f)
        )
        val night = EnvMap("Night", "night.ktx", 0.2f, 13f, 0.77f)
        val effective = original + night.uniforms
        assertEquals(0.77f, effective["_Roughness"] as Float, 0f)
        assertEquals(0.2f, effective["_EnvSpecInt"] as Float, 0f)
        assertEquals(0f, effective["_EnvDiffInt"] as Float, 0f)
        assertArrayEquals(FloatArray(3), effective["_EnvDownCol"] as FloatArray, 0f)
        assertArrayEquals(FloatArray(3), effective["_EnvSideCol"] as FloatArray, 0f)
    }

    @Test fun unknownVehicleVariantUsesCommonPaletteBeforeDifferentTrimPalette() {
        val common = "UserResources/Configs/MyCar/Common/Dynasty/HC2/Common/ColorMaterial/ColorMaterial.json"
        val trim = "UserResources/Configs/MyCar/Common/Dynasty/HC2/HC_B0/NS15/ColorMaterial/ColorMaterial.json"
        assertEquals(common, ColorConfig.preferredPath(listOf(trim, common)))
    }

    @Test fun currentDesktopCommonConfigurationMustNotFallBackToAnotherApplication() {
        val current = "UserResources/Configs/MyCar/Common/Dynasty/HC2/HC_17E/NS15/ColorMaterial/ColorMaterial.json"
        val otherCar = "UserResources/Configs/MyCar/Common/Dynasty/SR/NS15/ColorMaterial/ColorMaterial.json"
        val srStyle = "UserResources/Configs/MyCar/CarDesk/Dynasty/HC2/Common/ColorMaterial/ColorMaterial.json"
        assertEquals(listOf(current, srStyle), ColorConfig.candidatePaths(listOf(current, otherCar, srStyle), "Dynasty", "HC2"))
    }
}
