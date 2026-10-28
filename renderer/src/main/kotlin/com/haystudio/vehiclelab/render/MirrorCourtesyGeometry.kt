package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.*
import kotlin.math.*

internal object MirrorCourtesyGeometry {
    const val LEFT="Lab_Courtesy_Left"
    const val RIGHT="Lab_Courtesy_Right"
    fun isLens(part:String)=part==LEFT || part==RIGHT
    fun enabled(part:String,lights:VehicleLights)=VehicleLamp.ATMOSPHERE in lights ||
        (if(part==LEFT)VehicleLamp.ADAS_LEFT else VehicleLamp.ADAS_RIGHT) in lights
    fun create(shell:MeshData):MeshData? {
        if(shell.part !in setOf("Rearview_Left_Plastics_Black_01","Rearview_Right_Plastics_Black_01"))return null
        val p=shell.positions
        val cx=p.indices.step(3).let { ids->(ids.minOf{p[it]}+ids.maxOf{p[it]})*.5f }
        val cz=p.indices.step(3).let { ids->(ids.minOf{p[it+2]}+ids.maxOf{p[it+2]})*.5f }
        fun bottom(x:Float,z:Float):Float? {
            var y=Float.POSITIVE_INFINITY
            for(t in shell.indices.indices step 3){
                val a=shell.indices[t]*3;val b=shell.indices[t+1]*3;val c=shell.indices[t+2]*3
                val den=(p[b+2]-p[c+2])*(p[a]-p[c])+(p[c]-p[b])*(p[a+2]-p[c+2])
                if(abs(den)<1e-9f)continue
                val u=((p[b+2]-p[c+2])*(x-p[c])+(p[c]-p[b])*(z-p[c+2]))/den
                val v=((p[c+2]-p[a+2])*(x-p[c])+(p[a]-p[c])*(z-p[c+2]))/den
                if(u>=-.0001f && v>=-.0001f && u+v<=1.0001f)y=minOf(y,u*p[a+1]+v*p[b+1]+(1-u-v)*p[c+1])
            }
            return y.takeIf{it.isFinite()}
        }
        val cy=bottom(cx,cz) ?: return null
        val vertices=ArrayList<Float>();val uv=ArrayList<Float>();val normals=ArrayList<Float>()
        for(i in 0..24){
            val angle=(i-1)*Math.PI*2/24
            val x=cx+if(i==0)0f else .009f*cos(angle).toFloat()
            val z=cz+if(i==0)0f else .009f*sin(angle).toFloat()
            vertices.addAll(listOf(x,(bottom(x,z) ?: cy)-.0008f,z));normals.addAll(listOf(0f,-1f,0f));uv.addAll(listOf(.5f,.5f))
        }
        val indices=ArrayList<Int>();for(i in 1..24)indices.addAll(listOf(0,i,if(i==24)1 else i+1))
        val material=DefaultMaterials.OTHER.copy(path="Lab_镜底灯珠",base=floatArrayOf(.055f,.085f,.12f),
            metallic=.18f,roughness=.24f,emissive=floatArrayOf(.10f,.22f,.34f),emissiveInt=.62f,
            unlit=false,doubleSided=false,faceCulling=FaceCulling.BACK)
        return MeshData(shell.name+"#镜底灯珠",if(shell.part.contains("Left"))LEFT else RIGHT,vertices.toFloatArray(),normals.toFloatArray(),uv.toFloatArray(),indices.toIntArray(),material,shell.node)
    }
}
