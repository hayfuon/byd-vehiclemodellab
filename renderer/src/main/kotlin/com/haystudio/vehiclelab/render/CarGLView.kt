package com.haystudio.vehiclelab.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Choreographer
import com.haystudio.vehiclelab.data.FramePacer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.MotionEvent
import android.view.VelocityTracker
import com.haystudio.vehiclelab.data.WindowControl
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface
import kotlin.math.sqrt

/** 管理 EGL、帧率请求与触摸输入，双指缩放后重置单指基线以避免残留旋转。 */
class CarGLView(context: Context, private val carRenderer: CarRenderer) : GLSurfaceView(context) {

    private var targetFrameRate = 0
    private val framePacer=FramePacer()
    private var attached=false
    private var resumed=true
    private var callbackScheduled=false
    private val frameCallback=object:Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos:Long) {
            callbackScheduled=false
            if(!attached||!resumed)return
            if(framePacer.shouldRender(frameTimeNanos))requestRender()
            scheduleFrame()
        }
    }
    private fun scheduleFrame() {
        if(attached&&resumed&&!callbackScheduled) {
            callbackScheduled=true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }
    private fun stopFrames() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        callbackScheduled=false;framePacer.reset()
    }
    override fun onAttachedToWindow() {super.onAttachedToWindow();attached=true;framePacer.reset();scheduleFrame()}
    override fun onDetachedFromWindow() {attached=false;stopFrames();super.onDetachedFromWindow()}
    override fun onResume() {super.onResume();resumed=true;framePacer.reset();scheduleFrame()}
    override fun onPause() {resumed=false;stopFrames();super.onPause()}

    private val frameRateSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) { applyTargetFrameRate(holder.surface) }
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { applyTargetFrameRate(holder.surface) }
        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
    }

    private val singleDrag = SingleDragGate()
    private var pointerSpacing = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var velocityTracker: VelocityTracker? = null
    private var hadMultiplePointers = false
    private var windowTouchClip: String? = null
    private val windowPreview = WindowPreviewDrawable()
    private var windowPreviewProgress = 0f
    private var windowTouchStartY = 0f
    private var windowTouchStartProgress = 0f
    private var windowTouchTravelPixels = 180f
    private var windowExitSwipeCandidate = false
    private var exitedWindowBySwipe = false
    private var windowTouchMoved = false
    private var windowTouchAdjacent = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private val windowExitSwipeDistance = 24f * context.resources.displayMetrics.density
    private val windowTapSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val minPinchSpacing = 28f * context.resources.displayMetrics.density

    init {
        
        setWillNotDraw(false)
        foreground = windowPreview
        setEGLContextClientVersion(3)
        
        setEGLConfigChooser(MultisampleConfigChooser(4))
        setEGLWindowSurfaceFactory(SrgbWindowSurfaceFactory())
        setRenderer(carRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        holder.addCallback(frameRateSurfaceCallback)
    }

    fun setTargetFrameRate(rate: Int) {
        targetFrameRate = rate.takeIf { it == 30 || it == 60 } ?: 0
        framePacer.setRate(targetFrameRate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) post { applyTargetFrameRate(holder.surface) }
    }

    private fun applyTargetFrameRate(surface: Surface) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !surface.isValid) return
        if (targetFrameRate == 0) {

            surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            surface.setFrameRate(targetFrameRate.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS)
        } else {
            surface.setFrameRate(targetFrameRate.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                windowPreview.settlePending()
                velocityTracker?.recycle(); velocityTracker=VelocityTracker.obtain().also{it.addMovement(event)}
                hadMultiplePointers=false
                activePointerId = event.getPointerId(0)
                singleDrag.reset(event.x, event.y)
                touchDownX = event.x; touchDownY = event.y
                exitedWindowBySwipe = false
                pointerSpacing = 0f
                val inWindowPreset = carRenderer.isWindowPreset()
                windowTouchClip = if (inWindowPreset) carRenderer.windowTouchClipAt(event.x, event.y) else null
                windowTouchMoved = false
                windowTouchAdjacent = windowTouchClip != null &&
                    windowTouchClip != WindowControl.sideClip(carRenderer.selectedCameraPreset)
                val glassRegion = windowTouchClip?.let(carRenderer::windowTouchRegion)
                windowExitSwipeCandidate = inWindowPreset && windowTouchClip == null
                if (windowTouchClip != null) {
                    windowTouchStartY = event.y
                    windowTouchStartProgress = carRenderer.currentWindowProgress(windowTouchClip!!)
                    windowTouchTravelPixels = glassRegion?.height ?: 180f
                    windowPreviewProgress = windowTouchStartProgress
                    if (glassRegion != null) windowPreview.show(glassRegion, windowPreviewProgress)
                    carRenderer.setWindowDrag(windowTouchClip)
                } else if (!inWindowPreset) carRenderer.beginCameraGesture()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                hadMultiplePointers=true
                windowTouchClip?.let { carRenderer.setWindowDrag(null) }
                windowPreview.hide()
                windowTouchClip = null
                pointerSpacing = spacing(event).takeIf { it >= minPinchSpacing } ?: 0f
                velocityTracker?.clear()
                if (!carRenderer.isWindowPreset()) carRenderer.beginCameraGesture()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val upIndex = event.actionIndex
                
                val other = (0 until event.pointerCount).firstOrNull { it != upIndex }
                if (other != null) {
                    activePointerId = event.getPointerId(other)
                    singleDrag.resetAfterPinch(event.getX(other), event.getY(other))
                } else {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                velocityTracker?.clear()
                windowTouchClip?.let { carRenderer.setWindowDrag(null) }
                windowPreview.hide()
                windowTouchClip = null
                pointerSpacing = 0f
                carRenderer.notifyTouch()
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (event.pointerCount >= 2) {
                    val d = spacing(event)
                    if (carRenderer.isWindowPreset()) {
                        pointerSpacing = 0f
                    } else if (d < minPinchSpacing) {
                        pointerSpacing = 0f
                    } else if (pointerSpacing > 0f) {
                        
                        val target = CameraResponse.pinchDistance(carRenderer.distance, pointerSpacing, d)
                        carRenderer.zoomCamera(target - carRenderer.distance)
                        pointerSpacing = d
                    } else {
                        pointerSpacing = d
                    }
                } else {
                    val index = event.findPointerIndex(activePointerId)
                    if (index < 0) return true
                    val x = event.getX(index)
                    val y = event.getY(index)
                    if (windowTouchClip != null) {
                        val moved = kotlin.math.hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) >= windowTapSlop.toDouble()
                        if (moved) windowTouchMoved = true
                        run {
                            val value = WindowControl.progressForDrag(
                                windowTouchStartProgress, windowTouchStartY, y, windowTouchTravelPixels)
                            windowPreviewProgress = value
                            windowPreview.preview(value)
                        }
                    } else {
                        val delta = singleDrag.consume(x, y)
                        if (windowExitSwipeCandidate && !exitedWindowBySwipe &&
                            kotlin.math.hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) >= windowExitSwipeDistance.toDouble()) {
                            exitedWindowBySwipe = true
                            windowExitSwipeCandidate = false
                            carRenderer.takeOverWindowCamera()
                        }

                        if (delta != null && !carRenderer.isWindowPreset())
                            carRenderer.dragCameraPixels(delta.dx, delta.dy, height.toFloat())
                    }
                }
                carRenderer.notifyTouch()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasWindowTouch = windowTouchClip != null
                val adjacentPreset=if(event.actionMasked==MotionEvent.ACTION_UP && windowTouchAdjacent && !windowTouchMoved)
                    WindowControl.presetForClip(windowTouchClip!!) else null
                val releasedClip = windowTouchClip
                val releasedProgress = windowPreviewProgress
                val commit = event.actionMasked == MotionEvent.ACTION_UP && windowTouchMoved && releasedClip != null
                windowPreview.hide {
                    if (commit) carRenderer.setWindowDrag(releasedClip, releasedProgress)
                    carRenderer.setWindowDrag(null)
                    adjacentPreset?.let(carRenderer::selectCameraPreset)
                }
                windowTouchClip = null
                windowTouchAdjacent = false
                windowTouchMoved = false
                if(event.actionMasked==MotionEvent.ACTION_UP && !hadMultiplePointers && !wasWindowTouch &&
                    kotlin.math.hypot((event.x-touchDownX).toDouble(),(event.y-touchDownY).toDouble())<windowTapSlop)
                    carRenderer.tapTrunkButton(event.x,event.y)
                if(event.actionMasked==MotionEvent.ACTION_UP && !hadMultiplePointers && !wasWindowTouch && !carRenderer.isWindowPreset()) velocityTracker?.let { tracker ->
                    tracker.addMovement(event);tracker.computeCurrentVelocity(1000)
                    carRenderer.flingCameraPixels(tracker.getXVelocity(activePointerId), tracker.getYVelocity(activePointerId), height.toFloat())
                }
                velocityTracker?.recycle();velocityTracker=null
                activePointerId = MotionEvent.INVALID_POINTER_ID
                pointerSpacing = 0f
                windowExitSwipeCandidate = false
                exitedWindowBySwipe = false
                carRenderer.notifyTouch()
            }
        }
        return true
    }

    private fun spacing(e: MotionEvent): Float {
        if (e.pointerCount < 2) return 0f
        val dx = e.getX(0) - e.getX(1)
        val dy = e.getY(0) - e.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private class SrgbWindowSurfaceFactory : EGLWindowSurfaceFactory {
        override fun createWindowSurface(egl: EGL10, display: EGLDisplay,
            config: EGLConfig, nativeWindow: Any): EGLSurface {
            val surface = egl.eglCreateWindowSurface(display, config, nativeWindow,
                intArrayOf(0x309D, 0x3089, EGL10.EGL_NONE))
            if (surface != EGL10.EGL_NO_SURFACE) return surface
            
            egl.eglGetError()
            return egl.eglCreateWindowSurface(display, config, nativeWindow, null)
        }

        override fun destroySurface(egl: EGL10, display: EGLDisplay, surface: EGLSurface) {
            egl.eglDestroySurface(display, surface)
        }
    }

    private class MultisampleConfigChooser(private val samples: Int) :
        EGLConfigChooser {

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val msaa = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_SAMPLE_BUFFERS, 1,
                EGL10.EGL_SAMPLES, samples,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL10.EGL_NONE
            )
            choose(egl, display, msaa)?.let { return it }

            val plain = intArrayOf(
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 24,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
                EGL10.EGL_NONE
            )
            return choose(egl, display, plain)
                ?: throw IllegalArgumentException("没有可用的 EGL 配置")
        }

        private fun choose(egl: EGL10, display: EGLDisplay, attribs: IntArray): EGLConfig? {
            val configs = arrayOfNulls<EGLConfig>(64)
            val count = IntArray(1)
            if (!egl.eglChooseConfig(display, attribs, configs, configs.size, count)) return null
            if (count[0] <= 0) return null
            return configs[0]
        }

        private companion object {
            const val EGL_OPENGL_ES3_BIT = 0x0040
        }
    }
}
