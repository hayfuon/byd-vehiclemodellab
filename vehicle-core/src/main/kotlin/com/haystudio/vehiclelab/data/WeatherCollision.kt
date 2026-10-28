package com.haystudio.vehiclelab.data

import kotlin.math.*

data class WeatherVec(val x:Float,val y:Float,val z:Float) {
    operator fun plus(b:WeatherVec)=WeatherVec(x+b.x,y+b.y,z+b.z)
    operator fun minus(b:WeatherVec)=WeatherVec(x-b.x,y-b.y,z-b.z)
    operator fun times(s:Float)=WeatherVec(x*s,y*s,z*s)
    fun dot(b:WeatherVec)=x*b.x+y*b.y+z*b.z
    fun cross(b:WeatherVec)=WeatherVec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
    fun unit():WeatherVec { val n=sqrt(dot(this));return if(n>1e-8f)this*(1f/n) else WeatherVec(0f,1f,0f) }
    fun at(i:Int)=if(i==0)x else if(i==1)y else z
    fun transform(m:FloatArray,vector:Boolean=false):WeatherVec {
        val w=if(vector)0f else 1f
        return WeatherVec(m[0]*x+m[4]*y+m[8]*z+m[12]*w,m[1]*x+m[5]*y+m[9]*z+m[13]*w,m[2]*x+m[6]*y+m[10]*z+m[14]*w)
    }
}

class WeatherFrame(val pose:DrivingKinematics.Pose=DrivingKinematics.Pose(),val pivot:Float=1.45f) {
    private val c=cos(pose.heading);private val s=sin(pose.heading)
    fun toWorld(p:WeatherVec)=WeatherVec((pose.x+pivot+c*(p.x-pivot)+s*p.z).toFloat(),p.y,(pose.z-s*(p.x-pivot)+c*p.z).toFloat())
    fun toCar(p:WeatherVec):WeatherVec {
        val x=p.x.toDouble()-pose.x-pivot;val z=p.z.toDouble()-pose.z
        return WeatherVec((pivot+c*x-s*z).toFloat(),p.y,(s*x+c*z).toFloat())
    }
    fun vectorToCar(p:WeatherVec)=WeatherVec((c*p.x-s*p.z).toFloat(),p.y,(s*p.x+c*p.z).toFloat())
    fun vectorToWorld(p:WeatherVec)=WeatherVec((c*p.x+s*p.z).toFloat(),p.y,(-s*p.x+c*p.z).toFloat())
}

