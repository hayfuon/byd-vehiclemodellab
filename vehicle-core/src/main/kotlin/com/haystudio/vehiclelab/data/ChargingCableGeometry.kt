package com.haystudio.vehiclelab.data

import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.PI

object ChargingCableGeometry {
    data class GroundLightFrame(val position: FloatArray, val direction: FloatArray, val range: Float)

    fun groundShadowPath(path: List<FloatArray>, groundY: Float): List<FloatArray> {
        if (!groundY.isFinite()) return emptyList()
        return path.asSequence()
            .filter { it.size >= 3 && it.take(3).all(Float::isFinite) }
            .map { floatArrayOf(it[0], groundY, it[2]) }
            .toList()
    }

    fun emitterSurfacePoint(bounds: FloatArray, matrix: FloatArray, outward: FloatArray): FloatArray {
        require(bounds.size >= 6 && matrix.size >= 16 && outward.size >= 3)
        val alignment = FloatArray(3) { axis ->
            (0..2).sumOf { row -> (matrix[axis * 4 + row] * outward[row]).toDouble() }.toFloat()
        }
        val axis = (0..2).maxBy { abs(alignment[it]) }
        val local = socketSurfacePoint(bounds, if (alignment[axis] < 0f) -1f else 1f, axis)
        return stationarySocketFrame(local, FloatArray(3).also { it[axis] = 1f }, matrix).first
    }

    fun groundLightFrame(socket: FloatArray, outward: FloatArray, groundY: Float, scale: Float): GroundLightFrame {
        require(socket.size >= 3 && outward.size >= 3 && groundY.isFinite())
        val len = sqrt(outward[0] * outward[0] + outward[1] * outward[1] + outward[2] * outward[2])
            .coerceAtLeast(1e-6f)
        val safeScale = if (scale.isFinite()) scale.coerceAtLeast(.001f) else 1f
        val n = FloatArray(3) { outward[it] / len }
        // 仅离开灯面两毫米以避开自遮挡，不能将光源移到活动盖板外侧。
        val position = FloatArray(3) { socket[it] + n[it] * .002f * safeScale }
        val down = (groundY - position[1]).coerceAtMost(-.001f)
        val raw = floatArrayOf(n[0] * .22f, down, n[2] * .22f)
        val rawLen = sqrt(raw[0] * raw[0] + raw[1] * raw[1] + raw[2] * raw[2]).coerceAtLeast(1e-6f)
        val direction = FloatArray(3) { raw[it] / rawLen }
        val groundDistance = sqrt((position[0] - socket[0]) * (position[0] - socket[0]) +
            down * down + (position[2] - socket[2]) * (position[2] - socket[2]))
        return GroundLightFrame(position, direction, groundDistance + .65f * safeScale)
    }

    fun plugCableJoin(socket: FloatArray, outward: FloatArray, scale: Float, plugScale: Float, plugDepth: Float = .085f): FloatArray {
        require(socket.size >= 3 && outward.size >= 3)
        val length = sqrt(outward[0] * outward[0] + outward[1] * outward[1] + outward[2] * outward[2])
            .coerceAtLeast(1e-6f)
        val safeScale = if (scale.isFinite()) scale.coerceAtLeast(.001f) else 1f
        val safePlugScale = if (plugScale.isFinite()) plugScale.coerceAtLeast(.1f) else 1f
        val safeDepth = if (plugDepth.isFinite()) plugDepth.coerceAtLeast(.01f) else .085f
        return FloatArray(3) { index ->
            socket[index] + outward[index] / length * safeDepth * safeScale * safePlugScale
        }
    }

