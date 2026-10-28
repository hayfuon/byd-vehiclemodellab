package com.haystudio.vehiclelab.data

enum class ChargingPort(
    val label: String,
    val clipSide: String,

    val socketLocal: FloatArray,
    val outwardLocal: FloatArray,
    
    val plugDepth: Float,
    val plugScale: Float,
    
    val cableRadius: Float
) {
    FAST("大快充", "Right", floatArrayOf(1.73043f, .89142f, -.86694f),
        floatArrayOf(.0668608f, .2039855f, -.9766881f), .090f, 1.24f, .0205f),
    SLOW("小慢充", "Right", floatArrayOf(1.66642f, .88545f, -.87263f),
        floatArrayOf(.0668608f, .2039855f, -.9766881f), .072f, .92f, .014f)
}
