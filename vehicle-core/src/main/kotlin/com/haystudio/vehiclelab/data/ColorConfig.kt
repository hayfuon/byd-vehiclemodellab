package com.haystudio.vehiclelab.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class CarTint(
    val base: FloatArray,
    val coat: FloatArray,
    val envSpecInt: Float
)

class CarColor(
    val label: String,
    val tints: List<CarTint>
) {
    val base: FloatArray get() = tints.firstOrNull()?.base ?: FALLBACK
    val coat: FloatArray get() = tints.firstOrNull()?.coat ?: FALLBACK_COAT

    private companion object {
        val FALLBACK = floatArrayOf(0.6f, 0.62f, 0.66f)
        val FALLBACK_COAT = floatArrayOf(1.4f, 0.06f)
    }
}

class EnvMap(
    val band: String,
    val name: String,
    val intensity: Float,
    val yaw: Float,
    val roughness: Float
) {
    
    val uniforms: Map<String, Any> = mapOf(
        "_EnvSpecInt" to intensity,
        "_EnvSpecYaw" to yaw,
        "_Roughness" to roughness,

        "_EnvDiffInt" to 0f,
        "_EnvDownCol" to FloatArray(3),
        "_EnvSideCol" to FloatArray(3)
    )
}

class CarPalette(
    val materials: List<String>,
    val colors: List<CarColor>,
    val defaultIndex: Int,
    val envMaps: List<EnvMap>,
    
    val colorIndexes: List<Int> = emptyList()
) {
    
    fun tintOf(color: CarColor, materialPath: String, trimColor: CarColor? = null): CarTint? {
        
        val base = materialPath.substringAfterLast('/')
        val i = materials.indexOfFirst {
            it.equals(materialPath, ignoreCase = true) ||
                it.substringAfterLast('/').equals(base, ignoreCase = true)
        }
        if (i < 0) return null
        return (if (regionOf(materialPath)==PaintRegion.TRIM) trimColor ?: color else color).tints.getOrNull(i)
    }

    fun materialIndex(path:String)=materials.indexOfFirst {it.equals(path,true)||it.substringAfterLast('/').equals(path.substringAfterLast('/'),true)}
    fun regionOf(path:String):PaintRegion? {
        val index=materialIndex(path)
        if(index<0)return null
        val name=materials[index].substringAfterLast('/')
        return when {
            name.contains("carpaint_up",true) || name.contains("paint_upper",true) -> PaintRegion.UPPER
            index==0 || name.contains("carpaint",true) -> PaintRegion.LOWER
            else -> PaintRegion.TRIM
        }
    }

    fun envFor(hourOfDay: Int): EnvMap? {
        if (envMaps.isEmpty()) return null
        val band = when {
            hourOfDay in 6..9 -> "Morning"
            hourOfDay in 10..17 -> "Day"
            else -> "Night"
        }
        return envMaps.firstOrNull { it.band.equals(band, ignoreCase = true) }
            ?: envMaps.getOrNull(1) ?: envMaps.first()
    }
}

object ColorConfig {

    private val CHINESE = mapOf(
        "Ball_AnYeHong" to "暗夜红",
        "Ball_WeiGuangFen" to "微光粉",
        "Ball_YaoShiHei" to "曜石黑",
        "Ball_JunYaoHui" to "钧曜灰",
        "Ball_DongRiQing" to "冬日青",
        "Ball_WuSongJin" to "乌松金",
        "Ball_XueYuBai" to "雪域白",
        "Ball_FanXingHui" to "繁星灰"
    )

    var lastDefaultIndex = 0
        private set

    internal fun preferredPath(paths: List<String>): String? =
        paths.firstOrNull { it.contains("/Common/ColorMaterial/") } ?: paths.sorted().firstOrNull()

    internal fun candidatePaths(paths: List<String>, brand: String?, family: String?): List<String> =
        paths.filter {
            it.endsWith("/ColorMaterial/ColorMaterial.json") &&
                (brand == null || family == null || it.contains("/CarDesk/$brand/$family/") ||
                    it.contains("/Common/$brand/$family/"))
        }

    fun load(
        reader: GpakReader?,
        extraReaders: List<GpakReader> = emptyList(),
        modelPath: String = ""
    ): CarPalette? {
        if (reader == null) return null
        val brand = modelPath.split('/').getOrNull(2)
        val family = modelPath.split('/').getOrNull(3)
        val all = reader.names()
        val candidates = candidatePaths(all, brand, family)
        if (candidates.isEmpty()) {
            val anyColor = all.filter { it.contains("ColorMaterial", ignoreCase = true) }
            android.util.Log.w(
                "CarDemo",
                "色板：$brand/$family 无 ColorMaterial 候选（共 ${all.size} 项，" +
                    "含 ColorMaterial 的 ${anyColor.size} 项：${anyColor.take(3)}）"
            )
            return null
        }

        val parsed = ArrayList<Pair<String, CarPalette>>(candidates.size)
        for (path in candidates.sorted()) {
            parseMaterial(reader, path)?.let { parsed.add(path to it) }
        }
        android.util.Log.i(
            "CarDemo",
            "色板候选 ${candidates.size} 个，可解析 ${parsed.size} 个：" +
                parsed.joinToString { "${it.first.substringAfterLast('/')}#${it.second.colors.size}" }
        )
        if (parsed.isEmpty()) return null
        val chosen = parsed.first { it.first == preferredPath(parsed.map { row -> row.first }) }
        val palette = chosen.second
        android.util.Log.i(
            "CarDemo",
            "色板明细 ${chosen.first}：" + palette.colors.mapIndexed { i, c ->
                "#${palette.colorIndexes.getOrElse(i) { -1 }} ${c.label} base=${c.base.joinToString(",")}"
            }.joinToString(" | ") + " 材质=${palette.materials.joinToString { it.substringAfterLast('/') }}"
        )

        val names = ballNames(extraReaders + reader, brand, family)
        val colors = if (names.isEmpty()) palette.colors else palette.colors.mapIndexed { i, c ->
            val key = palette.colorIndexes.getOrElse(i) { i + 1 }
            val label = names[key]?.let { labelOf(it) }
            if (label.isNullOrBlank()) c else CarColor(label, c.tints)
        }
        lastDefaultIndex = palette.defaultIndex
        return CarPalette(
            palette.materials, colors, palette.defaultIndex, palette.envMaps, palette.colorIndexes
        )
    }

