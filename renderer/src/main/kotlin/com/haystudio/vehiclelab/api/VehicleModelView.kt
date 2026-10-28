package com.haystudio.vehiclelab.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.FrameLayout
import com.haystudio.vehiclelab.render.CarGLView
import com.haystudio.vehiclelab.render.CarRenderer
import java.util.concurrent.Executor

/** 可直接嵌入 View 或 Compose AndroidView 的车辆画面，不包含参数面板或调试面板。 */
class VehicleModelView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null):FrameLayout(context,attrs) {
    private val renderer=CarRenderer()
    private val surface=CarGLView(context,renderer)
    private val main=Handler(Looper.getMainLooper())
    val controller=VehicleModelController(renderer,Executor{surface.queueEvent(it)},Executor{main.post(it)})
    init {addView(surface,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))}
    /** 宿主在对应生命周期调用；EGL 上下文重建由渲染器恢复资源。 */
    fun resume()=surface.onResume()
    fun pause()=surface.onPause()
    fun setFrameRateLimit(rate:Int) {require(rate in listOf(0,30,60));surface.setTargetFrameRate(rate)}
}