class WeatherCollision(surfaces:List<Surface>) {
    class Surface(val node:Int,val positions:FloatArray,val indices:IntArray,val part:String="")
    data class Hit(val fraction:Float,val position:WeatherVec,val normal:WeatherVec,val node:Int,
                   val local:WeatherVec=position,val localNormal:WeatherVec=normal,val part:String="")
    private class Triangle(val a:WeatherVec,val b:WeatherVec,val c:WeatherVec,val part:String) {
        val lo=WeatherVec(minOf(a.x,b.x,c.x),minOf(a.y,b.y,c.y),minOf(a.z,b.z,c.z))
        val hi=WeatherVec(maxOf(a.x,b.x,c.x),maxOf(a.y,b.y,c.y),maxOf(a.z,b.z,c.z))
        val center=(a+b+c)*(1f/3f)
    }
    private class Branch(val lo:WeatherVec,val hi:WeatherVec,val triangles:List<Triangle>,val left:Branch?=null,val right:Branch?=null)
    private class Body(val id:Int,val root:Branch,var matrix:FloatArray=identity(),var inverse:FloatArray=identity()) {
        var lo=root.lo;var hi=root.hi
    }
    private val bodies=surfaces.groupBy { it.node }.mapNotNull { (id,parts) ->
        val triangles=parts.flatMap { part ->
            fun p(i:Int)=WeatherVec(part.positions[i*3],part.positions[i*3+1],part.positions[i*3+2])
            part.indices.toList().chunked(3).mapNotNull { ix ->
                if(ix.size<3) null else Triangle(p(ix[0]),p(ix[1]),p(ix[2]),part.part).takeIf { (it.b-it.a).cross(it.c-it.a).dot((it.b-it.a).cross(it.c-it.a))>1e-16f }
            }
        }
        if(triangles.isEmpty())null else Body(id,build(triangles))
    }
    private val byId=bodies.associateBy { it.id }
    private val names=surfaces.groupBy { it.node }.mapValues { it.value.joinToString(" ") { s->s.part } }
    fun part(node:Int)=names[node].orEmpty()
    private var enabled:Set<Int>?=null
    private var overallLo=WeatherVec(bodies.minOfOrNull{it.lo.x} ?: Float.POSITIVE_INFINITY,
        bodies.minOfOrNull{it.lo.y} ?: Float.POSITIVE_INFINITY,bodies.minOfOrNull{it.lo.z} ?: Float.POSITIVE_INFINITY)
    private var overallHi=WeatherVec(bodies.maxOfOrNull{it.hi.x} ?: Float.NEGATIVE_INFINITY,
        bodies.maxOfOrNull{it.hi.y} ?: Float.NEGATIVE_INFINITY,bodies.maxOfOrNull{it.hi.z} ?: Float.NEGATIVE_INFINITY)
    val triangleCount=surfaces.sumOf { it.indices.size/3 }
    fun setTransforms(matrices:Map<Int,FloatArray>,visible:Set<Int>?=null) {
        enabled=visible
        var low=WeatherVec(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY)
        var high=WeatherVec(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY)
        for(b in bodies) {
            val m=matrices[b.id] ?: identity();b.matrix=m;b.inverse=GwmNodeTree.invert(m)
            var lo=WeatherVec(Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY)
            var hi=WeatherVec(Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY)
            for(i in 0..7) {
                val p=WeatherVec(if(i and 1==0)b.root.lo.x else b.root.hi.x,if(i and 2==0)b.root.lo.y else b.root.hi.y,if(i and 4==0)b.root.lo.z else b.root.hi.z).transform(m)
                lo=WeatherVec(min(lo.x,p.x),min(lo.y,p.y),min(lo.z,p.z));hi=WeatherVec(max(hi.x,p.x),max(hi.y,p.y),max(hi.z,p.z))
            }
            b.lo=lo;b.hi=hi
            if(visible(b.id)) { low=WeatherVec(min(low.x,lo.x),min(low.y,lo.y),min(low.z,lo.z));high=WeatherVec(max(high.x,hi.x),max(high.y,hi.y),max(high.z,hi.z)) }
        }
        overallLo=low;overallHi=high
    }
    fun attached(hit:Hit)=byId[hit.node]?.let { hit.local.transform(it.matrix) } ?: hit.position
    fun attachedNormal(hit:Hit)=byId[hit.node]?.let { normal(hit.localNormal,it.inverse) } ?: hit.normal
    fun visible(node:Int)=enabled?.contains(node) ?: true
    /** 检查整片轮廓的支撑，拒绝中心命中而边缘悬空的车门边、窄饰条和轮胎侧面。 */
    fun supportsFootprint(hit:Hit,size:Float,phase:Float,aspect:Float=1.5f):Boolean {
        val center=attached(hit);val n=attachedNormal(hit)
        val (a,b)=footprintAxes(n,phase)
        for(i in 0 until 8) {
            val angle=i*PI.toFloat()/4f
            val point=center+a*(cos(angle)*size)+b*(sin(angle)*size*aspect)
            val probe=sweep(point+n*.018f,point-n*.025f) ?: return false
            if(probe.node!=hit.node || probe.normal.dot(n)<.86f ||
                abs((probe.position-point).dot(n))>.009f)return false
        }
        return true
    }
    fun skyHit(u:Float,v:Float):Hit? {
        if(!overallLo.x.isFinite()||!overallHi.x.isFinite()||overallHi.x<overallLo.x)return null
        val x=overallLo.x+(overallHi.x-overallLo.x)*u
        val z=overallLo.z+(overallHi.z-overallLo.z)*v
        return sweep(WeatherVec(x,overallHi.y+.1f,z),WeatherVec(x,overallLo.y-.1f,z))
    }
    /** 雪片轮廓保留真实表面锚点，增长时重采样，绘制时仅更新节点变换。 */
    fun snowFootprint(hit:Hit,size:Float,phase:Float):List<Hit>? {
        if(!supportsFootprint(hit,size*1.3f,phase,1.2f/1.3f))return null
        val center=attached(hit);val n=attachedNormal(hit);val (a,b)=footprintAxes(n,phase)
        val rim=ArrayList<Hit>(SnowShape.SEGMENTS)
        for(i in 0 until SnowShape.SEGMENTS) {
            val angle=i*2f*PI.toFloat()/SnowShape.SEGMENTS
            val radius=SnowShape.radius(angle,phase)+.035f
            val p=center+a*(cos(angle)*size*1.3f*radius)+b*(sin(angle)*size*1.2f*radius)
            val probe=sweep(p+n*.018f,p-n*.025f) ?: return null
            if(probe.node!=hit.node||probe.normal.dot(n)<.86f)return null
            rim.add(probe)
        }
        return rim
    }
    fun sweep(from:WeatherVec,to:WeatherVec):Hit? {
        var closest:Hit?=null
        val worldDelta=to-from
        if(!intersects(overallLo,overallHi,from,worldDelta,1f))return null
        for(b in bodies) {
            if(!visible(b.id)||!intersects(b.lo,b.hi,from,worldDelta,closest?.fraction ?: 1f))continue
            val a=from.transform(b.inverse);val d=to.transform(b.inverse)-a
            fun visit(branch:Branch) {
                if(!intersects(branch.lo,branch.hi,a,d,closest?.fraction ?: 1f))return
                if(branch.left!=null) { visit(branch.left);visit(branch.right!!);return }
                for(t in branch.triangles) {
                    val e1=t.b-t.a;val e2=t.c-t.a;val p=d.cross(e2);val det=e1.dot(p)
                    if(abs(det)<1e-10f)continue
                    val inv=1f/det;val tv=a-t.a;val u=tv.dot(p)*inv
                    if(u<0f||u>1f)continue
                    val q=tv.cross(e1);val v=d.dot(q)*inv
                    if(v<0f||u+v>1f)continue
                    val f=e2.dot(q)*inv
                    if(f<0f||f>(closest?.fraction ?: 1f))continue
                    val local=a+d*f;var n=e1.cross(e2).unit()
                    if(n.dot(d)>0f)n=n*(-1f)
                    closest=Hit(f,local.transform(b.matrix),normal(n,b.inverse),b.id,local,n,t.part)
                }
            }
            visit(b.root)
        }
        return closest
    }
    companion object {
        fun footprintAxes(n:WeatherVec,phase:Float):Pair<WeatherVec,WeatherVec> {
            val ref=if(abs(n.y)<.9f)WeatherVec(0f,1f,0f) else WeatherVec(1f,0f,0f)
            val u=n.cross(ref).unit();val v=n.cross(u).unit()
            val a=u*cos(phase)+v*sin(phase)
            return a to n.cross(a)
        }

        private fun identity()=FloatArray(16){if(it%5==0)1f else 0f}
        private fun normal(n:WeatherVec,inverse:FloatArray)=WeatherVec(
            inverse[0]*n.x+inverse[1]*n.y+inverse[2]*n.z,
            inverse[4]*n.x+inverse[5]*n.y+inverse[6]*n.z,
            inverse[8]*n.x+inverse[9]*n.y+inverse[10]*n.z).unit()
        private fun build(ts:List<Triangle>):Branch {
            val lo=WeatherVec(ts.minOf { it.lo.x },ts.minOf { it.lo.y },ts.minOf { it.lo.z })
            val hi=WeatherVec(ts.maxOf { it.hi.x },ts.maxOf { it.hi.y },ts.maxOf { it.hi.z })
            if(ts.size<=12)return Branch(lo,hi,ts)
            val extent=hi-lo;val axis=(0..2).maxBy { extent.at(it) };val sorted=ts.sortedBy { it.center.at(axis) };val mid=ts.size/2
            return Branch(lo,hi,emptyList(),build(sorted.subList(0,mid)),build(sorted.subList(mid,ts.size)))
        }
        private fun intersects(lo:WeatherVec,hi:WeatherVec,o:WeatherVec,d:WeatherVec,max:Float):Boolean {
            var near=0f;var far=max
            for(i in 0..2) {
                if(abs(d.at(i))<1e-9f) { if(o.at(i)<lo.at(i)-.0001f||o.at(i)>hi.at(i)+.0001f)return false }
                else {
                    val a=(lo.at(i)-o.at(i))/d.at(i);val b=(hi.at(i)-o.at(i))/d.at(i)
                    near=maxOf(near,minOf(a,b));far=minOf(far,maxOf(a,b));if(near>far)return false
                }
            }
            return true
        }
    }
}
