package com.haystudio.vehiclelab.data

import android.util.Log
import org.json.JSONObject

enum class DayPeriod(val key: String, val label: String) {
    MORNING("morn", "清晨"),
    NOON("noon", "白天"),
    NIGHT("night", "夜晚");

    companion object {
        fun of(key: String?): DayPeriod = entries.firstOrNull { it.key == key } ?: NOON
    }
}

class ScenePart(
    val name: String,
    
    val group: String,
    
    val textureKey: String?,
    val positions: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
    val texture: ByteArray?,
    
    val tint: FloatArray,
    
    val add: FloatArray,
    val opacity: Float,
    val uvScale: FloatArray,
    val additive: Boolean,
    val depthWrite: Boolean,
    
    val blended: Boolean = false,
    
    val emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
    
    val metallic: Float = 0f,
    
    val planarReflection: Boolean = false
) {
    val vertexCount: Int get() = positions.size / 3
}

class SceneAsset(
    val parts: List<ScenePart>,
    val groundY: Float = 0f,
    val shadow: List<ScenePart> = emptyList(),
    val officialMeshes: List<MeshData> = emptyList(),
    val textures: Map<String, ByteArray> = emptyMap(),
    val environment: ByteArray? = null
) {
    val triangleCount: Int get() = parts.sumOf { it.indices.size / 3 }
}

object SceneLoader {

    private const val ROOT = "MyCar/CarDesk/Dynasty/Common/Scenes/NS15"
    private const val MODELS = "$ROOT/Models"
    private const val MATERIAL = "$ROOT/Material"
    private const val TEXTURES = "$ROOT/Textures"

    fun load(reader: GpakReader, period: DayPeriod): SceneAsset? {
        val meshes = ArrayList<MeshData>()
        val textures = LinkedHashMap<String, ByteArray>()
        for (prefab in listOf("Sky", "Ground_${period.key}")) {
            val path = "MyCar/CarDesk/Dynasty/Common/Prefabs/NS15/$prefab.gpref"
            val raw = requireNotNull(reader.read(path)) { "缺少原厂场景 $path" }
            val instances = PrefabScene.resolve(JSONObject(raw.toString(Charsets.UTF_8))) { include ->
                JSONObject(requireNotNull(reader.read(include)).toString(Charsets.UTF_8))
            }
            for (instance in instances) {
                val materialArray = instance.getJSONArray("MeshMaterialArray")
                val paths = (0 until materialArray.length()).map { materialArray.getString(it) }
                val specs = MaterialLibrary.load(reader, paths).mapValues { (_, spec) ->
                    if (spec.path.contains("skybox")) {
                        val texture = "$TEXTURES/t_env_skybox_${period.key}_01_bc.ktx"
                        spec.copy(uniforms = spec.uniforms + mapOf("s_texColor" to texture,
                            "s_subTexColor" to texture, "u_subTexRatio" to 0f))
                    } else spec
                }
                val meshPath = instance.getString("MeshPath")
                val transform = transform(instance)
                val model = GwmParser.parse(requireNotNull(reader.read(meshPath)), null, null,
                    paths.map { requireNotNull(specs[it]) }, transform)
                meshes.addAll(model.meshes)
                for (spec in specs.values) {
                    for (texture in spec.textureUniforms.values) {
                        if (texture !in textures) reader.read(texture)?.let { textures[texture] = it }
                    }
                }
            }
        }
        val shadow = ArrayList<ScenePart>()
        addShadow(reader, shadow, period)
        return SceneAsset(emptyList(), 0f, shadow, meshes, textures,
            requireNotNull(reader.read("$TEXTURES/t_env_reflection_${period.key}_01_bc.genv")))
    }

    private fun transform(json: JSONObject): FloatArray {
        val p = vec3(json, "Pos") ?: floatArrayOf(0f, 0f, 0f)
        val r = vec3(json, "Rot") ?: floatArrayOf(0f, 0f, 0f)
        val s = vec3(json, "Scale") ?: floatArrayOf(1f, 1f, 1f)
        fun constant(v: Float) = AnimTrack(floatArrayOf(0f), floatArrayOf(v))
        return NodeClip("scene", "scene", 0f,
            Array(3) { constant(p[it]) }, Array(3) { constant(r[it]) }, Array(3) { constant(s[it]) }).localAt(0f)
    }

    private fun addShadow(
        reader: GpakReader,
        out: MutableList<ScenePart>,
        period: DayPeriod
    ) {
        val prefabPath = "MyCar/AppCommon/Dynasty/HC2/HC_17E/Prefabs/NS15/CarBeam.gpref"
        val prefab = JSONObject(requireNotNull(reader.read(prefabPath)).toString(Charsets.UTF_8))
        val instance = PrefabScene.resolve(prefab) { path ->
            JSONObject(requireNotNull(reader.read(path)).toString(Charsets.UTF_8))
        }.single { it.optString("MeshPath").contains("sm_selfcar_shadow_01") }
        val meshPath = instance.getString("MeshPath")
        val model = GwmParser.parse(requireNotNull(reader.read(meshPath)), null, null,
            rootTransform = transform(instance))
        for ((index, mesh) in model.meshes.withIndex()) {
            val materialPath = requireNotNull(ModelMaterialBinding.path(instance, mesh.part, index)) {
                "车底投影未绑定材质：${mesh.part}"
            }
            val material = MaterialLibrary.load(reader, listOf(materialPath)).getValue(materialPath)
            
            val texturePath = requireNotNull(material.tintTex)
                .replace(Regex("_[0-9]{3}\\.ktx$"), "_${shadowFrame(period)}.ktx")
            out.add(ScenePart(
                name = mesh.name,
                group = "shadow",
                textureKey = texturePath,
                positions = mesh.positions,
                uvs = mesh.uvs,
                indices = mesh.indices,
                texture = requireNotNull(reader.read(texturePath)),
                tint = material.base,
                add = BLACK,
                opacity = material.opacity,
                uvScale = material.uvScale,
                additive = material.additive,
                depthWrite = false,
                blended = material.forceTransparent
            ))
        }
        Log.i("CarDemo", "车底投影 ${model.meshes.size} 段")
    }