    fun stationarySocketFrame(
        localPoint: FloatArray,
        localNormal: FloatArray,
        vehicleTransform: FloatArray
    ): Pair<FloatArray, FloatArray> {
        require(localPoint.size >= 3 && localNormal.size >= 3 && vehicleTransform.size >= 16)
        fun transform(v: FloatArray, point: Boolean): FloatArray {
            val w = if (point) 1f else 0f
            val result = floatArrayOf(
                vehicleTransform[0] * v[0] + vehicleTransform[4] * v[1] + vehicleTransform[8] * v[2] + vehicleTransform[12] * w,
                vehicleTransform[1] * v[0] + vehicleTransform[5] * v[1] + vehicleTransform[9] * v[2] + vehicleTransform[13] * w,
                vehicleTransform[2] * v[0] + vehicleTransform[6] * v[1] + vehicleTransform[10] * v[2] + vehicleTransform[14] * w
            )
            if (point) return result
            val length = sqrt(result.sumOf { (it * it).toDouble() }.toFloat()).coerceAtLeast(1e-6f)
            return FloatArray(3) { result[it] / length }
        }
        return transform(localPoint, true) to transform(localNormal, false)
    }

    fun stationarySocketFrame(
        bounds: FloatArray,
        vehicleTransform: FloatArray,
        outwardSign: Float,
        axis: Int
    ): Pair<FloatArray, FloatArray> {
        require(vehicleTransform.size >= 16)
        val localPoint = socketSurfacePoint(bounds, outwardSign, axis)
        val localNormal = FloatArray(3).also { it[axis] = if (outwardSign < 0f) -1f else 1f }
        return stationarySocketFrame(localPoint, localNormal, vehicleTransform)
    }

    fun socketSurfacePoint(bounds: FloatArray, outwardSign: Float, axis: Int = 2): FloatArray {
        require(bounds.size >= 6 && bounds.take(6).all(Float::isFinite))
        require(outwardSign.isFinite() && outwardSign != 0f)
        require(axis in 0..2)
        return FloatArray(3) { component ->
            if (component != axis) (bounds[component] + bounds[component + 3]) * .5f
            else if (outwardSign < 0f) minOf(bounds[component], bounds[component + 3])
            else maxOf(bounds[component], bounds[component + 3])
        }
    }

    fun path(
        socket: FloatArray,
        outward: FloatArray,
        groundY: Float,
        scale: Float,
        segments: Int = 24
    ): List<FloatArray> {
        require(socket.size >= 3 && outward.size >= 3)
        require(socket.take(3).all(Float::isFinite) && groundY.isFinite())

        val safeScale = if (scale.isFinite()) scale.coerceAtLeast(.001f) else 1f
        val length = sqrt(outward[0] * outward[0] + outward[1] * outward[1] + outward[2] * outward[2])
        val direction = if (length > 1e-5f && length.isFinite()) {
            FloatArray(3) { outward[it] / length }
        } else floatArrayOf(0f, 0f, 1f)
        val ground = groundY + .018f * safeScale
        val end = floatArrayOf(
            socket[0] + direction[0] * 1.02f * safeScale,
            ground,
            socket[2] + direction[2] * 1.02f * safeScale
        )
        val drop = floatArrayOf(end[0] - direction[0] * .31f * safeScale, ground, end[2] - direction[2] * .31f * safeScale)
        val firstControl = FloatArray(3) { socket[it] + direction[it] * .24f * safeScale }
        val secondControl = FloatArray(3) { drop[it] - direction[it] * .18f * safeScale }
        secondControl[1] = ground
        val dropControlA = floatArrayOf(drop[0] + direction[0] * .08f * safeScale, ground, drop[2] + direction[2] * .08f * safeScale)
        val dropControlB = floatArrayOf(end[0] - direction[0] * .08f * safeScale, ground, end[2] - direction[2] * .08f * safeScale)
        val count = segments.coerceIn(2, 128)
        val arcSegments = (count * .68f).roundToInt().coerceIn(1, count - 1)
        val dropSegments = count - arcSegments
        val side = floatArrayOf(-direction[2], 0f, direction[0])
        return List(count + 1) { index ->
            val point = if (index >= arcSegments) {
                val t = ((index - arcSegments).toFloat() / dropSegments).coerceIn(0f, 1f)
                val u = 1f - t
                FloatArray(3) { axis ->
                    u * u * u * drop[axis] + 3f * u * u * t * dropControlA[axis] +
                        3f * u * t * t * dropControlB[axis] + t * t * t * end[axis]
                }
            } else {
                val t = index.toFloat() / arcSegments
                val u = 1f - t
                FloatArray(3) { axis ->
                    u * u * u * socket[axis] + 3f * u * u * t * firstControl[axis] +
                        3f * u * t * t * secondControl[axis] + t * t * t * drop[axis]
                }
            }
            val progress = index.toFloat() / count
            
            val slackProgress = ((progress - .12f) / .88f).coerceIn(0f, 1f)
            val slack = sin(slackProgress * PI).toFloat() * .16f * safeScale
            point[0] += side[0] * slack
            point[2] += side[2] * slack
            point
        }
    }

