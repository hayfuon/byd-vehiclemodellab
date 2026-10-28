package com.haystudio.vehiclelab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haystudio.vehiclelab.render.RenderDebugSnapshot
import java.util.Locale

@Composable
internal fun DebugOverlay(samples: List<RenderDebugSnapshot>, onClose: () -> Unit) {
    VehicleLabTheme {
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation=0.dp,shadowElevation=3.dp,
            modifier=Modifier.graphicsLayer { alpha=.8f;compositingStrategy=CompositingStrategy.Offscreen }) {
            Column(Modifier.heightIn(max=(LocalConfiguration.current.screenHeightDp-32).coerceAtLeast(240).dp)) {
                Row(Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,top=16.dp,bottom=8.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MonitorHeart,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary)
                    Text("调试信息",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleSmall)
                    IconButton(onClose,Modifier.size(32.dp)) { Icon(Icons.Outlined.Close,"关闭调试信息",Modifier.size(20.dp)) }
                }
                Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())
                    .padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val current=samples.lastOrNull()
                    if(current==null) {
                        Text("正在采样…",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val fps=remember(samples) { samples.map { it.fps } }
                        val memory=remember(samples) { samples.map { it.appPssMiB.toFloat() } }
                        DebugTrend("帧率", "${format(current.fps.toDouble())} FPS",fps,samples,60f,"FPS")
                        DebugTrend("应用内存 · PSS", "${format(current.appPssMiB,0)} MiB",memory,samples,8f,"MiB")
                        DebugSection(Icons.Outlined.Speed,"帧时与进程")
                        DebugValue("帧间隔 P95", "${format(current.frameP95Millis)} ms")
                        DebugValue("渲染提交", "${format(current.submissionMillis)} ms")
                        DebugValue("渲染线程 CPU", "${format(current.renderCpuMillis)} ms")
                        DebugValue("进程 CPU · 单核", current.processCpuPercent?.let { "${format(it,0)}%" } ?: "—")
                        DebugSection(Icons.Outlined.Memory,"内存")
                        val fraction=if(current.deviceTotalMiB>0)current.appPssMiB/current.deviceTotalMiB*100 else Double.NaN
                        DebugValue("应用占设备内存", "${format(fraction)}%")
                        DebugValue("Java / 原生堆", "${format(current.javaHeapMiB,0)} / ${format(current.nativeHeapMiB,0)} MiB")
                        DebugValue("图形 PSS", current.graphicsPssMiB?.let { "${format(it,0)} MiB" } ?: "不可用")
                        DebugValue("系统已用 / 总量", if(current.deviceTotalMiB>0)
                            "${format(current.deviceUsedMiB/1024,1)} / ${format(current.deviceTotalMiB/1024,1)} GiB" else "不可用")
                        DebugSection(Icons.Outlined.Layers,"渲染阶段 · CPU 提交")
                        DebugValue("场景 / 车辆", "${format(current.sceneMillis)} / ${format(current.carMillis)} ms")
                        DebugValue("灯光 / 天气气流", "${format(current.lightMillis)} / ${format(current.weatherMillis)} ms")
                        DebugValue("后处理", "${format(current.postMillis)} ms")
                        DebugSection(Icons.Outlined.ViewInAr,"模型资源")
                        DebugValue("已上传网格 / 三角面", "${count(current.meshes)} / ${count(current.triangles)}")
                        DebugValue("纹理 / 材质程序", "${count(current.textures)} / ${count(current.shaders)}")
                        DebugValue("几何 GPU 缓冲", "${format(current.geometryMiB)} MiB")
                        Text("近 30 秒 · 500 ms 采样\nCPU 100% = 1 核；PSS 为按比例分摊的进程内存。\nP95 取最近 120 帧；阶段耗时为最新帧 CPU 提交，GPU 耗时不可用。",
                            style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugSection(icon: ImageVector,title: String) {
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Icon(icon,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
        Text(title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DebugValue(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.labelSmall,fontFamily=FontFamily.Monospace,textAlign=TextAlign.End)
    }
}

@Composable
private fun DebugTrend(title:String,value:String,values:List<Float>,samples:List<RenderDebugSnapshot>,span:Float,unit:String) {
    val color=MaterialTheme.colorScheme.primary
    val guide=MaterialTheme.colorScheme.outlineVariant
    val range=remember(values) {debugChartRange(values,span)}
    val target=remember(values,samples,range) {
        val end=samples.lastOrNull()?.sampleNanos ?: 0L
        DebugTrendFrame(values.mapIndexed { index,v ->
            DebugTrendPoint(samples[index].sampleNanos,
                ((samples[index].sampleNanos-end+30_000_000_000L)/30_000_000_000.0).toFloat(),
                1f-(v-range.first)/(range.second-range.first))
        })
    }
    var morph by remember { mutableStateOf(DebugTrendMorph(target,target)) }
    val progress=remember { Animatable(1f) }
    LaunchedEffect(target) {
        val displayed=morph.frame(progress.value)
        morph=DebugTrendMorph(displayed,target)
        progress.snapTo(0f)
        progress.animateTo(1f,tween(320,easing=FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        DebugValue(title,value)
        Canvas(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(2.dp)).semantics { contentDescription="$title，最近30秒趋势" }) {
            drawLine(guide,Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx())
            drawLine(guide.copy(alpha=.5f),Offset(0f,size.height/2),Offset(size.width,size.height/2),.5.dp.toPx())
            val path=Path()
            val phase=progress.value
            var begun=false
            var previousX=0f
            var previousY=0f
            var lastPoint: Offset?=null
            for(point in morph.points) {
                val x=interpolate(point.startX,point.endX,phase)*size.width
                val y=interpolate(point.startY,point.endY,phase)*size.height
                if (!x.isFinite() || !y.isFinite()) { begun=false;lastPoint=null;continue }
                if(begun) {
                    // 水平切线连接相邻真实采样点，曲线圆润且不越过两个读数的取值范围。
                    val control=(x-previousX)/3f
                    path.cubicTo(previousX+control,previousY,x-control,y,x,y)
                } else path.moveTo(x,y)
                begun=true
                previousX=x
                previousY=y
                lastPoint=Offset(x,y)
            }
            drawPath(path,color,style=Stroke(width=1.5.dp.toPx(),cap=StrokeCap.Round,join=StrokeJoin.Round))
            lastPoint?.let {drawCircle(color,2.dp.toPx(),it)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("−30 s",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${format(range.first.toDouble(),0)}–${format(range.second.toDouble(),0)} $unit",
                style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun format(value:Double,digits:Int=1):String = if(value.isFinite()) String.format(Locale.ROOT,"%.${digits}f",value) else "—"
private fun count(value:Int):String = java.text.NumberFormat.getIntegerInstance().format(value)
