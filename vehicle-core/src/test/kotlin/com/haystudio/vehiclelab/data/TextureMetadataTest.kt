package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class TextureMetadataTest {
    private val signature=byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)
    private fun chunk(kind:String,payload:ByteArray):ByteArray {
        val type=kind.toByteArray()
        val crc=CRC32().apply {update(type);update(payload)}.value.toInt()
        return ByteBuffer.allocate(payload.size+12).putInt(payload.size).put(type).put(payload).putInt(crc).array()
    }
    private fun ktxEntry(key:String,value:String):ByteArray {
        val payload=(key+"\u0000"+value+"\u0000").toByteArray()
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array()+payload+ByteArray((4-payload.size%4)%4)
    }
    private fun ktx(metadata:ByteArray,pixels:ByteArray):ByteArray {
        val header=ByteArray(64)
        byteArrayOf(0xab.toByte(),0x4b,0x54,0x58,0x20,0x31,0x31,0xbb.toByte(),0x0d,0x0a,0x1a,0x0a).copyInto(header)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(12,0x04030201).putInt(60,metadata.size)
        return header+metadata+pixels
    }
    @Test fun ktxPreservesRightsAndPixelDataWhileRemovingSourcePaths() {
        val rights=ktxEntry("Copyright","Copyright Example")+ktxEntry("License","CC-BY-4.0")+ktxEntry("Author","Example")
        val pixels=byteArrayOf(1,3,5,7)
        assertArrayEquals(ktx(rights,pixels),TextureMetadata.clean(ktx(rights+ktxEntry("SourcePath","/device/private"),pixels)))
    }
    @Test fun pngPreservesTextLicensesAndPixelsWhileRemovingPrivateFields() {
        val color=chunk("sRGB",byteArrayOf(0));val pixels=chunk("IDAT",byteArrayOf(1,2,3));val end=chunk("IEND",byteArrayOf())
        val copyright=chunk("tEXt","Copyright\u0000Copyright Example".toByteArray())
        val license=chunk("iTXt","License\u0000\u0000\u0000\u0000\u0000CC-BY-4.0".toByteArray())
        val author=chunk("zTXt","Author\u0000\u0000compressed".toByteArray())
        val rights=copyright+license+author
        val input=signature+color+rights+chunk("tEXt","SourcePath\u0000/device/private".toByteArray())+pixels+end
        assertArrayEquals(signature+color+rights+pixels+end,TextureMetadata.clean(input))
    }
    @Test fun exifKeepsArtistButErasesHardwareIdentity() {
        val exif=ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        exif.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8).putShort(2)
        exif.putShort(315).putShort(2).putInt(7).putInt(40)
        exif.putShort(272).putShort(2).putInt(7).putInt(48).putInt(0)
        exif.position(40);exif.put("Author\u0000".toByteArray());exif.position(48);exif.put("Device\u0000".toByteArray())
        val output=TextureMetadata.clean(signature+chunk("eXIf",exif.array())+chunk("IEND",byteArrayOf()))
        val text=output.toString(Charsets.ISO_8859_1)
        assertTrue(text.contains("Author"));assertFalse(text.contains("Device"))
    }
    @Test fun malformedMetadataIsRejected() {
        assertThrows(IllegalArgumentException::class.java){TextureMetadata.clean(signature+byteArrayOf(1))}
        assertThrows(IllegalArgumentException::class.java){TextureMetadata.clean(ktx(byteArrayOf(1,2,3,4),byteArrayOf(5)))}
    }
}
