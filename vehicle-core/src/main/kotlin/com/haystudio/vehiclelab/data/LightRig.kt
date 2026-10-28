package com.haystudio.vehiclelab.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

class CarLight(
    val name: String,
    
    val type: Int,
    
    val pos: FloatArray,
    
    val forward: FloatArray,
    val color: FloatArray,
    val intensity: Float,
    val range: Float,
    val innerDeg: Float,
    val outerDeg: Float,
    val specularScale: Float,
    val atten: FloatArray,
    
    val castShadow: Boolean = false,
    val useLinearColor: Boolean = false
) {
    
    internal fun radiance() = FloatArray(3) { i ->
        (if (useLinearColor) color[i] else color[i].pow(2.2f)) * intensity
    }
}

class LightRig(val lights: List<CarLight>) {

    fun shadowCasterIndex(requested: Boolean): Int {
        val candidates=lights.take(MAX_LIGHTS)
        val configured=candidates.indexOfFirst { it.castShadow }
        if(!requested)return configured
        
        return candidates.indices.filter { candidates[it].type == 0 }
            .maxByOrNull { candidates[it].radiance().sum() }
            ?: configured.takeIf { it >= 0 }
            ?: candidates.indices.filter { candidates[it].type == 2 }
                .maxByOrNull { candidates[it].radiance().sum() } ?: -1
    }

    fun pack(matrix: FloatArray, requestedShadows: Boolean = false): PackedLights {
        val n = lights.size.coerceAtMost(MAX_LIGHTS)
        val block = FloatArray(MAX_LIGHTS * VEC_PER_LIGHT * 4 + 4)
        val physical = FloatArray(MAX_LIGHTS * 4)
        
        for (i in 0 until MAX_LIGHTS) block[i * VEC_PER_LIGHT * 4 + 18] = -1f

        val p = FloatArray(4)
        val o = FloatArray(4)

        val shadowLight = shadowCasterIndex(requestedShadows)
        for (i in 0 until n) {
            val l = lights[i]
            val base = i * VEC_PER_LIGHT * 4
            p[0] = l.pos[0]; p[1] = l.pos[1]; p[2] = l.pos[2]; p[3] = 1f
            android.opengl.Matrix.multiplyMV(o, 0, matrix, 0, p, 0)

            val dx = l.forward[0]
            val dy = l.forward[1]
            val dz = l.forward[2]
            var tx = matrix[0] * dx + matrix[4] * dy + matrix[8] * dz
            var ty = matrix[1] * dx + matrix[5] * dy + matrix[9] * dz
            var tz = matrix[2] * dx + matrix[6] * dy + matrix[10] * dz
            val len = kotlin.math.sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1.0e-6f)
            tx /= len; ty /= len; tz /= len

            val radiance = l.radiance()
            block[base] = radiance[0]
            block[base + 1] = radiance[1]
            block[base + 2] = radiance[2]
            
            block[base + 4] = radiance[0] * l.specularScale
            block[base + 5] = radiance[1] * l.specularScale
            block[base + 6] = radiance[2] * l.specularScale
            
            block[base + 8] = o[0]; block[base + 9] = o[1]; block[base + 10] = o[2]
            
            block[base + 12] = tx; block[base + 13] = ty; block[base + 14] = tz
            
            block[base + 16] = cos(Math.toRadians(l.innerDeg.toDouble())).toFloat()
            block[base + 17] = cos(Math.toRadians(l.outerDeg.toDouble())).toFloat()
            block[base + 18] = l.type.toFloat()
            block[base + 19] = l.range
            
            block[base + 21] = l.atten[0]
            block[base + 22] = l.atten[1]
            block[base + 23] = l.atten[2]

            block[base + 24] = if (i == shadowLight) 1f else -1f
        }

