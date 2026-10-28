package com.haystudio.vehiclelab

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

internal data class SettingsAnchor(val key:String,val index:Int)
internal data class SettingsChapter(val anchor:SettingsAnchor,val title:String,val icon:ImageVector)

@Composable
internal fun CompactChapterNavigation(chapters: List<SettingsChapter>, selected: String?,
    selectionReady: Boolean, expanded: Boolean, position: MutableFloatState, active: Boolean,
    onExpandedChange: (Boolean) -> Unit, onSelect: (SettingsChapter) -> Unit,
    modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val expandLabel = "展开分组侧栏"
    val collapseLabel = "收起分组侧栏"
    val interactions = remember { MutableInteractionSource() }
    val handleCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val transition = updateTransition(expanded, label = "chapterDrawer")
    val progress = transition.animateFloat(transitionSpec = { tween(260) }, label = "drawerProgress") {
        if (it) 1f else 0f
    }
    val contentAlpha = transition.animateFloat(transitionSpec = {
        if (targetState) tween(160, delayMillis = 80) else tween(120)
    }, label = "drawerContent") { if (it) 1f else 0f }
    val handleOpacity = animateFloatAsState(if (dragging) .7f else .9f, tween(120), label = "chapterHandleOpacity")
    BackHandler(enabled = expanded && active) { onExpandedChange(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val handleWidth = 28.dp.coerceAtMost(maxWidth)
        
        val inset = 24.dp.coerceAtMost(maxHeight / 4)
        val availableHeight = (maxHeight - inset * 2).coerceAtLeast(0.dp)
        val handleHeight = 40.dp.coerceAtMost(availableHeight)
        val panelWidth = 208.dp.coerceAtMost(maxWidth)
        val panelHeight = availableHeight.coerceAtMost((chapters.size * 52 + 12).dp)
            .coerceAtLeast(handleHeight)
        val travelPx = with(density) { (availableHeight - handleHeight).toPx() }
        DisposableEffect(expanded, travelPx, active) { onDispose { dragging = false } }
        val width by transition.animateDp(transitionSpec = { tween(260) }, label = "drawerWidth") {
            if (it) panelWidth else handleWidth
        }
        val height by transition.animateDp(transitionSpec = { tween(260) }, label = "drawerHeight") {
            if (it) panelHeight else handleHeight
        }
        
        val closedTop = with(density) { inset.toPx() + travelPx * position.floatValue }
        val openTop = with(density) { ((maxHeight - panelHeight) / 2).toPx() }
        val outline = remember(handleWidth, handleHeight, progress) {
            ChapterDrawerShape(handleWidth, handleHeight) { progress.value }
        }
        if (transition.currentState || transition.targetState) {
            Box(Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black.copy(alpha = .2f * progress.value))
            }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                onExpandedChange(false)
            })
        }
        Surface(Modifier.offset {
            IntOffset(0, (closedTop + (openTop - closedTop) * progress.value).roundToInt())
        }.width(width).height(height).chapterDrawerShadow(outline).graphicsLayer {
            alpha = handleOpacity.value + (1f - handleOpacity.value) * progress.value
        }, shape = outline,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Layout(content = {
                Box(Modifier.graphicsLayer { alpha = contentAlpha.value }) {
                    if (transition.currentState || transition.targetState) {
                        ChapterNavigation(chapters, selected, selectionReady, onSelect,
                            Modifier.fillMaxSize(), enabled = expanded && active)
                    }
                }
                Box(Modifier.clip(RoundedCornerShape(10.dp))
                    .onGloballyPositioned { handleCoordinates.value = it }
                    .pointerInput(expanded, travelPx, active) {
                        var dragStartY = 0f
                        var dragStartPosition = 0f
                        if (!expanded && active) detectDragGestures(
                            onDragStart = {
                                dragging = true
                                dragStartY = handleCoordinates.value?.localToRoot(it)?.y ?: 0f
                                dragStartPosition = position.floatValue
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                            onDrag = { change, _ ->
                                change.consume()

                                val rootY = handleCoordinates.value?.takeIf { it.isAttached }
                                    ?.localToRoot(change.position)?.y
                                if (travelPx > 0f && rootY != null) position.floatValue =
                                    (dragStartPosition + (rootY - dragStartY) / travelPx).coerceIn(0f, 1f)
                            })
                    }.clickable(interactionSource = interactions, indication = ripple(), enabled = active,
                        onClickLabel = if (expanded) collapseLabel else expandLabel) {
                        onExpandedChange(!expanded)
                    }.semantics { contentDescription = if (expanded) collapseLabel else expandLabel },
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.FormatListBulleted, null,
                        Modifier.size(22.dp).graphicsLayer { alpha = 1f - progress.value })
                    Icon(Icons.Outlined.ChevronLeft, null,
                        Modifier.size(20.dp).graphicsLayer { alpha = progress.value })
                }
            }) { children, constraints ->
                
                val navigationWidth = with(density) { (panelWidth - handleWidth).roundToPx() }.coerceAtLeast(0)
                val navigationHeight = with(density) { panelHeight.roundToPx() }.coerceAtLeast(0)
                val navigation = children[0].measure(Constraints.fixed(navigationWidth, navigationHeight))
                val handle = children[1].measure(Constraints.fixed(
                    with(density) { handleWidth.roundToPx() },
                    minOf(with(density) { handleHeight.roundToPx() }, constraints.maxHeight)))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    navigation.placeRelative(0, (constraints.maxHeight - navigation.height) / 2)
                    handle.placeRelative(constraints.maxWidth - handle.width, (constraints.maxHeight - handle.height) / 2)
                }
            }
        }
    }
}