    private fun shadowFrame(period: DayPeriod): String {
        val t = if (period == DayPeriod.MORNING) 0f else 0.5f
        val index = Math.round(t * (SHADOW_FRAMES - 1))
        return String.format("%03d", index)
    }

    private fun addModel(
        reader: GpakReader,
        out: MutableList<ScenePart>,
        path: String,
        style: PartStyle
    ): GwmModel? {
        val name = path.substringAfterLast('/')
        var rot: FloatArray? = null
        var scale: FloatArray? = null
        var meshPath = "$path/$name.gwm"
        reader.read("$path.gmdl")?.let { raw ->
            try {
                val json = JSONObject(String(raw, Charsets.UTF_8))
                rot = vec3(json, "Rot")
                scale = vec3(json, "Scale")
                json.optString("MeshPath", "").takeIf { it.isNotBlank() }?.let { meshPath = it }
            } catch (_: Throwable) {
            }
        }
        val gwm = reader.read(meshPath)
            ?: reader.read("$path.gwm")
            ?: run {
                Log.w("CarDemo", "缺少背景模型 $meshPath")
                return null
            }
        val model = try {
            GwmParser.parse(gwm, rot, scale)
        } catch (t: Throwable) {
            Log.w("CarDemo", "背景模型解析失败 $name：${t.message}")
            return null
        }
        val texture = style.texturePath?.let { reader.read(it) }
        if (style.texturePath != null && texture == null) {
            Log.w("CarDemo", "缺少背景贴图 ${style.texturePath}")
        }
        Log.i(
            "CarDemo",
            "背景模型 $name 包围盒 ${model.boundsMin.joinToString(",")} .. " +
                "${model.boundsMax.joinToString(",")} · ${model.meshes.size} 段"
        )
        for (mesh in model.meshes) {
            out.add(
                ScenePart(
                    name = mesh.name,
                    group = name,
                    textureKey = style.texturePath,
                    positions = mesh.positions,
                    uvs = mesh.uvs,
                    indices = mesh.indices,
                    texture = texture,
                    tint = style.tint,
                    add = style.add,
                    opacity = style.opacity,
                    uvScale = style.uvScale,
                    additive = false,
                    depthWrite = style.depthWrite,
                    emissive = style.emissive,
                    metallic = style.metallic,
                    planarReflection = style.planarReflection
                )
            )
        }
        return model
    }

    private class PartStyle(
        val texturePath: String?,
        val tint: FloatArray,
        val add: FloatArray,
        val opacity: Float,
        val uvScale: FloatArray,
        val depthWrite: Boolean,
        
        val blended: Boolean = false,
        val emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
        val metallic: Float = 0f,
        val planarReflection: Boolean = false
    )

    private fun skyboxTint(period: DayPeriod): PartStyle = PartStyle(
        texturePath = "$TEXTURES/t_env_skybox_${period.key}_01_bc.ktx",
        tint = WHITE,
        add = BLACK,
        opacity = 1f,
        uvScale = UV_ONE,
        depthWrite = false
    )

    private fun mountainTint(period: DayPeriod): PartStyle = PartStyle(
        texturePath = "$TEXTURES/t_env_mountain_${period.key}_01_bc.ktx",
        tint = WHITE,
        add = BLACK,
        opacity = if (period == DayPeriod.NOON) 0.4f else 1f,
        uvScale = UV_ONE,
        depthWrite = false
    )

    private fun groundTint(period: DayPeriod): PartStyle {
        val base = when (period) {
            DayPeriod.MORNING, DayPeriod.NOON -> floatArrayOf(1f, 0.72157f, 0.72157f)
            DayPeriod.NIGHT -> floatArrayOf(0.56863f, 0.56863f, 0.56863f)
        }
        val emisColor = when (period) {
            DayPeriod.MORNING, DayPeriod.NOON -> floatArrayOf(1f, 0.89412f, 0.89412f)
            DayPeriod.NIGHT -> floatArrayOf(0.50588f, 0.43922f, 0.47843f)
        }
        val emisIntensity = if (period == DayPeriod.NIGHT) 0.5f else 0.59f
        return PartStyle(
            texturePath = "$TEXTURES/t_env_ground_01_bc.ktx",
            tint = base,
            add = BLACK,
            opacity = 1f,
            uvScale = floatArrayOf(GROUND_UV, GROUND_UV),
            depthWrite = true,
            emissive = floatArrayOf(
                emisColor[0] * emisIntensity,
                emisColor[1] * emisIntensity,
                emisColor[2] * emisIntensity
            ),
            metallic = 0.8f,
            planarReflection = period == DayPeriod.NOON
        )
    }

    private const val GROUND_UV = 10.8f

    private val WHITE = floatArrayOf(1f, 1f, 1f)
    private val BLACK = floatArrayOf(0f, 0f, 0f)
    private val UV_ONE = floatArrayOf(1f, 1f)

    private const val SHADOW_FRAMES = 39

    private fun vec3(json: JSONObject, key: String): FloatArray? {
        val arr = json.optJSONArray(key) ?: return null
        if (arr.length() < 3) return null
        return floatArrayOf(
            arr.getDouble(0).toFloat(),
            arr.getDouble(1).toFloat(),
            arr.getDouble(2).toFloat()
        )
    }
}
