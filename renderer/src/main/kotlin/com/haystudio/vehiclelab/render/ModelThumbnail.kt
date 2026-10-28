package com.haystudio.vehiclelab.render

import android.graphics.Bitmap
import com.haystudio.vehiclelab.data.GwmModel
import kotlin.math.*

/** 固定正交机位和留白，逐像素深度测试避免内饰或远侧部件覆盖车壳。 */
fun rasterizeThumbnail(model: GwmModel,checkCancelled:()->Unit = { if(Thread.currentThread().isInterrupted)throw InterruptedException("缩略图生成已取消") }): Bitmap {
    val width=480;val height=320
    val meshes=model.meshes.filterNot { mesh ->
        val name=(mesh.part+" "+mesh.name).lowercase()
        name.contains("shadow") || name.contains("controller") || name.contains("bloom") ||
            name.contains("_clip") || mesh.material.light || mesh.material.opacity<.05f
    }
    val pixels=IntArray(width*height){android.graphics.Color.rgb(35,40,46)}
    val depth=FloatArray(pixels.size){Float.NEGATIVE_INFINITY}
    var minX=Float.POSITIVE_INFINITY;var maxX=Float.NEGATIVE_INFINITY
    var minY=Float.POSITIVE_INFINITY;var maxY=Float.NEGATIVE_INFINITY
    fun projectX(x:Float,z:Float)=(x-z)*.70710677f
    fun projectY(x:Float,y:Float,z:Float)=-y*.8660254f+(x+z)*.3535534f
    for(mesh in meshes) for(i in mesh.positions.indices step 3) {
        val p=mesh.positions
        val x=projectX(p[i],p[i+2]);val y=projectY(p[i],p[i+1],p[i+2])
        minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
    }
    if(!minX.isFinite())return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
    val scale=min(width*.90f/(maxX-minX).coerceAtLeast(.001f),height*.84f/(maxY-minY).coerceAtLeast(.001f))
    val centerX=(minX+maxX)*.5f;val centerY=(minY+maxY)*.5f
    for(mesh in meshes) {
        checkCancelled()
        val p=mesh.positions
        val projected=FloatArray(p.size)
        for(i in p.indices step 3) {
            projected[i]=width*.5f+(projectX(p[i],p[i+2])-centerX)*scale
            projected[i+1]=height*.5f+(projectY(p[i],p[i+1],p[i+2])-centerY)*scale
            projected[i+2]=(p[i]+p[i+2])*.6123724f+p[i+1]*.5f
        }
        val part=(mesh.part+" "+mesh.name+" "+mesh.material.path).lowercase()
        val base=when {
            listOf("tire","tyre","rubber").any(part::contains)->floatArrayOf(37f,41f,46f)
            listOf("glass","window").any(part::contains)->floatArrayOf(49f,70f,83f)
            listOf("chrome","rim","wheelhub").any(part::contains)->floatArrayOf(185f,191f,197f)
            else->floatArrayOf(143f,161f,173f)
        }
        for(t in mesh.indices.indices step 3) {
            if(t+2>=mesh.indices.size)break
            if(t%1536==0)checkCancelled()
            val a=mesh.indices[t]*3;val b=mesh.indices[t+1]*3;val c=mesh.indices[t+2]*3
            if(a<0||b<0||c<0||a+2>=p.size||b+2>=p.size||c+2>=p.size)continue
            val ax=projected[a];val ay=projected[a+1];val bx=projected[b];val by=projected[b+1];val cx=projected[c];val cy=projected[c+1]
            val determinant=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
            if(abs(determinant)<.001f)continue
            val left=floor(minOf(ax,bx,cx)).toInt().coerceIn(0,width-1)
            val right=ceil(maxOf(ax,bx,cx)).toInt().coerceIn(0,width-1)
            val top=floor(minOf(ay,by,cy)).toInt().coerceIn(0,height-1)
            val bottom=ceil(maxOf(ay,by,cy)).toInt().coerceIn(0,height-1)
            val ux=p[b]-p[a];val uy=p[b+1]-p[a+1];val uz=p[b+2]-p[a+2]
            val vx=p[c]-p[a];val vy=p[c+1]-p[a+1];val vz=p[c+2]-p[a+2]
            val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx
            val norm=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.000001f)
            val lighting=.48f+.52f*abs((nx*.25f+ny*.90f+nz*.35f)/norm).coerceIn(0f,1f)
            val color=android.graphics.Color.rgb((base[0]*lighting).roundToInt(),(base[1]*lighting).roundToInt(),(base[2]*lighting).roundToInt())
            for(y in top..bottom)for(x in left..right) {
                val sx=x+.5f;val sy=y+.5f
                val wa=((by-cy)*(sx-cx)+(cx-bx)*(sy-cy))/determinant
                val wb=((cy-ay)*(sx-cx)+(ax-cx)*(sy-cy))/determinant
                val wc=1f-wa-wb
                if(wa<0f||wb<0f||wc<0f)continue
                val z=wa*projected[a+2]+wb*projected[b+2]+wc*projected[c+2]
                val index=y*width+x
                if(z>depth[index]) {depth[index]=z;pixels[index]=color}
            }
        }
    }
    val supersampled=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
    return Bitmap.createScaledBitmap(supersampled,width/2,height/2,true).also {supersampled.recycle()}
}
