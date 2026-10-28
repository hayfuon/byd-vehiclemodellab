package com.haystudio.vehiclelab.data

object TireGroundContact {
    fun height(tires:List<FloatArray>,matrix:FloatArray):Float? {
        var bottom=Float.POSITIVE_INFINITY
        for(vertices in tires) for(i in 0 until vertices.size-2 step 3) {
            val y=matrix[1]*vertices[i]+matrix[5]*vertices[i+1]+matrix[9]*vertices[i+2]+matrix[13]
            if(y.isFinite())bottom=minOf(bottom,y)
        }
        return bottom.takeIf {it.isFinite()}
    }
}
