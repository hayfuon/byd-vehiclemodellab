package com.haystudio.vehiclelab

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

internal data class VehiclePanelSection(val title:String,val keywords:Array<String>,val icon:ImageVector)
internal val vehiclePanelSections=listOf(
    VehiclePanelSection("模型",arrayOf("模型","车型","资源","导入","导出","车辆"),Icons.Outlined.ViewModule),
    VehiclePanelSection("机位",arrayOf("机位","视角","驾驶","岛台","车内"),Icons.Outlined.PhotoCamera),
    VehiclePanelSection("车辆外观",arrayOf("车型","车身","外观","颜色","色盘","珠光","磨砂","金属","渐变","粗糙度","饰件","车牌","牌照"),Icons.Outlined.Palette),
    VehiclePanelSection("车身开合",arrayOf("动作","车门","车窗","天窗","遮阳帘","后视镜","盖板","把手","尾门","前盖"),Icons.Outlined.DoorFront),
    VehiclePanelSection("行驶模拟",arrayOf("行驶","车速","速度","方向盘","油门","刹车","驻车","挡位","倒车","前进"),Icons.Outlined.Speed),
    VehiclePanelSection("车辆灯光",arrayOf("灯光","日行灯","近光","远光","转向","尾灯"),Icons.Outlined.Lightbulb),
    VehiclePanelSection("充电模拟",arrayOf("充电","充电口","电量","连接","模拟充电","功率","电流"),Icons.Outlined.EvStation),
    VehiclePanelSection("座舱",arrayOf("座舱","空调","冷暖","风量","氛围灯","吹面","吹脚","除雾","普通风"),Icons.Outlined.Air),
    VehiclePanelSection("场景与地面",arrayOf("场景","时段","地板","地面","环境"),Icons.Outlined.Landscape),
    VehiclePanelSection("天气效果",arrayOf("天气","雨","雪","落叶","樱花","花瓣","降落","附着"),Icons.Outlined.Cloud),
    VehiclePanelSection("渲染与性能",arrayOf("调试信息","高级","HDR","抗锯齿","FXAA","调试","帧率","阴影","泛光","反射"),Icons.Outlined.Tune),
    VehiclePanelSection("关于",arrayOf("关于","版本","BYD VehicleModelLab","HayFuon","许可"),Icons.Outlined.Info)
)

internal data class PanelVisibleRow(val index:Int,val top:Int,val bottom:Int)

internal fun selectedPanelSectionAtViewport(
    anchors:List<String>, visible:List<PanelVisibleRow>, viewportStart:Int, viewportEnd:Int,
    current:String?, edgePx:Int=80, hysteresisPx:Int=32, atStart:Boolean=false, atEnd:Boolean=false
):String? = selectedPanelChapterAtViewport(anchors.mapIndexed { index,key -> SettingsAnchor(key,index) },
    visible,viewportStart,viewportEnd,current,edgePx,hysteresisPx,atStart,atEnd)

/** 按实际卡片索引确定分组，使用视口参考线与双向滞回防止选中抖动。 */
internal fun selectedPanelChapterAtViewport(
    anchors:List<SettingsAnchor>, visible:List<PanelVisibleRow>, viewportStart:Int, viewportEnd:Int,
    current:String?, edgePx:Int=80, hysteresisPx:Int=32, atStart:Boolean=false, atEnd:Boolean=false
):String? {
    if(anchors.isEmpty())return null
    if(visible.isEmpty())return current?.takeIf {key -> anchors.any {it.key==key}}?:anchors.first().key
    if(atStart)return anchors.first().key
    if(atEnd)return anchors.last().key
    val inset=edgePx.coerceIn(0,(viewportEnd-viewportStart).coerceAtLeast(0)/3)
    val contentStart=viewportStart+inset
    val reference=contentStart+((viewportEnd-inset-contentStart)*.38f).toInt()
    val row=visible.lastOrNull { it.top<=reference }?:visible.first()
    val candidate=(anchors.lastOrNull { it.index<=row.index }?:anchors.first()).key
    val old=anchors.indexOfFirst {it.key==current};val next=anchors.indexOfFirst {it.key==candidate}
    if(old<0||next==old)return candidate
    val boundaryIndex=if(next>old)anchors[next].index else anchors[old].index
    val boundary=visible.find { it.index==boundaryIndex }?.top
    if(boundary!=null && (next>old && boundary>reference-hysteresisPx ||
            next<old && boundary<reference+hysteresisPx))return current
    return candidate
}

