package com.haystudio.vehiclelab.data

import org.json.JSONObject
import kotlin.math.atan
import kotlin.math.tan

data class DesktopCamera(
    val name: String,
    val armLength: Float,
    val pitch: Float,
    val yaw: Float,
    val target: FloatArray,
    val origin: FloatArray,
    val focalLength: Float,
    val sensorSize: Float,
    val sensorAspect: Float,
    val sensorMode: Int,
    val shiftX: Float,
    val shiftY: Float,
    val duration: Float
) {
    val verticalFovDegrees: Float get() {
        val height = if (sensorMode == 0) sensorSize / sensorAspect else sensorSize
        return Math.toDegrees(2.0 * atan(height / (2.0 * focalLength))).toFloat()
    }

    fun projection(aspect: Float, near: Float = 0.1f, far: Float = 300f): FloatArray {
        require(aspect.isFinite() && aspect > 0f && near > 0f && far > near)
        val f = (1.0 / tan(Math.toRadians(verticalFovDegrees.toDouble()) / 2.0)).toFloat()
        val sx = if (sensorMode == 0) shiftX else shiftX / sensorAspect
        val sy = if (sensorMode == 0) shiftY * sensorAspect else shiftY
        return floatArrayOf(
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            2f * sx, 2f * sy, -(far + near) / (far - near), -1f,
            0f, 0f, -2f * far * near / (far - near), 0f
        )
    }

    companion object {
        const val PATH = "UserResources/Configs/MyCar/CarDesk/Dynasty/HC2/Common/CameraControl/CameraLocation.json"

        fun parse(source: String, preset: String = "Default"): DesktopCamera {
            val entries = JSONObject(source).getJSONArray("UNFUSION")
            val value = (0 until entries.length()).map { entries.getJSONObject(it) }
                .firstOrNull { it.getString("Name") == preset }
                ?: throw IllegalArgumentException("原厂相机机位不存在：$preset")
            fun vec(key: String): FloatArray {
                val a = value.getJSONArray(key)
                require(a.length() == 3) { "相机向量长度无效：$key" }
                return FloatArray(3) { a.getDouble(it).toFloat().also { v -> require(v.isFinite()) } }
            }
            fun number(key: String): Float = value.getDouble(key).toFloat().also { require(it.isFinite()) }
            val angles = vec("ArmRot")
            require(angles[2] == 0f) { "尚未支持带横滚的原厂相机" }
            val target = vec("CameraTarget")
            val shift = value.getJSONArray("Shift")
            val result = DesktopCamera(
                preset, number("ArmLength"), angles[0], angles[1], target,
                if (value.has("ArmOrigin")) vec("ArmOrigin") else floatArrayOf(0f, 0f, 0f),
                number("FocalLength"), number("SensorSize"), number("SensorAspectRatio"),
                value.getInt("SensorSizeMode"), shift.getDouble(0).toFloat(), shift.getDouble(1).toFloat(),
                number("Duration")
            )
            require(result.armLength > 0f && result.focalLength > 0f && result.sensorSize > 0f &&
                result.sensorAspect > 0f && result.sensorMode in 0..1 && result.duration >= 0f &&
                result.shiftX.isFinite() && result.shiftY.isFinite()) { "原厂物理相机参数无效" }
            return result
        }
    }
}