    fun dragPath(
        plugTail: FloatArray,
        outward: FloatArray,
        groundAnchor: FloatArray,
        scale: Float,
        segments: Int = 40
    ): List<FloatArray> {
        require(plugTail.size >= 3 && outward.size >= 3 && groundAnchor.size >= 3)
        require(plugTail.take(3).all(Float::isFinite) && groundAnchor.take(3).all(Float::isFinite))
        val safeScale = if (scale.isFinite()) scale.coerceAtLeast(.001f) else 1f
        val length = sqrt(outward[0] * outward[0] + outward[1] * outward[1] + outward[2] * outward[2])
            .coerceAtLeast(1e-6f)
        val direction = FloatArray(3) { outward[it] / length }
        val count = segments.coerceIn(8, 128)
        val side = floatArrayOf(-direction[2], 0f, direction[0])
        val release = FloatArray(3) { plugTail[it] + direction[it] * .10f * safeScale }
        val settle = floatArrayOf(
            groundAnchor[0] - direction[0] * .32f * safeScale,
            groundAnchor[1],
            groundAnchor[2] - direction[2] * .32f * safeScale
        )
        val controlA = FloatArray(3) { release[it] + direction[it] * .24f * safeScale }
        val controlB = FloatArray(3) { settle[it] - direction[it] * .24f * safeScale }
        controlB[1] = groundAnchor[1]
        val straightCount = (count * .10f).roundToInt().coerceAtLeast(1)
        val landingIndex = (count * .70f).roundToInt().coerceIn(straightCount + 2, count - 2)
        return List(count + 1) { index ->
            when {
                index <= straightCount -> {
                    val t = index.toFloat() / straightCount
                    FloatArray(3) { plugTail[it] + (release[it] - plugTail[it]) * t }
                }
                index < landingIndex -> {
                    val t = (index - straightCount).toFloat() / (landingIndex - straightCount)
                    val u = 1f - t

                    val bow = sin(t * PI).toFloat().let { it * it } * .11f * safeScale
                    FloatArray(3) { axis ->
                        u*u*u*release[axis] + 3f*u*u*t*controlA[axis] +
                            3f*u*t*t*controlB[axis] + t*t*t*settle[axis] + side[axis]*bow
                    }
                }
                else -> {
                    val t = (index - landingIndex).toFloat() / (count - landingIndex)
                    FloatArray(3) { settle[it] + (groundAnchor[it] - settle[it]) * t }
                }
            }
        }
    }

    fun groundAnchor(socket: FloatArray, outward: FloatArray, groundY: Float, scale: Float): FloatArray {
        val path = path(socket, outward, groundY, scale, segments = 8)
        return path.last().copyOf()
    }

