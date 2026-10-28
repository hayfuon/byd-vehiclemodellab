package com.haystudio.vehiclelab.api

import com.haystudio.vehiclelab.data.*
import com.haystudio.vehiclelab.render.*

/** 面向宿主的画面配置；不包含演示面板、调试 UI 或 GPU 对象。 */
data class VehicleVisualState(
    val ambientColor: List<Float> = listOf(.025f,.21f,.66f),
    val airflowMode: AirflowMode = AirflowMode.OFF,
    val airflowFan: Float = .5f,
    val airflowDirections: Set<AirflowDirection> = setOf(AirflowDirection.FACE),
    val weather: WeatherMode = WeatherMode.NONE,
    val weatherIntensity: Float = .55f,
    val temperatureC: Float = -4f,
    val groundMaterial: Int = 0,
    val plate: PlateAppearance = PlateAppearance(),
    val plateEnabled: Boolean = true,
    val turnFrequencyPerMinute: Float = 90f,
    val groundLights: Boolean = true,
    val lampReflections: Boolean = true,
    val renderOptions: DesktopViewOptions = DesktopViewOptions()
) {
    fun normalized(): VehicleVisualState {
        fun finite(value:Float,fallback:Float)=value.takeIf(Float::isFinite) ?: fallback
        require(ambientColor.size==3){"氛围灯颜色需要三个线性色彩分量"}
        return copy(ambientColor=ambientColor.map{finite(it,0f).coerceIn(0f,1f)},
            airflowFan=finite(airflowFan,.5f).coerceIn(0f,1f),
            airflowDirections=airflowDirections.toSet(),
            weatherIntensity=finite(weatherIntensity,.55f).coerceIn(0f,1f),
            temperatureC=finite(temperatureC,-4f).coerceIn(-50f,60f),
            groundMaterial=groundMaterial.coerceIn(0,6),
            turnFrequencyPerMinute=finite(turnFrequencyPerMinute,90f).coerceIn(30f,240f),
            plate=plate.copy(base=plate.base.copyOf(),textColor=plate.textColor.copyOf(),image=plate.image?.copyOf()))
    }
}

enum class ChargingCommand { CONNECT, START, STOP, COMPLETE, DISCONNECT }
