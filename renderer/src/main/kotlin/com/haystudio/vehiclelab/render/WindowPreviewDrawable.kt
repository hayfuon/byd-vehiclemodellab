package com.haystudio.vehiclelab.render

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Bitmap
import kotlin.math.ceil
import kotlin.math.floor
import android.graphics.Color
import android.graphics.RectF
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.haystudio.vehiclelab.data.WindowTouchRegion

internal class WindowPreviewDrawable : Drawable() {
    private var resolvedMask: Bitmap? = null
    private var resolvedPixels = IntArray(0)
    private var maskLeft = 0
    private var maskTop = 0
    private var maskWidth = 0
    private var maskHeight = 0
    private var apertureReady = false
    private var blockerDepth = FloatArray(0)
    private var aperture = BooleanArray(0)
    private var glassDepth = FloatArray(0)
    private var pixels = IntArray(0)
    var lastGeometryMillis = 0f
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination=RectF()
    private var sampleScale=3f
    private var region: WindowTouchRegion? = null
    private var progress = 0f
    private var strength = 0f
    private var transition: ValueAnimator? = null
    private var pendingCommit: (() -> Unit)? = null

    fun show(region: WindowTouchRegion, progress: Float) {
        settlePending()
        this.region = region
        apertureReady = false
        this.progress = progress.coerceIn(0f, 1f)
        rebuildOutline()
        
        strength=maxOf(strength,.94f)
        invalidateSelf()
        animateTo(1f)
    }

    fun preview(progress: Float) {
        val next=progress.coerceIn(0f,1f)
        if(next==this.progress) return
        this.progress = next
        rebuildOutline()
        invalidateSelf()
    }

    fun hide(commit: (() -> Unit)? = null) {
        pendingCommit = commit
        animateTo(0f)
    }

    fun settlePending() {
        transition?.cancel()
        transition = null
        val commit = pendingCommit
        pendingCommit = null
        commit?.invoke()
    }

    private fun animateTo(target: Float) {
        transition?.cancel()
        transition = ValueAnimator.ofFloat(strength, target).apply {
            duration = if(target>0f) 65L else 100L
            addUpdateListener {
                strength = it.animatedValue as Float
                invalidateSelf()
                if (it.animatedFraction >= 1f && target == 0f) {
                    val commit = pendingCommit
                    pendingCommit = null
                    commit?.invoke()
                }
            }
            start()
        }
    }

    private fun rasterize(triangles: List<FloatArray>, depth: FloatArray) {
        depth.fill(Float.POSITIVE_INFINITY)
        for (t in triangles) {
            val ax=t[0]; val ay=t[1]; val bx=t[2]; val by=t[3]; val cx=t[4]; val cy=t[5]
            val det=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
            if (kotlin.math.abs(det)<.01f) continue
            if (maxOf(ax,bx,cx)<maskLeft || minOf(ax,bx,cx)>maskLeft+maskWidth/sampleScale) continue
            val first=maxOf(0,ceil((minOf(ay,by,cy)-maskTop)*sampleScale-.5f).toInt())
            val last=minOf(maskHeight-1,floor((maxOf(ay,by,cy)-maskTop)*sampleScale-.5f).toInt())
            if (first>last) continue
            val za=t.getOrElse(6){0f}; val zb=t.getOrElse(7){0f}; val zc=t.getOrElse(8){0f}
            val gx=((by-cy)*(za-zc)+(cy-ay)*(zb-zc))/det
            val gy=((cx-bx)*(za-zc)+(ax-cx)*(zb-zc))/det
            for (row in first..last) {
                val y=maskTop+(row+.5f)/sampleScale
                var left=Float.POSITIVE_INFINITY
                var right=Float.NEGATIVE_INFINITY
                for (edge in 0..2) {
                    val next=(edge+1)%3
                    val x0=t[edge*2]; val y0=t[edge*2+1]
                    val x1=t[next*2]; val y1=t[next*2+1]
                    if ((y0<=y && y<y1)||(y1<=y && y<y0)) {
                        val x=x0+(y-y0)*(x1-x0)/(y1-y0)
                        left=minOf(left,x); right=maxOf(right,x)
                    }
                }
                if (!left.isFinite() || !right.isFinite()) continue
                val start=maxOf(0,ceil((left-maskLeft)*sampleScale-.5f).toInt())
                val end=minOf(maskWidth-1,floor((right-maskLeft)*sampleScale-.5f).toInt())
                var z=zc+gx*(maskLeft+(start+.5f)/sampleScale-cx)+gy*(y-cy)
                var index=row*maskWidth+start
                for (column in start..end) {
                    if (z<depth[index]) depth[index]=z
                    index++; z+=gx/sampleScale
                }
            }
        }
    }

