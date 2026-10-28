package com.haystudio.vehiclelab.render

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils

/** 按需生成确定性平铺地面纹理；不依赖外部分发的纹理图片。 */
internal class GroundTextures {
    private val textures = HashMap<Int, Int>()
    fun texture(style: Int, normal: Boolean = false): Int {
        require(style in 1..6)
        return textures.getOrPut(style + if(normal)100 else 0) {
            val bitmap = generate(style, normal)
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            if(normal) GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            else GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_SRGB8_ALPHA8, bitmap, 0)
            bitmap.recycle()
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            if(GLES30.glGetString(GLES30.GL_EXTENSIONS)?.contains("GL_EXT_texture_filter_anisotropic")==true) {
                val max=FloatArray(1);GLES30.glGetFloatv(0x84FF,max,0)
                GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,0x84FE,minOf(4f,max[0]))
            }
            ids[0]
        }
    }

    private var maps:Pair<Int,com.haystudio.vehiclelab.data.GroundSurfacePattern.Maps>?=null
    private fun generate(style:Int,normal:Boolean):Bitmap {
        val pattern=maps?.takeIf{it.first==style}?.second ?: com.haystudio.vehiclelab.data.GroundSurfacePattern.generate(style).also{maps=style to it}
        return Bitmap.createBitmap(if(normal)pattern.normal else pattern.color,512,512,Bitmap.Config.ARGB_8888)
    }
    fun onContextCreated() = textures.clear()
    fun release() {
        if (textures.isNotEmpty()) GLES30.glDeleteTextures(textures.size, textures.values.toIntArray(), 0)
        textures.clear()
    }
}
