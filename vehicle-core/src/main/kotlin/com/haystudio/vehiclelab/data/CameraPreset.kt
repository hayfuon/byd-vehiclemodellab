package com.haystudio.vehiclelab.data

enum class CameraPreset(val label:String) {
    DEFAULT("默认"), FRONT("正前"), REAR("正后"), LEFT("正左"), RIGHT("正右"),
    FRONT_LEFT("左前45°"), FRONT_RIGHT("右前45°"), REAR_LEFT("左后45°"), REAR_RIGHT("右后45°"),
    TOP("俯瞰"), DRIVER("驾驶"), CABIN("车内中央"), CABIN_LEFT_FRONT("车内左前"),
    CABIN_RIGHT_FRONT("车内右前"), CABIN_LEFT_REAR("车内左后"), CABIN_RIGHT_REAR("车内右后"), ISLAND("岛台"),
    WINDOW_LEFT_FRONT("左前车窗"), WINDOW_RIGHT_FRONT("右前车窗"),
    WINDOW_LEFT_REAR("左后车窗"), WINDOW_RIGHT_REAR("右后车窗"), SUNROOF_WINDOW("天窗");
    val interior get() = this == CABIN || name.startsWith("CABIN_") || this == ISLAND
    fun cabinEye(): FloatArray = when(this) {
        CABIN_LEFT_FRONT -> floatArrayOf(-.08f,1.13f,.37f)
        CABIN_RIGHT_FRONT -> floatArrayOf(-.08f,1.13f,-.37f)
        CABIN_LEFT_REAR -> floatArrayOf(.94f,1.16f,.38f)
        CABIN_RIGHT_REAR -> floatArrayOf(.94f,1.16f,-.38f)
        
        ISLAND -> floatArrayOf(.34f,1.02f,0f)
        else -> floatArrayOf(.70f,1.12f,0f)
    }
}
