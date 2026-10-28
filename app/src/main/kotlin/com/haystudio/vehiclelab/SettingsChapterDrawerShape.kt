package com.haystudio.vehiclelab

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal class ChapterDrawerShape(private val tabWidth: Dp, private val tabHeight: Dp,
    private val expansion: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val width = with(density) { tabWidth.toPx() }.coerceAtMost(size.width)
        val height = with(density) { tabHeight.toPx() }.coerceAtMost(size.height)
        val panelEnd = (size.width - width).coerceAtLeast(0f)
        val top = (size.height - height) / 2f
        val bottom = top + height
        val corner = minOf(with(density) { 10.dp.toPx() }, width / 2f, height / 2f)
        val path = Path()
        if (panelEnd < 1f) {
            val start = CornerRadius.Zero
            val end = CornerRadius(corner)
            val left = if (layoutDirection == LayoutDirection.Rtl) end else start
            val right = if (layoutDirection == LayoutDirection.Rtl) start else end
            return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height,
                topLeftCornerRadius = left, topRightCornerRadius = right,
                bottomRightCornerRadius = right, bottomLeftCornerRadius = left))
        }
        val panelCorner = minOf(with(density) { 12.dp.toPx() }, panelEnd / 2f, top / 2f)
        val join = minOf(with(density) { 8.dp.toPx() }, top / 2f, width / 4f) * expansion().coerceIn(0f, 1f)
        fun x(value: Float) = if (layoutDirection == LayoutDirection.Rtl) size.width - value else value
        path.apply {
            moveTo(x(0f), 0f)
            lineTo(x(panelEnd - panelCorner), 0f)
            quadraticBezierTo(x(panelEnd), 0f, x(panelEnd), panelCorner)
            lineTo(x(panelEnd), top - join)
            quadraticBezierTo(x(panelEnd), top, x(panelEnd + join), top)
            lineTo(x(size.width - corner), top)
            quadraticBezierTo(x(size.width), top, x(size.width), top + corner)
            lineTo(x(size.width), bottom - corner)
            quadraticBezierTo(x(size.width), bottom, x(size.width - corner), bottom)
            lineTo(x(panelEnd + join), bottom)
            quadraticBezierTo(x(panelEnd), bottom, x(panelEnd), bottom + join)
            lineTo(x(panelEnd), size.height - panelCorner)
            quadraticBezierTo(x(panelEnd), size.height, x(panelEnd - panelCorner), size.height)
            lineTo(x(0f), size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

internal fun Modifier.chapterDrawerShadow(shape: ChapterDrawerShape): Modifier =
    if (Build.VERSION.SDK_INT >= 29) shadow(4.dp, shape, clip = false)
    else drawWithCache {
        val path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        val strokes = (8 downTo 1).map { Stroke(width = it * 1.dp.toPx()) }
        onDrawBehind {
            
            translate(top = 1.dp.toPx()) {
                strokes.forEach { drawPath(path, Color.Black.copy(alpha = .014f), style = it) }
            }
        }
    }
