package com.haystudio.vehiclelab.data
import org.junit.Assert.*
import org.junit.Test
class VehicleCornerTest {
    @Test fun allFourDoorsAndWindowsResolveToTheirOwnCorner() {
        for(corner in VehicleCorner.entries) {
            assertEquals(corner,VehicleCorner.fromClip(corner.doorClip))
            assertEquals(corner,VehicleCorner.fromClip(corner.windowClip))
        }
        assertNull(VehicleCorner.fromClip("Door_Hood_Clip"))
    }
}
