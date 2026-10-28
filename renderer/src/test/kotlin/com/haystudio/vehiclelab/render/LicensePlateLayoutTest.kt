package com.haystudio.vehiclelab.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensePlateLayoutTest {
    @Test fun characterLayoutUsesOneVisibleHeightAndEqualGaps() {
        val boxes=LicensePlateLayout.characterBoxes(listOf(1f,.5f,.45f,.48f,.48f,.48f,.48f,.48f),970f,190f,52f,1f)
        assertEquals(8,boxes.size)
        val height=boxes.first()[3]
        boxes.forEach { assertEquals(height,it[3],.0001f) }
        val gap=boxes[1][0]-boxes[0][0]-boxes[0][2]
        for(i in 1 until boxes.size)assertEquals(gap,boxes[i][0]-boxes[i-1][0]-boxes[i-1][2]-(if(i==2)52f else 0f),.001f)
        assertTrue(boxes.last()[0]+boxes.last()[2]<=970.001f)
    }

    @Test fun glyphFittingPreservesSourceAspectWithoutIndependentAxisStretching() {
        val size=LicensePlateLayout.fitGlyph(75f,147f,96f,220f)
        assertEquals(75f/147f,size[0]/size[1],.00001f)
        assertTrue(size[0]<=96f && size[1]<=220f)
        val chinese=LicensePlateLayout.fitGlyph(100f,100f,96f,220f)
        assertEquals(chinese[0],chinese[1],0f)
    }

    @Test fun newEnergyPlateIsTheDefaultAppearance() {
        assertEquals(PlateStyle.GREEN, PlateAppearance().style)
        assertEquals("测AF88888", PlateAppearance().text)
    }

    @Test fun greenPresetUsesEightCharactersAndOtherPresetsUseSeven() {
        for (style in PlateStyle.entries) {
            val text = PlateAppearance(style=style).text
            assertEquals(if (style == PlateStyle.GREEN) 8 else 7, text.length)
            assertTrue("预设不使用号牌排除字母 I/O", text.none { it == 'I' || it == 'O' })
            assertEquals(text.length-1, LicensePlateLayout.glyphs(text.drop(1)).size)
        }
    }
    @Test fun fixedExperimentPlateUsesOriginalProvinceAndAsciiIndexes() {
        assertEquals(listOf(36, 10, 1, 2, 3, 4, 5),
            LicensePlateLayout.glyphs(LicensePlateLayout.EXPERIMENT_TEXT))
    }

    @Test fun unsupportedCharactersAreRejectedInsteadOfSubstituted() {
        try {
            LicensePlateLayout.glyphs("京A?1")
            throw AssertionError("应拒绝未映射字形")
        } catch (_: IllegalArgumentException) {
        }
    }
    @Test fun presetsExposeBlueGreenWhiteAndYellowPlateBases() {
        assertEquals(floatArrayOf(0f,82f/255f,180f/255f).toList(), PlateStyle.BLUE.base.toList())
        assertEquals(floatArrayOf(53f/255f,185f/255f,99f/255f).toList(), PlateStyle.GREEN.base.toList())
        assertEquals(floatArrayOf(.95f,.96f,.96f).toList(), PlateStyle.WHITE.base.toList())
        assertEquals(floatArrayOf(1f,202f/255f,0f).toList(), PlateStyle.YELLOW.base.toList())
    }
    @Test fun rearPlateUpVectorAlwaysPointsUpward() {
        val up=PlateAnchor.upFromNormal(floatArrayOf(.98f,-.20f,0f))
        assertTrue(up[1] > 0f)
    }
    @Test fun rearEndFaceAnchorPreservesTheMeasuredBlackPlateSlope() {
        val normal = floatArrayOf(.956f, .289f, 0f)
        val center = floatArrayOf(2.539f, .727f, 0f)
        val up = PlateAnchor.upFromNormal(normal)
        fun point(lateral: Float, vertical: Float) = floatArrayOf(
            center[0] + up[0] * vertical,
            center[1] + up[1] * vertical,
            lateral
        )
        val positions = listOf(point(-.241f, -.075f), point(.241f, -.075f),
            point(.241f, .075f), point(-.241f, .075f)).flatMap { it.toList() }.toFloatArray()
        val normals = FloatArray(12) { normal[it % 3] }
        val anchor = PlateAnchor.fromEndFace(positions, normals, rear = true)!!
        assertEquals(.956f, anchor.normal[0], .002f)
        assertEquals(.289f, anchor.normal[1], .002f)
        assertEquals(.482f, anchor.width, .003f)
        assertEquals(.15f, anchor.height, .003f)
        assertTrue("后牌从车尾看文字应正向", anchor.lateral[2] < 0f)
        val bottomLeft = point(.241f, -.075f)
        val topRight = point(-.241f, .075f)
        val uv0 = anchor.uv(bottomLeft[0], bottomLeft[1], bottomLeft[2])
        val uv1 = anchor.uv(topRight[0], topRight[1], topRight[2])
        assertEquals(0f, uv0[0], .003f)
        assertEquals(1f, uv0[1], .003f)
        assertEquals(1f, uv1[0], .003f)
        assertEquals(0f, uv1[1], .003f)
    }
}
