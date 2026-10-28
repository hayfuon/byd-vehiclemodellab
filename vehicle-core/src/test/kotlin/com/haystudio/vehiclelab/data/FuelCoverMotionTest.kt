package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 人工构造的左右镜像轨迹，不包含车型资源或实车坐标。 */
class FuelCoverMotionTest {
    private fun clip(left:Boolean):NodeClip {
        val out=ByteArrayOutputStream()
        fun int(value:Int) {out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())}
        fun float(value:Float)=int(value.toRawBits())
        fun text(value:String) {val bytes=value.toByteArray();int(bytes.size);out.write(bytes)}
        int(0x307f);text("SyntheticCover");float(1000f);float(1000f);int(1)
        text("SyntheticCover");text("SyntheticCover");int(0)
        val tracks=listOf("Pos x" to floatArrayOf(2f,2.4f),"Pos y" to floatArrayOf(1f,1.5f),
            "Pos z" to floatArrayOf(.5f,.8f),"Rot x" to floatArrayOf(.2f,.6f))
        repeat(10) {index ->
            val track=tracks.getOrNull(index)
            text(track?.first ?: "");int(if(track==null)0 else 2)
            if(track!=null)repeat(2) {key ->
                float(key*1000f)
                float(track.second[key]*(if(left && track.first in setOf("Pos y","Rot x"))-1f else 1f))
                repeat(2){float(0f)};repeat(2){float(1f/3f)}
                int(1);repeat(3){int(0)}
            }
        }
        return GwaParser.parse(out.toByteArray()).single()
    }
    @Test fun syntheticMirroredTracksPreservePositionAndRotationAtEveryStep() {
        val left=clip(true);val right=clip(false)
        for(frame in 0..96) {
            val a=left.localAt(frame/96f);val b=right.localAt(frame/96f)
            assertEquals(b[12],a[12],.00001f)
            assertEquals(-b[13],a[13],.00001f)
            assertEquals(b[14],a[14],.00001f)
            assertEquals(b[5],a[5],.00001f)
            assertEquals(-b[6],a[6],.00001f)
        }
    }
    @Test fun syntheticCoverDeltaIsIdentityAtRest() {
        for(left in listOf(false,true))assertArrayEquals(floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f),clip(left).deltaAt(0f),.00001f)
    }
    @Test fun syntheticCoverEndpointsHaveFiniteContinuousTransforms() {
        val clip=clip(false)
        assertEquals(1f,clip.duration,0f)
        assertArrayEquals(clip.localAt(0f),clip.localAt(.000001f),.00001f)
        assertEquals(2.4f,clip.localAt(1f)[12],.00001f)
        assertTrue(clip.localAt(1f).all(Float::isFinite))
    }
}
