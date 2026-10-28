package com.haystudio.vehiclelab

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.haystudio.vehiclelab.data.DrivingControls
import com.haystudio.vehiclelab.data.DriveGear
import com.haystudio.vehiclelab.data.AirflowMode
import com.haystudio.vehiclelab.data.CameraPreset
import com.haystudio.vehiclelab.data.ChargingSimulation
import com.haystudio.vehiclelab.data.ChargingSnapshot
import com.haystudio.vehiclelab.data.ChargingState
import com.haystudio.vehiclelab.data.ChargingPort
import com.haystudio.vehiclelab.data.PaintColor
import com.haystudio.vehiclelab.data.DayPeriod
import com.haystudio.vehiclelab.data.EnvironmentPreset
import com.haystudio.vehiclelab.data.GwmModel
import com.haystudio.vehiclelab.data.ModelRepository
import com.haystudio.vehiclelab.data.VehicleLamp
import com.haystudio.vehiclelab.data.VehicleLights
import com.haystudio.vehiclelab.data.WeatherMode
import com.haystudio.vehiclelab.render.CarRenderer
import com.haystudio.vehiclelab.render.PlateAppearance
import com.haystudio.vehiclelab.render.PlateStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val LocalPanelCardTitle = staticCompositionLocalOf<String?> { null }

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    if (LocalPanelCardTitle.current?.let { it != title } == true) return
    Column(Modifier.fillMaxWidth()) {
        Text(title,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary,
            modifier=Modifier.padding(horizontal=16.dp,vertical=12.dp).semantics { heading() })
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) { content() }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp)
        .toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange=onChange)
        .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f))
        Switch(checked=checked,enabled=enabled,onCheckedChange=null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, enabled:Boolean = true, onSelect: (Int) -> Unit) {
    if(labels.size in 2..3) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            labels.forEachIndexed { index,label ->
                SegmentedButton(selected=selected==index,onClick={if(index!=selected)onSelect(index)},enabled=enabled,
                    shape=SegmentedButtonDefaults.itemShape(index,labels.size),
                    label={Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)})
            }
        }
    } else {
        FlowRow(Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                FilterChip(selected=index==selected,onClick={if(enabled && index!=selected)onSelect(index)},
                    enabled=enabled,label={Text(label,maxLines=1,overflow=TextOverflow.Ellipsis)})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehiclePanel(
    renderer: CarRenderer,
    modelItems: List<ModelRepository.ModelInfo>,
    selectedModelKey: String?,
    modelLoading: Boolean,
    resourceBusy: Boolean,
    selectModel: (ModelRepository.ModelInfo) -> Unit,
    importResourcePack: () -> Unit,
    exportCurrentModel: () -> Unit,
    deleteImportedResource: (ModelRepository.ModelInfo) -> Unit,
    importShaderLibrary: () -> Unit,
    thumbnailLoader: suspend (ModelRepository.ModelInfo) -> GwmModel?,
    palette: List<com.haystudio.vehiclelab.data.CarColor>,
    selectedColor: Int,
    selectColor: (Int) -> Unit,
    dayPeriod: DayPeriod,
    selectPeriod: (DayPeriod) -> Unit,
    revision: Int,
    modelReady: Boolean,
    onLightsChanged: (VehicleLights) -> Unit,
    lights: VehicleLights,
    selectedEnvironment: EnvironmentPreset,
    selectEnvironment: (EnvironmentPreset) -> Unit,
    environmentLoading: Boolean,
    frameRateLimit: Int,
    onFrameRateLimitChanged: (Int) -> Unit,
    modelLoadError:String?=null,
    retryModel:()->Unit={}
) {
    VehicleLabTheme {
        var collapsed by rememberSaveable { mutableStateOf(true) }
        var query by rememberSaveable { mutableStateOf("") }
        var uiRevision by remember { mutableIntStateOf(0) }
        val parameterRevision=revision+uiRevision
        val refresh: () -> Unit = { uiRevision++ }
        val panelCards = remember(query) { vehiclePanelCardsForQuery(query) }
        val sectionLabels = vehiclePanelSections.filter { section -> panelCards.any { it.section==section.title } }
            .map { it.title to it.keywords }

        val loadingOffset = if (modelReady) 0 else 1
        val chapterAnchors = androidx.compose.runtime.remember(panelCards, loadingOffset) {
            sectionLabels.map { entry -> SettingsAnchor(entry.first, panelCards.indexOfFirst { it.section == entry.first } + loadingOffset) }
        }

        val wide=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp>=840
        val context=LocalContext.current
        val versionName=remember(context) {context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()}
        val panelShape = RoundedCornerShape(20.dp)
        val scrollState=rememberLazyListState()
        var activeSection by remember { mutableStateOf(sectionLabels.firstOrNull()?.first.orEmpty()) }
        var navigationTarget by remember { mutableStateOf<String?>(null) }
        val density=LocalDensity.current
        val edgePx=with(density){80.dp.roundToPx()}
        LaunchedEffect(navigationTarget, sectionLabels, loadingOffset) {
            val target = navigationTarget ?: return@LaunchedEffect
            val index = panelCards.indexOfFirst { it.section == target }
            try {
                if (index >= 0) {
                    val itemIndex = index + loadingOffset
                    val layout = scrollState.layoutInfo
                    val viewport = layout.viewportSize.height.toFloat()
                    val first = layout.visibleItemsInfo.firstOrNull()
                    val average = layout.visibleItemsInfo.map { it.size }.average().toFloat()
                    val distance = first?.let { estimatedSettingsScrollDistance(itemIndex,it.index,it.offset,average) } ?: 0f
                    if (layout.visibleItemsInfo.none { it.index == itemIndex } && viewport > 0 && abs(distance) > viewport * 4f) {
                        scrollState.scrollToItem(itemIndex, -edgePx)
                        androidx.compose.runtime.withFrameNanos { }
                    }
                    scrollState.smoothSettingsScrollToItem(itemIndex, targetInsetPx = edgePx)
                }
            } finally {
                if (navigationTarget == target) navigationTarget = null
            }
        }
        var sidebarExpanded by rememberSaveable { mutableStateOf(false) }
        val compactPosition=rememberSaveable { mutableFloatStateOf(.5f) }
        val hysteresisPx=with(density){32.dp.roundToPx()}
        LaunchedEffect(sectionLabels, modelReady) {
            snapshotFlow {
                // 程序跳转期间保持目标选中，避免沿途分组驱动侧栏反复滚动。
                val navigating = navigationTarget != null
                val layout=scrollState.layoutInfo
                Triple(layout.visibleItemsInfo.map { item ->
                    PanelVisibleRow(item.index,item.offset,item.offset+item.size)
                },layout.viewportStartOffset to layout.viewportEndOffset,
                    (!scrollState.canScrollBackward && !navigating) to (!scrollState.canScrollForward&&scrollState.canScrollBackward&&!navigating))
            }.collect { (visible,viewport,ends) ->
                if (navigationTarget != null) return@collect
                if (visible.isEmpty()) return@collect
                selectedPanelChapterAtViewport(chapterAnchors,visible,viewport.first,viewport.second,
                    activeSection.takeIf { sectionLabels.any { item->item.first==it } },edgePx,hysteresisPx,
                    atStart=ends.first,atEnd=ends.second)?.let { activeSection=it }
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shape = panelShape,
            modifier = Modifier.onSizeChanged { renderer.settingsPanelWidthPixels=if(collapsed)0f else it.width.toFloat()+with(density){32.dp.toPx()} }.clip(panelShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .animateContentSize(animationSpec=spring(dampingRatio=1f,stiffness=650f),alignment=Alignment.TopStart)
        ) {
            Column(
                Modifier
                    .width(if (collapsed) 56.dp else if(wide) 553.dp else 372.dp)
                    .height(if (collapsed) 56.dp else Dp.Unspecified)
                    .padding(if (collapsed) 4.dp else 16.dp)
            ) {
                if (!collapsed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("搜索") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = if (query.isNotEmpty()) ({
                                IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空搜索") }
                            }) else null,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsMorphButton(expanded=true,onClick={
                            collapsed=true
                            renderer.settingsPanelWidthPixels=0f
                        })
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    SettingsMorphButton(expanded=false,onClick={collapsed=false})
                }
                if (!collapsed && uiRevision >= 0) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(modifier=Modifier.padding(start=if(wide)165.dp else 0.dp).pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed=false)
                            navigationTarget=null
                        }
                    },state=scrollState,verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=androidx.compose.foundation.layout.PaddingValues(bottom=0.dp)) {
                        if (!modelReady) item("loading") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(modelIdentificationTitle(modelLoading), style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(6.dp))
                                    Text(if(modelLoading)"模型识别完成后可调整车辆参数。" else modelLoadError ?: "请导入模型资源包或重试。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(!modelLoading)TextButton(onClick=retryModel) { Text("重试") }
                                }
                            }
                        }
                        itemsIndexed(panelCards, key = { _, card -> card.key }, contentType = { _, card -> card.title }) { _, card ->
                            CompositionLocalProvider(LocalPanelCardTitle provides card.title) {
                                when (card.section) {
                                    "模型" -> ModelGroup(modelItems, selectedModelKey, modelLoading, resourceBusy, selectModel,
                                        importResourcePack, exportCurrentModel, thumbnailLoader, deleteImportedResource, importShaderLibrary)
                                    "机位" -> CameraGroup(renderer,revision)
                                    "车辆外观" -> AppearanceGroup(renderer,parameterRevision,palette,selectedColor,selectColor,refresh)
                                    "车身开合", "行驶模拟" -> ActionGroup(renderer,parameterRevision,refresh)
                                    "车辆灯光" -> LightGroup(renderer,lights,parameterRevision,refresh,onLightsChanged)
                                    "充电模拟" -> ChargingGroup(renderer,refresh,parameterRevision)
                                    "座舱" -> CabinGroup(renderer,parameterRevision)
                                    "场景与地面", "天气效果" -> SceneGroup(renderer,parameterRevision,dayPeriod,selectPeriod,selectedEnvironment,selectEnvironment,environmentLoading)
                                    "渲染与性能" -> DiagnosticGroup(renderer,parameterRevision,refresh,frameRateLimit,onFrameRateLimitChanged)
                                    "关于" -> AboutGroup(versionName)
                                }
                            }
                        }
                    }
                    if(sectionLabels.isNotEmpty()) {
                        val chapters=sectionLabels.mapIndexed { index,entry -> SettingsChapter(chapterAnchors[index],entry.first,vehiclePanelSections.first { it.title==entry.first }.icon) }
                        val navigate:(SettingsChapter)->Unit={ chapter ->
                            sidebarExpanded=false
                            activeSection=chapter.anchor.key
                            navigationTarget=chapter.anchor.key
                        }
                        if(wide) Row(Modifier.width(149.dp).fillMaxHeight()) {
                            ChapterNavigation(chapters,activeSection,true,navigate,Modifier.width(148.dp).fillMaxHeight())
                            VerticalDivider(Modifier.fillMaxHeight())
                        } else CompactChapterNavigation(chapters,activeSection,true,sidebarExpanded,compactPosition,
                            active=true,onExpandedChange={sidebarExpanded=it},onSelect=navigate)
                    }
                    }

                }
            }
        }
    }
}
}

