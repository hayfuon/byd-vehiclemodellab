package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ChargingCableGeometryTest {
    @Test fun chargingGroundPoolStartsNearTheWheelInsteadOfFarOutsideTheBody() {
        val frame = ChargingCableGeometry.groundLightFrame(floatArrayOf(0f,1f,0f),floatArrayOf(0f,0f,-1f),0f,1f)
        val t = -frame.position[1] / frame.direction[1]
        val offset = kotlin.math.abs(frame.position[2] + t * frame.direction[2])
        assertTrue(offset in .15f.. .25f)
    }

    @Test fun groundEmitterUsesTheOuterLensFaceInsteadOfItsInteriorCenter() {
        val identity = floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
        val point = ChargingCableGeometry.emitterSurfacePoint(
            floatArrayOf(1.68f,.96f,-.90f,1.73f,.99f,-.86f), identity, floatArrayOf(0f,0f,-1f))
        assertEquals(1.705f, point[0], .0001f)
        assertEquals(.975f, point[1], .0001f)
        assertEquals(-.90f, point[2], .0001f)
    }

    @Test fun insertedCollarStaysInsideTheSocketRecess() {
        val vertices=ChargingCableGeometry.connectorVertices(floatArrayOf(0f,0f,0f),floatArrayOf(0f,0f,-1f),1f,1.24f,.09f,1f)
        for(i in vertices.indices step 6)if(vertices[i+2]>0f) {
            assertTrue("插入段不得穿过充电口背板",vertices[i+2]<=.030f)
            assertTrue("插入段不得穿过充电口侧壁",kotlin.math.abs(vertices[i])<=.032f)
        }
    }
    @Test fun socketAnchorUsesTheOuterFaceOfTheChargePort() {
        val bounds = floatArrayOf(1.55f, .84f, -.95f, 1.80f, .99f, -.89f)

        val socket = ChargingCableGeometry.socketSurfacePoint(bounds, outwardSign = -1f)

        assertEquals(1.675f, socket[0], .0001f)
        assertEquals(.915f, socket[1], .0001f)
        assertEquals(-.95f, socket[2], .0001f)
    }

    @Test fun socketFrameUsesTheStationaryVehicleTransformInsteadOfTheAnimatedCoverTransform() {
        val bounds = floatArrayOf(1.55f, .84f, -.95f, 1.80f, .99f, -.89f)
        val vehicle = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            2f, 3f, 4f, 1f
        )

        val frame = ChargingCableGeometry.stationarySocketFrame(bounds, vehicle, outwardSign = -1f, axis = 2)

        assertEquals(3.675f, frame.first[0], .0001f)
        assertEquals(3.915f, frame.first[1], .0001f)
        assertEquals(3.05f, frame.first[2], .0001f)
        assertEquals(0f, frame.second[0], .0001f)
        assertEquals(0f, frame.second[1], .0001f)
        assertEquals(-1f, frame.second[2], .0001f)
    }

    @Test fun connectedCableStartsAtPortAndSettlesNearGroundOutsideVehicle() {
        val socket = floatArrayOf(0.4f, 0.8f, 1.2f)
        val outward = floatArrayOf(0f, 0f, 1f)

        val path = ChargingCableGeometry.path(socket, outward, groundY = 0f, scale = 1f)

        assertEquals(25, path.size)
        assertEquals(socket.toList(), path.first().toList())
        assertTrue(path.last()[1] in 0f..0.03f)
        assertEquals(socket[2] + 1.02f, path.last()[2], .001f)
        assertTrue(path[1][1] < socket[1])
        assertTrue(path[path.lastIndex - 1][1] < .04f)
        assertTrue(path.all { point -> point.size == 3 && point.all(Float::isFinite) })
    }

    @Test fun connectedCableHasAVisibleGroundRunInsteadOfEndingLikeATautRod() {
        val path = ChargingCableGeometry.path(
            socket = floatArrayOf(0.4f, 0.8f, 1.2f),
            outward = floatArrayOf(0f, 0f, 1f),
            groundY = 0f,
            scale = 1f
        )

        assertTrue(path[12][1] > path.last()[1])
        assertEquals(.018f, path.last()[1], .003f)
        assertTrue(path.last()[2] > path[path.lastIndex - 1][2])
        assertTrue(path[1][2] > path.first()[2])
    }

    @Test fun cableTransitionsToGroundWithoutAKinkAndKeepsNaturalSlack() {
        val path = ChargingCableGeometry.path(
            socket = floatArrayOf(1.2f, 1.1f, -0.8f),
            outward = floatArrayOf(.35f, .2f, -.91f),
            groundY = 0f,
            scale = 1f,
            segments = 40
        )

        val before = path[26]
        val joint = path[27]
        val after = path[28]
        fun direction(a: FloatArray, b: FloatArray) = FloatArray(3) { b[it] - a[it] }
        fun dot(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()
        val incoming = direction(before, joint)
        val outgoing = direction(joint, after)
        val cosine = dot(incoming, outgoing) /
            (sqrt(dot(incoming, incoming)) * sqrt(dot(outgoing, outgoing)))

        assertTrue("cable transition should have a continuous tangent", cosine > .95f)
        assertTrue("ground run should retain cable length", path.last()[2] < joint[2] - .12f)
        assertEquals(.018f, path.last()[1], .002f)
    }

    @Test fun tubeGeometryHasRoundCrossSectionAndUnitNormals() {
        val path = listOf(
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(.2f, .5f, .1f),
            floatArrayOf(.6f, .02f, .2f)
        )

        val vertices = ChargingCableGeometry.tubeVertices(path, radius = .02f, sides = 8)

        assertEquals((path.size - 1) * 8 * 6, vertices.size / 6)
        for (index in vertices.indices step 6) {
            val normalLength = sqrt(
                vertices[index + 3] * vertices[index + 3] +
                    vertices[index + 4] * vertices[index + 4] +
                    vertices[index + 5] * vertices[index + 5]
            )
            assertEquals(1f, normalLength, .001f)
        }
    }

    @Test fun roundCableDoesNotFlipItsFrameAsItBendsDownward() {
        val path = listOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, .9f, .06f),
            floatArrayOf(0f, .8f, .09f), floatArrayOf(0f, .7f, .1f))
        val tube = ChargingCableGeometry.tubeVertices(path, .02f, 8)
        val normalAtFirstRing = tube.sliceArray(3..5)
        val normalAtSecondRing = tube.sliceArray(33..35)
        val dot = (0..2).sumOf { (normalAtFirstRing[it] * normalAtSecondRing[it]).toDouble() }
        assertTrue("cross section must not twist at the reference axis threshold", dot > .95)
    }

    @Test fun cableDirectionAndScaleAreNormalizedAndBounded() {
        val path = ChargingCableGeometry.path(
            socket = floatArrayOf(1f, 1f, -1f),
            outward = floatArrayOf(0f, 0f, -4f),
            groundY = 0.2f,
            scale = 2f,
            segments = 12
        )

        assertEquals(13, path.size)
        assertTrue(path.last()[2] < -2f)
        assertTrue(path.last()[1] in 0.2f..0.26f)
    }

    @Test fun cableStartsAtThePlugTailWithoutAGapOrAnOffset() {
        val socket = floatArrayOf(1.2f, .9f, -.95f)
        val outward = floatArrayOf(0f, 0f, -1f)
        val join = ChargingCableGeometry.plugCableJoin(socket, outward, scale = .4f, plugScale = 1.24f)
        val path = ChargingCableGeometry.path(join, outward, groundY = 0f, scale = .4f)

        assertEquals(join.toList(), path.first().toList())
        assertTrue(join[2] < socket[2])
    }

    @Test fun cableLeavesThePlugWithAnExactlyAlignedShortStrainRelief() {
        val path = ChargingCableGeometry.path(
            floatArrayOf(1.7f, .89f, -1f), floatArrayOf(0f, 0f, -1f), 0f, 1f, 40
        )

        assertEquals(path[0][0], path[1][0], .000001f)
        assertTrue(path[1][1] < path[0][1])
        assertTrue(path[1][2] < path[0][2] - .01f)
    }

    @Test fun draggedCableKeepsItsGroundEndFixedWhileThePlugMovesIntoTheSocket() {
        val outward = floatArrayOf(0f, 0f, -1f)
        val anchor = floatArrayOf(1.78f, .018f, -1.92f)
        val parked = ChargingCableGeometry.dragPath(
            floatArrayOf(1.7f, .9f, -1.05f), outward, anchor, scale = 1f, segments = 40
        )
        val inserted = ChargingCableGeometry.dragPath(
            floatArrayOf(1.7f, .9f, -.96f), outward, anchor, scale = 1f, segments = 40
        )

        assertEquals(anchor.toList(), parked.last().toList())
        assertEquals(anchor.toList(), inserted.last().toList())
        assertEquals(.018f, inserted.last()[1], .00001f)
        assertTrue("the moving plug must pull only the local hanging section",
            kotlin.math.abs(parked[4][2] - inserted[4][2]) > .01f)
        assertTrue("the grounded tail must remain settled while the plug inserts",
            kotlin.math.abs(parked[parked.lastIndex - 2][2] - inserted[inserted.lastIndex - 2][2]) < .02f)
    }

    @Test fun cableVertexFadeStartsAtTheGroundEndAndReachesAnOpaqueJacketNearThePlug() {
        val path = listOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, .7f, -.1f),
            floatArrayOf(0f, .2f, -.4f), floatArrayOf(0f, .018f, -.7f))

        val vertices = ChargingCableGeometry.fadedTubeVertices(path, .02f, sides = 8)

        assertTrue(vertices[6] > .99f)
        assertEquals(0f, vertices[vertices.size - 1], .00001f)
        assertTrue(vertices.indices.step(7).all { vertices[it + 6] in 0f..1f })
    }

    @Test fun draggedLeadHasContinuousGroundContactAndAnAlignedStrainRelief() {
        val anchor = floatArrayOf(1.7f, .0205f, -1.92f)
        for (offset in listOf(0f, .07f, .135f)) {
            val points = ChargingCableGeometry.dragPath(floatArrayOf(1.7f, .9f, -.99f - offset),
                floatArrayOf(0f, 0f, -1f), anchor, 1f, 40)
            assertEquals(points[0][0], points[1][0], .000001f)
            assertEquals(points[0][1], points[1][1], .000001f)
            assertTrue(points.all { it[1] >= anchor[1] - .000001f })
            for (i in 1 until points.lastIndex) {
                val a = FloatArray(3) { points[i][it] - points[i-1][it] }
                val b = FloatArray(3) { points[i+1][it] - points[i][it] }
                fun dot(x: FloatArray, y: FloatArray) = (0..2).sumOf { (x[it]*y[it]).toDouble() }
                val cosine = dot(a,b) / kotlin.math.sqrt(dot(a,a)*dot(b,b))
                assertTrue("abrupt cable bend at $i: $cosine", cosine > .93)
            }
        }
    }

    @Test fun connectorHasARecessedRightAngleSocketStepAndEndsAtTheExactCableJoin() {
        val socket = floatArrayOf(1.7f, .89f, -.9f)
        val normal = floatArrayOf(0f, 0f, -1f)
        val join = ChargingCableGeometry.plugCableJoin(socket, normal, 1f, 1.24f, .09f)
        val tube = ChargingCableGeometry.connectorVertices(socket, normal, 1f, 1.24f, .09f, insertion = 1f)
        assertTrue(tube.isNotEmpty() && tube.size % 18 == 0)
        val points = tube.indices.step(6).map { floatArrayOf(tube[it], tube[it + 1], tube[it + 2]) }
        assertEquals(join[2], points.minOf { it[2] }, .000001f)
        assertTrue(points.any { kotlin.math.abs(it[0] - join[0]) < .000001f &&
            kotlin.math.abs(it[1] - join[1]) < .000001f && kotlin.math.abs(it[2] - join[2]) < .000001f })
        for (v in tube.indices step 6) {
            val n = sqrt(tube[v + 3]*tube[v + 3] + tube[v + 4]*tube[v + 4] + tube[v + 5]*tube[v + 5])
            assertEquals(1f, n, .0001f)
        }
        assertTrue("fully inserted plug must extend behind the socket face",
            points.any { it[2] > socket[2] + .02f })
        assertTrue("socket side must retain a short square collar step",
            points.count { kotlin.math.abs(it[2] - (socket[2] - .012f)) < .012f } >= 4)
    }

    @Test fun connectorBodyTransitionsFromTheShortStepToARoundCableShell() {
        val socket = floatArrayOf(0f, .9f, 0f)
        val normal = floatArrayOf(0f, 0f, 1f)
        val vertices = ChargingCableGeometry.connectorVertices(socket, normal, 1f, 1f, .08f, insertion = 0f)
        val points = vertices.indices.step(6).map { floatArrayOf(vertices[it], vertices[it + 1], vertices[it + 2]) }
        val stepWidth = points.filter { it[2] in .009f.. .018f }.maxOf { kotlin.math.abs(it[0]) }
        val bodyWidth = points.filter { it[2] > .025f }.maxOf { kotlin.math.abs(it[0]) }
        assertTrue("the square interface must be short", stepWidth > .02f)
        assertTrue("the rounded shell must taper toward the cable", bodyWidth > .01f)
        assertTrue("the shell must contain off-axis round-section vertices",
            points.any { kotlin.math.abs(it[0]) > .001f && kotlin.math.abs(it[1] - socket[1]) > .001f })
    }

    @Test fun socketMatingNoseHasARoundedRectangularSectionInsteadOfADisc() {
        val vertices = ChargingCableGeometry.roundedRectSectionVertices(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, .04f),
            floatArrayOf(0f, 0f, 1f), .03f, .022f
        )

        val points = vertices.asList().chunked(6).map { it.take(3) }
        assertTrue(points.any { kotlin.math.abs(it[0] - .03f) < .0001f })
        assertTrue(points.any { kotlin.math.abs(it[1] - .022f) < .0001f })
        assertTrue("rounded nose must not retain sharp rectangle corners",
            points.none { kotlin.math.abs(it[0]) > .029f && kotlin.math.abs(it[1]) > .021f })
        assertTrue(points.any { kotlin.math.abs(it[2]) < .0001f })
        assertTrue(points.any { kotlin.math.abs(it[2] - .04f) < .0001f })
    }

    @Test fun groundLightStaysAtTheLensSoTheOuterCoverCanOccludeIt() {
        val socket = floatArrayOf(1.72936f, .89327f, -.89872f)
        val outward = floatArrayOf(0f, 0f, -1f)

        val frame = ChargingCableGeometry.groundLightFrame(socket, outward, groundY = -.006f, scale = 1f)

        val offset = sqrt((0..2).sumOf { axis ->
            val d = frame.position[axis] - socket[axis]
            (d * d).toDouble()
        }.toFloat())
        assertTrue("光源不得越过外盖，灯面偏移不得超过 3 毫米", offset <= .003f)
        assertTrue("range must reach the actual ground height", frame.range > socket[1] - -.006f)
        assertTrue("ground-facing direction must contain both downward and outward components",
            frame.direction[1] < -.1f && frame.direction[2] < -.1f)
    }
}
