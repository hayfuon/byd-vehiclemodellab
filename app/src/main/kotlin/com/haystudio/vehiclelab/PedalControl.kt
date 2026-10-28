package com.haystudio.vehiclelab

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

/** 纵向踏板保留松手后的设定深度，数值与外部 0～1 接口一致。 */
@Composable
internal fun PedalControl(label:String,value:Float,modifier:Modifier=Modifier,height:androidx.compose.ui.unit.Dp=140.dp,change:(Float)->Unit) {
    val update by rememberUpdatedState(change)
    Column(modifier,verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                contentDescription=label+"深度"
                progressBarRangeInfo=ProgressBarRangeInfo(value,0f..1f)
                setProgress {update(it.coerceIn(0f,1f));true}
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down=awaitFirstDown();down.consume()
                    update((1f-down.position.y/size.height).coerceIn(0f,1f))
                    do {
                        val event=awaitPointerEvent()
                        val point=event.changes.firstOrNull{it.id==down.id} ?: break
                        if(point.pressed)update((1f-point.position.y/size.height).coerceIn(0f,1f))
                        point.consume()
                    }while(point.pressed)
                }
            }) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(value.coerceIn(0f,1f)).background(MaterialTheme.colorScheme.primaryContainer))
            Text("${(value*100).roundToInt()}%",Modifier.align(Alignment.Center),style=MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick={change(0f)},modifier=Modifier.fillMaxWidth()) {Text("松开")}
    }
}

@Composable
internal fun DrivingSpeedReadout(renderer:com.haystudio.vehiclelab.render.CarRenderer) {
    var speed by remember(renderer){mutableFloatStateOf(0f)}
    LaunchedEffect(renderer) {
        while(true) {
            withFrameNanos {
                val next=(kotlin.math.abs(renderer.drivingSnapshot()[1])*10).roundToInt()/10f
                if(next!=speed)speed=next
            }
        }
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text("%.1f".format(java.util.Locale.ROOT,speed),style=MaterialTheme.typography.headlineSmall)
        Text("km/h",style=MaterialTheme.typography.labelMedium)
    }
}