@OptIn(ExperimentalLayoutApi::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ModelGroup(
    items: List<ModelRepository.ModelInfo>,
    selectedKey: String?,
    busy: Boolean,
    resourceBusy: Boolean,
    onSelect: (ModelRepository.ModelInfo) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    thumbnailLoader: suspend (ModelRepository.ModelInfo) -> GwmModel?,
    onDelete: (ModelRepository.ModelInfo) -> Unit,
    onImportShaders: () -> Unit
) {
    SectionCard("模型") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 3
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val shape = CardDefaults.shape
                Card(
                    modifier = Modifier.width(148.dp).clip(shape).combinedClickable(
                        enabled = !busy && !resourceBusy,
                        onClick = { if (!selected) onSelect(item) },
                        onLongClick = if(item.resourcePack.isNotEmpty()) ({onDelete(item)}) else null,
                        onLongClickLabel = if(item.resourcePack.isNotEmpty()) "删除导入模型" else null
                    ),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.5f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            VehicleModelThumbnail(item, thumbnailLoader)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            if (item.resourcePack.isEmpty()) "车机资源" else "导入资源",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onImport, enabled = !busy && !resourceBusy) { Text("导入资源包") }
            TextButton(onClick = onExport, enabled = selectedKey != null && !busy && !resourceBusy) { Text("导出当前模型") }
            TextButton(onClick = onImportShaders, enabled = !busy && !resourceBusy) { Text("导入着色器") }
        }
    }
}

