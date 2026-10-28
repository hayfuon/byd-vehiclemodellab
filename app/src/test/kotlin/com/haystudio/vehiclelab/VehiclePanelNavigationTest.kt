package com.haystudio.vehiclelab

import org.junit.Assert.*
import org.junit.Test

class VehiclePanelNavigationTest {
    @Test fun drivingWeatherAndBodyActionsHaveSeparateAnchors() {
        val cards=vehiclePanelCardsForQuery("")
        assertEquals("行驶模拟",cards.single{it.title=="行驶与转向"}.section)
        assertEquals("天气效果",cards.single{it.title=="天气效果"}.section)
        assertEquals("车身开合",cards.single{it.title=="车顶开合"}.section)
        assertEquals(cards.size,cards.map{it.key}.toSet().size)
    }
    @Test fun searchFindsTheActualCardWithoutUnrelatedControls() {
        assertEquals(listOf("牌照样式"),vehiclePanelCardsForQuery("车牌").map{it.title})
        assertEquals(listOf("内饰氛围灯"),vehiclePanelCardsForQuery("氛围灯").map{it.title})
        assertEquals(listOf("行驶与转向"),vehiclePanelCardsForQuery("油门").map{it.title})
        assertEquals(listOf("渲染质量与性能"),vehiclePanelCardsForQuery("帧率").map{it.title})
    }

    @Test fun visibleCardsResolveToTheirActualChapterStartIndex() {
        val anchors=listOf(SettingsAnchor("机位",1),SettingsAnchor("车辆外观",2),SettingsAnchor("车身动作",5))
        val visible=listOf(PanelVisibleRow(3,-100,200),PanelVisibleRow(4,200,500),PanelVisibleRow(5,500,900))
        assertEquals("车辆外观",selectedPanelChapterAtViewport(anchors,visible,0,600,"车辆外观"))
        val shifted=listOf(PanelVisibleRow(4,-100,100),PanelVisibleRow(5,100,600))
        assertEquals("车身动作",selectedPanelChapterAtViewport(anchors,shifted,0,600,"车辆外观"))
    }

    @Test fun shortLastChapterSelectsAtTheNativeScrollLimit() {
        val anchors=listOf(SettingsAnchor("渲染与性能",14),SettingsAnchor("关于",16))
        assertEquals("关于", selectedPanelChapterAtViewport(anchors,listOf(PanelVisibleRow(15,-40,300),PanelVisibleRow(16,300,420)),0,600,"渲染与性能",atEnd=true))
    }

    @Test fun splitCardsKeepUniqueKeysAndSectionOrder() {
        val titles = vehiclePanelSections.map { it.title }
        val cards = vehiclePanelCards(titles)
        assertEquals(cards.size, cards.map { it.key }.distinct().size)
        assertEquals(titles, cards.map { it.section }.distinct())
        assertEquals(listOf("车身颜色", "饰件颜色", "牌照样式"),
            cards.filter { it.section == "车辆外观" }.map { it.title })
    }

    @Test fun filteredCardsKeepOnlyMatchingSectionAnchors() {
        val cards = vehiclePanelCards(listOf("座舱", "关于"))
        assertEquals(listOf("座舱", "座舱", "座舱", "关于"), cards.map { it.section })
        assertEquals("阅读与行李厢照明", cards.first().title)
        assertEquals("关于", cards.last().title)
    }
}
