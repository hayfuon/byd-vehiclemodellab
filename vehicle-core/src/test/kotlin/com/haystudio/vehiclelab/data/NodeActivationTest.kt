package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class NodeActivationTest {
    @Test fun runtimeCanActivateTheSameNodeHiddenByThePrefab() {
        assertFalse(NodeActivation.isHidden(setOf("Body", "Light_03", "mesh"),
            setOf("Light_03"), setOf("Light_03"), emptySet()))
    }

    @Test fun activatingAChildCannotActivateItsHiddenParent() {
        assertTrue(NodeActivation.isHidden(setOf("Custom_Headstock_EV_01", "Light_03"),
            setOf("Custom_Headstock_EV_01", "Light_03"), setOf("Light_03"), emptySet()))
    }

    @Test fun activatingAParentCannotActivateItsHiddenChild() {
        assertTrue(NodeActivation.isHidden(setOf("Light_03", "Light_03_bloom"),
            setOf("Light_03", "Light_03_bloom"), setOf("Light_03"), emptySet()))
    }

    @Test fun explicitDisableStillHidesAnInitiallyVisibleNode() {
        assertTrue(NodeActivation.isHidden(setOf("Body", "Day"), emptySet(),
            emptySet(), setOf("Day")))
    }

    @Test fun visibleUncontrolledLampNodesKeepTheirPrefabState() {
        assertFalse(NodeActivation.isHidden(setOf("Body", "Light_Front_Mid_Off"),
            setOf("Light_03"), emptySet(), emptySet()))
    }

    @Test fun hiddenUncontrolledLampNodesKeepTheirPrefabState() {
        assertTrue(NodeActivation.isHidden(setOf("Body", "Light_20"),
            setOf("Light_20"), emptySet(), emptySet()))
    }
}
