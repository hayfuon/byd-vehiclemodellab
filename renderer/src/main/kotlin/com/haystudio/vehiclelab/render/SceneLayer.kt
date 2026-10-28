package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import com.haystudio.vehiclelab.data.KtxTexture
import com.haystudio.vehiclelab.data.SceneAsset
import com.haystudio.vehiclelab.data.ScenePart
import com.haystudio.vehiclelab.data.MeshData
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SceneLayer(
    private val uploadTexture: (KtxTexture) -> Int,
    private val officialFactory: (MeshData, Int, Int) -> () -> Unit
) {
    private class OfficialPart(val mesh: MeshData, val vbo: Int, val ibo: Int, val draw: () -> Unit)
    private val officialParts = ArrayList<OfficialPart>()

    private class GpuPart(
        val vbo: Int,
        val ibo: Int,
        val count: Int,
        val tex: Int,
        val tint: FloatArray,
        val add: FloatArray,
        val opacity: Float,
        val uvScale: FloatArray,
        val additive: Boolean,
        val depthWrite: Boolean,
        val blended: Boolean,
        val emissive: FloatArray,
        val metallic: Float,
        val planar: Boolean
    )

    private var program = 0
    private var uVp = 0
    private var uTex = 0
    private var uTint = 0
    private var uAdd = 0
    private var uUvScale = 0
    private var uHasTex = 0
    private var uShadowXf = 0
    private var uModel = 0
    private var uReflect = 0
    private var uScreenSize = 0
    private var uReflectOn = 0
    private var uMetallic = 0
    private var uEmissive = 0
    private var uReflOnly = 0
    private var uLinear = 0
    
    var debugReflectionOnly = false
    
    var linearOutput = false
    var externalBackground=false
    var headingRadians=0f
    private val backgroundMatrix=FloatArray(16)
    private fun backgroundTransform():FloatArray {
        Matrix.setIdentityM(backgroundMatrix,0)
        Matrix.rotateM(backgroundMatrix,0,-Math.toDegrees(headingRadians.toDouble()).toFloat(),0f,1f,0f)
        return backgroundMatrix
    }
    
    private val groundTextures=GroundTextures()
    var groundMaterialStyle=0
    var groundReflectionTexture = 0
        private set
    private var aPos = 0
    private var aUv = 0

    private var reflectionBound = false
    private var reflectLogged = false

    private val sky = ArrayList<GpuPart>()
    private val mountain = ArrayList<GpuPart>()
    private val ground = ArrayList<GpuPart>()
    
    private val shadow = ArrayList<GpuPart>()

    @Volatile private var pending: SceneAsset? = null
    private var retainedAsset: SceneAsset? = null
    private var uploadedName = ""

    val visible: Boolean get() = officialParts.isNotEmpty() || ground.isNotEmpty() || mountain.isNotEmpty() || sky.isNotEmpty()

    fun setAsset(asset: SceneAsset?) {
        retainedAsset = asset
        pending = asset
        if (asset == null) uploadedName = ""
    }

    fun onContextCreated() {
        groundTextures.onContextCreated()
        officialParts.clear()
        for (list in listOf(sky, mountain, ground, shadow)) list.clear()
        program = 0
        uploadedName = ""
        reflectionBound = false
        pending = retainedAsset
    }

    fun currentAssetName(): String = uploadedName

    fun uploadPending(texStore: MutableMap<String, Int>) {
        val asset = pending ?: return
        pending = null
        release()
        ensureProgram()
        for ((key, bytes) in asset.textures) {
            if (key !in texStore) {
                val texture = requireNotNull(KtxTexture.parse(bytes)) { "场景贴图不可读：$key" }
                val id = uploadTexture(texture)
                check(id != 0) { "场景贴图上传失败：$key 格式=${texture.internalFormat}" }
                texStore[key] = id
            }
        }
        for (mesh in asset.officialMeshes) {
            val vertices = ByteBuffer.allocateDirect(mesh.positions.size / 3 * 48)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (i in 0 until mesh.positions.size / 3) {
                vertices.put(mesh.positions, i * 3, 3)
                vertices.put(mesh.normals, i * 3, 3)
                vertices.put(mesh.uvs, i * 2, 2)
                for (j in 0..2) vertices.put((mesh.tangents.getOrElse(i * 4 + j) { if (j == 0) 1f else 0f }) * 0.5f + 0.5f)
                vertices.put(if (mesh.tangents.getOrElse(i * 4 + 3) { 1f } < 0) 0f else 1f)
            }
            vertices.position(0)
            val ids = IntArray(2)
            GLES30.glGenBuffers(2, ids, 0)
            GLES30.glBindVertexArray(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.capacity() * 4, vertices, GLES30.GL_STATIC_DRAW)
            val indices = ByteBuffer.allocateDirect(mesh.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
            indices.put(mesh.indices).position(0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, indices, GLES30.GL_STATIC_DRAW)
            officialParts.add(OfficialPart(mesh, ids[0], ids[1], officialFactory(mesh, ids[0], ids[1])))
        }
        officialParts.sortBy { it.mesh.material.renderOrder }
        for (part in asset.parts) {
            val gpu = build(part, texStore) ?: continue
            when {
                part.group.contains("skybox", true) -> sky.add(gpu)
                part.group.contains("mountain", true) -> mountain.add(gpu)
                else -> ground.add(gpu)
            }
        }
        for (part in asset.shadow) build(part, texStore)?.let { shadow.add(it) }
        uploadedName = "${sky.size}/${mountain.size}/${ground.size}"
        Log.i(
            "CarDemo",
            "背景上传：天空 ${sky.size} 段 · 远山 ${mountain.size} 段 · 地面 ${ground.size} 段" +
                " · 投影 ${shadow.size} 段"
        )
    }

    fun draw(vp: FloatArray, reflection: Int = 0, screen: FloatArray = ZERO2) {
        groundReflectionTexture = reflection
        if (officialParts.isNotEmpty()) {
            drawOfficialParts(false)
            return
        }
        if (program == 0 || (sky.isEmpty() && mountain.isEmpty() && ground.isEmpty())) return
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uVp, 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, IDENTITY4, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform1f(uReflectOn, 0f)
        GLES30.glUniform1f(uLinear, if (linearOutput) 1f else 0f)

        GLES30.glUniformMatrix4fv(uModel,1,false,backgroundTransform(),0)
        drawParts(sky)
        drawParts(mountain)
        GLES30.glUniformMatrix4fv(uModel,1,false,IDENTITY4,0)

        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glUniform1f(uReflOnly, if (debugReflectionOnly) 1f else 0f)
        reflectionBound = reflection != 0
        if (reflectionBound && !reflectLogged) {
            reflectLogged = true
            val v = IntArray(1)
            GLES30.glGetUniformiv(program, uReflect, v, 0)
            Log.i("CarDemo", "反射采样器 loc=$uReflect 值=${v[0]} screen=$uScreenSize")
        }
        if (reflectionBound) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, reflection)
            GLES30.glUniform2f(uScreenSize, screen[0], screen[1])
        }
        drawParts(ground, forceWrite = true)
        reflectionBound = false
        GLES30.glUniform1f(uReflectOn, 0f)
        GLES30.glUniform1f(uLinear, if (linearOutput) 1f else 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        if (ground.isEmpty()) GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun drawSky(vp: FloatArray) {
        groundReflectionTexture = 0
        if (officialParts.isNotEmpty()) {
            drawOfficialParts(true)
            return
        }
        if (program == 0 || (sky.isEmpty() && mountain.isEmpty())) return
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uVp, 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, backgroundTransform(), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform1f(uReflectOn, 0f)
        GLES30.glUniform1f(uLinear, if (linearOutput) 1f else 0f)
        drawParts(sky)
        drawParts(mountain)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun drawShadow(vp: FloatArray, xf: FloatArray, model: FloatArray) {
        if (program == 0 || shadow.isEmpty()) return
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(uVp, 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform1f(uLinear, if (linearOutput) 1f else 0f)
        drawParts(shadow, xf = xf)
        GLES30.glDisable(GLES30.GL_BLEND)
        
        GLES30.glDepthMask(true)
    }

    private fun drawParts(
        parts: List<GpuPart>,
        forceWrite: Boolean = false,
        xf: FloatArray = IDENTITY
    ) {
        GLES30.glUniform4fv(uShadowXf, 1, xf, 0)
        for (p in parts) {
            if (p.count <= 0) continue
            val blend = p.opacity < 0.999f || p.additive || p.blended
            if (blend) {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(
                    GLES30.GL_SRC_ALPHA,
                    if (p.additive) GLES30.GL_ONE else GLES30.GL_ONE_MINUS_SRC_ALPHA
                )
            } else {
                GLES30.glDisable(GLES30.GL_BLEND)
            }
            GLES30.glDepthMask(forceWrite || p.depthWrite)

            val customGround=parts===ground && groundMaterialStyle>0
            val groundTexture=if(customGround)groundTextures.texture(groundMaterialStyle) else p.tex
            GLES30.glUniform4f(uTint,if(customGround)1f else p.tint[0],if(customGround)1f else p.tint[1],if(customGround)1f else p.tint[2],p.opacity)
            GLES30.glUniform3f(uAdd, p.add[0], p.add[1], p.add[2])
            GLES30.glUniform2f(uUvScale, p.uvScale[0]*(if(customGround)3f else 1f), p.uvScale[1]*(if(customGround)3f else 1f))
            GLES30.glUniform3f(uEmissive, p.emissive[0], p.emissive[1], p.emissive[2])
            GLES30.glUniform1f(uMetallic, p.metallic)
            GLES30.glUniform1f(
                uReflectOn, if (reflectionBound && p.planar) 1f else 0f
            )
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            if (groundTexture != 0) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, groundTexture)
                GLES30.glUniform1f(uHasTex, 1f)
            } else {
                GLES30.glUniform1f(uHasTex, 0f)
            }

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, p.vbo)
            GLES30.glEnableVertexAttribArray(aPos)
            GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_FLOAT, false, 20, 0)
            GLES30.glEnableVertexAttribArray(aUv)
            GLES30.glVertexAttribPointer(aUv, 2, GLES30.GL_FLOAT, false, 20, 12)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.ibo)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.count, GLES30.GL_UNSIGNED_INT, 0)
        }
    }

    private fun build(part: ScenePart, texStore: MutableMap<String, Int>): GpuPart? {
        if (part.indices.isEmpty()) return null
        val vc = part.vertexCount
        
        val inter = ByteBuffer.allocateDirect(vc * 5 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until vc) {
            inter.put(part.positions[i * 3])
            inter.put(part.positions[i * 3 + 1])
            inter.put(part.positions[i * 3 + 2])
            inter.put(part.uvs.getOrElse(i * 2) { 0f })
            inter.put(part.uvs.getOrElse(i * 2 + 1) { 0f })
        }
        inter.position(0)

        val ids = IntArray(2)
        GLES30.glGenBuffers(2, ids, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER, vc * 5 * 4, inter, GLES30.GL_STATIC_DRAW
        )
        val idx = ByteBuffer.allocateDirect(part.indices.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer()
        idx.put(part.indices)
        idx.position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER, part.indices.size * 4, idx, GLES30.GL_STATIC_DRAW
        )

        val tex = part.texture?.let { bytes ->
            val key = part.textureKey ?: part.group
            texStore[key] ?: run {
                val id = try {
                    KtxTexture.parse(bytes)?.let { uploadTexture(it) } ?: 0
                } catch (t: Throwable) {
                    Log.w("CarDemo", "背景贴图解析失败 $key：${t.message}")
                    0
                }
                if (id != 0) texStore[key] = id
                id
            }
        } ?: 0

        return GpuPart(
            vbo = ids[0],
            ibo = ids[1],
            count = part.indices.size,
            tex = tex,
            tint = part.tint,
            add = part.add,
            opacity = part.opacity,
            uvScale = part.uvScale,
            additive = part.additive,
            depthWrite = part.depthWrite,
            blended = part.blended,
            emissive = part.emissive,
            metallic = part.metallic,
            planar = part.planarReflection
        )
    }

    private fun ensureProgram() {
        if (program != 0) return
        program = GlUtil.program(VERTEX.trimIndent(), FRAGMENT.trimIndent())
        uVp = GLES30.glGetUniformLocation(program, "uVp")
        uTex = GLES30.glGetUniformLocation(program, "uTex")
        uTint = GLES30.glGetUniformLocation(program, "uTint")
        uAdd = GLES30.glGetUniformLocation(program, "uAdd")
        uUvScale = GLES30.glGetUniformLocation(program, "uUvScale")
        uHasTex = GLES30.glGetUniformLocation(program, "uHasTex")
        uShadowXf = GLES30.glGetUniformLocation(program, "uShadowXf")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uReflect = GLES30.glGetUniformLocation(program, "uReflect")
        uScreenSize = GLES30.glGetUniformLocation(program, "uScreenSize")
        uReflectOn = GLES30.glGetUniformLocation(program, "uReflectOn")
        uMetallic = GLES30.glGetUniformLocation(program, "uMetallic")
        uEmissive = GLES30.glGetUniformLocation(program, "uEmissive")
        uReflOnly = GLES30.glGetUniformLocation(program, "uReflOnly")
        uLinear = GLES30.glGetUniformLocation(program, "uLinear")
        
        GLES30.glUseProgram(program)
        GLES30.glUniform1i(uReflect, 1)
        GLES30.glUseProgram(0)
        aPos = GLES30.glGetAttribLocation(program, "aPos")
        aUv = GLES30.glGetAttribLocation(program, "aUv")
    }

    private fun drawOfficialParts(backgroundOnly: Boolean) {
        GLES30.glBindVertexArray(0)
        for (part in officialParts) {
            val spec = part.mesh.material
            
            if(externalBackground && (spec.path.contains("skybox")||spec.path.contains("mountain")||spec.path.contains("m_env_fog_")))continue
            if (backgroundOnly && !spec.path.contains("skybox") && !spec.path.contains("mountain")) continue
            GlUtil.faceCulling(spec.faceCulling)
            if (spec.blended) GLES30.glEnable(GLES30.GL_BLEND) else GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, if (spec.additive) GLES30.GL_ONE else GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDepthMask(!spec.blended)
            part.draw()
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    fun drawGroundReceiver() {
        GLES30.glEnableVertexAttribArray(0)
        for (part in officialParts) {
            if (!part.mesh.material.path.contains("m_env_ground_")) continue
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, part.vbo)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 48, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, part.ibo)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, part.mesh.indices.size, GLES30.GL_UNSIGNED_INT, 0)
        }
    }

    fun release() {
        for (p in officialParts) GLES30.glDeleteBuffers(2, intArrayOf(p.vbo, p.ibo), 0)
        officialParts.clear()
        for (list in listOf(sky, mountain, ground, shadow)) {
            for (p in list) {
                GLES30.glDeleteBuffers(2, intArrayOf(p.vbo, p.ibo), 0)
            }
            list.clear()
        }
    }

    fun dispose() {
        release()
        groundTextures.release()
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
        pending = null
        retainedAsset = null
        uploadedName = ""
    }

    private companion object {
        val IDENTITY = floatArrayOf(1f, 1f, 0f, 0f)
        val IDENTITY4 = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }
        val ZERO2 = floatArrayOf(1f, 1f)

        const val VERTEX = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uVp;
            uniform mat4 uModel;
            uniform vec2 uUvScale;
            uniform vec4 uShadowXf;
            out vec2 vUv;
            void main() {
                vUv = aUv * uUvScale;
                vec3 p = vec3(aPos.x * uShadowXf.x + uShadowXf.z, aPos.y,
                              aPos.z * uShadowXf.y + uShadowXf.w);
                gl_Position = uVp * uModel * vec4(p, 1.0);
            }
        """

        const val FRAGMENT = """
            #version 300 es
            precision highp float;
            in vec2 vUv;
            uniform sampler2D uTex;
            uniform sampler2D uReflect;
            uniform vec4 uTint;
            uniform vec3 uAdd;
            uniform float uHasTex;
            uniform float uReflectOn;
            uniform float uMetallic;
            uniform vec3 uEmissive;
            uniform vec2 uScreenSize;
            uniform float uReflOnly;
            uniform float uLinear;
            out vec4 fragColor;
            vec3 unlitTextureColor(vec3 linearSample) {
                // 与原厂 fs_unlit_textured_color.es 的 modifysRGBTextureColor 一致。
                // sRGB 贴图由硬件按分段传递函数解码；原材质再转到引擎的 gamma 2.2 工作空间。
                // 直接对硬件线性值做 1/2.2 会把接近黑色的 ASTC 误差放大成阴影斑纹。
                vec3 srgb = mix(linearSample * 12.92,
                    pow(max(linearSample, vec3(0.0)), vec3(1.0 / 2.4)) * 1.055 - 0.055,
                    step(vec3(0.0031308), linearSample));
                return pow(max(srgb, vec3(0.0)), vec3(2.2));
            }
            void main() {
                vec4 tex = uHasTex > 0.5 ? texture(uTex, vUv) : vec4(1.0);
                tex.rgb = unlitTextureColor(tex.rgb);
                vec3 base = tex.rgb * uTint.rgb;
                vec3 c;
                if (uReflectOn > 0.5 && uReflOnly > 0.5) {
                    fragColor = vec4(texture(uReflect, vec2(0.5, 0.3)).rgb, 1.0);
                    return;
                }
                if (uReflectOn > 0.5) {
                    // 官方 m_env_ground_*.mat：metallic 0.8，金属无漫反射，
                    // 看到的是平面反射图按 F0 着色
                    vec2 uv = gl_FragCoord.xy / uScreenSize;
                    vec3 refl = texture(uReflect, uv).rgb;
                    refl = pow(max(refl, vec3(0.0)), vec3(2.2));
                    vec3 f0 = mix(vec3(0.04), base, uMetallic);
                    c = base * (1.0 - uMetallic) + f0 * refl;
                } else {
                    c = base + uAdd;
                }
                // emissiveTexture 与 baseTexture 同图，官方按 emissiveColor × 强度叠加
                c += tex.rgb * uEmissive;
                vec3 linear = max(c, vec3(0.0));
                vec3 shown = uLinear > 0.5 ? linear : pow(linear, vec3(1.0 / 2.2));
                fragColor = vec4(shown, tex.a * uTint.a);
            }
        """
    }
}
