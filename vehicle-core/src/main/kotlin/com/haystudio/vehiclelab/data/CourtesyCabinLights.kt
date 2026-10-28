package com.haystudio.vehiclelab.data

import kotlin.math.abs

enum class CabinReadingLamp(val label:String) {
    FRONT_LEFT("左前阅读灯"), FRONT_RIGHT("右前阅读灯"), REAR_LEFT("左后阅读灯"), REAR_RIGHT("右后阅读灯")
}

/** 顶棚阅读灯与行李厢照明共用环境响应，开度控制仅用于模拟灯光，不约束尾门动作。 */
object CourtesyCabinLights {
    fun sceneGain(period:DayPeriod)=when(period){DayPeriod.NOON->.23f;DayPeriod.MORNING->.58f;DayPeriod.NIGHT->1f}
    fun trunkTarget(progress:Float,enabled:Boolean):Float=if(enabled)smooth(.035f,.32f,progress) else 0f
    fun rearLens(x:Float):Float=smooth(.54f,.56f,x)
    fun frontLens(x:Float,y:Float,z:Float):Float =
        (1f-smooth(.069f,.074f,abs(x+.2105f)))*(1f-smooth(.025f,.030f,abs(abs(z)-.1625f)))*
        smooth(1.333f,1.341f,y)*(1f-smooth(1.377f,1.385f,y))
    private fun smooth(a:Float,b:Float,v:Float):Float {val x=((v-a)/(b-a)).coerceIn(0f,1f);return x*x*(3-2*x)}
}