@Composable
private fun AboutGroup(versionName:String) {
    SectionCard("关于") { AboutContent(versionName) }
}

@Composable
private fun LightGroup(renderer: CarRenderer, lights: VehicleLights, revision:Int, refresh: () -> Unit, onLightsChanged: (VehicleLights) -> Unit) {
    val sections = listOf(
        "行驶照明" to listOf(VehicleLamp.DRL, VehicleLamp.POSITION, VehicleLamp.LOW, VehicleLamp.HIGH),
        "信号与尾灯" to listOf(VehicleLamp.TURN_LEFT, VehicleLamp.TURN_RIGHT, VehicleLamp.BRAKE, VehicleLamp.REAR, VehicleLamp.REVERSE, VehicleLamp.LOGO),
        "辅助照明" to listOf(VehicleLamp.FOG, VehicleLamp.CORNER_LEFT, VehicleLamp.CORNER_RIGHT, VehicleLamp.ADAS_LEFT, VehicleLamp.ADAS_RIGHT, VehicleLamp.ATMOSPHERE)
    )
    sections.forEach { (title, lamps) ->
        SectionCard(title) {
            lamps.forEach { lamp ->
                ToggleRow(lamp.label, lamp in lights) { enabled ->
                    onLightsChanged(lights.with(lamp, enabled));refresh()
                }
            }
        }
    }
    SectionCard("转向灯节奏") {
        var frequency by remember(renderer,revision) { mutableFloatStateOf(renderer.turnFrequencyPerMinute) }
        Text("转向灯频率 · ${frequency.roundToInt()} 次/分钟", style=MaterialTheme.typography.bodyMedium)
        Slider(frequency,{ frequency=it;renderer.turnFrequencyPerMinute=it },valueRange=30f..180f)
        Button(onClick = { onLightsChanged(VehicleLights()); refresh() }, modifier = Modifier.fillMaxWidth()) { Text("全部熄灭") }
    }
}

