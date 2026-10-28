package com.haystudio.vehiclelab.data

import org.json.JSONObject
import kotlin.math.sqrt

data class VehicleCone(
    val name: String, val lamp: VehicleLamp, val position: FloatArray, val direction: FloatArray,
    val color: FloatArray, val angle: Float, val distance: Float, val radius: Float,
    val nativeIntensity: Float
)

class VehicleBeamRig(val cones: List<VehicleCone>, val source: String = "") {
    companion object {
        
        const val PATH = "MyCar/AppCommon/Dynasty/HC2/HC_17E/Prefabs/NS15/CarBeam.gpref"
        fun load(reader: GpakReader?): VehicleBeamRig? = reader?.read(PATH)?.let {
            parse(JSONObject(it.toString(Charsets.UTF_8)), PATH)
        }
        fun parse(root: JSONObject, source: String = ""): VehicleBeamRig {
            val result = ArrayList<VehicleCone>()
            fun walk(node: JSONObject, parent: FloatArray) {
                fun tracks(key: String, fallback: Float): Array<AnimTrack?> = Array(3) { i ->
                    AnimTrack(floatArrayOf(0f), floatArrayOf(node.optJSONArray(key)
                        ?.optDouble(i, fallback.toDouble())?.toFloat() ?: fallback))
                }
                val local = NodeClip("beam", "beam", 0f, tracks("Pos", 0f),
                    tracks("Rot", 0f), tracks("Scale", 1f)).localAt(0f)
                val world = GwmNodeTree.multiply(parent, local)
                val name = node.optString("Name")
                val lamp = when {
                    name.startsWith("LowLight_") -> VehicleLamp.LOW
                    name.startsWith("HighLight_") -> VehicleLamp.HIGH
                    else -> null
                }
                if (node.optString("Class") == "gw::engine::ConeVolumeLight" && lamp != null) {
                    val len = sqrt(world[8]*world[8]+world[9]*world[9]+world[10]*world[10]).coerceAtLeast(1e-6f)
                    result.add(VehicleCone(name, lamp, floatArrayOf(world[12],world[13],world[14]),
                        floatArrayOf(world[8]/len,world[9]/len,world[10]/len),
                        FloatArray(3) { node.optJSONArray("Color")?.optDouble(it,1.0)?.toFloat() ?: 1f },
                        node.getDouble("Angle").toFloat(), node.getDouble("Distance").toFloat(),
                        node.optDouble("Radius",.05).toFloat(),node.getDouble("Intensity").toFloat()))
                }
                val children = node.optJSONArray("Child")
                if (children != null) for (i in 0 until children.length()) {
                    children.optJSONObject(i)?.let { walk(it, world) }
                }
            }
            walk(root, FloatArray(16) { if (it % 5 == 0) 1f else 0f })
            return VehicleBeamRig(result, source)
        }
    }
}

object GroundPhotometry {
    fun attenuation(distance: Float, range: Float, cosAngle: Float, outerCos: Float): Float {
        if (distance >= range || range <= 0f || cosAngle <= outerCos) return 0f
        val x = (distance / range).coerceIn(0f, 1f)
        val window = (1f - x*x*x*x).let { it*it }
        val a = ((cosAngle-outerCos)/(1f-outerCos).coerceAtLeast(.0001f)).coerceIn(0f,1f)
        val edge = a*a*(3f-2f*a)
        return edge*window/(distance*distance+.16f)
    }
}
