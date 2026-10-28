package com.haystudio.vehiclelab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun modelIdentificationTitle(loading:Boolean)=if(loading)"正在识别车辆模型" else "模型未就绪"

@Composable
internal fun ModelLoadStatus(loading:Boolean,ready:Boolean,stage:String,error:String?,onRetry:()->Unit) {
    VehicleLabTheme {
        AnimatedVisibility(visible=!ready,enter=fadeIn(tween(160)),exit=fadeOut(tween(240))) {
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                Surface(shape=RoundedCornerShape(24.dp),tonalElevation=3.dp,
                    modifier=Modifier.padding(32.dp).widthIn(max=400.dp)) {
                    Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        if(loading || ready)CircularProgressIndicator()
                        Text(if(loading || ready)stage else if(error!=null)"模型载入失败" else "暂无车辆模型",style=MaterialTheme.typography.titleMedium)
                        if(!loading && !ready) {
                            Text(error ?: "未找到可用车辆模型",style=MaterialTheme.typography.bodyMedium)
                            Button(onClick=onRetry) { Text("重试") }
                        }
                    }
                }
            }
        }
    }
}