@Composable
private fun ActionGroup(renderer: CarRenderer, revision: Int, refresh: () -> Unit) {
    var sunroofOpen by remember(renderer, revision) { mutableStateOf(renderer.sunroofOpen) }
    var sunroofVentOpen by remember(renderer, revision) { mutableStateOf(renderer.sunroofVentOpen) }
    var curtainOpen by remember(renderer, revision) { mutableStateOf(renderer.curtainOpen) }
    var trunkOpen by remember(renderer, revision) { mutableStateOf(renderer.trunkOpen) }
    var hoodOpen by remember(renderer, revision) { mutableStateOf(renderer.hoodOpen) }
    var mirrorsExpanded by remember(renderer, revision) { mutableStateOf(renderer.mirrorsExpanded) }
    var leftChargerOpen by remember(renderer, revision) { mutableStateOf(renderer.leftChargerOpen) }
    var rightChargerOpen by remember(renderer, revision) { mutableStateOf(renderer.rightChargerOpen) }
    var handlesExtended by remember(renderer, revision) { mutableStateOf(renderer.handlesExtended) }
    var drivingSimulation by remember(renderer, revision) { mutableStateOf(renderer.drivingSimulation) }
    SectionCard("车门与车窗") {
        CornerControls("车门",revision,renderer::doorOpen,{ corner,open -> renderer.setDoorOpen(corner,open);refresh() },
            { renderer.doorsOpen=it;renderer.notifyTouch();refresh() })
        CornerControls("车窗",revision,renderer::windowOpen,{ corner,open -> renderer.setWindowOpen(corner,open);refresh() },
            { renderer.windowsOpen=it;renderer.notifyTouch();refresh() })
    }
    SectionCard("车顶开合") {
        ToggleRow("天窗", sunroofOpen) { sunroofOpen = it; renderer.sunroofOpen = it; if (it) { sunroofVentOpen = false }; refresh() }
        ToggleRow("天窗通风", sunroofVentOpen) { sunroofVentOpen = it; renderer.sunroofVentOpen = it; if (it) { sunroofOpen = false }; refresh() }
        ToggleRow("遮阳帘", curtainOpen) { curtainOpen = it; renderer.curtainOpen = it; refresh() }
    }
    SectionCard("舱盖与外部部件") {
        ToggleRow("尾门", trunkOpen) { trunkOpen = it; renderer.trunkOpen = it; refresh() }
        ToggleRow("前盖", hoodOpen) { hoodOpen = it; renderer.hoodOpen = it; refresh() }
        ToggleRow("后视镜展开", mirrorsExpanded) { mirrorsExpanded = it; renderer.mirrorsExpanded = it; refresh() }
        ToggleRow("左油箱盖", leftChargerOpen) { leftChargerOpen = it; renderer.leftChargerOpen = it; refresh() }
        ToggleRow("右充电盖", rightChargerOpen,
            enabled = !rightChargerOpen || ChargingSimulation.canClosePort(renderer.chargingState)
        ) { rightChargerOpen = it; renderer.rightChargerOpen = it; refresh() }
        ToggleRow("门把手伸出", handlesExtended) { handlesExtended = it; renderer.handlesExtended = it; refresh() }
    }
    SectionCard("行驶与转向") {
        ToggleRow("行驶模拟", drivingSimulation) { drivingSimulation = it; renderer.drivingSimulation = it; refresh() }
        DrivingControlsPanel(renderer,revision)

    }

}

@Composable
private fun DrivingControlsPanel(renderer:CarRenderer,revision:Int) {
    var controls by remember(renderer,revision) {mutableStateOf(renderer.drivingControls)}
    fun applyControls(value:DrivingControls) {controls=value.normalized();renderer.applyDrivingControls(controls)}
    ChoiceRow(listOf("倒车", "驻车", "前进"),controls.gear.ordinal) {
        applyControls(controls.copy(gear=DriveGear.entries[it]))
    }
    var steering by remember(renderer,revision) {mutableFloatStateOf(renderer.steeringAngleDegrees ?: 0f)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            Text("方向盘 · ${steering.roundToInt()}°",style=MaterialTheme.typography.labelLarge)
            TextButton(onClick={steering=0f;renderer.steeringAngleDegrees=0f}) {Text("回正")}
        }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
        androidx.compose.material3.Slider(value=steering,onValueChange={steering=it;renderer.steeringAngleDegrees=it},valueRange=-450f..450f,
            modifier=Modifier.width(240.dp))
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        PedalControl("刹车",controls.brake,Modifier.width(76.dp),140.dp) {applyControls(controls.copy(brake=it))}
        Spacer(Modifier.width(24.dp))
        DrivingSpeedReadout(renderer)
        Spacer(Modifier.width(24.dp))
        PedalControl("油门",controls.throttle,Modifier.width(76.dp),140.dp) {applyControls(controls.copy(throttle=it))}
    }
}

