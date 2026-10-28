package com.haystudio.vehiclelab.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.GLUtils
import android.opengl.GLES30

enum class PlateStyle(val base: FloatArray, val text: FloatArray, val label: String, val sample: String) {
    BLUE(floatArrayOf(0f,82f/255f,180f/255f), floatArrayOf(.97f,.98f,.98f),"蓝牌","京A12345"),
    GREEN(floatArrayOf(53f/255f,185f/255f,99f/255f), floatArrayOf(.018f,.024f,.020f),"新能源","测AF88888"),
    WHITE(floatArrayOf(.95f,.96f,.96f), floatArrayOf(.045f,.05f,.05f),"白牌","京A12345"),
    YELLOW(floatArrayOf(1f,202f/255f,0f), floatArrayOf(.045f,.05f,.035f),"黄牌","京A12345")
}

data class PlateAppearance(val style: PlateStyle = PlateStyle.GREEN,
    val text: String = style.sample,
    val base: FloatArray = style.base, val textColor: FloatArray = style.text,
    val textScale: Float = .9f, val characterSpacing: Float = 1f,
    val image: ByteArray? = null, val cropImage: Boolean = false)

data class PlateSurface(val metallic: Float, val roughness: Float, val clearCoat: FloatArray, val envSpecular: Float)
object PlateLighting {
    fun surface() = PlateSurface(0f, .20f, floatArrayOf(.95f, .12f), 1.12f)
}

object LicensePlateLayout {
    const val EXPERIMENT_TEXT = "京A12345"
    private val glyphs = buildMap {
        for (i in 0..9) put(('0'.code + i).toChar(), i)
        for (i in 0 until 26) put(('A'.code + i).toChar(), 10 + i)
        put('京', 36)
    }
    /** 字形只做等比缩放，避免将图集或备用字体压成槽位的宽高比例。 */
    fun fitGlyph(sourceWidth:Float,sourceHeight:Float,width:Float,height:Float):FloatArray {
        require(listOf(sourceWidth,sourceHeight,width,height).all {it.isFinite() && it>0f})
        val scale=minOf(width/sourceWidth,height/sourceHeight)
        return floatArrayOf(sourceWidth*scale,sourceHeight*scale)
    }
    /** 按实际笔画边界统一高度与净字距，分隔标志单独预留空间。 */
    fun characterBoxes(aspects:List<Float>,width:Float,height:Float,separator:Float,spacing:Float):List<FloatArray> {
        if(aspects.isEmpty())return emptyList()
        require(aspects.all {it.isFinite() && it>0f})
        val minimumGap=14f*spacing.coerceIn(.7f,1.25f)
        val marker=if(aspects.size>2)separator else 0f
        val h=minOf(height,(width-marker-minimumGap*(aspects.size-1))/aspects.sum()).coerceAtLeast(1f)
        val spare=(width-marker-aspects.sum()*h-minimumGap*(aspects.size-1)).coerceAtLeast(0f)
        val gap=minimumGap+if(aspects.size>1)spare*.9f*spacing.coerceIn(.7f,1.1f)/(aspects.size-1) else 0f
        val used=aspects.sum()*h+gap*(aspects.size-1)+marker
        var x=(width-used)*.5f
        return aspects.mapIndexed { index,aspect ->
            if(index==2)x+=marker
            floatArrayOf(x,(350f-h)*.5f,aspect*h,h).also {x+=aspect*h+gap}
        }
    }
    fun glyphs(text: String): List<Int> = text.map { c ->
        glyphs[c] ?: throw IllegalArgumentException("原厂车牌图集不含字符：$c")
    }
}

