package com.haystudio.vehiclelab.data

enum class VehicleCorner(val label:String,val token:String) {
    LEFT_FRONT("左前","LeftFront"),LEFT_REAR("左后","LeftBack"),
    RIGHT_FRONT("右前","RightFront"),RIGHT_REAR("右后","RightBack");
    val doorClip get()="Door_${token}_Clip"
    val windowClip get()="Window_${token}_Clip"
    companion object {
        fun fromClip(clip:String)=entries.firstOrNull {
            clip.equals(it.doorClip,true) || clip.equals(it.windowClip,true)
        }
    }
}
