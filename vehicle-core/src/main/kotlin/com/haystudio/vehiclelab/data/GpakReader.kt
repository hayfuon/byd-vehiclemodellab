package com.haystudio.vehiclelab.data

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class GpakEntry(val name: String, val offset: Long, val size: Int)

/** 随机读取 ZIP Stored 条目中的 GPAK 索引，支持大小写别名和共享数据偏移。 */
class GpakReader private constructor(
    private val raf: RandomAccessFile,
    val apkPath: String,
    val entryName: String,
    private val entries: LinkedHashMap<String, GpakEntry>,
    val recordCount: Int
) : Closeable {

    internal var onRead: ((String, ByteArray) -> Unit)? = null

    val entryCount: Int get() = entries.size

    private val lowerIndex: Map<String, String> by lazy {
        val out = HashMap<String, String>(entries.size * 2)
        for (name in entries.keys) if (!out.containsKey(name.lowercase())) out[name.lowercase()] = name
        out
    }

    private fun entryOf(name: String): GpakEntry? {
        entries[name]?.let { return it }
        return lowerIndex[name.lowercase()]?.let { entries[it] }
    }

    fun names(): List<String> = entries.keys.toList()

    fun has(name: String): Boolean = entryOf(name) != null

    fun sizeOf(name: String): Int? = entryOf(name)?.size

    @Synchronized
    fun read(name: String): ByteArray? {
        val e = entryOf(name) ?: return null
        val out = ByteArray(e.size)
        raf.seek(e.offset)
        raf.readFully(out)
        onRead?.invoke(e.name, out)
        return out
    }

    override fun close() {
        try {
            raf.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val SIG_EOCD = 0x06054b50
        private const val SIG_CEN = 0x02014b50
        private const val SIG_LOC = 0x04034b50

        fun open(apkPath: String, entryName: String): GpakReader {
            val raf = RandomAccessFile(File(apkPath), "r")
            try {
                val located = locateApkEntry(raf, entryName)
                var records = 0
                val entries = readIndex(raf, located[0], located[1]) { records = it }
                return GpakReader(raf, apkPath, entryName, entries, records)
            } catch (t: Throwable) {
                try {
                    raf.close()
                } catch (_: Throwable) {
                }
                throw if (t is IOException) t else IOException("读取 $apkPath 失败", t)
            }
        }

        private fun locateApkEntry(raf: RandomAccessFile, entryName: String): LongArray {
            val fileLen = raf.length()
            val tailLen = minOf(fileLen, 70_000L).toInt()
            val tail = ByteArray(tailLen)
            raf.seek(fileLen - tailLen)
            raf.readFully(tail)

            var eocd = -1
            var i = tailLen - 22
            while (i >= 0) {
                if (le32(tail, i) == SIG_EOCD) {
                    val commentLen = le16(tail, i + 20)
                    if (i + 22 + commentLen <= tailLen) {
                        eocd = i
                        break
                    }
                }
                i--
            }
            if (eocd < 0) throw IOException("APK 中未找到 ZIP 目录（文件被截断？）")

            val cdSizeRaw = le32(tail, eocd + 12).toLong() and 0xFFFFFFFFL
            val cdOffRaw = le32(tail, eocd + 16).toLong() and 0xFFFFFFFFL
            if (cdOffRaw == 0xFFFFFFFFL || cdSizeRaw == 0xFFFFFFFFL) {
                throw IOException("ZIP64 目录暂不支持")
            }
            if (cdSizeRaw <= 0 || cdSizeRaw > 32L * 1024 * 1024) {
                throw IOException("ZIP 目录大小异常：$cdSizeRaw")
            }

            val cd = ByteArray(cdSizeRaw.toInt())
            raf.seek(cdOffRaw)
            raf.readFully(cd)

            var p = 0
            while (p + 46 <= cd.size) {
                if (le32(cd, p) != SIG_CEN) break
                val method = le16(cd, p + 10)
                val uncompSize = le32(cd, p + 24).toLong() and 0xFFFFFFFFL
                val nameLen = le16(cd, p + 28)
                val extraLen = le16(cd, p + 30)
                val commentLen = le16(cd, p + 32)
                val localOff = le32(cd, p + 42).toLong() and 0xFFFFFFFFL
                if (p + 46 + nameLen > cd.size) break
                val name = String(cd, p + 46, nameLen, Charsets.UTF_8)

                if (name == entryName) {
                    if (method != 0) {
                        throw IOException("$entryName 在 APK 内是压缩存放（method=$method），无法随机读取")
                    }
                    val lh = ByteArray(30)
                    raf.seek(localOff)
                    raf.readFully(lh)
                    if (le32(lh, 0) != SIG_LOC) throw IOException("ZIP 本地头校验失败")
                    val lNameLen = le16(lh, 26)
                    val lExtraLen = le16(lh, 28)
                    val dataOff = localOff + 30 + lNameLen + lExtraLen
                    if (uncompSize <= 0 || dataOff + uncompSize > fileLen) {
                        throw IOException("$entryName 数据区间越界")
                    }
                    return longArrayOf(dataOff, uncompSize)
                }
                p += 46 + nameLen + extraLen + commentLen
            }
            throw IOException("APK 内未找到 $entryName")
        }

        private fun readIndex(
            raf: RandomAccessFile,
            base: Long,
            entrySize: Long,
            onCount: (Int) -> Unit
        ): LinkedHashMap<String, GpakEntry> {
            val head = ByteArray(8)
            raf.seek(base)
            raf.readFully(head)
            val nameTableOff = le32(head, 4).toLong() and 0xFFFFFFFFL
            if (nameTableOff < 16 || nameTableOff > entrySize) {
                throw IOException("gpak 名称表偏移异常：$nameTableOff")
            }
            val count = (nameTableOff / 16).toInt()
            if (count <= 0 || count > 2_000_000) throw IOException("gpak 条目数异常：$count")
            onCount(count)

            val index = ByteArray(count * 16)
            raf.seek(base)
            raf.readFully(index)

            val nameOffs = LongArray(count)
            val nameLens = IntArray(count)
            val dataOffs = LongArray(count)
            var minDataOff = Long.MAX_VALUE
            for (i in 0 until count) {
                val o = i * 16
                nameOffs[i] = le32(index, o + 4).toLong() and 0xFFFFFFFFL
                nameLens[i] = le32(index, o + 8)
                dataOffs[i] = le32(index, o + 12).toLong() and 0xFFFFFFFFL
                
                if (dataOffs[i] != 0L && dataOffs[i] < minDataOff) minDataOff = dataOffs[i]
            }
            if (minDataOff <= nameTableOff || minDataOff > entrySize) {
                throw IOException("gpak 数据区偏移异常：$minDataOff")
            }

            val nameTableLen = (minDataOff - nameTableOff).toInt()
            val nameTable = ByteArray(nameTableLen)
            raf.seek(base + nameTableOff)
            raf.readFully(nameTable)

            val sorted = LongArray(count)
            var real = 0
            for (i in 0 until count) {
                if (dataOffs[i] != 0L) sorted[real++] = dataOffs[i]
            }

            val distinct = LongArray(real)
            for (i in 0 until real) distinct[i] = sorted[i]
            distinct.sort()
            var unique = 0
            for (i in 0 until real) {
                if (unique == 0 || distinct[unique - 1] != distinct[i]) distinct[unique++] = distinct[i]
            }
            val sizeAt = HashMap<Long, Long>(unique * 2)
            for (i in 0 until unique) {
                val end = if (i + 1 < unique) distinct[i + 1] else entrySize
                sizeAt[distinct[i]] = end - distinct[i]
            }

            val out = LinkedHashMap<String, GpakEntry>(count * 2)
            for (i in 0 until count) {
                if (dataOffs[i] == 0L) continue
                val no = nameOffs[i]
                val nl = nameLens[i]
                if (nl <= 0 || nl > 1024) continue
                if (no < nameTableOff || no + nl > minDataOff) continue
                val size = sizeAt[dataOffs[i]] ?: continue
                if (size <= 0 || size > Int.MAX_VALUE) continue
                val name = String(nameTable, (no - nameTableOff).toInt(), nl, Charsets.UTF_8)
                if (out.containsKey(name)) continue
                out[name] = GpakEntry(name, base + dataOffs[i], size.toInt())
            }
            if (out.isEmpty()) throw IOException("gpak 索引为空")
            return out
        }

        private fun le16(b: ByteArray, o: Int): Int =
            (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

        private fun le32(b: ByteArray, o: Int): Int =
            (b[o].toInt() and 0xFF) or
                ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16) or
                ((b[o + 3].toInt() and 0xFF) shl 24)
    }
}