data class PlateAnchor(val center:FloatArray,val normal:FloatArray,val lateral:FloatArray,val up:FloatArray,val width:Float,val height:Float) {
    fun uv(x: Float, y: Float, z: Float): FloatArray {
        val dx = x - center[0]; val dy = y - center[1]; val dz = z - center[2]
        return floatArrayOf(
            .5f + (dx * lateral[0] + dy * lateral[1] + dz * lateral[2]) / width,
            .5f - (dx * up[0] + dy * up[1] + dz * up[2]) / height
        )
    }
    companion object {
        fun upFromNormal(normal:FloatArray):FloatArray { var v=floatArrayOf(normal[1],-normal[0],0f);if(v[1]<0f)v=floatArrayOf(-v[0],-v[1],0f);val l=kotlin.math.sqrt(v[0]*v[0]+v[1]*v[1]).coerceAtLeast(1e-5f);return floatArrayOf(v[0]/l,v[1]/l,0f) }

        fun fromEndFace(positions: FloatArray, normals: FloatArray, rear: Boolean): PlateAnchor? {
            if (positions.size < 9 || normals.size != positions.size) return null
            var nx = 0f; var ny = 0f; var nz = 0f
            for (i in normals.indices step 3) { nx += normals[i]; ny += normals[i + 1]; nz += normals[i + 2] }
            val length = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
            nx /= length; ny /= length; nz /= length
            if (if (rear) nx < 0f else nx > 0f) { nx = -nx; ny = -ny; nz = -nz }
            val normal = floatArrayOf(nx, ny, nz)
            val lateral = floatArrayOf(0f, 0f, if (rear) -1f else 1f)
            val up = upFromNormal(normal)
            var nMin = Float.MAX_VALUE; var nMax = -Float.MAX_VALUE
            var lMin = Float.MAX_VALUE; var lMax = -Float.MAX_VALUE
            var uMin = Float.MAX_VALUE; var uMax = -Float.MAX_VALUE
            for (i in positions.indices step 3) {
                val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
                val n = x * normal[0] + y * normal[1] + z * normal[2]
                val l = z * lateral[2]
                val u = x * up[0] + y * up[1] + z * up[2]
                nMin = minOf(nMin, n); nMax = maxOf(nMax, n)
                lMin = minOf(lMin, l); lMax = maxOf(lMax, l)
                uMin = minOf(uMin, u); uMax = maxOf(uMax, u)
            }
            val n = (nMin + nMax) * .5f
            val l = (lMin + lMax) * .5f
            val u = (uMin + uMax) * .5f
            val center = floatArrayOf(
                normal[0] * n + lateral[0] * l + up[0] * u,
                normal[1] * n + lateral[1] * l + up[1] * u,
                normal[2] * n + lateral[2] * l + up[2] * u
            )
            return PlateAnchor(center, normal, lateral, up, lMax - lMin, uMax - uMin)
        }
    }
}

class LicensePlateLayer {
    private var texture = 0
    private var appearanceKey = ""
    private var imageSource: ByteArray? = null
    private var imageHash = 0

    fun onContextCreated() { texture = 0; appearanceKey = "" }

