package com.haystudio.vehiclelab

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.haystudio.vehiclelab.data.*
import kotlin.math.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PaintPalettePanel(
    title:String,palette:CarPalette,selectedIndex:Int,trim:Boolean,selection:PaintSelection,
    selectPreset:(Int)->Unit,update:(PaintRegion,PaintStyle?)->Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var advanced by remember {mutableStateOf(false)}
    var region by remember {mutableStateOf(if(trim)PaintRegion.TRIM else PaintRegion.LOWER)}
    val available=palette.materials.mapNotNull(palette::regionOf).distinct()
    val material=palette.materials.indexOfFirst {palette.regionOf(it)==region}.coerceAtLeast(0)
    val selectedColor=palette.colors.getOrNull(selectedIndex)
    val fallback=PaintStyle(primary=PaintColor.fromLinear(selectedColor?.tints?.getOrNull(material)?.base ?: floatArrayOf(.1f,.2f,.15f)))
    val custom=selection.style(region)
    var draft by remember(custom,selectedIndex,region) { mutableStateOf(custom ?: fallback) }
    fun apply(value:PaintStyle) {draft=value.normalized();update(region,draft)}
    val hasCustom=if(trim)selection.trim!=null else selection.body!=null || selection.upper!=null
    val options=palette.colors.withIndex().let {entries ->
        if(trim)entries.distinctBy {it.value.tints.getOrNull(material)?.let {t->t.base.toList()+t.coat.toList()}} else entries
    }
    val representative=if(trim)options.firstOrNull {
        val a=it.value.tints.getOrNull(material);val b=selectedColor?.tints?.getOrNull(material)
        a!=null && b!=null && a.base.contentEquals(b.base) && a.coat.contentEquals(b.coat)
    }?.index else selectedIndex
    Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            options.forEach { (index,color) ->
                val active=!hasCustom && representative==index
                val source=color.tints.getOrNull(material)?.base ?: color.base
                val paint=PaintColor.fromLinear(source)
                val shape=RoundedCornerShape(10.dp)
                Box(Modifier.size(44.dp).clip(shape)
                    .clickable {selectPreset(index)}
                    .semantics {contentDescription=if(trim)"饰件预设 ${index+1}" else "车身预设 ${index+1}";role=Role.RadioButton;selected=active}) {
                    Canvas(Modifier.fillMaxSize()) {
                        if(!trim && PaintRegion.UPPER in available) {
                            val upperIndex=palette.materials.indexOfFirst{palette.regionOf(it)==PaintRegion.UPPER}
                            val lowerIndex=palette.materials.indexOfFirst{palette.regionOf(it)==PaintRegion.LOWER}
                            val upper=PaintColor.fromLinear(color.tints[upperIndex].base).asColor()
                            val lower=PaintColor.fromLinear(color.tints[lowerIndex].base).asColor()
                            drawRect(lower)
                            // 分界平均高度为 35%，下色面积占 65%。
                            drawPath(Path().apply {
                                moveTo(0f,0f);lineTo(size.width,0f)
                                lineTo(size.width,size.height*.15f)
                                lineTo(0f,size.height*.55f);close()
                            },upper)
                        } else drawRect(paint.asColor())
                    }
                    Box(Modifier.fillMaxSize().border(if(active)3.dp else 1.dp,
                        if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,shape))
                }
            }
        }
        OutlinedButton(onClick={
            expanded=!expanded
            if(expanded && custom==null)apply(fallback)
        },modifier=Modifier.fillMaxWidth().semantics {selected=hasCustom},
            border=androidx.compose.foundation.BorderStroke(if(hasCustom)2.dp else 1.dp,if(hasCustom)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
            Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(draft.primary.asColor(),if(draft.gradient)draft.secondary.asColor() else draft.primary.asColor()))))
            Text("自定义$title",Modifier.weight(1f).padding(horizontal=10.dp))
            Icon(if(expanded)Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,if(expanded)"收起${title}色盘" else "展开${title}色盘")
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(!trim && PaintRegion.UPPER in available) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(PaintRegion.LOWER,PaintRegion.UPPER).forEachIndexed {index,item ->
                            SegmentedButton(selected=region==item,onClick={region=item},shape=SegmentedButtonDefaults.itemShape(index,2),label={Text(item.label)})
                        }
                    }
                }
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    PaintFinish.entries.forEach {finish ->
                        FilterChip(selected=draft.finish==finish,onClick={apply(draft.withFinish(finish))},label={Text(finish.label)})
                    }
                }
                ColorEditor(if(draft.gradient)"起始颜色" else region.label+"颜色",draft.primary){apply(draft.copy(primary=it))}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("渐变",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    Switch(draft.gradient,{apply(draft.copy(gradient=it))})
                }
                if(draft.gradient) {
                    ColorEditor("结束颜色",draft.secondary){apply(draft.copy(secondary=it))}
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("前后","上下","左右").forEachIndexed {index,label ->
                            FilterChip(selected=draft.axis==index,onClick={apply(draft.copy(axis=index))},label={Text(label)})
                        }
                    }
                }
                TextButton(onClick={advanced=!advanced}) {Text(if(advanced)"收起材质细调" else "材质细调")}
                AnimatedVisibility(advanced) {
                    Column {
                        PaintSlider("粗糙度",draft.roughness,.04f..1f){apply(draft.copy(roughness=it))}
                        PaintSlider("金属度",draft.metallic){apply(draft.copy(metallic=it))}
                        PaintSlider("珠光强度",draft.pearl){apply(draft.copy(pearl=it))}
                        if(draft.gradient) {
                            PaintSlider("渐变中心",draft.gradientCenter){apply(draft.copy(gradientCenter=it))}
                            PaintSlider("过渡范围",draft.gradientWidth,.1f..1f){apply(draft.copy(gradientWidth=it))}
                        }
                    }
                }
                TextButton(onClick={update(region,null)}) {Text("恢复${region.label}预设")}
            }
        }
    }
}

