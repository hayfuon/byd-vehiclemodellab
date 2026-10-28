package com.haystudio.vehiclelab

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
internal fun SettingsMorphButton(expanded:Boolean,onClick:()->Unit) {
    val progress by animateFloatAsState(if(expanded)1f else 0f,
        spring(dampingRatio=1f,stiffness=450f),label="设置关闭形变")
    val color=MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(onClick=onClick,modifier=Modifier.size(48.dp).semantics {
        contentDescription=if(expanded)"关闭设置" else "展开设置"
    }) {
        Canvas(Modifier.size(24.dp)) {
            val radius=size.minDimension*.46f
            val path=Path().apply {fillType=PathFillType.EvenOdd}
            repeat(128) {i->
                val angle=2*PI*i/128
                val gear=.84f+.13f*((cos(angle*8)+1)*.5).toFloat()
                val crossAngle=angle-PI/4
                val cx=abs(cos(crossAngle)).toFloat().coerceAtLeast(.0001f)
                val cy=abs(sin(crossAngle)).toFloat().coerceAtLeast(.0001f)
                val cross=min(1f,max(min(1f/cx,.16f/cy),min(.16f/cx,1f/cy)))
                val r=radius*(gear+(cross-gear)*progress)
                val x=center.x+r*cos(angle).toFloat();val y=center.y+r*sin(angle).toFloat()
                if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
            }
            path.close()
            val hole=radius*.32f*(1f-progress)
            if(hole>.01f)path.addOval(androidx.compose.ui.geometry.Rect(center.x-hole,center.y-hole,center.x+hole,center.y+hole))
            drawPath(path,color)
        }
    }
}