    fun texture(png: ByteArray?, appearance: PlateAppearance): Int {
        if (png == null) return 0
        if(imageSource !== appearance.image) { imageSource=appearance.image;imageHash=appearance.image?.contentHashCode() ?: 0 }
        val key = appearance.text + appearance.style.name + appearance.base.joinToString() + appearance.textColor.joinToString() + appearance.textScale + appearance.characterSpacing + imageHash + appearance.cropImage
        if (texture != 0 && appearanceKey == key) return texture
        val atlas = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return 0
        val bitmap = drawPlate(atlas, appearance)
        atlas.recycle()
        if (texture == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            texture = ids[0]
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        appearanceKey = key
        return texture
    }

    private fun drawPlate(atlas: Bitmap, appearance: PlateAppearance): Bitmap {
        val glyphs = appearance.text.take(12).map { runCatching { LicensePlateLayout.glyphs(it.toString()).first() }.getOrDefault(-1) }
        val result = Bitmap.createBitmap(1100, 350, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun color(rgb: FloatArray, gain: Float = 1f): Int = Color.rgb(
            (rgb[0] * gain * 255f).toInt().coerceIn(0,255),
            (rgb[1] * gain * 255f).toInt().coerceIn(0,255),
            (rgb[2] * gain * 255f).toInt().coerceIn(0,255))
        val base = color(appearance.base)
        val text = color(appearance.textColor)
        appearance.image?.let { bytes ->
            val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            if(image!=null) {
                canvas.drawColor(base)
                val factor=if(appearance.cropImage) maxOf(1100f/image.width,350f/image.height) else minOf(1100f/image.width,350f/image.height)
                val w=image.width*factor;val h=image.height*factor
                canvas.drawBitmap(image,null,RectF((1100-w)/2,(350-h)/2,(1100+w)/2,(350+h)/2),paint)
                image.recycle()
                return result
            }
        }
        canvas.drawColor(base)
        val top = if (appearance.style == PlateStyle.GREEN) color(FloatArray(3) { appearance.base[it]*.18f+.82f }) else base
        val bottom = base
        paint.shader = LinearGradient(0f, 0f, 0f, 350f, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,1100f,350f,paint)
        
        paint.shader = null
        paint.color = text
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawRoundRect(RectF(20f,17f,1080f,333f), 15f,15f,paint)
        paint.style = Paint.Style.FILL

        val pixels = IntArray(atlas.width * atlas.height)
        atlas.getPixels(pixels,0,atlas.width,0,0,atlas.width,atlas.height)
        val rgb = text and 0x00ffffff
        for (i in pixels.indices) pixels[i] = (pixels[i] and -0x1000000) or rgb
        val colored = Bitmap.createBitmap(pixels,atlas.width,atlas.height,Bitmap.Config.ARGB_8888)
        val cw = atlas.width / 10
        val ch = atlas.height / 10
        val dotWidth = if (appearance.style == PlateStyle.GREEN) 52f else 38f
        val left = 65f
        val sources=glyphs.mapIndexed { i,g ->
            if(g>=0) {
                val cellX=(g%10)*cw;val cellY=(g/10)*ch
                var minX=cw;var minY=ch;var maxX=-1;var maxY=-1
                for(y in 0 until ch)for(x in 0 until cw) {
                    if((pixels[(cellY+y)*atlas.width+cellX+x] ushr 24)>24) {
                        minX=minOf(minX,x);minY=minOf(minY,y);maxX=maxOf(maxX,x);maxY=maxOf(maxY,y)
                    }
                }
                if(maxX>=minX)Rect(cellX+minX,cellY+minY,cellX+maxX+1,cellY+maxY+1)
                else Rect(cellX,cellY,cellX+cw,cellY+ch)
            } else {
                paint.typeface=android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD)
                paint.textSize=200f;paint.textAlign=Paint.Align.LEFT
                Rect().also {paint.getTextBounds(appearance.text[i].toString(),0,1,it)}
            }
        }
        val boxes=LicensePlateLayout.characterBoxes(sources.map {it.width().coerceAtLeast(1).toFloat()/it.height().coerceAtLeast(1)},
            970f,190f*appearance.textScale.coerceIn(.65f,1.15f),dotWidth,appearance.characterSpacing)
        for((i,g)in glyphs.withIndex()) {
            val src=sources[i];val box=boxes[i]
            val dst=RectF(left+box[0],box[1],left+box[0]+box[2],box[1]+box[3])
            if(g>=0)canvas.drawBitmap(colored,src,dst,paint) else {
                val scale=dst.height()/src.height().coerceAtLeast(1)
                val save=canvas.save()
                canvas.translate(dst.left,dst.top)
                canvas.scale(scale,scale)
                canvas.drawText(appearance.text[i].toString(),-src.left.toFloat(),-src.top.toFloat(),paint)
                canvas.restoreToCount(save)
            }
        }
        if (glyphs.size > 2) {
            val cx=left+(boxes[1][0]+boxes[1][2]+boxes[2][0])*.5f
            if (appearance.style == PlateStyle.GREEN) {
                
                val cy=185f
                val ring=RectF(cx-22f,cy-22f,cx+22f,cy+22f)
                paint.style=Paint.Style.STROKE
                paint.strokeWidth=5f
                paint.color=Color.rgb(36,139,84)
                canvas.drawArc(ring,20f,155f,false,paint)
                paint.color=Color.rgb(32,131,185)
                canvas.drawArc(ring,180f,135f,false,paint)
                paint.color=Color.rgb(221,187,49)
                canvas.drawArc(ring,320f,50f,false,paint)
                paint.style=Paint.Style.FILL
                paint.color=Color.rgb(30,125,75)
                val e=Path().apply {
                    moveTo(cx-10f,cy-13f);lineTo(cx+11f,cy-13f)
                    lineTo(cx+8f,cy-7f);lineTo(cx-4f,cy-7f)
                    lineTo(cx-6f,cy-3f);lineTo(cx+7f,cy-3f)
                    lineTo(cx+4f,cy+3f);lineTo(cx-8f,cy+3f)
                    lineTo(cx-10f,cy+8f);lineTo(cx+5f,cy+8f)
                    lineTo(cx+2f,cy+14f);lineTo(cx-18f,cy+14f);close()
                }
                canvas.drawPath(e,paint)
                paint.color=text
            } else canvas.drawCircle(cx,185f,9f,paint)
        }
        colored.recycle()
        
        paint.color = Color.rgb(105,112,120)
        for (x in listOf(155f,945f)) {
            canvas.drawCircle(x,37f,9f,paint)
            paint.color = Color.rgb(195,201,208)
            canvas.drawCircle(x-1f,35f,5f,paint)
            paint.color = Color.rgb(105,112,120)
        }
        return result
    }

    fun release() {
        if (texture != 0) GLES30.glDeleteTextures(1,intArrayOf(texture),0)
        onContextCreated()
    }
}
