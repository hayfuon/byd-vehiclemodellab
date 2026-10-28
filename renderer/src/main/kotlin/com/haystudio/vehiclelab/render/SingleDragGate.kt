package com.haystudio.vehiclelab.render

internal class SingleDragGate {
    data class Delta(val dx: Float, val dy: Float)
    private var x = 0f
    private var y = 0f
    private var rebase = false

    fun reset(x: Float, y: Float) {
        this.x = x
        this.y = y
        rebase = false
    }

    fun resetAfterPinch(x: Float, y: Float) {
        reset(x, y)
        rebase = true
    }

    fun consume(x: Float, y: Float): Delta? {
        val delta = if (rebase) null else Delta(x - this.x, y - this.y)
        this.x = x
        this.y = y
        rebase = false
        return delta
    }
}
