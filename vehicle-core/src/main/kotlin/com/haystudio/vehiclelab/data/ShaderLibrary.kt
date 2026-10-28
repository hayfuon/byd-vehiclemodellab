package com.haystudio.vehiclelab.data

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** 隔离校验着色器 ZIP，并以目录替换方式安装；拒绝路径穿越及超限输入。 */
object ShaderLibrary {
    private val required = setOf("vs_common.es", "fs_pbr.es", "fragmentOutput.es")

    fun install(input: InputStream, destination: File) {
        val stage = File(destination.parentFile, ".shader-import")
        require(!stage.exists()) { "已有着色器导入任务" }
        check(stage.mkdirs()) { "无法创建导入目录" }
        try {
            val names = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.removePrefix("engine/")
                    require(name.matches(Regex("[A-Za-z0-9_]+\\.es"))) { "只允许 engine 目录内的 .es 文件" }
                    require(names.add(name) && names.size <= 512) { "文件重复或数量过多" }
                    val file = File(stage, name)
                    file.outputStream().use { output ->
                        var size = 0L
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            size += count
                            total += count
                            require(size <= 2 * 1024 * 1024 && total <= 16 * 1024 * 1024) { "着色器库过大" }
                            output.write(buffer, 0, count)
                        }
                        require(size > 0) { "着色器为空" }
                    }
                }
            }
            require(names.containsAll(required)) { "缺少必需的着色器头文件" }
            val include = Regex("""#include\s*<([A-Za-z0-9_]+)>""")
            val builtIns = setOf("finalWorldMatrix", "batchDrawDeclaration", "bonesVertexDeclaration")
            val graph = names.associate { name ->
                name.removeSuffix(".es") to include.findAll(File(stage, name).readText()).map { it.groupValues[1] }.toList()
            }
            val checked = mutableSetOf<String>()
            val visiting = mutableSetOf<String>()
            fun validate(name: String) {
                if (name in builtIns || name in checked) return
                require(name in graph) { "缺少着色器依赖：$name.es" }
                require(visiting.add(name)) { "着色器循环引用：$name.es" }
                graph.getValue(name).forEach(::validate)
                visiting.remove(name)
                checked.add(name)
            }
            graph.keys.forEach(::validate)
            val previous = File(destination.parentFile, ".shader-previous")
            require(!previous.exists()) { "上次导入未完成" }
            if (destination.exists()) check(destination.renameTo(previous))
            if (!stage.renameTo(destination)) {
                if (previous.exists()) previous.renameTo(destination)
                error("无法保存着色器库")
            }
            previous.deleteRecursively()
        } finally {
            stage.deleteRecursively()
        }
    }
}
