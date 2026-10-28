package com.haystudio.vehiclelab.data

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** HCVP 资源容器使用 SHA-256 内容寻址；导入时校验体积、清单和内容摘要。 */
object VehicleResourcePack {
    const val EXTENSION = "hcvpack"
    private const val MAGIC = 0x48435650
    private const val MAX_ENTRIES = 30_000
    private const val MAX_BYTES = 1024L * 1024 * 1024
    private const val MAX_BLOB = 256L * 1024 * 1024
    private val sources = mapOf(
        "mycar" to "BydMyCar.apk",
        "sr" to "BydSR.apk",
        "scene" to "scene.apk"
    )

    data class Info(
        val name: String,
        val model: String,
        val fromMyCar: Boolean,
        val vehicle: String,
        val settings: Map<String, String> = emptyMap()
    )

    private data class Ref(
        val source: String,
        val name: String,
        val hash: String,
        val size: Long
    )

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65_536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    class Builder(private val work: File) : Closeable {
        private val refs = linkedMapOf<Pair<String, String>, Ref>()
        private var total = 0L
        private var manifestBytes = 0L

        init {
            require(work.mkdirs() || work.isDirectory)
        }

        fun add(source: String, name: String, bytes: ByteArray) {
            require(
                source in sources && name.isNotEmpty() &&
                    name.toByteArray(Charsets.UTF_8).size <= 1024 &&
                    bytes.isNotEmpty() && bytes.size <= MAX_BLOB
            )
            val key = source to name
            if (key in refs) return
            require(refs.size < MAX_ENTRIES)
            manifestBytes += source.length + name.toByteArray(Charsets.UTF_8).size + 80
            require(manifestBytes <= 7L * 1024 * 1024) { "资源清单过大" }
            val sha = hash(bytes)
            val blob = File(work, sha)
            if (!blob.exists()) {
                total += bytes.size
                require(total <= MAX_BYTES) { "资源总量超过 1 GB" }
                blob.writeBytes(bytes)
            }
            refs[key] = Ref(source, name, sha, bytes.size.toLong())
        }

        fun write(output: OutputStream, info: Info) {
            require(refs.isNotEmpty()) { "资源包为空" }
            require(listOf(info.name, info.model, info.vehicle).all {
                it.toByteArray(Charsets.UTF_8).size <= 1024
            })
            require(info.settings.all { (key, value) ->
                key.toByteArray(Charsets.UTF_8).size <= 1024 &&
                    value.toByteArray(Charsets.UTF_8).size <= 1024
            })
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.setLevel(6)
                zip.putNextEntry(ZipEntry("manifest.hcvp"))
                val data = DataOutputStream(zip)
                data.writeInt(MAGIC)
                data.writeInt(1)
                data.writeUTF(info.name)
                data.writeUTF(info.model)
                data.writeBoolean(info.fromMyCar)
                data.writeUTF(info.vehicle)
                require(info.settings.size <= 64)
                data.writeInt(info.settings.size)
                info.settings.toSortedMap().forEach { (key, value) ->
                    data.writeUTF(key)
                    data.writeUTF(value)
                }
                data.writeInt(refs.size)
                refs.values.forEach { ref ->
                    data.writeUTF(ref.source)
                    data.writeUTF(ref.name)
                    data.writeUTF(ref.hash)
                    data.writeLong(ref.size)
                }
                data.flush()
                zip.closeEntry()
                refs.values.distinctBy { it.hash }.forEach { ref ->
                    zip.putNextEntry(ZipEntry("blobs/${ref.hash}"))
                    File(work, ref.hash).inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

        override fun close() {
            work.deleteRecursively()
        }
    }

    fun extract(archive: File, directory: File): Info {
        if (ZipFile(archive).use { it.getEntry("manifest.json") != null }) {
            PortableModelArchive.extract(archive,directory)
            return Info("车辆模型","model",true,"")
        }
        require(!directory.exists() || directory.listFiles().isNullOrEmpty()) { "导入目录必须为空" }
        require(directory.mkdirs() || directory.isDirectory)
        val work = File(directory, "blobs").apply { mkdirs() }
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(
                    entries.size in 2..MAX_ENTRIES + 1 &&
                        entries.map { it.name }.distinct().size == entries.size
                ) { "资源包目录异常" }
                val manifest = zip.getEntry("manifest.hcvp") ?: error("不是车辆模型资源包")
                require(manifest.size in 1..8L * 1024 * 1024) { "资源清单过大" }
                val refs = ArrayList<Ref>()
                val info = DataInputStream(zip.getInputStream(manifest).buffered()).use { input ->
                    require(input.readInt() == MAGIC && input.readInt() == 1) { "不支持此资源包版本" }
                    val name = input.readUTF()
                    val model = input.readUTF()
                    val fromMyCar = input.readBoolean()
                    val vehicle = input.readUTF()
                    val settings = linkedMapOf<String, String>()
                    val settingsCount = input.readInt()
                    require(settingsCount in 0..64)
                    repeat(settingsCount) { settings[input.readUTF()] = input.readUTF() }
                    val count = input.readInt()
                    require(count in 1..MAX_ENTRIES)
                    repeat(count) {
                        val ref = Ref(input.readUTF(), input.readUTF(), input.readUTF(), input.readLong())
                        require(
                            ref.source in sources && ref.name.isNotEmpty() &&
                                ref.name.toByteArray(Charsets.UTF_8).size <= 1024 &&
                                ref.size in 1..MAX_BLOB &&
                                ref.hash.matches(Regex("[a-f0-9]{64}"))
                        ) { "资源清单无效" }
                        refs.add(ref)
                    }
                    require(input.read() == -1)
                    Info(name, model, fromMyCar, vehicle, settings)
                }
                require(refs.map { it.source to it.name }.distinct().size == refs.size) { "资源名称重复" }
                val blobs = refs.groupBy { it.hash }
                require(
                    entries.map { it.name }.toSet() ==
                        blobs.keys.map { "blobs/$it" }.toSet() + "manifest.hcvp"
                ) { "资源目录与清单不一致" }
                var total = 0L
                for ((sha, aliases) in blobs) {
                    val size = aliases.first().size
                    require(aliases.all { it.size == size })
                    total += size
                    require(total <= MAX_BYTES) { "解压资源超过 1 GB" }
                    val entry = zip.getEntry("blobs/$sha")
                    require(entry.size == size)
                    val file = File(work, sha)
                    zip.getInputStream(entry).use { input ->
                        file.outputStream().use { output ->
                            var copied = 0L
                            val buffer = ByteArray(65_536)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                require(copied <= size)
                                output.write(buffer, 0, count)
                            }
                            require(copied == size)
                        }
                    }
                    require(digest(file) == sha) { "资源校验失败" }
                }
                refs.groupBy { it.source }.forEach { (source, rows) ->
                    writeGpak(File(directory, sources.getValue(source)), rows, work)
                }
                return info
            }
        } finally {
            work.deleteRecursively()
        }
    }

    private fun writeGpak(apk: File, refs: List<Ref>, blobs: File) {
        val raw = File(apk.parentFile, "${apk.name}.gpak")
        try {
            val names = refs.map { it.name.toByteArray(Charsets.UTF_8) }
            val nameStart = refs.size * 16L + 4L
            var dataStart = nameStart + names.sumOf { it.size.toLong() }
            val offsets = linkedMapOf<String, Long>()
            refs.forEach { ref ->
                if (ref.hash !in offsets) {
                    offsets[ref.hash] = dataStart
                    dataStart += ref.size
                }
            }
            require(dataStart < Int.MAX_VALUE)
            DataOutputStream(raw.outputStream().buffered()).use { output ->
                fun littleEndian(number: Long) = output.writeInt(Integer.reverseBytes(number.toInt()))
                var nameOffset = nameStart
                refs.forEachIndexed { index, ref ->
                    littleEndian(if (index == 0) refs.size.toLong() else index.toLong())
                    littleEndian(nameOffset)
                    littleEndian(names[index].size.toLong())
                    littleEndian(offsets.getValue(ref.hash))
                    nameOffset += names[index].size
                }
                littleEndian(0)
                names.forEach { output.write(it) }
                offsets.keys.forEach { hash ->
                    File(blobs, hash).inputStream().use { it.copyTo(output) }
                }
            }
            val crc = CRC32()
            raw.inputStream().use { input ->
                val buffer = ByteArray(65_536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    crc.update(buffer, 0, count)
                }
            }
            ZipOutputStream(apk.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(ModelRepository.GPak_ENTRY).apply {
                    method = ZipEntry.STORED
                    size = raw.length()
                    compressedSize = size
                    this.crc = crc.value
                })
                raw.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        } finally {
            raw.delete()
        }
    }
}
