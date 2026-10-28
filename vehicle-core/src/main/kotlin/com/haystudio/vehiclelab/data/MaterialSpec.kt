package com.haystudio.vehiclelab.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.pow

enum class FaceCulling { NONE, BACK, FRONT }

data class MaterialSpec(
    val path: String,
    val base: FloatArray,
    val metallic: Float,
    val roughness: Float,
    val opacity: Float,
    val emissive: FloatArray,
    val emissiveInt: Float,
    val coat: Float,
    val coatRough: Float,
    val dirSpecInt: Float,
    val envSpecInt: Float,
    val doubleSided: Boolean,
    val renderOrder: Int,
    val kind: MaterialKind,
    val baseTex: String? = null,
    val emissiveTex: String? = null,
    val tintTex: String? = null,
    val uvScale: FloatArray = floatArrayOf(1f, 1f),
    val uvOffset: FloatArray = floatArrayOf(0f, 0f),
    val additive: Boolean = false,
    val forceTransparent: Boolean = false,
    val alphaCutoff: Float = 0f,
    
    val alphaMaskTex: String? = null,
    val directLighting: Boolean = true,
    val useLinearColor: Boolean = false,
    val alphaUvScale: FloatArray = floatArrayOf(1f, 1f),
    val normalTex: String? = null,
    val normalUvScale: FloatArray = floatArrayOf(1f, 1f),
    val normalUvOffset: FloatArray = floatArrayOf(0f, 0f),
    val bumpScale: Float = 1f,
    
    val unlit: Boolean = false,
    
    val envTex: String? = null,
    val envYaw: Float = 13f,
    
    val ormTex: String? = null,
    
    val ormScale: FloatArray = floatArrayOf(1f, 1f),
    
    val envTop: FloatArray? = null,
    val envSide: FloatArray? = null,
    val envDown: FloatArray? = null,
    
    val dirLight: Boolean = true,
    
    val offBase: FloatArray? = null,
    val offMetallic: Float = 0f,
    val offRoughness: Float = 0.55f,
    
    val carCl: Boolean = true,
    
    val envDiffInt: Float = 0.85f,
    
    val lampOffMat: Boolean = false,
    
    val vsPath: String? = null,
    
    val fsPath: String? = null,
    
    val vsSource: String? = null,
    val fsSource: String? = null,
    
    val vsOptions: Set<String> = emptySet(),
    val fsOptions: Set<String> = emptySet(),
    
    val uniforms: Map<String, Any> = emptyMap(),

    val genericShader: Boolean = false,
    
    val brdfType: String = "",
    
    val engineShader: Boolean = false,
    
    val materialBlock: FloatArray? = null,
    
    val specularColor: FloatArray = floatArrayOf(0f, 0f, 0f),
    val glossiness: Float = 0f,
    val ambientColor: FloatArray = floatArrayOf(0f, 0f, 0f),
    val reflectivity: Float = 0.5f,
    val faceCulling: FaceCulling = if (doubleSided) FaceCulling.NONE else FaceCulling.BACK
) {
    
    val official: Boolean get() = vsSource != null && fsSource != null

    val textureUniforms: Map<String, String> = buildMap {
        for ((name, value) in uniforms) {
            if (value is String && value.endsWith(".ktx", ignoreCase = true)) put(name, value)
        }
    }

    val paint: Boolean get() = kind == MaterialKind.PAINT
    val light: Boolean get() = kind == MaterialKind.LIGHT_WHITE ||
        kind == MaterialKind.LIGHT_RED || kind == MaterialKind.LIGHT_OFF || lampOffMat ||
        path.contains("Light", ignoreCase = true)

    val restingLamp: Boolean get() = lampOffMat || kind == MaterialKind.LIGHT_OFF

    val blended: Boolean
        get() = forceTransparent || opacity < 0.999f || alphaMaskTex != null

    val albedoTexture: String? get() = tintTex ?: baseTex

    val effectiveEmissiveTexture: String?
        get() = emissiveTex ?: if (kind == MaterialKind.LIGHT_WHITE ||
            kind == MaterialKind.LIGHT_RED || kind == MaterialKind.LIGHT_OFF
        ) albedoTexture else null
}

object MaterialLibrary {

