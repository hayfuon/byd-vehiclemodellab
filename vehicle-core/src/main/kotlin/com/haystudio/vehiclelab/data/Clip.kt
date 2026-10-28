package com.haystudio.vehiclelab.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class AnimTrack(
    private val times: FloatArray,
    private val values: FloatArray,
    private val interpolation: IntArray = IntArray(times.size) { 1 },
    private val inTangents: FloatArray = FloatArray(times.size),
    private val outTangents: FloatArray = FloatArray(times.size),
    private val inWeights: FloatArray = FloatArray(times.size) { 0.333f },
    private val outWeights: FloatArray = FloatArray(times.size) { 0.333f },
    private val constantModes: IntArray = IntArray(times.size)
) {
    init {
        require(listOf(values.size, interpolation.size, inTangents.size, outTangents.size,
            inWeights.size, outWeights.size, constantModes.size).all { it == times.size })
    }

    fun sample(time: Float): Float {
        if (times.isEmpty()) return 0f
        if (time <= times[0]) return values[0]
        val last = times.size - 1
        if (time >= times[last]) return values[last]
        var lo = 0
        var hi = last
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= time) lo = mid else hi = mid
        }
        val span = times[hi] - times[lo]
        if (span <= 1e-6f) return values[lo]
        val f = (time - times[lo]) / span
        val left = values[lo]
        val right = values[hi]
        return when (interpolation[lo]) {
            0 -> if (constantModes[lo] == 0) left else right
            1 -> left + (right - left) * f
            else -> {
                val p1 = left + span * outTangents[lo] * outWeights[lo]
                val p2 = right - span * inTangents[hi] * inWeights[hi]
                val flatLeft = kotlin.math.abs(p1 - left) < 1e-6f
                val flatRight = kotlin.math.abs(right - p2) < 1e-6f
                val inv = 1f - f
                when {
                    flatLeft && flatRight -> left + (right - left) * f
                    flatLeft -> inv * inv * left + 2f * f * inv * p2 + f * f * right
                    flatRight -> inv * inv * left + 2f * f * inv * p1 + f * f * right
                    else -> inv * inv * inv * left + 3f * f * inv * inv * p1 +
                        3f * f * f * inv * p2 + f * f * f * right
                }
            }
        }
    }
}

class NodeClip(
    val name: String,
    val target: String,
    
    val duration: Float,
    private val pos: Array<AnimTrack?>,
    private val rot: Array<AnimTrack?>,
    private val scale: Array<AnimTrack?>
) {
    private val rest: FloatArray by lazy { localAt(0f) }
    private val restInv: FloatArray by lazy { GwmNodeTree.invert(rest) }

    fun localAt(time: Float): FloatArray {
        val tx = pos[0]?.sample(time) ?: 0f
        val ty = pos[1]?.sample(time) ?: 0f
        val tz = pos[2]?.sample(time) ?: 0f
        val rx = rot[0]?.sample(time) ?: 0f
        val ry = rot[1]?.sample(time) ?: 0f
        val rz = rot[2]?.sample(time) ?: 0f
        val sx = scale[0]?.sample(time) ?: 1f
        val sy = scale[1]?.sample(time) ?: 1f
        val sz = scale[2]?.sample(time) ?: 1f
        return trs(tx, ty, tz, rx, ry, rz, sx, sy, sz)
    }

    private val travelSamples: FloatArray by lazy {
        val result=FloatArray(257)
        var previous=localAt(0f)
        for(i in 1..256) {
            val current=localAt(duration*i/256f)
            val dx=current[12]-previous[12]; val dy=current[13]-previous[13]; val dz=current[14]-previous[14]
            result[i]=result[i-1]+kotlin.math.sqrt(dx*dx+dy*dy+dz*dz)
            previous=current
        }
        result
    }

    fun localAtTravel(progress: Float): FloatArray {
        val p=progress.coerceIn(0f,1f)
        if(p<=0f || p>=1f) return localAt(p*duration)
        val samples=travelSamples
        if(samples.last()<1e-5f) return localAt(p*duration)
        val target=p*samples.last()
        var lo=0; var hi=256
        while(hi-lo>1) { val mid=(lo+hi)/2; if(samples[mid]<target) lo=mid else hi=mid }
        val fraction=(target-samples[lo])/(samples[hi]-samples[lo]).coerceAtLeast(1e-8f)
        return localAt((lo+fraction)/256f*duration)
    }

    fun renamed(name: String, target: String): NodeClip = NodeClip(name, target, duration, pos, rot, scale)

    fun deltaAt(time: Float): FloatArray =
        GwmNodeTree.multiply(localAt(time), restInv)

    private fun trs(
        tx: Float, ty: Float, tz: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float
    ): FloatArray {

        val cz = cos(rz); val szn = sin(rz)
        val cx = cos(rx); val sxn = sin(rx)
        val cy = cos(ry); val syn = sin(ry)

        val rzM = floatArrayOf(
            cz, szn, 0f, 0f,
            -szn, cz, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        val rxM = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, cx, sxn, 0f,
            0f, -sxn, cx, 0f,
            0f, 0f, 0f, 1f
        )
        val ryM = floatArrayOf(
            cy, 0f, -syn, 0f,
            0f, 1f, 0f, 0f,
            syn, 0f, cy, 0f,
            0f, 0f, 0f, 1f
        )

        val r = GwmNodeTree.multiply(GwmNodeTree.multiply(rzM, ryM), rxM)
        val m = FloatArray(16)
        m[0] = r[0] * sx; m[1] = r[1] * sx; m[2] = r[2] * sx; m[3] = 0f
        m[4] = r[4] * sy; m[5] = r[5] * sy; m[6] = r[6] * sy; m[7] = 0f
        m[8] = r[8] * sz; m[9] = r[9] * sz; m[10] = r[10] * sz; m[11] = 0f
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
        return m
    }
}

