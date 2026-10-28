package com.haystudio.vehiclelab.render

/** 跨线程发布的不可变调试样本；不可采集的指标保留为空值。 */
data class RenderDebugSnapshot(
    val sampleNanos: Long = 0L,
    val fps: Float = 0f,
    val frameP95Millis: Double = 0.0,
    val submissionMillis: Double = 0.0,
    val renderCpuMillis: Double = 0.0,
    val sceneMillis: Double = 0.0,
    val carMillis: Double = 0.0,
    val lightMillis: Double = 0.0,
    val weatherMillis: Double = 0.0,
    val postMillis: Double = 0.0,
    val appPssMiB: Double = Double.NaN,
    val javaHeapMiB: Double = Double.NaN,
    val nativeHeapMiB: Double = Double.NaN,
    val graphicsPssMiB: Double? = null,
    val deviceUsedMiB: Double = Double.NaN,
    val deviceTotalMiB: Double = Double.NaN,
    val processCpuPercent: Double? = null,
    val meshes: Int = 0,
    val triangles: Int = 0,
    val textures: Int = 0,
    val shaders: Int = 0,
    val geometryMiB: Double = 0.0
)
