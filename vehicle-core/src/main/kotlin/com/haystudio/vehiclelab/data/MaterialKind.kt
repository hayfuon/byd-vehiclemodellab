package com.haystudio.vehiclelab.data

enum class MaterialKind(
    val base: FloatArray,
    val metallic: Float,
    val roughness: Float,
    val emissive: Float,
    val alpha: Float
) {
    PAINT(floatArrayOf(0.62f, 0.66f, 0.70f), 0.55f, 0.22f, 0f, 1f),
    GLASS(floatArrayOf(0.035f, 0.05f, 0.07f), 0.10f, 0.06f, 0f, 0.42f),
    CHROME(floatArrayOf(0.92f, 0.94f, 0.97f), 1.0f, 0.10f, 0f, 1f),
    BLACK_TRIM(floatArrayOf(0.045f, 0.050f, 0.058f), 0.18f, 0.62f, 0f, 1f),
    TIRE(floatArrayOf(0.035f, 0.035f, 0.038f), 0.02f, 0.88f, 0f, 1f),
    RIM(floatArrayOf(0.72f, 0.74f, 0.78f), 0.95f, 0.26f, 0f, 1f),
    CALIPER(floatArrayOf(0.62f, 0.10f, 0.09f), 0.55f, 0.34f, 0f, 1f),
    LIGHT_WHITE(floatArrayOf(1.0f, 0.98f, 0.94f), 0.0f, 0.18f, 1.6f, 1f),
    LIGHT_RED(floatArrayOf(1.0f, 0.10f, 0.08f), 0.0f, 0.22f, 1.2f, 1f),
    LIGHT_OFF(floatArrayOf(0.16f, 0.17f, 0.19f), 0.35f, 0.10f, 0.05f, 1f),
    INTERIOR(floatArrayOf(0.10f, 0.11f, 0.125f), 0.05f, 0.72f, 0f, 1f),
    SHADOW(floatArrayOf(0.0f, 0.0f, 0.0f), 0f, 1f, 0f, 0.30f),
    OTHER(floatArrayOf(0.42f, 0.45f, 0.50f), 0.30f, 0.48f, 0f, 1f);

    companion object {
        fun of(material: String, meshName: String): MaterialKind {
            val m = material.lowercase()
            val n = meshName.lowercase()
            return when {
                m.contains("carpaint") -> PAINT
                m.contains("glass") || m.contains("window") -> GLASS
                m.contains("chrome") -> CHROME
                m.contains("tireshadow") || m.contains("shadow") -> SHADOW
                m.contains("tire_disk") || m.contains("disk") -> RIM
                m.contains("tire") -> TIRE
                m.contains("calliper") -> CALIPER
                m.contains("light_red") || m.contains("light_rear") -> LIGHT_RED
                m.contains("light_white") || m.contains("light_line") ||
                    m.contains("light_blue") -> LIGHT_WHITE
                m.contains("light") -> LIGHT_OFF
                m.contains("seat") || m.contains("dash") || m.contains("controller") ->
                    INTERIOR
                m.contains("plastics") || m.contains("baked_paint") ||
                    m.contains("gap") || m.contains("door") -> BLACK_TRIM
                n.contains("glass") -> GLASS
                n.contains("wheel") || n.contains("tire") -> RIM
                n.contains("paint") -> PAINT
                else -> OTHER
            }
        }
    }
}