    fun tubeVertices(path: List<FloatArray>, radius: Float, sides: Int = 8, radii: FloatArray? = null, closed: Boolean = false): FloatArray {
        require(path.size >= 2 && path.all { it.size >= 3 && it.take(3).all(Float::isFinite) })
        require(radius.isFinite() && radius > 0f)
        val sideCount = sides.coerceIn(6, 24)
        require(radii == null || (radii.size == path.size && radii.all { it.isFinite() && it > 0f }))
        val output = FloatArray(((path.size - 1) * sideCount * 6 + if (closed) sideCount * 6 else 0) * 6)
        var cursor = 0

        fun normalized(x: Float, y: Float, z: Float): FloatArray {
            val length = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
            return floatArrayOf(x / length, y / length, z / length)
        }

        fun append(point: FloatArray, normal: FloatArray) {
            output[cursor++] = point[0]; output[cursor++] = point[1]; output[cursor++] = point[2]
            output[cursor++] = normal[0]; output[cursor++] = normal[1]; output[cursor++] = normal[2]
        }

        var previousAxis: FloatArray? = null
        val rings = path.indices.map { index ->
            val a = path[maxOf(0, index - 1)]
            val b = path[minOf(path.lastIndex, index + 1)]
            val tangent = normalized(b[0] - a[0], b[1] - a[1], b[2] - a[2])
            val reference = if (kotlin.math.abs(tangent[1]) < .88f)
                floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
            val initialAxis = normalized(
                tangent[1] * reference[2] - tangent[2] * reference[1],
                tangent[2] * reference[0] - tangent[0] * reference[2],
                tangent[0] * reference[1] - tangent[1] * reference[0]
            )
            val axisX = previousAxis?.let { previous ->
                val projection = (0..2).sumOf { (previous[it] * tangent[it]).toDouble() }.toFloat()
                val projected = FloatArray(3) { previous[it] - tangent[it] * projection }
                if (projected.sumOf { (it * it).toDouble() } > 1e-8)
                    normalized(projected[0], projected[1], projected[2]) else initialAxis
            } ?: initialAxis
            previousAxis = axisX
            val axisY = normalized(
                tangent[1] * axisX[2] - tangent[2] * axisX[1],
                tangent[2] * axisX[0] - tangent[0] * axisX[2],
                tangent[0] * axisX[1] - tangent[1] * axisX[0]
            )
            val normals = Array(sideCount) { side ->
                val angle = (2.0 * Math.PI * side / sideCount).toFloat()
                val x = kotlin.math.cos(angle); val y = kotlin.math.sin(angle)
                floatArrayOf(axisX[0] * x + axisY[0] * y,
                    axisX[1] * x + axisY[1] * y, axisX[2] * x + axisY[2] * y)
            }
            normals
        }
        for (segment in 0 until path.lastIndex) {
            val a = path[segment]
            val b = path[segment + 1]
            val na = rings[segment]
            val nb = rings[segment + 1]
            for (side in 0 until sideCount) {
                val next = (side + 1) % sideCount
                val ra = radii?.get(segment) ?: radius
                val rb = radii?.get(segment + 1) ?: radius
                val a0 = FloatArray(3) { a[it] + na[side][it] * ra }
                val a1 = FloatArray(3) { a[it] + na[next][it] * ra }
                val b0 = FloatArray(3) { b[it] + nb[side][it] * rb }
                val b1 = FloatArray(3) { b[it] + nb[next][it] * rb }
                append(a0, na[side]); append(a1, na[next]); append(b1, nb[next])
                append(a0, na[side]); append(b1, nb[next]); append(b0, nb[side])
            }
        }
        if (closed) for (ring in listOf(0, path.lastIndex)) {
            val other = if (ring == 0) 1 else path.lastIndex - 1
            val normal = normalized(path[ring][0] - path[other][0], path[ring][1] - path[other][1], path[ring][2] - path[other][2])
            for (side in 0 until sideCount) {
                val next = (side + 1) % sideCount
                val r = radii?.get(ring) ?: radius
                append(path[ring], normal)
                append(FloatArray(3) { path[ring][it] + rings[ring][side][it] * r }, normal)
                append(FloatArray(3) { path[ring][it] + rings[ring][next][it] * r }, normal)
            }
        }
        return output
    }

    fun fadedTubeVertices(path: List<FloatArray>, radius: Float, sides: Int = 8): FloatArray {
        val tube = tubeVertices(path, radius, sides)
        val sideCount = sides.coerceIn(6, 24)
        val verticesPerSegment = sideCount * 6
        val out = FloatArray(tube.size / 6 * 7)
        var source = 0
        var target = 0
        while (source < tube.size) {
            val vertex = source / 6
            val segment = vertex / verticesPerSegment
            val inTriangle = vertex % 6
            val endsAtNext = inTriangle == 2 || inTriangle == 4 || inTriangle == 5
            val p = (segment + if (endsAtNext) 1f else 0f) / (path.size - 1).toFloat()
            
            val t = ((.985f - p) / .19f).coerceIn(0f, 1f)
            val fade = t * t * (3f - 2f * t)
            repeat(6) { out[target++] = tube[source++] }
            out[target++] = fade
        }
        return out
    }