private fun PaintColor.asColor()=Color(r.coerceIn(0f,1f),g.coerceIn(0f,1f),b.coerceIn(0f,1f))

@Composable
private fun ColorEditor(label:String,value:PaintColor,change:(PaintColor)->Unit) {
    var hex by remember(value){mutableStateOf(value.hex())}
    Text(label,style=MaterialTheme.typography.labelLarge)
    HueColorWheel(value,change,Modifier.fillMaxWidth().height(250.dp))
    OutlinedTextField(hex,{text ->hex=text.removePrefix("#").take(6).uppercase();PaintColor.fromHex(hex)?.let(change)},
        label={Text("HEX")},prefix={Text("#")},singleLine=true,modifier=Modifier.fillMaxWidth(),isError=PaintColor.fromHex(hex)==null)
}

/** 外环取色相，内方盘取饱和度与明度；手势由按下区域锁定，避免跨区跳色。 */
@Composable
internal fun HueColorWheel(value:PaintColor,change:(PaintColor)->Unit,modifier:Modifier) {
    fun hsvOf(color:PaintColor)=FloatArray(3).also {AndroidColor.RGBToHSV((color.r*255).toInt(),(color.g*255).toInt(),(color.b*255).toInt(),it)}
    var hsv by remember {mutableStateOf(hsvOf(value))}
    val notify by rememberUpdatedState(change)
    val latest by rememberUpdatedState(hsv)
    LaunchedEffect(value) {
        val updated=hsvOf(value)
        if(updated[1]<.001f || updated[2]<.001f)updated[0]=hsv[0]
        if(updated[2]<.001f)updated[1]=hsv[1]
        hsv=updated
    }
    val ringColors=listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)
    Canvas(modifier.semantics {contentDescription="色盘"}.pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val center=Offset(size.width/2f,size.height/2f)
            val outer=min(size.width,size.height)*.47f
            val inner=outer*ColorWheelGeometry.INNER
            val half=outer*ColorWheelGeometry.HALF
            val start=(down.position-center)/outer
            val region=ColorWheelGeometry.region(start.x,start.y)
            if(region==0)return@awaitEachGesture
            val ring=region==1
            fun update(position:Offset) {
                val current=latest.copyOf()
                val offset=position-center
                if(ring)current[0]=((atan2(offset.y,offset.x)*180f/PI.toFloat())+360f)%360f
                else {
                    current[1]=((offset.x+half)/(2*half)).coerceIn(0f,1f)
                    current[2]=(1f-(offset.y+half)/(2*half)).coerceIn(0f,1f)
                }
                hsv=current
                val rgb=AndroidColor.HSVToColor(current)
                notify(PaintColor(AndroidColor.red(rgb)/255f,AndroidColor.green(rgb)/255f,AndroidColor.blue(rgb)/255f))
            }
            update(down.position);down.consume()
            do {
                val event=awaitPointerEvent()
                val pointer=event.changes.firstOrNull{it.id==down.id} ?: break
                if(pointer.pressed) {
                    val point=(pointer.position-center)/outer
                    if(ColorWheelGeometry.region(point.x,point.y)==region)update(pointer.position)
                }
                pointer.consume()
            }while(event.changes.any{it.pressed})
        }
    }) {
        val outer=min(size.width,size.height)*.47f
        val inner=outer*ColorWheelGeometry.INNER
        val half=outer*ColorWheelGeometry.HALF
        drawCircle(Brush.sweepGradient(ringColors,center),radius=(outer+inner)*.5f,style=Stroke(outer-inner))
        val origin=center-Offset(half,half)
        val side=Size(half*2,half*2)
        val hue=Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0],1f,1f)))
        drawRect(Brush.horizontalGradient(listOf(Color.White,hue),origin.x,origin.x+side.width),origin,side)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black),origin.y,origin.y+side.height),origin,side)
        val theta=hsv[0]*PI.toFloat()/180f
        val ringPoint=center+Offset(cos(theta),sin(theta))*((inner+outer)*.5f)
        val point=origin+Offset(hsv[1]*side.width,(1-hsv[2])*side.height)
        for(p in listOf(ringPoint,point)) {
            drawCircle(Color.Black,7.dp.toPx(),p,style=Stroke(3.dp.toPx()))
            drawCircle(Color.White,7.dp.toPx(),p,style=Stroke(1.5.dp.toPx()))
        }
    }
}

@Composable
private fun PaintSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float> = 0f..1f,change:(Float)->Unit) {
    Row(Modifier.fillMaxWidth()) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
        Text("${(value*100).toInt()}%",style=MaterialTheme.typography.labelMedium)
    }
    Slider(value.coerceIn(range),change,valueRange=range)
}
