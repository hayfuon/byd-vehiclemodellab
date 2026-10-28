package com.haystudio.vehiclelab.data

import kotlin.math.*

/** 周期多尺度高度场同时生成底色和法线，纹理边缘无接缝。 */
internal object GroundSurfacePattern {
    data class Maps(val color:IntArray,val normal:IntArray)
    fun generate(style:Int,size:Int=512):Maps {
        require(style in 1..6 && size>=16)
        fun hash(x:Int,y:Int,period:Int):Float {
            var n=(Math.floorMod(x,period)*374761393) xor (Math.floorMod(y,period)*668265263) xor (style*1274126177)
            n=(n xor (n ushr 13))*1274126177
            return ((n xor (n ushr 16)) and 65535)/65535f
        }
        fun noise(x:Float,y:Float,cells:Int):Float {
            val px=x*cells;val py=y*cells;val ix=floor(px).toInt();val iy=floor(py).toInt()
            var tx=px-ix;var ty=py-iy;tx=tx*tx*(3-2*tx);ty=ty*ty*(3-2*ty)
            val a=hash(ix,iy,cells);val b=hash(ix+1,iy,cells);val c=hash(ix,iy+1,cells);val d=hash(ix+1,iy+1,cells)
            return (a+(b-a)*tx)*(1-ty)+(c+(d-c)*tx)*ty
        }
        val height=FloatArray(size*size)
        val colors=IntArray(size*size)
        val base=arrayOf(intArrayOf(153,151,146),intArrayOf(87,89,91),intArrayOf(148,145,138),
            intArrayOf(176,173,165),intArrayOf(179,160,126),intArrayOf(136,130,119))[style-1]
        for(y in 0 until size)for(x in 0 until size) {
            val u=x.toFloat()/size;val v=y.toFloat()/size
            val broad=noise(u,v,4)-.5f;val medium=noise(u,v,24)-.5f;val fine=noise(u,v,128)-.5f
            val grain=hash(x,y,size)-.5f
            var h=medium*.25f+fine*.22f+grain*.06f
            var shade=broad*11f+medium*15f+fine*12f+grain*7f
            when(style) {
                2 -> {h=medium*.12f+fine*.4f+grain*.09f;shade=medium*11+fine*27+grain*10}
                3 -> {
                    val a=(u*4)%1f;val b=(v*4)%1f
                    val edge=min(min(a,1-a),min(b,1-b))
                    val grout=(1f-(edge/.015f).coerceIn(0f,1f))
                    h-=grout*.16f;shade-=grout*34
                    shade+=(hash((u*4).toInt(),(v*4).toInt(),4)-.5f)*13
                }
                4 -> {val chip=((noise(u,v,80)-.63f)*9).coerceIn(0f,1f);shade+=chip*30;h=medium*.04f+fine*.025f}
                5 -> {h=medium*.12f+fine*.13f;shade=broad*22+medium*17+fine*9}
                6 -> {h=medium*.48f+fine*.3f;shade=broad*17+medium*40+fine*19}
            }
            val index=y*size+x;height[index]=h
            val c=IntArray(3){(base[it]+shade).roundToInt().coerceIn(0,255)}
            colors[index]=(255 shl 24) or (c[0] shl 16) or (c[1] shl 8) or c[2]
        }
        val normals=IntArray(size*size)
        for(y in 0 until size)for(x in 0 until size) {
            fun h(dx:Int,dy:Int)=height[Math.floorMod(y+dy,size)*size+Math.floorMod(x+dx,size)]
            val nx=(h(-1,0)-h(1,0))*.8f;val ny=(h(0,-1)-h(0,1))*.8f
            val inv=1f/sqrt(nx*nx+ny*ny+1f)
            fun pack(v:Float)=((v*inv*.5f+.5f)*255).roundToInt().coerceIn(0,255)
            normals[y*size+x]=(255 shl 24) or (pack(nx) shl 16) or (pack(ny) shl 8) or pack(1f)
        }
        return Maps(colors,normals)
    }
}