@Composable
private fun ChargingGroup(renderer: CarRenderer, refresh: () -> Unit, revision: Int) {
    var snapshot by remember(renderer) { mutableStateOf(renderer.chargingSnapshot()) }
    var selectedPort by remember(renderer) { mutableStateOf(renderer.selectedChargingPort) }
    androidx.compose.runtime.LaunchedEffect(renderer.selectedChargingPort, revision) {
        selectedPort = renderer.selectedChargingPort
    }
    var power by remember(renderer, selectedPort) { mutableFloatStateOf(renderer.chargingPowerKw) }
    androidx.compose.runtime.LaunchedEffect(revision, selectedPort) { power=renderer.chargingPowerKw }
    val listener: (ChargingSnapshot) -> Unit = { snapshot = it }
    DisposableEffect(renderer) {
        renderer.onChargingSnapshot = listener
        onDispose { if (renderer.onChargingSnapshot === listener) renderer.onChargingSnapshot = null }
    }
    SectionCard("充电模拟") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = selectedPort == ChargingPort.FAST,
                onClick = { selectedPort = ChargingPort.FAST; renderer.selectedChargingPort = selectedPort; refresh() },
                enabled = snapshot.state == ChargingState.DISCONNECTED,
                label = { Text("大快充") })
            FilterChip(selected = selectedPort == ChargingPort.SLOW,
                onClick = { selectedPort = ChargingPort.SLOW; renderer.selectedChargingPort = selectedPort; refresh() },
                enabled = snapshot.state == ChargingState.DISCONNECTED,
                label = { Text("小慢充") })
        }
        Text("充电功率 · ${String.format(java.util.Locale.ROOT,"%.1f",power)} kW", style=MaterialTheme.typography.titleSmall)
        Slider(value=power, onValueChange={ power=it; renderer.chargingPowerKw=it },
            valueRange=0f..com.haystudio.vehiclelab.data.ChargingFlowProfile.limit(selectedPort),
            modifier=Modifier.fillMaxWidth())
        Text(
            when (snapshot.state) {
                ChargingState.DISCONNECTED -> "未连接"
                ChargingState.CONNECTED -> "已连接 · ${snapshot.stateOfChargePercent.toInt()}%"
                ChargingState.CHARGING -> "充电中 · ${snapshot.stateOfChargePercent.toInt()}%"
                ChargingState.COMPLETE -> "充电完成 · 100%"
            },
            style = MaterialTheme.typography.titleSmall
        )
        if (snapshot.state != ChargingState.DISCONNECTED) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { snapshot.stateOfChargePercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            when (snapshot.state) {
                ChargingState.DISCONNECTED -> Button(
                    onClick = { renderer.connectCharging(); refresh() },
                    modifier = Modifier.weight(1f)
                ) { Text("模拟连接") }
                ChargingState.CONNECTED -> Button(
                    onClick = { renderer.startCharging(); refresh() },
                    modifier = Modifier.weight(1f)
                ) { Text("开始充电") }
                ChargingState.CHARGING -> Button(
                    onClick = { renderer.stopCharging(); refresh() },
                    modifier = Modifier.weight(1f)
                ) { Text("暂停") }
                ChargingState.COMPLETE -> Spacer(Modifier.weight(1f))
            }
            if (snapshot.state != ChargingState.DISCONNECTED) {
                TextButton(onClick = { renderer.disconnectCharging(); refresh() }, modifier = Modifier.weight(1f)) {
                    Text("断开")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable
private fun CornerControls(title:String,revision:Int,selected:(com.haystudio.vehiclelab.data.VehicleCorner)->Boolean,
    change:(com.haystudio.vehiclelab.data.VehicleCorner,Boolean)->Unit,all:(Boolean)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(title,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            TextButton(onClick={all(true)}) { Text("全开") }
            TextButton(onClick={all(false)}) { Text("全关") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            com.haystudio.vehiclelab.data.VehicleCorner.entries.forEach { corner ->
                var active by remember(revision,corner) {mutableStateOf(selected(corner))}
                FilterChip(selected=active,onClick={active=!active;change(corner,active)},modifier=Modifier.weight(1f),
                    label={Text(corner.label,maxLines=1)},
                    colors=androidx.compose.material3.FilterChipDefaults.filterChipColors())
            }
        }
    }
}

@Composable
private fun AppearanceGroup(renderer: CarRenderer, revision: Int,
    palette: List<com.haystudio.vehiclelab.data.CarColor>, selectedColor: Int,
    selectColor: (Int) -> Unit, refresh: () -> Unit) {
    var appearance by remember(renderer, revision) { mutableStateOf(renderer.plateAppearance) }
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var imageStatus by remember { mutableStateOf("") }
    fun applyPlate(value: PlateAppearance) { appearance=value;renderer.plateAppearance=value;renderer.notifyTouch() }
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            imageStatus="正在读取图片…"
            val bytes=withContext(Dispatchers.IO) { runCatching {
                val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
                require(options.outWidth>0 && options.outHeight>0) { "无法识别图片" }
                options.inSampleSize=1
                while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2
                options.inJustDecodeBounds=false
                val bitmap=context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,options) } ?: error("读取失败")
                java.io.ByteArrayOutputStream().use { output -> bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();output.toByteArray() }
            } }
            bytes.onSuccess { applyPlate(renderer.plateAppearance.copy(image=it));imageStatus="图片已应用到前后原始牌面" }
                .onFailure { imageStatus="图片读取失败，请选择有效图片" }
        }
    }
    SectionCard("车身颜色") {
        renderer.palette?.let { resourcePalette ->
            PaintPalettePanel("车身颜色",resourcePalette,selectedColor,false,renderer.paintSelection,
                {selectColor(it);refresh()},{region,style ->renderer.customizePaint(region,style);refresh()})
        }
    }
    SectionCard("饰件颜色") {
        if (renderer.palette?.let {p ->p.materials.any {p.regionOf(it)==com.haystudio.vehiclelab.data.PaintRegion.TRIM}}==true) {
            PaintPalettePanel("饰件颜色",renderer.palette!!,renderer.trimColorIndex,true,renderer.paintSelection,
                {renderer.selectTrimColor(it);refresh()},{region,style ->renderer.customizePaint(region,style);refresh()})
        } else Text("当前模型未提供独立饰件配色")
    }
    SectionCard("牌照样式") {
        ChoiceRow(PlateStyle.entries.map { it.label },PlateStyle.entries.indexOf(appearance.style)) {
            applyPlate(PlateAppearance(style=PlateStyle.entries[it]))
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(appearance.text,{ applyPlate(appearance.copy(text=it.take(12).uppercase(),image=null)) },
            label={Text("车牌文字")},singleLine=true,modifier=Modifier.fillMaxWidth())
        @Composable fun colorField(label:String, value:FloatArray, update:(FloatArray)->Unit) {
            val hex=value.joinToString("") { "%02X".format((it*255).roundToInt().coerceIn(0,255)) }
            var input by remember(hex) { mutableStateOf(hex) }
            androidx.compose.material3.OutlinedTextField(input,{ raw ->
                input=raw.removePrefix("#").take(6).uppercase()
                if(input.matches(Regex("[0-9A-F]{6}"))) update(FloatArray(3){ input.substring(it*2,it*2+2).toInt(16)/255f })
            },label={Text(label)},prefix={Text("#")},singleLine=true,
                isError=input.length!=6 || !input.matches(Regex("[0-9A-F]{6}")),modifier=Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        colorField("底色 RGB",appearance.base) { applyPlate(appearance.copy(base=it,image=null)) }
        Spacer(Modifier.height(8.dp))
        colorField("文字颜色 RGB",appearance.textColor) { applyPlate(appearance.copy(textColor=it,image=null)) }
        Text("字体大小 · ${(appearance.textScale*100).roundToInt()}%")
        Slider(appearance.textScale,{applyPlate(appearance.copy(textScale=it,image=null))},valueRange=.65f..1.15f)
        Text("字宽 · ${(appearance.characterSpacing*100).roundToInt()}%")
        Slider(appearance.characterSpacing,{applyPlate(appearance.copy(characterSpacing=it,image=null))},valueRange=.7f..1.25f)
        Button({imagePicker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()) { Text("选择图片车牌") }
        if(appearance.image!=null) {
            ToggleRow("图片铺满裁切（关闭则完整显示）",appearance.cropImage) { applyPlate(appearance.copy(cropImage=it)) }
            TextButton({applyPlate(appearance.copy(image=null));imageStatus=""}) { Text("恢复文字车牌") }
        }
        if(imageStatus.isNotEmpty()) Text(imageStatus,style=MaterialTheme.typography.bodySmall)
    }

}

@Composable
private fun CameraGroup(renderer:CarRenderer,revision:Int) {
    var selected by remember(renderer,revision){mutableStateOf(renderer.selectedCameraPreset)}
    SectionCard("观察机位") {
        Text("车外",style=MaterialTheme.typography.labelLarge)
        val outside=CameraPreset.entries.filter { !it.interior }
        ChoiceRow(outside.map { it.label },outside.indexOf(selected)) { selected=outside[it];renderer.selectCameraPreset(selected) }
        Spacer(Modifier.height(8.dp))
        Text("车内",style=MaterialTheme.typography.labelLarge)
        val inside=listOf(CameraPreset.CABIN_LEFT_FRONT,CameraPreset.CABIN,CameraPreset.CABIN_RIGHT_FRONT,CameraPreset.CABIN_LEFT_REAR,CameraPreset.ISLAND,CameraPreset.CABIN_RIGHT_REAR)
        ChoiceRow(inside.map { it.label.removePrefix("车内") },inside.indexOf(selected)) { selected=inside[it];renderer.selectCameraPreset(selected) }
    }
}

@Composable
private fun CabinGroup(renderer:CarRenderer,revision:Int) {
    var interiorAmbientEnabled by remember(renderer,revision){mutableStateOf(renderer.interiorAmbientEnabled)}
    SectionCard("阅读与行李厢照明") {
        var lamps by remember(renderer,revision){mutableStateOf(renderer.readingLamps)}
        com.haystudio.vehiclelab.data.CabinReadingLamp.entries.forEach {lamp ->
            ToggleRow(lamp.label,lamp in lamps){enabled ->
                lamps=if(enabled)lamps+lamp else lamps-lamp
                renderer.readingLamps=lamps
            }
        }
        var luggage by remember(renderer,revision){mutableStateOf(renderer.luggageLampEnabled)}
        ToggleRow("后备厢照明",luggage){luggage=it;renderer.luggageLampEnabled=it}
    }
    SectionCard("内饰氛围灯") {
        ToggleRow("座舱氛围灯", interiorAmbientEnabled) { interiorAmbientEnabled = it; renderer.interiorAmbientEnabled = it;  }
        var brightness by remember(renderer, revision) { mutableFloatStateOf(renderer.interiorAmbientBrightness * 100f) }
        Text("氛围灯亮度 · ${brightness.roundToInt()}%")
        Slider(value = brightness, onValueChange = { brightness = it; renderer.interiorAmbientBrightness = it / 100f }, valueRange = 0f..100f)
        var ambientColor by remember(renderer, revision) {
            mutableStateOf(PaintColor.fromLinear(renderer.interiorAmbientColor))
        }
        Text("氛围灯颜色", style = MaterialTheme.typography.labelLarge)
        HueColorWheel(ambientColor, { color ->
            ambientColor = color
            renderer.interiorAmbientColor = color.linear()
        }, Modifier.fillMaxWidth().height(210.dp))
    }
    SectionCard("空调气流") {
        var mode by remember(renderer,revision){mutableStateOf(renderer.airflowMode)}
        var fan by remember(renderer,revision){mutableFloatStateOf(renderer.airflowFan)}
        ChoiceRow(AirflowMode.entries.map { it.label },AirflowMode.entries.indexOf(mode)) {mode=AirflowMode.entries[it];renderer.airflowMode=mode}
        var directions by remember(renderer,revision){mutableStateOf(renderer.airflowDirections)}
        com.haystudio.vehiclelab.data.AirflowDirection.entries.forEach { direction ->
            ToggleRow(direction.label,direction in directions) { enabled ->
                directions=if(enabled)directions+direction else directions-direction
                renderer.airflowDirections=directions
            }
        }
        Text("风量 · ${(fan*100).roundToInt()}%")
        Slider(fan,{fan=it;renderer.airflowFan=it},valueRange=0f..1f)
    }
}

@Composable
private fun SceneGroup(renderer:CarRenderer,revision:Int,dayPeriod:DayPeriod,selectPeriod:(DayPeriod)->Unit,environment:EnvironmentPreset,selectEnvironment:(EnvironmentPreset)->Unit,loading:Boolean) {
    var weather by remember(renderer,revision){mutableStateOf(renderer.weatherMode)}
    var intensity by remember(renderer,revision){mutableFloatStateOf(renderer.weatherIntensity)}
    var ground by remember(renderer,revision){mutableIntStateOf(renderer.groundMaterialStyle)}
    SectionCard("场景与时段") {
        ChoiceRow(EnvironmentPreset.entries.map { it.label },EnvironmentPreset.entries.indexOf(environment),enabled=!loading) { selectEnvironment(EnvironmentPreset.entries[it]) }
        if(loading) Text("正在载入场景…",style=MaterialTheme.typography.bodySmall)
        Text("场景时段",style=MaterialTheme.typography.labelLarge)
        ChoiceRow(DayPeriod.entries.map { it.label },DayPeriod.entries.indexOf(dayPeriod),enabled=environment==EnvironmentPreset.DESKTOP) {selectPeriod(DayPeriod.entries[it])}
        if(environment!=EnvironmentPreset.DESKTOP) Text("此环境使用日景光照",style=MaterialTheme.typography.bodySmall)
    }
    SectionCard("地面材质") {
        ChoiceRow(listOf("原厂","混凝土","沥青","石砖","水磨石","砂岩","碎石"),ground) {ground=it;renderer.groundMaterialStyle=it}
    }
    SectionCard("天气效果") {
        ChoiceRow(WeatherMode.entries.map { it.label },WeatherMode.entries.indexOf(weather)) {weather=WeatherMode.entries[it];renderer.weatherMode=weather}
        Text("降落强度 · ${(intensity*100).roundToInt()}%")
        Slider(intensity,{intensity=it;renderer.weatherIntensity=it},valueRange=0f..1f,enabled=weather!=WeatherMode.NONE)
        TextButton(onClick={renderer.clearSurfaceWeather()}) {Text("清除表面附着")}
    }
}

@Composable
private fun DiagnosticGroup(renderer: CarRenderer, revision: Int, refresh: () -> Unit,
    frameRateLimit: Int, onFrameRateLimitChanged: (Int) -> Unit) {
    var antiAliasing by remember(renderer, revision) { mutableStateOf(renderer.viewOptions.antiAliasing) }
    var hdr by remember(renderer, revision) { mutableStateOf(renderer.viewOptions.hdr) }
    var bloom by remember(renderer, revision) { mutableStateOf(renderer.viewOptions.bloom) }
    var planarReflection by remember(renderer, revision) { mutableStateOf(renderer.viewOptions.planarReflection) }
    var selfShadow by remember(renderer, revision) { mutableStateOf(renderer.viewOptions.selfShadow) }
    var toneMapping by remember(renderer, revision) { mutableStateOf(renderer.viewOptions.toneMapping) }
    var debugInfo by remember(renderer, revision) { mutableStateOf(renderer.debugInfoEnabled) }
    SectionCard("渲染质量与性能") {
        Text("帧率上限",style=MaterialTheme.typography.labelLarge)
        ChoiceRow(listOf("30 帧","60 帧","自动"),when(frameRateLimit) {30->0;60->1;else->2}) {
            onFrameRateLimitChanged(listOf(30,60,0)[it])
        }
        ToggleRow("车轮运动模糊",renderer.viewOptions.wheelMotionBlur) { renderer.viewOptions=renderer.viewOptions.copy(wheelMotionBlur=it);refresh() }
        ToggleRow("抗锯齿", antiAliasing) { antiAliasing = it; renderer.viewOptions = renderer.viewOptions.copy(antiAliasing = it); refresh() }
        ToggleRow("HDR", hdr) { hdr = it; renderer.viewOptions = renderer.viewOptions.copy(hdr = it); refresh() }
        ToggleRow("高亮泛光", bloom) { bloom = it; renderer.viewOptions = renderer.viewOptions.copy(bloom = it); refresh() }
        ToggleRow("平面反射", planarReflection) { planarReflection = it; renderer.viewOptions = renderer.viewOptions.copy(planarReflection = it); refresh() }
        ToggleRow("直射光自阴影", selfShadow) { selfShadow = it; renderer.viewOptions = renderer.viewOptions.copy(selfShadow = it); refresh() }
        ToggleRow("色调映射", toneMapping) { toneMapping = it; renderer.viewOptions = renderer.viewOptions.copy(toneMapping = it); refresh() }
        ToggleRow("调试信息", debugInfo) { debugInfo = it; renderer.debugInfoEnabled = it; refresh() }
    }
    SectionCard("资源统计") {
        fun count(value:Int)=java.text.NumberFormat.getIntegerInstance().format(value)
        fun bytes(value:Long)=String.format(java.util.Locale.ROOT,"%.2f MiB",value/1048576.0)
        renderer.resourceStats?.let { stats ->
            val rows=listOf(
                "网格 / 已上传" to "${count(stats.meshes)} / ${count(stats.uploadedMeshes)}",
                "顶点 / 三角面" to "${count(stats.vertices)} / ${count(stats.triangles)}",
                "节点 / 材质" to "${count(stats.nodes)} / ${count(stats.materials)}",
                "动画 / 节点通道" to "${count(stats.animations)} / ${count(stats.animationChannels)}",
                "纹理文件" to count(stats.textureFiles),
                "纹理源数据" to bytes(stats.textureSourceBytes),
                "几何源数据" to bytes(stats.geometrySourceBytes),
                "几何 GPU 缓冲" to bytes(stats.geometryBufferBytes)
            )
            rows.forEach { (label,value) ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(label,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
                    Text(value,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(start=12.dp))
                }
            }
            HorizontalDivider()
            Text(renderer.textureStats,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } ?: Text("等待模型加载",style=MaterialTheme.typography.bodyMedium)
    }
}

