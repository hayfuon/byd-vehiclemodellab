package com.haystudio.vehiclelab

/** 只累计前台等待时间；资源读取与渲染初始化分别拥有完整的等待预算。 */
internal class ModelLoadDeadline(private val timeoutMillis:Long=45_000L) {
    private var remaining=timeoutMillis
    private var started:Long?=null
    private var pending=false
    fun begin(now:Long,active:Boolean) { remaining=timeoutMillis;pending=true;started=if(active)now else null }
    fun resume(now:Long) { if(pending && started==null)started=now }
    fun pause(now:Long) { started?.let { remaining-=(now-it).coerceAtLeast(0) };started=null }
    fun finish() { pending=false;started=null }
    fun expired(now:Long):Boolean= pending && started?.let { now-it>=remaining }==true
}
