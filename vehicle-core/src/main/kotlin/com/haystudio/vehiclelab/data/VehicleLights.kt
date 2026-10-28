package com.haystudio.vehiclelab.data

enum class VehicleLamp(val token: String, val label: String, val keys: List<String>) {
    DRL("drl", "日行灯", listOf("LightDay", "LightDayLeft", "LightDayRight")),
    LOW("low", "近光灯", listOf("LightNear")),
    HIGH("high", "远光灯", listOf("LightLeftFar", "LightRightFar")),
    TURN_LEFT("turn_left", "左转向", listOf("LightTurnLeft")),
    TURN_RIGHT("turn_right", "右转向", listOf("LightTurnRight")),
    BRAKE("brake", "刹车灯", listOf("LightBreak")),
    FOG("fog", "后雾灯", listOf("LightFog")),
    LOGO("logo", "尾标灯", listOf("LightLogo")),
    REVERSE("reverse", "倒车灯", listOf("LightReversing")),
    CORNER_LEFT("corner_left", "左辅助灯", listOf("LightLeftCorner")),
    CORNER_RIGHT("corner_right", "右辅助灯", listOf("LightRightCorner")),
    ATMOSPHERE("atmosphere", "外部迎宾灯", listOf("LightAtmoOut")),
    AROUND("around", "环绕灯", listOf("LightDay", "LightDayLeft", "LightDayRight", "LightAround", "LightAdjustment", "LightRear")),
    ADAS_LEFT("adas_left", "左镜照地灯", listOf("LightAdasLeft")),
    ADAS_RIGHT("adas_right", "右镜照地灯", listOf("LightAdasRight")),
    REAR("rear", "后位灯·高位", listOf("LightHighIndependent")),
    ADJUSTMENT("adjustment", "灯光调节", listOf("LightDay", "LightDayLeft", "LightDayRight", "LightAround", "LightAdjustment", "LightRear")),
    POSITION("position", "位置灯", listOf("LightDay", "LightDayLeft", "LightDayRight", "LightAround", "LightAdjustment", "LightRear"));
}

data class VehicleLights(val mask: Long = 0) {
    init { require(mask >= 0 && mask ushr VehicleLamp.entries.size == 0L) }
    operator fun contains(lamp: VehicleLamp) = mask and (1L shl lamp.ordinal) != 0L
    fun toggle(lamp: VehicleLamp) = VehicleLights(mask xor (1L shl lamp.ordinal))
    fun with(lamp: VehicleLamp, enabled: Boolean): VehicleLights =
        if (enabled) VehicleLights(mask or (1L shl lamp.ordinal))
        else VehicleLights(mask and (1L shl lamp.ordinal).inv())
    fun keys(): Set<String> {
        val front = FrontLampState.resolve(this)
        return buildSet {
            VehicleLamp.entries.filter { it in this@VehicleLights }.forEach { addAll(it.keys) }
            if (front.enabled) addAll(VehicleLamp.DRL.keys)
            if (front.position) addAll(VehicleLamp.POSITION.keys)
        }
    }
    fun tokens(): String = VehicleLamp.entries.filter { it in this }.joinToString(",") { it.token }
    companion object {
        fun preset(value: Int) = VehicleLights(when { value >= 2 -> 7L; value == 1 -> 3L; else -> 0L })
        fun all() = VehicleLights(VehicleLamp.entries.fold(0L) { bits, lamp ->
            bits or (1L shl lamp.ordinal)
        })
        fun parse(text: String): VehicleLights {
            if (text.isBlank() || text == "off") return VehicleLights()
            var bits = 0L
            for (token in text.split(',')) {
                val lamp = requireNotNull(VehicleLamp.entries.find { it.token == token.trim() }) {
                    "未知实验灯光：$token"
                }
                bits = bits or (1L shl lamp.ordinal)
            }
            return VehicleLights(bits)
        }
    }
}
