package com.haystudio.vehiclelab.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DesktopViewOptionsTest {
    @Test fun antialiasingCanBeDisabledWithoutChangingOtherRenderingOptions() {
        val enabled=DesktopViewOptions.parse(JSONObject("{\"EnableAntialiasing\":true}"))
        val disabled=DesktopViewOptions.parse(JSONObject("{\"EnableAntialiasing\":false}"))
        assertNotEquals(enabled,disabled)
        assertEquals(enabled,DesktopViewOptions.parse(JSONObject("{}")))
    }

    @Test fun desktopDisablesRealtimePassesButKeepsToneMapping() {
        val options = DesktopViewOptions.parse(JSONObject("""{
            "EnableHdr":false,"ToneMapping":true,
            "ShadowOption":{"HasShadowing":false},
            "PlanarReflectionOptions":{"Enable":false},
            "BloomOptions":{"Enable":false}}
        """))
        assertFalse(options.selfShadow)
        assertFalse(options.planarReflection)
        assertFalse(options.hdr)
        assertTrue(options.toneMapping)
        assertFalse(options.bloom)
    }

    @Test fun enabledViewOptionsAreNotDiscarded() {
        val options = DesktopViewOptions.parse(JSONObject("""{
            "EnableHdr":true,"ToneMapping":false,
            "ShadowOption":{"HasShadowing":true},
            "PlanarReflectionOptions":{"Enable":true},
            "BloomOptions":{"Enable":true}}
        """))
        assertTrue(options.selfShadow)
        assertTrue(options.planarReflection)
        assertTrue(options.hdr)
        assertFalse(options.toneMapping)
        assertTrue(options.bloom)
    }
}