object GwaParser {

    private const val MAGIC = 0x307f

    fun parse(bytes: ByteArray): List<NodeClip> {
        
        fun attempt(interleaved: Boolean) = runCatching { parseLayout(bytes, interleaved) }.getOrDefault(emptyList())
        return attempt(false).ifEmpty { attempt(true) }
    }

    private fun parseLayout(bytes: ByteArray, interleaved: Boolean): List<NodeClip> {
        if (bytes.size < 32) return emptyList()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.getInt(0) != MAGIC) return emptyList()
        var o = 4
        val nameLen = buf.getInt(o)
        if (nameLen !in 0..256 || o + 4 + nameLen > bytes.size) return emptyList()
        o += 4
        val name = string(bytes, o, nameLen)
        o += nameLen
        if (o + 12 > bytes.size) return emptyList()
        val durationMs = buf.getFloat(o)
        o += 8
        val targetCount = buf.getInt(o)
        o += 4
        if (targetCount !in 1..64) return emptyList()

        val anims = ArrayList<String>(targetCount)
        for (i in 0 until if (interleaved) 0 else targetCount) {
            val len = buf.getInt(o)
            if (len !in 0..256 || o + 4 + len > bytes.size) return emptyList()
            o += 4
            anims.add(string(bytes, o, len))
            o += len
        }
        val targets = ArrayList<String>(targetCount)
        for (i in 0 until if (interleaved) 0 else targetCount) {
            val len = buf.getInt(o)
            if (len !in 0..256 || o + 4 + len > bytes.size) return emptyList()
            o += 4
            targets.add(string(bytes, o, len))
            o += len
        }
        if (!interleaved) o += targetCount * 4
        if (o > bytes.size) return emptyList()

        val out = ArrayList<NodeClip>(targetCount)
        val duration = if (durationMs > 0f) durationMs / 1000f else 0f
        for (t in 0 until targetCount) {
            if (interleaved) {
                for (list in listOf(anims, targets)) {
                    val len = buf.getInt(o)
                    if (len !in 1..256 || o + 4 + len > bytes.size) return emptyList()
                    o += 4
                    list.add(string(bytes, o, len))
                    o += len
                }
                o += 4
            }
            val pos = arrayOfNulls<AnimTrack>(3)
            val rot = arrayOfNulls<AnimTrack>(3)
            val scale = arrayOfNulls<AnimTrack>(3)
            var truncated = false
            for (i in 0 until if (interleaved) 9 else 10) {
                if (o + 4 > bytes.size) {
                    truncated = true
                    break
                }
                val len = buf.getInt(o)
                
                if (len !in 0..32 || o + 4 + len > bytes.size) {
                    truncated = true
                    break
                }
                o += 4
                val trackName = string(bytes, o, len)
                o += len
                if (o + 4 > bytes.size) {
                    truncated = true
                    break
                }
                val keyCount = buf.getInt(o)
                o += 4
                if (keyCount !in 0..100_000 || o.toLong() + keyCount.toLong() * 40 > bytes.size) {
                    truncated = true
                    break
                }
                val times = FloatArray(keyCount)
                val values = FloatArray(keyCount)
                val incoming = FloatArray(keyCount)
                val outgoing = FloatArray(keyCount)
                val inWeights = FloatArray(keyCount)
                val outWeights = FloatArray(keyCount)
                val interpolation = IntArray(keyCount)
                val constants = IntArray(keyCount)
                for (k in 0 until keyCount) {
                    val key = o + k * 40
                    times[k] = buf.getFloat(key) / 1000f
                    values[k] = buf.getFloat(key + 4)
                    incoming[k] = buf.getFloat(key + 8) * 1000f
                    outgoing[k] = buf.getFloat(key + 12) * 1000f
                    inWeights[k] = buf.getFloat(key + 16)
                    outWeights[k] = buf.getFloat(key + 20)
                    interpolation[k] = buf.getInt(key + 24)
                    constants[k] = buf.getInt(key + 36)
                }
                o += keyCount * 40
                val track = AnimTrack(times, values, interpolation, incoming, outgoing,
                    inWeights, outWeights, constants)
                when (trackName) {
                    "Pos x" -> pos[0] = track
                    "Pos y" -> pos[1] = track
                    "Pos z" -> pos[2] = track
                    "Rot x" -> rot[0] = track
                    "Rot y" -> rot[1] = track
                    "Rot z" -> rot[2] = track
                    "Scale x" -> scale[0] = track
                    "Scale y" -> scale[1] = track
                    "Scale z" -> scale[2] = track
                }
            }
            if (truncated) return emptyList()
            val target = targets[t]
            if (target.isBlank()) continue
            out.add(
                NodeClip(
                    name.ifBlank { anims[t] },
                    target,
                    duration,
                    pos,
                    rot,
                    scale
                )
            )
        }
        return out
    }

    private fun string(bytes: ByteArray, offset: Int, len: Int): String =
        if (len <= 0) "" else String(bytes, offset, len, Charsets.UTF_8)
}