        val noDir = block.copyOf()
        for (i in 0 until n) {
            if (lights[i].type == 0) noDir[i * VEC_PER_LIGHT * 4 + 18] = -1f
        }
        return PackedLights(n, block, noDir, physical)
    }

    companion object {
        const val MAX_LIGHTS = 8

        const val VEC_PER_LIGHT = 7

        fun load(reader: GpakReader?, modelPath: String, period: DayPeriod): LightRig? {
            if (reader == null) return null
            val name = when (period) {
                DayPeriod.MORNING -> "LightMorn"
                DayPeriod.NOON -> "LightNoon"
                DayPeriod.NIGHT -> "LightNight"
            }
            val path = pathOf(reader, modelPath, name) ?: return null
            val raw = try {
                reader.read(path)
            } catch (_: Throwable) {
                null
            } ?: return null
            val root = try {
                JSONObject(String(raw, Charsets.UTF_8))
            } catch (_: Throwable) {
                return null
            }
            return parse(root)
        }

        internal fun parse(root: JSONObject): LightRig? {
            val out = ArrayList<CarLight>(4)
            walk(root, identity(), out)
            if (out.isEmpty()) return null
            return LightRig(out)
        }

        private fun pathOf(reader: GpakReader, modelPath: String, name: String): String? {
            val parts = modelPath.split('/')
            val brand = parts.getOrNull(2)
            val family = parts.getOrNull(3)
            if (brand != null && family != null) {
                val direct = "MyCar/CarDesk/$brand/$family/Common/Prefabs/NS15/Light/$name.gpref"
                if (reader.read(direct) != null) return direct
            }
            val suffix = "/Light/$name.gpref"
            val hit = try {
                reader.names().firstOrNull { it.endsWith(suffix, ignoreCase = true) }
            } catch (_: Throwable) {
                null
            }
            return hit
        }

        private fun identity() = FloatArray(16) { if (it % 5 == 0) 1f else 0f }

        private fun localTransform(node: JSONObject): FloatArray {
            fun tracks(key: String, fallback: Float): Array<AnimTrack?> {
                val values = node.optJSONArray(key)
                return Array(3) { i ->
                    AnimTrack(floatArrayOf(0f), floatArrayOf(values?.optDouble(i, fallback.toDouble())?.toFloat() ?: fallback))
                }
            }
            return NodeClip("light", "light", 0f, tracks("Pos", 0f), tracks("Rot", 0f),
                tracks("Scale", 1f)).localAt(0f)
        }

        private fun walk(node: JSONObject, parent: FloatArray, out: MutableList<CarLight>) {
            if (!node.optBoolean("Active", true)) return
            val world = GwmNodeTree.multiply(parent, localTransform(node))
            if (node.optString("Class").equals("Light", ignoreCase = true)) {
                parseLight(node)?.let { light ->
                    val length = kotlin.math.sqrt(world[8]*world[8] + world[9]*world[9] + world[10]*world[10])
                        .coerceAtLeast(1e-6f)
                    out.add(CarLight(light.name, light.type, floatArrayOf(world[12], world[13], world[14]),
                        floatArrayOf(world[8]/length, world[9]/length, world[10]/length), light.color,
                        light.intensity, light.range, light.innerDeg, light.outerDeg, light.specularScale,
                        light.atten, light.castShadow, light.useLinearColor))
                }
            }
            val children = node.optJSONArray("Child") ?: return
            for (i in 0 until children.length()) {
                children.optJSONObject(i)?.let { walk(it, world, out) }
            }
        }

        private fun parseLight(node: JSONObject): CarLight? {
            val typeName = node.optString("LightType", "Directional")
            val type = when {
                typeName.equals("Directional", ignoreCase = true) -> 0
                typeName.equals("Point", ignoreCase = true) -> 1
                else -> 2
            }
            val rot = node.optJSONArray("Rot") ?: return null
            val ex = rot.optDouble(0, 0.0)
            val ey = rot.optDouble(1, 0.0)
            
            val fx = (cos(ex) * sin(ey)).toFloat()
            val fy = (-sin(ex)).toFloat()
            val fz = (cos(ex) * cos(ey)).toFloat()
            val p = node.optJSONArray("Pos") ?: JSONArray()
            val c = node.optJSONArray("Color") ?: JSONArray()
            val a = node.optJSONArray("AttenuationFactors") ?: JSONArray()
            return CarLight(
                name = node.optString("Name", ""),
                type = type,
                pos = floatArrayOf(
                    p.optDouble(0, 0.0).toFloat(),
                    p.optDouble(1, 0.0).toFloat(),
                    p.optDouble(2, 0.0).toFloat()
                ),
                forward = floatArrayOf(fx, fy, fz),
                color = floatArrayOf(
                    c.optDouble(0, 1.0).toFloat(),
                    c.optDouble(1, 1.0).toFloat(),
                    c.optDouble(2, 1.0).toFloat()
                ),
                intensity = node.optDouble("Intensity", 1.0).toFloat(),
                range = node.optDouble("Range", 10.0).toFloat(),
                innerDeg = node.optDouble("SpotInnerAngle", 30.0).toFloat(),
                outerDeg = node.optDouble("SpotOutterAngle", 45.0).toFloat(),
                specularScale = node.optDouble("SpecularScale", 1.0).toFloat(),
                atten = floatArrayOf(
                    a.optDouble(0, 1.0).toFloat(),
                    a.optDouble(1, 0.09).toFloat(),
                    a.optDouble(2, 0.032).toFloat()
                ),
                castShadow = node.optBoolean("CastShadow", false),
                useLinearColor = node.optBoolean("UseLinearColor", false)
            )
        }
    }
}

class PackedLights(
    val count: Int,
    
    val block: FloatArray,
    
    val blockNoDir: FloatArray,
    val physical: FloatArray
)