@Composable
internal fun ChapterNavigation(chapters: List<SettingsChapter>, selected: String?, selectionReady: Boolean,
    onSelect: (SettingsChapter) -> Unit, modifier: Modifier, enabled: Boolean = true) {
    val scroll = rememberScrollState()
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val contentInset = 0
    val edge = with(density) { 12.dp.roundToPx() }
    val selectedBounds = rowBounds[selected]
    LaunchedEffect(selected, selectionReady, enabled, viewportHeight, selectedBounds, edge, contentInset) {
        if (!enabled || !selectionReady || selectedBounds == null || viewportHeight == 0) return@LaunchedEffect
        val targetOffset = settingsChapterScrollTarget(selectedBounds.top.roundToInt() + contentInset,
            selectedBounds.bottom.roundToInt() + contentInset, scroll.value, viewportHeight, scroll.maxValue, edge)

        if (targetOffset != scroll.value) scroll.animateScrollTo(targetOffset,
            spring(dampingRatio = 1f, stiffness = 450f))
    }
    var previousTarget by remember { mutableStateOf<Rect?>(null) }
    val target = rowBounds[selected].takeIf { selectionReady } ?: previousTarget
    SideEffect { if (target != null && target != previousTarget) previousTarget = target }

    val indicator = if (target != null) animateRectAsState(target,
        spring(dampingRatio = 1f, stiffness = 450f,
            visibilityThreshold = Rect(.5f, .5f, .5f, .5f)), label = "chapterIndicator") else null
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    Box(modifier.onSizeChanged { viewportHeight = it.height }
        
        .verticalScroll(scroll).padding(end=16.dp)) {
        Box(Modifier.matchParentSize().drawBehind {

            indicator?.value?.let { bounds ->
                drawRoundRect(indicatorColor, topLeft = bounds.topLeft, size = bounds.size,
                    cornerRadius = CornerRadius(12.dp.toPx()))
            }
        })
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chapters.forEach { chapter ->
                val active = chapter.anchor.key == selected
                val text = chapter.title
                val foreground by animateColorAsState(if (active) MaterialTheme.colorScheme.onSecondaryContainer else
                    MaterialTheme.colorScheme.onSurfaceVariant, spring(dampingRatio = 1f, stiffness = 450f),
                    label = "chapterForeground")
                CompositionLocalProvider(LocalContentColor provides foreground) {
                    Row(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInParent()
                        val bounds = Rect(position.x, position.y, position.x + coordinates.size.width,
                            position.y + coordinates.size.height)
                        if (rowBounds[chapter.anchor.key] != bounds) rowBounds[chapter.anchor.key] = bounds
                    }.clip(RoundedCornerShape(12.dp)).selectable(active, enabled = enabled, role = Role.Tab,
                        onClick = { onSelect(chapter) }).height(48.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(chapter.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(text, style = MaterialTheme.typography.labelLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

