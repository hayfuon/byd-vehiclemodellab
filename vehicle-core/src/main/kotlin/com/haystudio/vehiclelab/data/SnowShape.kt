package com.haystudio.vehiclelab.data

import kotlin.math.*

/** 稳定种子的多频轮廓；顶点边界与片元抗锯齿使用相同的形状函数。 */
object SnowShape {
    const val SEGMENTS=24
    fun radius(angle:Float,seed:Float):Float = .72f+.13f*sin(angle*3f+seed*2.17f)+
        .08f*cos(angle*5f-seed*1.31f)+.035f*sin(angle*9f+seed*.73f)
    const val GLSL="""
        float snowRadius(float angle,float seed) {
            return .72+.13*sin(angle*3.0+seed*2.17)+.08*cos(angle*5.0-seed*1.31)+.035*sin(angle*9.0+seed*.73);
        }
    """
}
