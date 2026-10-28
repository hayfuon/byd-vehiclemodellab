package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class CarAssemblyTest {
    private fun config(hidden: String) = """[{"Path":"car.gmdl","Rotation":[90,0,0],
        "Scale":[0.01,0.01,0.01],"Position":[0.25,-0.018,0],"Hidden":["$hidden","Controller"]}]"""

    @Test fun desktopAssemblyUsesPrefabPositionAndOnlyRequestedFascia() {
        val assembly = requireNotNull(CarAssembly.select(listOf(config("Custom_Headstock_DM_01"),
            config("Custom_Headstock_EV_01")), "car.gwm", "DM"))
        assertEquals(0.25f, assembly.transform[12], 0.000001f)
        assertEquals(-0.018f, assembly.transform[13], 0.000001f)
        assertEquals(0.01f, assembly.transform[6], 0.000001f)
        assertTrue("Custom_Headstock_EV_01" in assembly.hidden)
        assertFalse("Custom_Headstock_DM_01" in assembly.hidden)
        assertTrue("Controller" in assembly.hidden)
    }

    @Test fun anotherModelCannotSupplyAssemblyTransform() {
        assertNull(CarAssembly.select(listOf(config("Custom_Headstock_EV_01")), "other.gwm", "DM"))
    }
}
