package com.haystudio.vehiclelab.data

/** 按单调时钟筛选同步帧；迟到后重建节奏，不补绘已经错过的帧。 */
class FramePacer(rate:Int=0) {
    private var period=0L
    private var next=-1L
    init {setRate(rate)}
    fun setRate(rate:Int) {period=if(rate==30||rate==60)1_000_000_000L/rate else 0L;reset()}
    fun reset() {next=-1L}
    fun shouldRender(now:Long):Boolean {
        if(period==0L)return true
        if(next>=0L && now+200_000L<next)return false
        next=if(next<0L || now-next>period*2)now+period else next+period
        return true
    }
}
