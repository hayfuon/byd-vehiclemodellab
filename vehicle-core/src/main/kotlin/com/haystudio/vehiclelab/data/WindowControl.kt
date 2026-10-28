package com.haystudio.vehiclelab.data

object WindowControl {
    fun progressForDrag(startProgress: Float, startY: Float, currentY: Float, travelPixels: Float = 180f): Float {
        val travel = travelPixels.takeIf { it.isFinite() && it > 1f } ?: 180f
        return (startProgress + (currentY - startY) / travel).coerceIn(0f, 1f)
    }

    fun sideClip(preset: CameraPreset): String? = when (preset) {
        CameraPreset.WINDOW_LEFT_FRONT -> "Window_LeftFront_Clip"
        CameraPreset.WINDOW_RIGHT_FRONT -> "Window_RightFront_Clip"
        CameraPreset.WINDOW_LEFT_REAR -> "Window_LeftBack_Clip"
        CameraPreset.WINDOW_RIGHT_REAR -> "Window_RightBack_Clip"
        CameraPreset.SUNROOF_WINDOW -> "SunRoof_Window"
        else -> null
    }

    fun sideClips(preset: CameraPreset): List<String> = when (preset) {
        CameraPreset.WINDOW_LEFT_FRONT -> listOf("Window_LeftFront_Clip", "Window_LeftBack_Clip")
        CameraPreset.WINDOW_LEFT_REAR -> listOf("Window_LeftBack_Clip", "Window_LeftFront_Clip")
        CameraPreset.WINDOW_RIGHT_FRONT -> listOf("Window_RightFront_Clip", "Window_RightBack_Clip")
        CameraPreset.WINDOW_RIGHT_REAR -> listOf("Window_RightBack_Clip", "Window_RightFront_Clip")
        CameraPreset.SUNROOF_WINDOW -> listOf("SunRoof_Window")
        else -> emptyList()
    }

    fun presetForClip(clip: String): CameraPreset? = when (clip) {
        "Window_LeftFront_Clip" -> CameraPreset.WINDOW_LEFT_FRONT
        "Window_LeftBack_Clip" -> CameraPreset.WINDOW_LEFT_REAR
        "Window_RightFront_Clip" -> CameraPreset.WINDOW_RIGHT_FRONT
        "Window_RightBack_Clip" -> CameraPreset.WINDOW_RIGHT_REAR
        "SunRoof_Window" -> CameraPreset.SUNROOF_WINDOW
        else -> null
    }

}

class WindowTouchRegion private constructor(
    private val bounds: FloatArray,
    private val triangles: List<FloatArray> = emptyList(),
    private val polygon: List<Pair<Float, Float>> = emptyList(),
    private val occluders: () -> List<FloatArray> = { emptyList() },
    private val preview: ((Float) -> List<FloatArray>)? = null
) {
    constructor(left: Float, top: Float, right: Float, bottom: Float) :
        this(floatArrayOf(left, top, right, bottom))

    constructor(points: List<Pair<Float, Float>>) : this(floatArrayOf(
        points.minOfOrNull { it.first } ?: 0f,
        points.minOfOrNull { it.second } ?: 0f,
        points.maxOfOrNull { it.first } ?: 0f,
        points.maxOfOrNull { it.second } ?: 0f
    ), polygon = convexHull(points))

    constructor(projectedTriangles: List<FloatArray>, triangleMarker: Unit = Unit, occluders: List<FloatArray> = emptyList()) :
        this(triangleBounds(projectedTriangles), triangles = projectedTriangles, occluders = { occluders })

    constructor(projectedTriangles: List<FloatArray>, occluders: () -> List<FloatArray>) :
        this(triangleBounds(projectedTriangles), triangles = projectedTriangles, occluders = occluders)

    constructor(projectedTriangles: List<FloatArray>, occluders: () -> List<FloatArray>, preview: (Float) -> List<FloatArray>) :
        this(triangleBounds(projectedTriangles), triangles = projectedTriangles, occluders = occluders, preview = preview)

    val left get() = bounds[0]
    val top get() = bounds[1]
    val right get() = bounds[2]
    val bottom get() = bounds[3]

    fun contains(x: Float, y: Float): Boolean =
        x.isFinite() && y.isFinite() && x in left..right && y in top..bottom &&
            if (triangles.isNotEmpty()) triangles.any { t -> pointInTriangle(x, y, t) && occluders().none { o ->
                pointInTriangle(x, y, o) && depthAt(x, y, o) < depthAt(x, y, t) - .0001f
            } }
            else polygon.size < 3 || polygon.indices.all { i ->
                val a = polygon[i]
                val b = polygon[(i + 1) % polygon.size]
                (b.first - a.first) * (y - a.second) - (b.second - a.second) * (x - a.first) >= -0.5f
            }

    fun overlayTriangles(progress: Float): List<FloatArray> = preview?.invoke(progress.coerceIn(0f, 1f)) ?: triangles
    fun overlayOccluders(): List<FloatArray> = occluders()

    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

private fun triangleBounds(triangles: List<FloatArray>): FloatArray {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    for (triangle in triangles) for (i in 0 until minOf(6, triangle.size) - 1 step 2) {
        val x = triangle[i]; val y = triangle[i + 1]
        if (x.isFinite() && y.isFinite()) {
            left = minOf(left, x); top = minOf(top, y)
            right = maxOf(right, x); bottom = maxOf(bottom, y)
        }
    }
    return if (left.isFinite()) floatArrayOf(left, top, right, bottom) else floatArrayOf(0f, 0f, 0f, 0f)
}

private fun pointInTriangle(x: Float, y: Float, t: FloatArray): Boolean {
    if (t.size < 6) return false
    val ax=t[0]; val ay=t[1]; val bx=t[2]; val by=t[3]; val cx=t[4]; val cy=t[5]
    val d=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
    if (kotlin.math.abs(d) < .01f) return false
    val a=((by-cy)*(x-cx)+(cx-bx)*(y-cy))/d
    val b=((cy-ay)*(x-cx)+(ax-cx)*(y-cy))/d
    val c=1f-a-b
    return a >= -.003f && b >= -.003f && c >= -.003f
}

private fun convexHull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    val sorted = points.filter { it.first.isFinite() && it.second.isFinite() }
        .distinct().sortedWith(compareBy<Pair<Float, Float>> { it.first }.thenBy { it.second })
    if (sorted.size <= 2) return sorted
    fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>) =
        (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
    val lower = mutableListOf<Pair<Float, Float>>()
    for (p in sorted) {
        while (lower.size >= 2 && cross(lower[lower.lastIndex - 1], lower.last(), p) <= 0f) lower.removeAt(lower.lastIndex)
        lower += p
    }
    val upper = mutableListOf<Pair<Float, Float>>()
    for (p in sorted.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.lastIndex - 1], upper.last(), p) <= 0f) upper.removeAt(upper.lastIndex)
        upper += p
    }
    return lower.dropLast(1) + upper.dropLast(1)
}

internal fun depthAt(x: Float, y: Float, t: FloatArray): Float {
    if (t.size < 9) return 0f
    val d=(t[3]-t[5])*(t[0]-t[4])+(t[4]-t[2])*(t[1]-t[5])
    if (kotlin.math.abs(d)<.01f) return Float.POSITIVE_INFINITY
    val a=((t[3]-t[5])*(x-t[4])+(t[4]-t[2])*(y-t[5]))/d
    val b=((t[5]-t[1])*(x-t[4])+(t[0]-t[4])*(y-t[5]))/d
    return a*t[6]+b*t[7]+(1f-a-b)*t[8]
}
