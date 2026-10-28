package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import android.util.Log
import com.haystudio.vehiclelab.data.MaterialSpec

class OfficialProgram private constructor(val id: Int) {

    private val uniforms = HashMap<String, Int>(64)
    private val attribs = HashMap<String, Int>(8)

    fun use() {
        GLES30.glUseProgram(id)
    }

    fun uniform(name: String): Int {
        uniforms[name]?.let { return it }
        val loc = GLES30.glGetUniformLocation(id, name)
        uniforms[name] = loc
        return loc
    }

    fun attrib(name: String): Int {
        attribs[name]?.let { return it }
        val loc = GLES30.glGetAttribLocation(id, name)
        attribs[name] = loc
        return loc
    }

    fun bindRaw(name: String, value: Any) {
        val loc = uniform(name)
        if (loc < 0) return
        when (value) {
            is Float -> GLES30.glUniform1f(loc, value)
            is FloatArray -> when (value.size) {
                2 -> GLES30.glUniform2f(loc, value[0], value[1])
                3 -> GLES30.glUniform3f(loc, value[0], value[1], value[2])
                4 -> GLES30.glUniform4f(loc, value[0], value[1], value[2], value[3])
            }
            is Int -> GLES30.glUniform1i(loc, value)
        }
    }

    fun bindTextures(
        spec: MaterialSpec,
        firstUnit: Int,
        lookup: (String, String) -> Pair<Int, Int>
    ): Int {
        var unit = firstUnit

        for ((name, type) in activeSamplers) {
            val path = spec.textureUniforms[name]
            val expectedTarget = if (type == GLES30.GL_SAMPLER_CUBE)
                GLES30.GL_TEXTURE_CUBE_MAP else GLES30.GL_TEXTURE_2D
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            val (target, tex) = if (path == null && name != "uLabGroundReflection") expectedTarget to 0 else lookup(name, path.orEmpty())
            GLES30.glBindTexture(target, tex)
            GLES30.glUniform1i(uniform(name), unit)
            unit++
        }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        return unit
    }

    private val activeSamplers: List<Pair<String, Int>> by lazy {
        val count = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_ACTIVE_UNIFORMS, count, 0)
        buildList {
            for (i in 0 until count[0]) {
                val size = IntArray(1); val type = IntArray(1)
                val name = GLES30.glGetActiveUniform(id, i, size, 0, type, 0)
                if (type[0] in intArrayOf(GLES30.GL_SAMPLER_2D, GLES30.GL_SAMPLER_CUBE,
                        GLES30.GL_SAMPLER_2D_SHADOW)) add(name to type[0])
            }
        }
    }

    fun bindBlock(name: String, point: Int) {
        val index = GLES30.glGetUniformBlockIndex(id, name)
        if (index == GLES30.GL_INVALID_INDEX) return
        GLES30.glUniformBlockBinding(id, index, point)
    }

    fun release() {
        if (id != 0) GLES30.glDeleteProgram(id)
    }

    companion object {
        
        var lastError: String? = null
            private set

        fun cacheKey(spec: MaterialSpec, shadow: Boolean): String {
            val vs = drawOptions(spec.vsOptions).sorted().joinToString(",")
            val fs = drawOptions(spec.fsOptions).sorted().joinToString(",")
            return "${spec.vsPath}|$vs#${spec.fsPath}|$fs|shadow=$shadow"
        }

        private fun drawOptions(options: Set<String>): Set<String> =
            options - setOf("DISCARD_ABOVE", "DISCARD_BELOW")

        /** 自定义车漆的固定映射服从全局开关，HDR 路径统一在帧末映射。 */
        internal fun adaptToneMapping(source: String): String = source.replace(
            "outrgb = ACESFilm2BYD(outrgb);",
            "if (!HdrEnabled() && NeedDoToneMapping()) outrgb = ACESFilm2BYD(outrgb);"
        )

        fun create(spec: MaterialSpec, shadow: Boolean = false): OfficialProgram? {
            val vs = spec.vsSource ?: return null
            val fs = spec.fsSource ?: return null
            val vsSrc = withDefines(vs, drawOptions(spec.vsOptions))

            val fsSrc = withDefines(
                adaptToneMapping(fs),
                drawOptions(if (shadow) spec.fsOptions + SHADOW_MAP else spec.fsOptions)
            )
            val id = try {
                GlUtil.program(vsSrc, fsSrc)
            } catch (t: Throwable) {
                lastError = "${spec.vsPath} / ${spec.fsPath}：${t.message}"
                Log.e(
                    "CarDemo",
                    "官方着色器编译失败 ${spec.vsPath} / ${spec.fsPath}：" +
                        "${spec.fsOptions}：${t.message}"
                )
                return null
            }
            return OfficialProgram(id).also {
                it.bindBlock("ubo_viewSettings", BLOCK_VIEW)
                it.bindBlock("ubo_lights", BLOCK_LIGHTS)
                it.bindBlock("ubo_physicalLights", BLOCK_PHYSICAL)
                it.bindBlock("ubo_material", BLOCK_MATERIAL)
                it.bindBlock("ubo_sphericalHarmonics", BLOCK_SH)
            }
        }

        const val BLOCK_VIEW = 0
        const val BLOCK_LIGHTS = 1
        const val BLOCK_PHYSICAL = 2
        const val BLOCK_MATERIAL = 3
        const val BLOCK_SH = 4

        const val SHADOW_MAP = "SHADOW_MAP"

        private fun withDefines(source: String, defines: Set<String>): String {
            if (defines.isEmpty()) return source
            val nl = source.indexOf('\n')
            if (nl < 0) return source
            val sb = StringBuilder(source.length + defines.size * 24)
            sb.append(source, 0, nl + 1)
            for (d in defines) sb.append("#define ").append(d).append(" 1\n")
            sb.append(source, nl + 1, source.length)
            return sb.toString()
        }
    }
}