    fun surfaceTubeVertices(path: List<FloatArray>, radius: Float, sides: Int = 16, scale: Float = 1f): FloatArray {
        require(scale.isFinite() && scale > 0f)
        val tube=fadedTubeVertices(path,radius,sides)
        val sideCount=sides.coerceIn(6,24)
        val arc=FloatArray(path.size)
        for(i in 1 until path.size) {
            val d=FloatArray(3) { path[i][it]-path[i-1][it] }
            arc[i]=arc[i-1]+sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2])/scale
        }
        val output=FloatArray(tube.size/7*9)
        for(vertex in 0 until tube.size/7) {
            val segment=vertex/(sideCount*6)
            val corner=vertex%6
            val nextRing=corner==2 || corner==4 || corner==5
            val nextSide=corner==1 || corner==2 || corner==4
            val side=vertex/6%sideCount
            for(c in 0..6) output[vertex*9+c]=tube[vertex*7+c]
            output[vertex*9+7]=arc[segment+if(nextRing)1 else 0]
            output[vertex*9+8]=((side+if(nextSide)1 else 0)*2.0*PI/sideCount).toFloat()
        }
        return output
    }

    fun connectorVertices(
        socket: FloatArray,
        outward: FloatArray,
        scale: Float,
        size: Float,
        depth: Float,
        insertion: Float = 0f
    ): FloatArray {
        require(socket.size >= 3 && outward.size >= 3)
        val unit = (scale * size).coerceAtLeast(.001f)
        val nLen = sqrt(outward[0] * outward[0] + outward[1] * outward[1] + outward[2] * outward[2])
            .coerceAtLeast(1e-6f)
        val n = FloatArray(3) { outward[it] / nLen }
        val inserted = insertion.coerceIn(0f, 1f)
        val innerDepth = .022f * unit * inserted
        val inner = FloatArray(3) { socket[it] - n[it] * innerDepth }

        val interfaceEnd = FloatArray(3) { socket[it] + n[it] * (.012f * unit) }
        val tail = plugCableJoin(socket, outward, scale, size, depth)

        val collar = roundedRectSectionVertices(inner, interfaceEnd, n,
            halfWidth = .025f * unit, halfHeight = .021f * unit)

        val controls = arrayOf(
            floatArrayOf(0f, .026f), floatArrayOf(.10f, .028f),
            floatArrayOf(.28f, .027f), floatArrayOf(.52f, .024f),
            floatArrayOf(.74f, .021f), floatArrayOf(1f, .017f)
        )
        val points = ArrayList<FloatArray>()
        val radii = ArrayList<Float>()
        for (i in 0 until controls.lastIndex) for (j in 0 until 6) {
            val t = j / 6f
            val ease = t * t * (3f - 2f * t)
            val a = controls[i]; val b = controls[i + 1]
            val along = a[0] + (b[0] - a[0]) * t
            points.add(FloatArray(3) { interfaceEnd[it] + (tail[it] - interfaceEnd[it]) * along })
            radii.add((a[1] + (b[1] - a[1]) * ease) * unit)
        }
        points.add(tail); radii.add(.017f * unit)
        val tube = tubeVertices(points, .017f * unit, 24, radii.toFloatArray(), closed = true)
        return FloatArray(collar.size + tube.size).also {
            collar.copyInto(it, 0)
            tube.copyInto(it, collar.size)
        }
    }

    private fun boxSectionVertices(
        start: FloatArray,
        end: FloatArray,
        axis: FloatArray,
        halfWidth: Float,
        halfHeight: Float
    ): FloatArray {
        val aLen = sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]).coerceAtLeast(1e-6f)
        val n = FloatArray(3) { axis[it] / aLen }
        val ref = if (kotlin.math.abs(n[1]) < .9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
        fun cross(x: FloatArray, y: FloatArray) = floatArrayOf(
            x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]
        )
        fun unit(v: FloatArray): FloatArray {
            val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
            return FloatArray(3) { v[it] / l }
        }
        val u = unit(cross(n, ref))
        val v = unit(cross(u, n))
        val corners = Array(8) { index ->
            val p = if (index < 4) start else end
            val sx = if (index and 1 == 0) -1f else 1f
            val sy = if (index and 2 == 0) -1f else 1f
            FloatArray(3) { p[it] + u[it] * sx * halfWidth + v[it] * sy * halfHeight }
        }
        val faces = arrayOf(
            intArrayOf(0, 1, 3, 2), intArrayOf(4, 6, 7, 5),
            intArrayOf(0, 4, 5, 1), intArrayOf(2, 3, 7, 6),
            intArrayOf(0, 2, 6, 4), intArrayOf(1, 5, 7, 3)
        )
        val out = FloatArray(faces.size * 6 * 6)
        var cursor = 0
        fun append(p: FloatArray, normal: FloatArray) {
            out[cursor++] = p[0]; out[cursor++] = p[1]; out[cursor++] = p[2]
            out[cursor++] = normal[0]; out[cursor++] = normal[1]; out[cursor++] = normal[2]
        }
        for (face in faces) {
            val p0 = corners[face[0]]; val p1 = corners[face[1]]; val p2 = corners[face[2]]
            val e0 = FloatArray(3) { p1[it] - p0[it] }
            val e1 = FloatArray(3) { p2[it] - p0[it] }
            val normal = unit(cross(e0, e1))
            append(p0, normal); append(p1, normal); append(p2, normal)
            append(p0, normal); append(p2, normal); append(corners[face[3]], normal)
        }
        return out
    }

    fun roundedRectSectionVertices(
        start: FloatArray,
        end: FloatArray,
        axis: FloatArray,
        halfWidth: Float,
        halfHeight: Float
    ): FloatArray {
        require(start.size >= 3 && end.size >= 3 && axis.size >= 3)
        val n = normalized(axis)
        val ref = if (kotlin.math.abs(n[1]) < .9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
        fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
            a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
        )
        val u = normalized(cross(n, ref))
        val v = normalized(cross(u, n))
        val sides = 24
        fun ring(center: FloatArray): List<FloatArray> = List(sides) { i ->
            val angle = 2f * PI.toFloat() * i / sides
            val c = cos(angle); val s = sin(angle)
            
            val x = kotlin.math.sign(c) * abs(c).pow(.625f) * halfWidth
            val y = kotlin.math.sign(s) * abs(s).pow(.625f) * halfHeight
            FloatArray(3) { center[it] + u[it] * x + v[it] * y }
        }
        val a = ring(start); val b = ring(end)
        val out = FloatArray(sides * 12 * 6)
        var cursor = 0
        fun append(p: FloatArray, normal: FloatArray) {
            out[cursor++] = p[0]; out[cursor++] = p[1]; out[cursor++] = p[2]
            out[cursor++] = normal[0]; out[cursor++] = normal[1]; out[cursor++] = normal[2]
        }
        fun triangle(p0: FloatArray, p1: FloatArray, p2: FloatArray) {
            val normal = normalized(cross(FloatArray(3) { p1[it] - p0[it] }, FloatArray(3) { p2[it] - p0[it] }))
            append(p0, normal); append(p1, normal); append(p2, normal)
        }
        for (i in 0 until sides) {
            val j = (i + 1) % sides
            triangle(a[i], a[j], b[j]); triangle(a[i], b[j], b[i])
            triangle(start, a[j], a[i]); triangle(end, b[i], b[j])
        }
        return out
    }

    private fun normalized(value: FloatArray): FloatArray {
        val length = sqrt(value.sumOf { (it * it).toDouble() }.toFloat()).coerceAtLeast(1e-6f)
        return FloatArray(3) { value[it] / length }
    }
}
