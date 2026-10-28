package com.haystudio.vehiclelab.data

object CourtesyLightMount {
    fun belongsToDoor(part:String,ancestors:Collection<String>,door:String):Boolean {
        val root=door.removeSuffix("_Inside")
        fun matches(name:String)=name==root||name.startsWith(root+"_")||name.endsWith("_"+root)||name.contains("_"+root+"_")
        return matches(part)||ancestors.any(::matches)
    }
    fun brightness(openFraction:Float):Float {
        val x=((openFraction-.32f)/.38f).coerceIn(0f,1f)
        return x*x*(3f-2f*x)
    }

    fun belowDoor(inner:FloatArray,doorBounds:List<FloatArray>):FloatArray {
        val bottom=doorBounds.minOfOrNull {it[1]} ?: inner[1]
        return floatArrayOf((inner[0]+inner[3])*.5f,minOf(bottom,inner[1])-.02f,(inner[2]+inner[5])*.5f)
    }
}
