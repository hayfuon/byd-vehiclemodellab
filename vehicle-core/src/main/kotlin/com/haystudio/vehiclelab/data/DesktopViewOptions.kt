package com.haystudio.vehiclelab.data

import org.json.JSONObject

data class DesktopViewOptions(
    val selfShadow: Boolean = false,
    val planarReflection: Boolean = false,
    val hdr: Boolean = false,
    val toneMapping: Boolean = true,
    val bloom: Boolean = false,
    val antiAliasing: Boolean = true,
    val wheelMotionBlur: Boolean = true
) {
    companion object {
        fun parse(json: JSONObject): DesktopViewOptions = DesktopViewOptions(
            selfShadow = json.optJSONObject("ShadowOption")?.optBoolean("HasShadowing", false) ?: false,
            planarReflection = json.optJSONObject("PlanarReflectionOptions")?.optBoolean("Enable", false) ?: false,
            hdr = json.optBoolean("EnableHdr", false),
            toneMapping = json.optBoolean("ToneMapping", true),
            bloom = json.optJSONObject("BloomOptions")?.optBoolean("Enable", false) ?: false,
            antiAliasing = json.optBoolean("EnableAntialiasing", true)
        )
    }
}