    private const val GENERIC_VS = "custom/MergedCarpaint/vs_merged_car.es"
    private const val GENERIC_FS = "custom/MergedCarpaint/fs_merged_car.es"

    private const val ENGINE_VS = "native/gles/vs_common.es"
    private const val ENGINE_FS = "native/gles/fs_pbr.es"

    private const val PHONG_VS = "native/gles/vs_common.es"
    private const val PHONG_FS = "native/gles/fs_phong.es"

    const val PBR_BLOCK_FLOATS = 48

    private val DEFAULT_TOP = floatArrayOf(0.0f, 1.2f, 4.0f)
    private val DEFAULT_SIDE = floatArrayOf(0.16f, 0.16f, 0.12f)
    private val DEFAULT_DOWN = floatArrayOf(0.53f, 0.8f, 1.4f)

    fun load(reader: GpakReader?, paths: List<String>): Map<String, MaterialSpec> {
        val out = LinkedHashMap<String, MaterialSpec>(paths.size * 2)
        val names = try {
            reader?.names() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        for (p in paths) {
            if (p.isBlank() || out.containsKey(p)) continue
            val spec = try {
                reader?.read(p)?.let { bytes -> parse(p, JSONObject(String(bytes, Charsets.UTF_8))) }
            } catch (_: Throwable) {
                null
            }
            val base = spec ?: fallback(p)
            out[p] = attachShaders(reader, withOffState(reader, base, names))
        }
        val official = out.values.filter { it.official }
        val generic = official.count { it.genericShader }
        android.util.Log.i(
            "CarDemo",
            "材质 ${out.size} 份，官方着色器 ${official.size} 份" +
                "（自带 ${official.size - generic} / 通用 PBR $generic）：" +
                official.joinToString { fileName(it.path) }
        )

        for (s in out.values) {
            android.util.Log.i(
                "CarDemo",
                "审计 ${fileName(s.path)} base=${s.base.joinToString(",")}" +
                    " m=${s.metallic} r=${s.roughness} op=${s.opacity}" +
                    " emis=${s.emissive.joinToString(",")} emisInt=${s.emissiveInt}" +
                    " envSpec=${s.envSpecInt} envDiff=${s.envDiffInt}" +
                    " dir=${s.dirLight} cl=${s.carCl} generic=${s.genericShader}" +
                    " offBase=${s.offBase?.joinToString(",")}"
            )
        }
        return out
    }

    private fun attachShaders(reader: GpakReader?, spec: MaterialSpec): MaterialSpec {
        if (reader == null) return spec
        val vsPath = spec.vsPath
        val fsPath = spec.fsPath
        if (vsPath != null && fsPath != null) {
            val vs = ShaderSource.load(reader, vsPath)
            val fs = ShaderSource.load(reader, fsPath)
            if (vs != null && fs != null) return spec.copy(vsSource = vs, fsSource = fs)
            android.util.Log.w(
                "CarDemo",
                "官方着色器源码缺失 ${spec.path}：$vsPath=${vs != null} $fsPath=${fs != null}"
            )
            return spec
        }
        if (spec.brdfType == "pbrMetallicRoughness") return withEnginePbr(reader, spec)
        if (spec.brdfType == "blinnPhong") return withEnginePhong(reader, spec)
        return withGenericShader(reader, spec)
    }

    private fun withEnginePbr(reader: GpakReader, spec: MaterialSpec): MaterialSpec {
        val vs = ShaderSource.assembleEngine("vs_common") ?: return withGenericShader(reader, spec)
        val fs = ShaderSource.assembleEngine("fs_pbr")
            ?: return withGenericShader(reader, spec)
        val options = LinkedHashSet<String>(8)
        options.add("GW_PLATFORM_ANDROID")
        options.add("SPHERICAL_HARMONICS")

        options.add("RADIANCE_MAP")
        val u = LinkedHashMap<String, Any>(spec.uniforms)
        spec.albedoTexture?.let {
            options.add("BASE_MAP")
            u["s_texColor"] = it
        }
        spec.emissiveTex?.let {
            options.add("EMISSIVE_MAP")
            u["s_emissive"] = it
        }
        spec.alphaMaskTex?.let {
            options.add("ALPHA_MAP")
            options.add("UVTRANSFORM_ALPHA")
            u["s_alpha"] = it
        }
        spec.normalTex?.let {
            options.addAll(listOf("BUMP_MAP", "NEEDS_TBN", "UVTRANSFORM_BUMP"))
            u["s_texBump"] = it
        }
        if (spec.alphaCutoff > 0f) options.add("ALPHA_TEST")
        return spec.copy(
            vsSource = vs,
            fsSource = fs,
            vsPath = ENGINE_VS,
            fsPath = ENGINE_FS,
            vsOptions = if (spec.normalTex != null) setOf("BUMP_MAP") else emptySet(),
            fsOptions = options,
            uniforms = u,
            brdfType = spec.brdfType,
            engineShader = true,
            materialBlock = pbrBlock(spec)
        )
    }

    private fun engineColor(spec: MaterialSpec, value: Float): Float =
        if (spec.useLinearColor) value else value.pow(2.2f)

    internal fun pbrBlock(spec: MaterialSpec): FloatArray {
        val b = FloatArray(PBR_BLOCK_FLOATS)
        b[0] = engineColor(spec, spec.base[0])
        b[1] = engineColor(spec, spec.base[1])
        b[2] = engineColor(spec, spec.base[2])
        b[3] = spec.opacity
        b[4] = engineColor(spec, spec.emissive[0])
        b[5] = engineColor(spec, spec.emissive[1])
        b[6] = engineColor(spec, spec.emissive[2])
        b[7] = spec.emissiveInt
        b[8] = spec.uvScale[0]
        b[9] = spec.uvScale[1]
        b[10] = spec.bumpScale
        b[11] = spec.bumpScale
        b[12] = spec.metallic
        b[13] = spec.roughness
        
        b[14] = 8f
        b[15] = 1f
        
        b[26] = spec.alphaCutoff
        return b
    }

    private fun withEnginePhong(reader: GpakReader, spec: MaterialSpec): MaterialSpec {
        val vs = ShaderSource.assembleEngine("vs_common")
            ?: return withGenericShader(reader, spec)
        val fs = ShaderSource.assembleEngine("fs_phong")
            ?: return withGenericShader(reader, spec)
        val options = LinkedHashSet<String>(8)
        options.add("GW_PLATFORM_ANDROID")
        
        options.add("SPHERICAL_HARMONICS")
        options.add("REFLECTION_MAP")
        options.add("REFLECTION_MAP_LOD")
        val u = LinkedHashMap<String, Any>(spec.uniforms)
        spec.albedoTexture?.let {
            options.add("DIFFUSE_MAP")
            u["s_texColor"] = it
        }
        spec.emissiveTex?.let {
            options.add("EMISSIVE_MAP")
            u["s_emissive"] = it
        }
        spec.alphaMaskTex?.let {
            options.add("ALPHA_MAP")
            options.add("UVTRANSFORM_ALPHA")
            u["s_alpha"] = it
        }
        spec.normalTex?.let {
            options.addAll(listOf("BUMP_MAP", "NEEDS_TBN", "UVTRANSFORM_BUMP"))
            u["s_texBump"] = it
        }
        if (spec.alphaCutoff > 0f) options.add("ALPHA_TEST")
        return spec.copy(
            vsSource = vs,
            fsSource = fs,
            vsPath = PHONG_VS,
            fsPath = PHONG_FS,
            vsOptions = if (spec.normalTex != null) setOf("BUMP_MAP") else emptySet(),
            fsOptions = options,
            uniforms = u,
            brdfType = spec.brdfType,
            engineShader = true,
            materialBlock = phongBlock(spec)
        )
    }

    internal fun phongBlock(spec: MaterialSpec): FloatArray {
        val b = FloatArray(24)
        b[0] = engineColor(spec, spec.base[0])
        b[1] = engineColor(spec, spec.base[1])
        b[2] = engineColor(spec, spec.base[2])
        b[3] = spec.opacity
        b[4] = engineColor(spec, spec.specularColor[0])
        b[5] = engineColor(spec, spec.specularColor[1])
        b[6] = engineColor(spec, spec.specularColor[2])
        b[7] = spec.glossiness
        b[8] = spec.uvScale[0]
        b[9] = spec.uvScale[1]
        b[10] = spec.bumpScale
        b[11] = spec.bumpScale
        b[12] = engineColor(spec, spec.ambientColor[0])
        b[13] = engineColor(spec, spec.ambientColor[1])
        b[14] = engineColor(spec, spec.ambientColor[2])
        b[15] = spec.reflectivity
        b[16] = engineColor(spec, spec.emissive[0])
        b[17] = engineColor(spec, spec.emissive[1])
        b[18] = engineColor(spec, spec.emissive[2])
        b[19] = spec.emissiveInt
        
        b[20] = 8f
        b[21] = spec.alphaCutoff
        return b
    }

    private fun withGenericShader(reader: GpakReader, spec: MaterialSpec): MaterialSpec {
        if (spec.unlit) return spec
        val vs = ShaderSource.load(reader, GENERIC_VS) ?: return spec
        val fs = ShaderSource.load(reader, GENERIC_FS) ?: return spec
        val u = LinkedHashMap<String, Any>(spec.uniforms)
        fun put(name: String, value: Any) {
            if (!u.containsKey(name)) u[name] = value
        }
        put("_BaseCol", spec.base)
        put("_ChameleonColor", floatArrayOf(0f, 0f, 0f))

        put("_Metallic", 0f)
        put("_Roughness", spec.roughness)
        put("_DirSpecInt", spec.dirSpecInt)
        put("_EmissiveCol", floatArrayOf(spec.emissive[0], spec.emissive[1], spec.emissive[2], 1f))

        val emissiveTex = spec.effectiveEmissiveTexture
            ?.takeUnless { sameImage(it, spec.albedoTexture) }
        put("_EmissiveInt", if (emissiveTex == null) 0f else spec.emissiveInt)
        
        put("_NorInt", 0f)
        put("_Opacity", spec.opacity)
        put("_EnvDiffInt", spec.envDiffInt)
        put("_EnvTopCol", spec.envTop ?: DEFAULT_TOP)
        put("_EnvSideCol", spec.envSide ?: DEFAULT_SIDE)
        put("_EnvDownCol", spec.envDown ?: DEFAULT_DOWN)
        put("_EnvSpecInt", spec.envSpecInt)
        put("_EnvSpecYaw", spec.envYaw)
        put(
            "_BaseMap_ST",
            floatArrayOf(spec.uvScale[0], spec.uvScale[1], spec.uvOffset[0], spec.uvOffset[1])
        )
        put("_RM_ST", floatArrayOf(spec.ormScale[0], spec.ormScale[1], 0f, 0f))
        put("_NormalMap_ST", floatArrayOf(1f, 1f, 0f, 0f))
        put("_RoughnessCLTex_ST", floatArrayOf(1f, 1f, 0f, 0f))
        spec.albedoTexture?.let { put("_BaseMap", it) }
        spec.ormTex?.let { put("_ORM", it) }
        emissiveTex?.let { put("_EmissiveTex", it) }

        val fsOptions = LinkedHashSet<String>(3)
        if (u.containsKey("_BaseMap")) fsOptions.add("USE_BASE_MAP")
        if (u.containsKey("_ORM")) fsOptions.add("USE_ORM")
        if (u.containsKey("_EmissiveTex")) fsOptions.add("USE_EMISSIVE_TEX")
        return spec.copy(
            vsSource = vs,
            fsSource = fs,
            vsOptions = emptySet(),
            fsOptions = fsOptions,
            uniforms = u,
            genericShader = true
        )
    }

    private fun withOffState(
        reader: GpakReader?,
        spec: MaterialSpec,
        names: List<String>
    ): MaterialSpec {
        if (spec.offBase != null || !spec.light || spec.lampOffMat) return spec
        val dir = spec.path.substringBeforeLast('/', "")
        val base = spec.path.substringAfterLast('/')
        val stem = base.substringBeforeLast('.')
        val offName = names.firstOrNull {
            it.substringBeforeLast('/') == dir &&
                it.substringAfterLast('/').equals("${stem}_off.mat", ignoreCase = true)
        } ?: return spec
        val raw = try {
            reader?.read(offName)
        } catch (_: Throwable) {
            null
        } ?: return spec
        val off = try {
            parse(offName, JSONObject(String(raw, Charsets.UTF_8)))
        } catch (_: Throwable) {
            return spec
        }
        return spec.copy(
            offBase = off.base,
            offMetallic = off.metallic,
            offRoughness = off.roughness
        )
    }

    internal fun parse(path: String, json: JSONObject): MaterialSpec {
        val kind = MaterialKind.of(fileName(path), fileName(path))
        val uniforms = json.optJSONObject("Material")?.optJSONArray("Uniforms")
        val opacity = json.optDouble("opacity", 1.0).toFloat()
        val doubleSided = json.optBoolean("doubleSided", false)
        val faceCulling = when (json.optJSONObject("Material")?.optJSONObject("RenderStateSettings")
            ?.optString("FaceCullingMode")) {
            "None" -> FaceCulling.NONE
            "Front" -> FaceCulling.FRONT
            "Back" -> FaceCulling.BACK
            else -> if (doubleSided) FaceCulling.NONE else FaceCulling.BACK
        }
        val order = json.optInt("RenderOrder", 0)
        val additive = json.optBoolean("additiveBlending", false)
        val forceTransparent = json.optBoolean("forceTransparent", false)

        val alphaCutoff = if (json.optBoolean("forceAlphaTest", false)) {
            json.optDouble("alphaCutoff", 0.0).toFloat()
        } else {
            0f
        }
        
        val vsPath = json.optString("Vertex", "").ifBlank { null }
        val fsPath = json.optString("Fragment", "").ifBlank { null }
        val vsOptions = options(json, "VertexShaderOptions")
        val fsOptions = options(json, "FragmentShaderOptions")
        val rawUniforms = uniforms?.let { uniformValues(it) } ?: emptyMap()

        if (uniforms != null && uniforms.length() > 0) {
            val u = index(uniforms)

            val vsOn = dropTextureMacros(vsOptions, u)
            val fsOn = dropTextureMacros(fsOptions, u)
            val st = u["_BaseMap_ST"]?.optJSONArray("Value")
            val scale = floatArrayOf(
                st?.optDouble(0, 1.0)?.toFloat() ?: 1f,
                st?.optDouble(1, 1.0)?.toFloat() ?: 1f
            )
            
            val carCl = shaderOption(json, "CAR_CL") ?: true
            return MaterialSpec(
                path = path,
                base = value3(u["_BaseCol"], kind.base),
                metallic = float(u["_Metallic"], kind.metallic),
                roughness = float(u["_Roughness"], kind.roughness),
                opacity = float(u["_Opacity"], opacity),
                emissive = value3(u["_EmissiveCol"], ZERO3),
                emissiveInt = float(u["_EmissiveInt"], 1f),
                coat = if (carCl) vec2(u["_CarCLParams"], 0) else 0f,
                coatRough = vec2(u["_CarCLParams"], 1, 0.06f),
                dirSpecInt = float(u["_DirSpecInt"], 0.8f),
                envSpecInt = float(u["_EnvSpecInt"], 0.8f),
                doubleSided = doubleSided,
                faceCulling = faceCulling,
                renderOrder = order,
                kind = kind,
                baseTex = u.string("_BaseMap"),
                emissiveTex = u.string("_EmissiveTex"),
                tintTex = null,
                uvScale = scale,
                uvOffset = floatArrayOf(
                    st?.optDouble(2, 0.0)?.toFloat() ?: 0f,
                    st?.optDouble(3, 0.0)?.toFloat() ?: 0f
                ),
                additive = additive,
                forceTransparent = forceTransparent,
                alphaCutoff = alphaCutoff,
                envTex = u.string("_EnvSpecMap"),
                envYaw = float(u["_EnvSpecYaw"], 13f),
                ormTex = if (shaderOption(json, "USE_ORM") == true) u.string("_ORM") else null,
                ormScale = u["_RM_ST"]?.optJSONArray("Value")?.let {
                    floatArrayOf(
                        it.optDouble(0, 1.0).toFloat(),
                        it.optDouble(1, 1.0).toFloat()
                    )
                } ?: floatArrayOf(1f, 1f),
                envTop = value3(u["_EnvTopCol"], DEFAULT_TOP),
                envSide = value3(u["_EnvSideCol"], DEFAULT_SIDE),
                envDown = value3(u["_EnvDownCol"], DEFAULT_DOWN),
                carCl = carCl,
                envDiffInt = float(u["_EnvDiffInt"], 0.85f),
                lampOffMat = isOffMaterial(path),
                brdfType = json.optString("brdfType", ""),
                vsPath = vsPath,
                fsPath = fsPath,
                vsOptions = vsOn,
                fsOptions = fsOn,
                uniforms = rawUniforms
            )
        }

        val brdf = json.optJSONObject("brdf") ?: return fallback(path)
        val type = json.optString("brdfType", "")
        val unlit = type == "unlit"

        val base = vec3(
            brdf.optJSONArray(
                when (type) {
                    "unlit" -> "tintColor"
                    "blinnPhong" -> "diffuseColor"
                    else -> "baseColor"
                }
            ),
            kind.base
        )
        val gloss = brdf.optDouble("glossiness", 0.0)
        val metallic = brdf.optDouble("metallic", kind.metallic.toDouble())
        val roughness = if (type == "blinnPhong" && gloss > 0.0) {
            
            (1.0 - gloss / 300.0).coerceIn(0.04, 1.0)
        } else {
            brdf.optDouble("roughness", kind.roughness.toDouble())
        }

        val emissiveColor = vec3(json.optJSONArray("emissiveColor"), ZERO3)
        val emissiveInt = json.optDouble("emissiveIntensity", 0.0).toFloat()
        val hasEmissiveIntensity = json.has("emissiveIntensity") && !json.isNull("emissiveIntensity")
        val emissive = if (hasEmissiveIntensity) {
            emissiveColor
        } else when (kind) {
            MaterialKind.LIGHT_WHITE -> floatArrayOf(1f, 0.97f, 0.92f)
            MaterialKind.LIGHT_RED -> floatArrayOf(1f, 0.09f, 0.07f)
            else -> ZERO3
        }
        val emissiveIntensity = when {
            hasEmissiveIntensity -> emissiveInt
            kind == MaterialKind.LIGHT_WHITE -> 2.2f
            kind == MaterialKind.LIGHT_RED -> 1.6f
            else -> 0f
        }
        val normal = json.optJSONObject("normalTexture")
        val brdfBase = brdf.optJSONObject("baseTexture")
        val diffuse = brdf.optJSONObject("diffuseTexture")
        val tint = brdf.optJSONObject("tintTexture")
        
        val alphaTex = textureSource(json.opt("alphaTexture"))
        val baseTex = brdfBase?.optString("source").orEmpty().ifBlank { null }
            ?: diffuse?.optString("source").orEmpty().ifBlank { null }
        val uvs = brdfBase ?: diffuse ?: tint
        
        val screen = unlit && baseTex == null && diffuse == null && tint == null
        val st = uvs?.optJSONArray("scale")
        val tr = uvs?.optJSONArray("translation")
        val tilling = brdf.optJSONArray("tilling")
        return MaterialSpec(
            path = path,
            base = when {
                screen -> floatArrayOf(0.022f, 0.027f, 0.034f)
                else -> base
            },
            metallic = metallic.toFloat(),
            roughness = roughness.toFloat(),
            opacity = opacity,
            emissive = emissive,
            emissiveInt = emissiveIntensity,
            coat = 0f,
            coatRough = 0.06f,
            dirSpecInt = if (brdf.optBoolean("enableDirectionalLight", true)) 0.8f else 0f,
            envSpecInt = if (unlit) 0f else 0.8f,
            doubleSided = doubleSided,
            faceCulling = faceCulling,
            renderOrder = order,
            kind = kind,
            baseTex = baseTex,

            emissiveTex = textureSource(json.opt("emissiveTexture")),
            tintTex = tint?.optString("source").orEmpty().ifBlank { null },
            uvScale = floatArrayOf(
                (st?.optDouble(0, 1.0)?.toFloat() ?: 1f) *
                    tillingFactor(uvs, "UFunction") * (tilling?.optDouble(0, 1.0)?.toFloat() ?: 1f),
                (st?.optDouble(1, 1.0)?.toFloat() ?: 1f) *
                    tillingFactor(uvs, "VFunction") * (tilling?.optDouble(1, 1.0)?.toFloat() ?: 1f)
            ),
            uvOffset = floatArrayOf(
                tr?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                tr?.optDouble(1, 0.0)?.toFloat() ?: 0f
            ),
            additive = additive,
            forceTransparent = forceTransparent,
            alphaCutoff = alphaCutoff,
            alphaMaskTex = alphaTex,
            alphaUvScale = floatArrayOf(
                tillingFactor(json.optJSONObject("alphaTexture"), "UFunction"),
                tillingFactor(json.optJSONObject("alphaTexture"), "VFunction")
            ),
            normalTex = textureSource(normal),
            normalUvScale = floatArrayOf(
                (normal?.optJSONArray("scale")?.optDouble(0, 1.0)?.toFloat() ?: 1f) * tillingFactor(normal, "UFunction"),
                (normal?.optJSONArray("scale")?.optDouble(1, 1.0)?.toFloat() ?: 1f) * tillingFactor(normal, "VFunction")
            ),
            normalUvOffset = floatArrayOf(
                normal?.optJSONArray("translation")?.optDouble(0, 0.0)?.toFloat() ?: 0f,
                normal?.optJSONArray("translation")?.optDouble(1, 0.0)?.toFloat() ?: 0f
            ),
            bumpScale = normal?.optDouble("bumpScale", 1.0)?.toFloat() ?: 1f,
            unlit = unlit,
            brdfType = type,
            useLinearColor = json.optBoolean("useLinearColor", false),
            
            envTop = null,
            envSide = null,
            envDown = null,
            directLighting = listOf("enableDirectionalLight", "enablePointLight", "enableSpotLight", "enableAreaLight")
                .any { brdf.optBoolean(it, true) },
            dirLight = brdf.optBoolean("enableDirectionalLight", true),
            carCl = false,
            
            envDiffInt = 1f,
            lampOffMat = isOffMaterial(path),
            vsPath = vsPath,
            fsPath = fsPath,
            vsOptions = vsOptions,
            fsOptions = fsOptions,
            uniforms = rawUniforms,
            specularColor = vec3(brdf.optJSONArray("specularColor"), ZERO3),
            glossiness = gloss.toFloat(),
            ambientColor = vec3(brdf.optJSONArray("ambientColor"), ZERO3),
            reflectivity = brdf.optDouble("reflectivity", 0.5).toFloat()
        )
    }

    private fun textureSource(v: Any?): String? = when (v) {
        is JSONObject -> v.optString("source", "").ifBlank { null }
        is String -> v.ifBlank { null }
        else -> null
    }?.takeIf { it.endsWith(".ktx", ignoreCase = true) }

    private fun shaderOption(json: JSONObject, name: String): Boolean? {
        val mat = json.optJSONObject("Material") ?: return null
        for (key in arrayOf("FragmentShaderOptions", "VertexShaderOptions")) {
            val arr = mat.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("Name") == name) return o.optBoolean("Value", false)
            }
        }
        return null
    }

    private val TEXTURE_MACROS = mapOf(
        "USE_BASE_MAP" to "_BaseMap",
        "USE_ORM" to "_ORM",
        "USE_NOR_MAP" to "_NormalMap",
        "USE_EMISSIVE_TEX" to "_EmissiveTex",
        "USE_FLAKES_CL" to "_FlakesNormal",
        "SPEC_SWITCHING" to "_EnvSpecMapPlus"
    )

    private fun dropTextureMacros(
        options: Set<String>,
        u: Map<String, JSONObject>
    ): Set<String> {
        if (options.isEmpty()) return options
        val out = LinkedHashSet<String>(options.size)
        for (name in options) {
            val tex = TEXTURE_MACROS[name]
            if (tex != null && u.string(tex).isNullOrEmpty()) continue
            out.add(name)
        }
        return out
    }

    private fun options(json: JSONObject, key: String): Set<String> {
        val mat = json.optJSONObject("Material") ?: return emptySet()
        val arr = mat.optJSONArray(key) ?: return emptySet()
        val out = HashSet<String>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("Value", false)) out.add(o.optString("Name", ""))
        }
        out.remove("")
        return out
    }

    private fun uniformValues(arr: JSONArray): Map<String, Any> {
        val out = HashMap<String, Any>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("Name", "")
            if (name.isEmpty()) continue
            when (val v = o.opt("Value")) {
                is Number -> out[name] = v.toFloat()
                is JSONArray -> {
                    if (v.length() in 2..4) {
                        out[name] = FloatArray(v.length()) { v.optDouble(it, 0.0).toFloat() }
                    }
                }
                is String -> if (v.isNotBlank()) out[name] = v
                is Boolean -> out[name] = if (v) 1f else 0f
            }
        }
        return out
    }

        private fun isOffMaterial(path: String): Boolean {
        val name = fileName(path).removeSuffix(".mat")
        return name.endsWith("_off", ignoreCase = true) || name.endsWith("_Off", ignoreCase = true)
    }

    private fun tillingFactor(tex: JSONObject?, key: String): Float {
        val f = tex?.optJSONObject(key) ?: return 1f
        if (f.optInt("UVFunctionType", 0) != 5) return 1f
        val p = f.optJSONArray("UVFunctionParam") ?: return 1f
        val v = p.optDouble(0, 1.0).toFloat()
        return if (v > 0f) v else 1f
    }

    private fun fallback(path: String): MaterialSpec {
        val name = fileName(path)
        val kind = MaterialKind.of(name, name)
        return MaterialSpec(
            path = path,
            base = kind.base,
            metallic = kind.metallic,
            roughness = kind.roughness,
            opacity = kind.alpha,
            emissive = if (kind.emissive > 0f) kind.base else ZERO3,
            emissiveInt = kind.emissive,
            coat = if (kind == MaterialKind.PAINT) 1.2f else 0f,
            coatRough = 0.06f,
            dirSpecInt = 0.8f,
            envSpecInt = 0.8f,
            doubleSided = false,
            renderOrder = 0,
            kind = kind,
            envTop = DEFAULT_TOP,
            envSide = DEFAULT_SIDE,
            envDown = DEFAULT_DOWN
        )
    }

    private fun fileName(path: String): String = path.substringAfterLast('/')

    private fun sameImage(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        fun key(p: String): String =
            p.substringAfterLast('/').substringBeforeLast('.').replace("-mipmap", "")
        return key(a).equals(key(b), ignoreCase = true)
    }

    private fun index(arr: JSONArray): Map<String, JSONObject> {
        val out = HashMap<String, JSONObject>(arr.length() * 2)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optString("Name", "")
            if (n.isNotEmpty()) out[n] = o
        }
        return out
    }

    private fun Map<String, JSONObject>.string(key: String): String? {
        val v = this[key]?.opt("Value") as? String ?: return null
        return v.takeIf { it.isNotBlank() && it.endsWith(".ktx", ignoreCase = true) }
    }

    private fun float(u: JSONObject?, def: Float): Float {
        if (u == null) return def
        val v = u.opt("Value") ?: return def
        return if (v is Number) v.toFloat() else def
    }

    private fun vec2(u: JSONObject?, index: Int, def: Float = 0f): Float {
        val a = u?.optJSONArray("Value") ?: return def
        return if (a.length() > index) a.optDouble(index, def.toDouble()).toFloat() else def
    }

    private fun value3(u: JSONObject?, def: FloatArray): FloatArray =
        vec3(u?.optJSONArray("Value"), def)

    private fun vec3(a: JSONArray?, def: FloatArray): FloatArray {
        if (a == null || a.length() < 3) return def.copyOf()
        val r = a.optDouble(0, def[0].toDouble()).toFloat()
        val g = a.optDouble(1, def[1].toDouble()).toFloat()
        val b = a.optDouble(2, def[2].toDouble()).toFloat()
        
        return floatArrayOf(r, g, b)
    }

    private val ZERO3 = floatArrayOf(0f, 0f, 0f)
}

object DefaultMaterials {
    val OTHER = MaterialSpec(
        path = "",
        base = floatArrayOf(0.42f, 0.45f, 0.50f),
        metallic = 0.30f,
        roughness = 0.48f,
        opacity = 1f,
        emissive = floatArrayOf(0f, 0f, 0f),
        emissiveInt = 0f,
        coat = 0f,
        coatRough = 0.06f,
        dirSpecInt = 0.8f,
        envSpecInt = 0.8f,
        doubleSided = false,
        renderOrder = 0,
        kind = MaterialKind.OTHER
    )
}