internal data class VehiclePanelCard(val section: String, val title: String) {
    val key: String get() = "$section:$title"
}

internal fun vehiclePanelCards(sections: List<String>): List<VehiclePanelCard> = sections.flatMap { section ->
    val titles = when (section) {
        "模型" -> listOf("模型")
        "机位" -> listOf("观察机位")
        "车辆外观" -> listOf("车身颜色", "饰件颜色", "牌照样式")
        "车身开合" -> listOf("车门与车窗", "车顶开合", "舱盖与外部部件")
        "行驶模拟" -> listOf("行驶与转向")
        "车辆灯光" -> listOf("行驶照明", "信号与尾灯", "辅助照明", "转向灯节奏")
        "充电模拟" -> listOf("充电模拟")
        "座舱" -> listOf("阅读与行李厢照明", "内饰氛围灯", "空调气流")
        "场景与地面" -> listOf("场景与时段", "地面材质")
        "天气效果" -> listOf("天气效果")
        "渲染与性能" -> listOf("渲染质量与性能", "资源统计")
        "关于" -> listOf("关于")
        else -> emptyList()
    }
    titles.map { VehiclePanelCard(section, it) }
}

/** 搜索精确到卡片；归类和锚点共用同一份结果，避免空分组或跳转偏移。 */
internal fun vehiclePanelCardsForQuery(query:String):List<VehiclePanelCard> {
    val cards=vehiclePanelCards(vehiclePanelSections.map {it.title})
    if(query.isBlank())return cards
    val terms=mapOf(
        "模型" to "车型 资源 导入 导出 着色器",
        "观察机位" to "机位 视角 驾驶 岛台 车内 车外",
        "车身颜色" to "外观 配色 色盘 珠光 磨砂 金属 渐变 粗糙度 上色 下色",
        "饰件颜色" to "饰件 色盘 珠光 磨砂 金属 渐变",
        "牌照样式" to "车牌 牌照 文字 字体 图片 新能源 蓝牌",
        "车门与车窗" to "车门 车窗 全开 全关",
        "车顶开合" to "天窗 通风 遮阳帘",
        "舱盖与外部部件" to "尾门 前盖 后视镜 油箱 充电盖 门把手",
        "行驶与转向" to "行驶 模拟 车速 速度 方向盘 油门 刹车 驻车 挡位 倒车 前进 回正",
        "行驶照明" to "灯光 日行灯 位置灯 近光 远光",
        "信号与尾灯" to "灯光 转向 刹车 高位 后位 尾灯 倒车 车标 logo",
        "辅助照明" to "灯光 雾灯 辅助 迎宾 照地 环绕",
        "转向灯节奏" to "频率 流水 双闪 转向",
        "充电模拟" to "快充 慢充 连接 电量 功率 电流",
        "阅读与行李厢照明" to "座舱 车内 顶灯 阅读灯 前排 后排 后备厢 后备箱 行李厢 按钮 照明",
        "内饰氛围灯" to "座舱 氛围灯 亮度 色轮 颜色",
        "空调气流" to "座舱 空调 冷暖 风量 吹面 吹脚 除雾 普通风",
        "场景与时段" to "环境 天空 清晨 白天 夜晚",
        "地面材质" to "地板 纹理 混凝土 沥青 石砖 水磨石 砂岩 碎石",
        "天气效果" to "天气 雨 雪 落叶 樱花 花瓣 强度 附着 融化",
        "渲染质量与性能" to "高级 HDR 抗锯齿 FXAA 调试信息 帧率 阴影 泛光 反射 色调 运动模糊",
        "资源统计" to "网格 顶点 三角面 材质 纹理 容量 缓冲",
        "关于" to "版本 BYD VehicleModelLab HayFuon 许可"
    )
    return cards.filter { card -> card.title.contains(query,true) || card.section.contains(query,true) ||
        terms[card.title].orEmpty().split(' ').any {it.contains(query,true)} }
}
