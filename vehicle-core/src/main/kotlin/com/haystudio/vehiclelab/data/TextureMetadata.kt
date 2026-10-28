package com.haystudio.vehiclelab.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** 保留版权及许可字段，仅移除已知的设备标识和本地路径；像素及压缩块保持不变。 */
internal object TextureMetadata {
    private val ktxSignature=byteArrayOf(0xab.toByte(),0x4b,0x54,0x58,0x20,0x31,0x31,0xbb.toByte(),0x0d,0x0a,0x1a,0x0a)
    private val pngSignature=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
    private val privateKeys=setOf("sourcepath","sourcefile","filepath","filename","originalpath","originalfilename",
        "device","deviceid","deviceserial","serialnumber","gps","location","hostcomputer","ktxwriter","ktxwriterscparams")
    private fun privateKey(key:String)=key.lowercase().filter(Char::isLetterOrDigit) in privateKeys

    fun clean(bytes:ByteArray):ByteArray = when {
        bytes.size>=64 && bytes.copyOfRange(0,12).contentEquals(ktxSignature)->cleanKtx(bytes)
        bytes.size>=8 && bytes.copyOfRange(0,8).contentEquals(pngSignature)->cleanPng(bytes)
        else->bytes
    }

    private fun cleanKtx(bytes:ByteArray):ByteArray {
        val endian=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        val order=when(endian) {0x04030201->ByteOrder.LITTLE_ENDIAN;0x01020304->ByteOrder.BIG_ENDIAN;else->throw IllegalArgumentException("KTX 字节序无效")}
        val buffer=ByteBuffer.wrap(bytes).order(order)
        val size=buffer.getInt(60)
        require(size>=0 && size<=bytes.size-64 && size%4==0) {"KTX 元数据长度无效"}
        val out=ByteArrayOutputStream()
        var at=64
        val end=64+size
        while(at<end) {
            require(end-at>=4)
            val count=buffer.getInt(at)
            require(count>0 && count<=end-at-4)
            val padding=(4-count%4)%4
            require(padding<=end-at-4-count)
            val start=at+4
            val nul=(start until start+count).firstOrNull {bytes[it]==0.toByte()} ?: throw IllegalArgumentException("KTX 元数据键无效")
            val key=String(bytes,start,nul-start,Charsets.UTF_8)
            if(!privateKey(key))out.write(bytes,at,4+count+padding)
            at+=4+count+padding
        }
        val metadata=out.toByteArray()
        if(metadata.size==size)return bytes
        val header=bytes.copyOfRange(0,64)
        ByteBuffer.wrap(header).order(order).putInt(60,metadata.size)
        return header+metadata+bytes.copyOfRange(end,bytes.size)
    }

    private fun cleanPng(bytes:ByteArray):ByteArray {
        val out=ByteArrayOutputStream();out.write(bytes,0,8)
        var at=8
        while(at<bytes.size) {
            require(bytes.size-at>=12)
            val count=ByteBuffer.wrap(bytes,at,4).int
            require(count>=0 && count<=bytes.size-at-12)
            val kind=String(bytes,at+4,4,Charsets.US_ASCII)
            when(kind) {
                "tEXt","iTXt","zTXt"->{
                    val start=at+8
                    val nul=(start until start+count).firstOrNull {bytes[it]==0.toByte()} ?: throw IllegalArgumentException("PNG 元数据键无效")
                    val key=String(bytes,start,nul-start,Charsets.ISO_8859_1)
                    if(!privateKey(key))out.write(bytes,at,count+12)
                }
                "eXIf"->{
                    val payload=cleanExif(bytes.copyOfRange(at+8,at+8+count))
                    val type=kind.toByteArray(Charsets.US_ASCII)
                    val crc=CRC32().apply {update(type);update(payload)}.value.toInt()
                    out.write(ByteBuffer.allocate(payload.size+12).putInt(payload.size).put(type).put(payload).putInt(crc).array())
                }
                else->out.write(bytes,at,count+12)
            }
            at+=count+12
        }
        return out.toByteArray()
    }

    private fun cleanExif(input:ByteArray):ByteArray {
        val bytes=input.copyOf()
        val base=if(bytes.size>=6 && String(bytes,0,6,Charsets.ISO_8859_1)=="Exif\u0000\u0000")6 else 0
        require(bytes.size-base>=8) {"EXIF 头不完整"}
        val order=when(String(bytes,base,2,Charsets.US_ASCII)) {"II"->ByteOrder.LITTLE_ENDIAN;"MM"->ByteOrder.BIG_ENDIAN;else->throw IllegalArgumentException("EXIF 字节序无效")}
        val buffer=ByteBuffer.wrap(bytes).order(order)
        require(buffer.getShort(base+2).toInt()==42)
        val visited=hashSetOf<Int>()
        val sensitive=setOf(0x010d,0x010f,0x0110,0x0131,0x013c,0x8825,0x927c,0xa420,0xa430,0xa431,0xa433,0xa434,0xa435)
        fun offset(value:Int):Int {require(value>=0 && value<=bytes.size-base);return base+value}
        fun visit(raw:Int,erase:Boolean=false) {
            if(raw==0)return
            val at=offset(raw)
            require(visited.add(at) && visited.size<=64 && bytes.size-at>=2) {"EXIF 目录无效"}
            val count=buffer.getShort(at).toInt() and 65535
            require(count<=4096 && count.toLong()*12+6<=bytes.size-at)
            val kept=ArrayList<ByteArray>()
            for(i in 0 until count) {
                val entry=at+2+i*12
                val tag=buffer.getShort(entry).toInt() and 65535
                val type=buffer.getShort(entry+2).toInt() and 65535
                val elements=buffer.getInt(entry+4).toLong() and 0xffffffffL
                val stride=when(type) {1,2,6,7->1;3,8->2;4,9,11->4;5,10,12->8;else->0}
                val size=elements*stride
                require(size<=bytes.size)
                val remove=erase || tag in sensitive
                val nested=tag in setOf(0x8769,0x8825,0xa005)
                if(nested)visit(buffer.getInt(entry+8),remove)
                if(remove) {
                    if(size>4 && !nested) {
                        val start=offset(buffer.getInt(entry+8));require(size<=bytes.size-start)
                        bytes.fill(0,start,start+size.toInt())
                    }
                } else kept+=bytes.copyOfRange(entry,entry+12)
            }
            val next=buffer.getInt(at+2+count*12)
            if(next!=0)visit(next,erase)
            bytes.fill(0,at,at+6+count*12)
            buffer.putShort(at,kept.size.toShort())
            kept.forEachIndexed {index,entry->entry.copyInto(bytes,at+2+index*12)}
            buffer.putInt(at+2+kept.size*12,if(erase)0 else next)
        }
        visit(buffer.getInt(base+4))
        return bytes
    }
}
