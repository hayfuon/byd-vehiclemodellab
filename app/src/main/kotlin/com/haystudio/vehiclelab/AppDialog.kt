package com.haystudio.vehiclelab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private const val DialogExitMillis=220
private val DialogEasing=CubicBezierEasing(.4f,0f,.6f,1f)
private val DialogEnter=tween<Float>(260,easing=DialogEasing)
private val DialogExit=tween<Float>(DialogExitMillis,easing=DialogEasing)

/** 非输入型全局弹窗：窗口限高、固定标题、正文滚动及可中断的进退场。 */
@Composable
internal fun AppDialog(
    visible:Boolean,
    onDismissRequest:()->Unit,
    title:String,
    preferredWidth:Dp=560.dp,
    content:@Composable ()->Unit
) {
    val visibility=remember {MutableTransitionState(false)}
    visibility.targetState=visible
    if(!visibility.currentState && !visibility.targetState && visibility.isIdle)return
    var retainedTitle by remember {mutableStateOf(title)}
    SideEffect {if(visible)retainedTitle=title}
    val transition=rememberTransition(visibility,label="appDialog")
    Dialog(onDismissRequest={if(visible)onDismissRequest()},properties=DialogProperties(
        usePlatformDefaultWidth=false,decorFitsSystemWindows=true
    )) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        val dim=remember(window) {window?.attributes?.dimAmount?.coerceIn(0f,1f) ?: .32f}
        SideEffect {
            window?.let {
                it.attributes=it.attributes.apply {
                    flags=flags and android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                    dimAmount=0f
                    windowAnimations=0
                }
            }
        }
        val scrim=transition.animateFloat(transitionSpec={if(targetState)DialogEnter else DialogExit},label="dialogScrim") {
            if(it)dim else 0f
        }
        BoxWithConstraints(Modifier.fillMaxSize().drawBehind {drawRect(Color.Black.copy(alpha=scrim.value))}
            .pointerInput(visible) {
                if(!visible)awaitPointerEventScope {
                    while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach {it.consume()}
                }
            }
            .clickable(interactionSource=remember {MutableInteractionSource()},indication=null) {if(visible)onDismissRequest()}
            .padding(16.dp),contentAlignment=Alignment.Center) {
            val width=minOf(preferredWidth,maxWidth)
            val height=maxHeight*.8f
            transition.AnimatedVisibility(visible={it},
                enter=fadeIn(DialogEnter)+scaleIn(initialScale=.96f,animationSpec=DialogEnter),
                exit=fadeOut(DialogExit)+scaleOut(targetScale=.96f,animationSpec=DialogExit)) {
                Surface(modifier=Modifier.width(width).heightIn(max=height).testTag("app-dialog")
                    .clickable(interactionSource=remember {MutableInteractionSource()},indication=null) {},
                    shape=RoundedCornerShape(16.dp),tonalElevation=0.dp,shadowElevation=8.dp,
                    color=MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=MaterialTheme.colorScheme.onSurface) {
                    Column(Modifier.padding(start=16.dp,end=16.dp,top=4.dp,bottom=16.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(if(visible)title else retainedTitle,style=MaterialTheme.typography.titleMedium,
                                modifier=Modifier.weight(1f).semantics {heading()})
                            IconButton(onClick={if(visible)onDismissRequest()},modifier=Modifier.offset(x=8.dp).size(40.dp)) {
                                Icon(Icons.Outlined.Close,"关闭弹窗")
                            }
                        }
                        Box(Modifier.weight(1f,fill=false).padding(top=4.dp).verticalScroll(rememberScrollState())) {content()}
                    }
                }
            }
        }
    }
}
