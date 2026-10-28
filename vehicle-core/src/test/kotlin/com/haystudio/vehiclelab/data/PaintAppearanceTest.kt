package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test

class PaintAppearanceTest {
    @Test fun upperLowerAndTrimCustomizationsRemainIndependent() {
        val a=PaintStyle(primary=PaintColor(1f,0f,0f))
        val b=PaintStyle(primary=PaintColor(0f,1f,0f))
        val selected=PaintSelection().customize(PaintRegion.LOWER,a).customize(PaintRegion.UPPER,b).selectTrim(1)
        assertEquals(a,selected.body);assertEquals(b,selected.upper)
        assertNull(selected.trim)
        assertNull(selected.selectBody(2).upper)
    }
    @Test fun changingTrimNeverChangesBodyOrReappliesBodyDefault() {
        val custom=PaintStyle(finish=PaintFinish.MATTE)
        val selected=PaintSelection().selectBody(2).customize(false,custom).selectTrim(0)
        assertEquals(2,selected.bodyIndex)
        assertEquals(0,selected.trimIndex)
        assertEquals(custom,selected.body)
        assertNull(selected.trim)
        assertEquals(0,selected.customize(true,custom).trimIndex)
    }
    @Test fun selectingBodyPresetResetsBothCustomizationsAndAssociatesTrimOnce() {
        val selected=PaintSelection(1,0,PaintStyle(),PaintStyle()).selectBody(2)
        assertEquals(2,selected.bodyIndex);assertEquals(2,selected.trimIndex)
        assertNull(selected.body);assertNull(selected.trim)
        val changed=selected.selectTrim(1)
        assertEquals(2,changed.bodyIndex);assertEquals(1,changed.trimIndex)
    }
    @Test fun gradientEndpointsAndColorConversionPreserveIntendedColors() {
        val style=PaintStyle(primary=PaintColor(1f,0f,0f),secondary=PaintColor(0f,0f,1f),gradient=true)
        assertArrayEquals(floatArrayOf(1f,0f,0f),style.colorAt(0f),.00001f)
        assertArrayEquals(floatArrayOf(0f,0f,1f),style.colorAt(1f),.00001f)
        val rgb=PaintColor(.2f,.5f,.8f)
        val restored=PaintColor.fromLinear(rgb.linear())
        assertEquals(rgb.r,restored.r,.00001f);assertEquals(rgb.g,restored.g,.00001f)
    }
    @Test fun finishesChangePhysicalParametersAndInputsStayFinite() {
        assertTrue(PaintFinish.MATTE.roughness>PaintFinish.GLOSS.roughness)
        assertTrue(PaintFinish.PEARL.pearl>0f)
        assertTrue(PaintFinish.METALLIC.metallic>PaintFinish.GLOSS.metallic)
        val clean=PaintStyle(roughness=Float.NaN,metallic=2f,pearl=-2f).normalized()
        assertTrue(clean.roughness.isFinite());assertEquals(1f,clean.metallic,0f);assertEquals(0f,clean.pearl,0f)
    }
}