    private fun rebuildOutline() {
        val r=region ?: return
        val started=System.nanoTime()
        if (!apertureReady) {
            val closed=r.overlayTriangles(0f)
            sampleScale=if((r.right-r.left)*(r.bottom-r.top)>240000f)2f else 3f
            maskLeft=floor(r.left).toInt()
            maskTop=floor(r.top).toInt()
            maskWidth=((ceil(r.right).toInt()-maskLeft)*sampleScale.toInt()).coerceAtLeast(1)
            maskHeight=((ceil(r.bottom).toInt()-maskTop)*sampleScale.toInt()).coerceAtLeast(1)
            val size=maskWidth*maskHeight
            blockerDepth=FloatArray(size)
            glassDepth=FloatArray(size)
            aperture=BooleanArray(size)
            pixels=IntArray(size)
            resolvedMask?.recycle()
            val samples=sampleScale.toInt()
            resolvedMask=Bitmap.createBitmap(maskWidth/samples,maskHeight/samples,Bitmap.Config.ARGB_8888)
            resolvedPixels=IntArray(maskWidth*maskHeight/(samples*samples))
            rasterize(r.overlayOccluders(),blockerDepth)
            rasterize(closed,glassDepth)
            for (i in 0 until size) aperture[i]=glassDepth[i].isFinite() && glassDepth[i]<=blockerDepth[i]+.0001f
            apertureReady=true
        }
        rasterize(r.overlayTriangles(progress),glassDepth)
        var first=maskHeight
        var last=0
        for (i in pixels.indices) {
            val visible=aperture[i] && glassDepth[i].isFinite() && glassDepth[i]<=blockerDepth[i]+.0001f
            pixels[i]=if(visible) -1 else 0
            if(visible) { val y=i/maskWidth; first=minOf(first,y); last=maxOf(last,y) }
        }
        for (y in first..last) {
            val f=(y-first).toFloat()/(last-first).coerceAtLeast(1)
            val color=Color.rgb((190-78*f).toInt(),(192-76*f).toInt(),(195-74*f).toInt())
            for(x in 0 until maskWidth) {
                val i=y*maskWidth+x
                if(pixels[i]!=0) pixels[i]=color
            }
        }
        val samples=sampleScale.toInt()
        val width=maskWidth/samples
        for(y in 0 until maskHeight/samples) for(x in 0 until width) {
            val i=y*samples*maskWidth+x*samples
            
            var n=0;var red=0;var green=0;var blue=0
            for(dy in 0 until samples) for(dx in 0 until samples) { val c=pixels[i+dy*maskWidth+dx]; if(c!=0) { n++;red+=Color.red(c);green+=Color.green(c);blue+=Color.blue(c) } }
            resolvedPixels[y*width+x]=if(n==0)0 else Color.argb(n*255/(samples*samples),red/n,green/n,blue/n)
        }
        resolvedMask!!.setPixels(resolvedPixels,0,width,0,0,width,maskHeight/samples)
        lastGeometryMillis=(System.nanoTime()-started)/1_000_000f
    }

    override fun draw(canvas: Canvas) {
        val bitmap=resolvedMask ?: return
        if(strength<=0f) return
        paint.alpha=(strength*255f).toInt()
        destination.set(maskLeft.toFloat(),maskTop.toFloat(),maskLeft+maskWidth/sampleScale,maskTop+maskHeight/sampleScale)
        canvas.drawBitmap(bitmap,null,destination,paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
