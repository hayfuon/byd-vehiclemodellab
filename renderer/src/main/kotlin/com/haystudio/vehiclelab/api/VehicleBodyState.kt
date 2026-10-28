package com.haystudio.vehiclelab.api

import com.haystudio.vehiclelab.data.VehicleCorner

/** 单门覆盖状态和节点行程；行程以 0 关闭、1 全开表示，null 清除该节点的外部覆盖。 */
data class VehicleBodyState(
    val doors: Map<VehicleCorner,Boolean> = emptyMap(),
    val windows: Map<VehicleCorner,Boolean> = emptyMap(),
    val progress: Map<String,Float?> = emptyMap()
) {
    fun validated():VehicleBodyState {
        require(progress.all{(node,value)->node.isNotBlank() && (value==null || value.isFinite() && value in 0f..1f)}) {
            "节点名称不能为空，行程必须为 0 至 1 或 null"
        }
        return copy(doors=doors.toMap(),windows=windows.toMap(),progress=progress.toMap())
    }
}