    private fun parseMaterial(reader: GpakReader, path: String): CarPalette? {
        return try {
            val raw = reader.read(path) ?: return null
            val root = JSONObject(String(raw, Charsets.UTF_8))
            val arr = root.optJSONArray("mColorMaterialInfos") ?: return null
            val materials = strings(root.optJSONArray("mCarMaterialNames"))
            val materialCount = materials.size.coerceAtLeast(1)

            val colors = ArrayList<CarColor>(arr.length())
            val indexes = ArrayList<Int>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val idx = o.optInt("mColorIndex", -1)
                if (idx < 0) continue
                val subs = o.optJSONArray("mColorMaterialSubInfos") ?: continue
                val tints = ArrayList<CarTint>(materialCount)
                for (m in 0 until materialCount) {
                    val sub = subs.optJSONObject(m) ?: subs.optJSONObject(0)
                    val base = vec3(sub?.optJSONArray("mBaseCol"), FALLBACK_BASE) ?: continue
                    val coat = vec3(sub?.optJSONArray("mCarCLParams"), FALLBACK_COAT)
                        ?: FALLBACK_COAT.copyOf()
                    val env = sub?.optDouble("mEnvSpecInt", 0.8)?.toFloat() ?: 0.8f
                    tints.add(CarTint(base, coat, env))
                }
                if (tints.size != materialCount) continue
                colors.add(CarColor(inferName(tints[0].base), tints))
                indexes.add(idx)
            }
            if (colors.isEmpty()) return null

            val defIndex = root.optInt("mDefaultBaseColorValue", -1)
            val defaultIndex = indexes.indexOf(defIndex).takeIf { it >= 0 }
                ?: (defIndex - 1).takeIf { it in colors.indices } ?: 0

            val envMaps = ArrayList<EnvMap>(3)
            root.optJSONArray("mEnvSpecMapInfos")?.let { e ->
                for (i in 0 until e.length()) {
                    val o = e.optJSONObject(i) ?: continue
                    val name = o.optString("mEnvSpecMapTexPath", "")
                    if (name.isBlank()) continue
                    envMaps.add(
                        EnvMap(
                            band = o.optString("nEnvSpecMapIndex", ""),
                            name = name,
                            intensity = o.optDouble("mEnvSpecInt", 0.8).toFloat(),
                            yaw = o.optDouble("EnvSpecYaw", 13.0).toFloat(),
                            roughness = o.optDouble("Roughness", 0.77).toFloat()
                        )
                    )
                }
            }
            CarPalette(materials, colors, defaultIndex, envMaps, indexes)
        } catch (_: Throwable) {
            null
        }
    }

    private fun ballNames(
        readers: List<GpakReader?>,
        brand: String?,
        family: String?
    ): Map<Int, String> {
        if (brand == null || family == null) return emptyMap()
        for (reader in readers) {
            if (reader == null) continue
            val path = reader.names().firstOrNull {
                it.endsWith("/ColorPaint/CarColor.json") &&
                    it.contains("/CarDesk/$brand/$family/")
            } ?: continue
            try {
                val raw = reader.read(path) ?: continue
                val arr = JSONObject(String(raw, Charsets.UTF_8)).optJSONArray("mColorBallInfos")
                    ?: continue
                val out = HashMap<Int, String>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("mColorButtonName", "")
                    if (name.isNotEmpty()) out[o.optInt("mColorIndex", i + 1)] = name
                }
                if (out.isNotEmpty()) return out
            } catch (_: Throwable) {
            }
        }
        return emptyMap()
    }

    private fun labelOf(name: String): String {
        CHINESE[name]?.let { return it }
        return name.removePrefix("Ball_").ifBlank { "原厂漆" }
    }

    private fun inferName(c: FloatArray): String {
        val lum = c[0] * 0.2126f + c[1] * 0.7152f + c[2] * 0.0722f
        return when {
            lum > 1.6f -> "雪域白"
            lum > 0.55f -> "极光银"
            lum > 0.06f -> "钧曜灰"
            else -> "曜石黑"
        }
    }

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>(a.length())
        for (i in 0 until a.length()) {
            val s = a.optString(i, "")
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }

    private fun vec3(a: JSONArray?, fallback: FloatArray): FloatArray? {
        if (a == null || a.length() < 3) return null
        return floatArrayOf(
            a.optDouble(0, fallback[0].toDouble()).toFloat(),
            a.optDouble(1, fallback[1].toDouble()).toFloat(),
            a.optDouble(2, fallback[2].toDouble()).toFloat()
        )
    }

    private val FALLBACK_BASE = floatArrayOf(0.6f, 0.62f, 0.66f)
    private val FALLBACK_COAT = floatArrayOf(1.4f, 0.06f)
}

fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
