package com.haystudio.vehiclelab.render

import org.junit.Assert.assertTrue
import org.junit.Test

class PlateLightingTest {
    @Test fun licensePlateKeepsAReflectiveClearCoatedSurface() {
        val p = PlateLighting.surface()
        assertTrue(p.metallic == 0f) 
        assertTrue(p.roughness <= .26f)
        assertTrue(p.clearCoat[0] >= .7f)
        assertTrue(p.envSpecular >= .6f)
    }

    @Test fun greenPlateUsesTheOfficialDarkLetteringContrast() {
        assertTrue(PlateStyle.GREEN.text.max() < .2f)
        assertTrue(PlateStyle.GREEN.base[1] > PlateStyle.GREEN.base[0])
    }
}
