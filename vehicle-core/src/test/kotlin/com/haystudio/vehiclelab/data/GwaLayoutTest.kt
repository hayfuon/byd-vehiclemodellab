package com.haystudio.vehiclelab.data
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
class GwaLayoutTest {
 @Test fun multipleTargetsCanStoreEachHeaderImmediatelyBeforeItsCurves() {
  val out=ByteArrayOutputStream()
  fun int(v:Int){out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())}
  fun float(v:Float)=int(v.toRawBits())
  fun text(v:String){val b=v.toByteArray();int(b.size);out.write(b)}
  int(0x307f);text("SunRoof");float(1000f);float(1000f);int(2)
  for(target in listOf("polySurface1","polySurface1.001")) {
   text(target);text(target);int(0)
   for(i in 0 until 9){text(if(i==0)"Pos x" else "");int(if(i==0)1 else 0)
    if(i==0){float(0f);float(if(target.endsWith("001"))20f else 10f);repeat(4){float(0f)};repeat(4){int(0)}}
   }
  }
  val clips=GwaParser.parse(out.toByteArray())
  assertEquals(listOf("polySurface1","polySurface1.001"),clips.map{it.target})
  assertEquals(10f,clips[0].localAt(0f)[12],0f)
  assertEquals(20f,clips[1].localAt(0f)[12],0f)
 }
}
