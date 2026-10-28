package com.haystudio.vehiclelab.data

import android.content.Context
import com.haystudio.vehiclelab.data.ModelRepository
import com.haystudio.vehiclelab.data.VehicleResourcePack
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.UUID

/** 导入资源按 UUID 隔离保存，完整验证成功后发布；系统资源始终只读。 */
class VehicleResourceLibrary(private val root: File,private val systemResourceDirectory:File?=null) {
    constructor(context: Context) : this(
        File(context.getExternalFilesDir(null) ?: context.filesDir, "vehicle-packs"),
        context.getExternalFilesDir(null) ?: context.filesDir
    )

    private fun folder(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "资源包标识无效" }
        return File(root, id)
    }

    private fun metadata(id: String): Properties = Properties().apply {
        File(folder(id), "pack.properties").inputStream().use { load(it) }
    }

    fun repository(id: String): ModelRepository {
        if (id.isEmpty()) return ModelRepository(localResourceDirectory=systemResourceDirectory)
        val properties = metadata(id)
        return ModelRepository(
            localResourceDirectory = folder(id),
            standalone = true,
            vehicleIdentity = properties.getProperty("vehicle").orEmpty()
        )
    }

    fun list(): List<ModelRepository.ModelInfo> {
        val items = ArrayList<ModelRepository.ModelInfo>()
        repository("").use { repository ->
            repository.open()
            items += repository.shortlist(repository.listModels())
        }
        items += listImported()
        return items
    }

    fun listImported(): List<ModelRepository.ModelInfo> =
        root.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.lastModified() }
            ?.mapNotNull { directory ->
                runCatching {
                    val properties = metadata(directory.name)
                    ModelRepository.ModelInfo(
                        label = properties.getProperty("name"),
                        path = properties.getProperty("model"),
                        fromMyCar = properties.getProperty("mycar").toBoolean(),
                        resourcePack = directory.name,
                        resourceBytes = properties.getProperty("size")?.toLongOrNull()
                            ?: resourceBytes(directory)
                    )
                }.getOrNull()
            }
            ?.toList()
            .orEmpty()

    fun export(
        selected: ModelRepository.ModelInfo,
        output: OutputStream,
        work: File
    ) {
        output.use { destination ->
            repository(selected.resourcePack).use { repository ->
                repository.open()
                val actual = repository.listModels().firstOrNull {
                    it.path == selected.path && it.fromMyCar == selected.fromMyCar
                } ?: error("当前资源包中未找到所选模型")
                repository.exportResources(actual, destination, work)
            }
        }
    }

    fun import(input: InputStream, suggestedName: String): ModelRepository.ModelInfo {
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val stage = File(root, ".$id").apply { mkdirs() }
        val incoming = File(stage, "incoming.hcvpack")
        try {
            input.use { source ->
                incoming.outputStream().use { output ->
                    var total = 0L
                    val buffer = ByteArray(65_536)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 512L * 1024 * 1024) { "资源包不能超过 512 MB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val ready = File(stage, "ready")
            val info = VehicleResourcePack.extract(incoming, ready)
            val label = suggestedName
                .substringBeforeLast('.', suggestedName)
                .trim()
                .take(48)
                .ifEmpty { info.name.trim().take(48) }
            require(label.isNotEmpty()) { "资源包名称不能为空" }
            ModelRepository(
                localResourceDirectory = ready,
                standalone = true,
                vehicleIdentity = info.vehicle
            ).use { repository ->
                repository.open()
                repository.validateResources(
                    ModelRepository.ModelInfo(info.name, info.model, info.fromMyCar)
                )
            }
            Properties().apply {
                setProperty("size", incoming.length().toString())
                setProperty("name", label)
                setProperty("model", info.model)
                setProperty("mycar", info.fromMyCar.toString())
                setProperty("vehicle", info.vehicle)
                File(ready, "pack.properties").outputStream().use { store(it, null) }
            }
            require(ready.renameTo(folder(id))) { "无法保存资源包" }
            return ModelRepository.ModelInfo(
                label = label,
                path = info.model,
                fromMyCar = info.fromMyCar,
                resourcePack = id,
                resourceBytes = incoming.length()
            )
        } finally {
            stage.deleteRecursively()
        }
    }

    fun thumbnailGeometry(info: ModelRepository.ModelInfo) =
        repository(info.resourcePack).use { repository ->
            repository.open()
            repository.thumbnailGeometry(info)
        }

    fun delete(info: ModelRepository.ModelInfo) {
        require(info.resourcePack.isNotEmpty()) { "车机资源不可删除" }
        val directory = folder(info.resourcePack)
        require(directory.isDirectory) { "资源包不存在" }
        require(directory.deleteRecursively()) { "删除失败，请重试" }
    }

    private fun resourceBytes(directory: File): Long =
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
