package com.haystudio.vehiclelab.data

import kotlin.math.pow

data class PaintColor(val r:Float=.15f,val g:Float=.34f,val b:Float=.26f) {
    fun linear()=floatArrayOf(channel(r).pow(2.2f),channel(g).pow(2.2f),channel(b).pow(2.2f))
    fun hex()=listOf(r,g,b).joinToString("") { "%02X".format((channel(it)*255f+.5f).toInt()) }
    companion object {
        private fun channel(v:Float)=if(v.isFinite())v.coerceIn(0f,1f) else 0f
        fun fromLinear(value:FloatArray)=PaintColor(channel(value.getOrElse(0){0f}).pow(1f/2.2f),channel(value.getOrElse(1){0f}).pow(1f/2.2f),channel(value.getOrElse(2){0f}).pow(1f/2.2f))
        fun fromHex(value:String):PaintColor? {
            val text=value.removePrefix("#")
            if(!text.matches(Regex("[0-9a-fA-F]{6}")))return null
            return PaintColor(text.substring(0,2).toInt(16)/255f,text.substring(2,4).toInt(16)/255f,text.substring(4,6).toInt(16)/255f)
        }
    }
}

enum class PaintFinish(val label:String,val roughness:Float,val metallic:Float,val pearl:Float,val coat:Float) {
    GLOSS("亮面",.18f,.15f,0f,1.2f),METALLIC("金属",.24f,.8f,.1f,1f),
    PEARL("珠光",.22f,.35f,.65f,1.3f),MATTE("磨砂",.76f,.1f,0f,.08f)
}

enum class PaintRegion(val label:String) {LOWER("下车身"),UPPER("上车身"),TRIM("饰件")}

data class PaintStyle(
    val primary:PaintColor=PaintColor(),val secondary:PaintColor=PaintColor(.12f,.2f,.55f),
    val finish:PaintFinish=PaintFinish.GLOSS,val gradient:Boolean=false,val axis:Int=1,
    val roughness:Float=finish.roughness,val metallic:Float=finish.metallic,val pearl:Float=finish.pearl,
    val gradientCenter:Float=.5f,val gradientWidth:Float=1f
) {
    fun normalized()=copy(axis=axis.coerceIn(0,2),roughness=unit(roughness,.18f).coerceAtLeast(.04f),
        metallic=unit(metallic,.15f),pearl=unit(pearl,0f),gradientCenter=unit(gradientCenter,.5f),gradientWidth=unit(gradientWidth,1f).coerceAtLeast(.1f))
    fun withFinish(value:PaintFinish)=copy(finish=value,roughness=value.roughness,metallic=value.metallic,pearl=value.pearl)
    fun colorAt(position:Float):FloatArray {
        val p=normalized();val raw=((position-p.gradientCenter)/p.gradientWidth+.5f).coerceIn(0f,1f)
        val t=if(gradient)raw*raw*(3f-2f*raw) else 0f
        val a=primary.linear();val b=secondary.linear()
        return FloatArray(3){a[it]+(b[it]-a[it])*t}
    }
    private fun unit(value:Float,fallback:Float)=if(value.isFinite())value.coerceIn(0f,1f) else fallback
}

/** 车色选择只在输入发生时同步饰件默认值；饰件和自定义材质之后独立保存。 */
data class PaintSelection(val bodyIndex:Int=0,val trimIndex:Int=0,val body:PaintStyle?=null,val trim:PaintStyle?=null,val upper:PaintStyle?=null) {
    fun selectBody(index:Int)=copy(bodyIndex=index,trimIndex=index,body=null,trim=null,upper=null)
    fun selectTrim(index:Int)=copy(trimIndex=index,trim=null)
    fun customize(isTrim:Boolean,value:PaintStyle?)=if(isTrim)copy(trim=value?.normalized()) else copy(body=value?.normalized())
    fun customize(region:PaintRegion,value:PaintStyle?)=when(region) {
        PaintRegion.LOWER -> copy(body=value?.normalized())
        PaintRegion.UPPER -> copy(upper=value?.normalized())
        PaintRegion.TRIM -> copy(trim=value?.normalized())
    }
    fun style(region:PaintRegion)=when(region) {PaintRegion.LOWER->body;PaintRegion.UPPER->upper;PaintRegion.TRIM->trim}
}
