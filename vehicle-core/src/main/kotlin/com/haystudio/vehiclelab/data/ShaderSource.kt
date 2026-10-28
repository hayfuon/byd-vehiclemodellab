package com.haystudio.vehiclelab.data

import android.util.Log
import java.io.File

/** 从用户授权的私有着色器库展开头文件，按 GLSL ES 3.0 编译材质程序。 */
object ShaderSource {

    private val official = HashMap<String, String>(8)

    private val officialRaw = HashMap<String, String>(8)

    val isReady: Boolean get() = officialRaw.isNotEmpty()

    private val ADAPTATIONS: Map<String, String> = mapOf(
        
        "finalWorldMatrix" to """
            mat4 modelMatrix = u_model;
        """.trimIndent(),
        "batchDrawDeclaration" to "",
        "bonesVertexDeclaration" to ""
    )

    fun init(library: File) {
        official.clear()
        officialRaw.clear()
        val names = try {
            library.listFiles()?.filter { it.isFile && it.extension == "es" }
                ?.map { it.nameWithoutExtension }
        } catch (_: Throwable) {
            null
        } ?: emptyList()
        for (name in names) {
            val text = try {
                File(library, "$name.es").readText()
            } catch (t: Throwable) {
                Log.w("CarDemo", "引擎头文件 $name 读取失败：${t.message}")
                continue
            }
            officialRaw[name] = text
            official[name] = if (name == "fragmentOutput") withGamma(text) else text
        }
        Log.i("CarDemo", "引擎头文件 ${official.size} 个：${official.keys.joinToString()}")
    }

    fun assembleEngine(name: String, prepend: String = ""): String? {
        val text = officialRaw[name] ?: return null
        return assemble(text, prepend)
    }

    private fun withGamma(src: String): String =
        src.replace("    FragColor = outColor;", "    FragColor = outColor;\n    DoPostGammaCorrection(FragColor.rgb);")

    fun load(reader: GpakReader, relative: String): String? {
        if (relative.isBlank()) return null
        val raw = readRelative(reader, relative) ?: return null
        val src = String(raw, Charsets.UTF_8)
        android.util.Log.i(
            "CarDemo",
            "着色器装配 $relative 长度=${src.length} 有shadowFactor=${src.contains("shadowFactor")} " +
                "命中行=${src.contains("mix(lightDiff * shadowColor")}"
        )
        return assemble(src)
    }

    private fun readRelative(reader: GpakReader, relative: String): ByteArray? {
        val rel = relative.trim().replace('\\', '/')
        val candidates = arrayOf(
            "MyCar/AppCommon/Common/Material/$rel",
            "MyCar/AppCommon/Common/Material/shaders/$rel",
            rel
        )
        for (path in candidates) {
            reader.read(path)?.let { return it }
        }
        val suffix = "/$rel"
        val hit = try {
            reader.names().firstOrNull { it.endsWith(suffix, ignoreCase = true) }
        } catch (_: Throwable) {
            null
        } ?: return null
        return reader.read(hit)
    }

    private fun assemble(source: String, prepend: String = ""): String {
        
        val selfGamma = source.contains("DoPostGammaCorrection(")
        val out = StringBuilder(source.length + 32768)
        out.append("#version 300 es\n")
        if (prepend.isNotBlank()) out.append(prepend).append('\n')
        fun expand(text: String, stack: Set<String>) {
            for (line in text.lineSequence()) {
                val match = Regex("""^\s*#include\s*<([A-Za-z0-9_]+)>\s*$""").matchEntire(line)
                if (match == null) {
                    out.append(line).append('\n')
                    continue
                }
                val name = match.groupValues[1]
                val header = ADAPTATIONS[name]
                    ?: if (name == "fragmentOutput" && selfGamma) officialRaw[name] else official[name]
                when {
                    name in stack -> out.append("#error circular engine include: ").append(name).append('\n')
                    header == null -> out.append("#error missing engine include: ").append(name).append('\n')
                    else -> expand(header, stack + name)
                }
            }
        }
        expand(source, emptySet())
        return out.toString()
    }
}
