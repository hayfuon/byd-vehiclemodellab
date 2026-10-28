package com.haystudio.vehiclelab.data

import java.util.Locale
import kotlin.math.hypot
import kotlin.math.exp

object InteriorLighting {
    fun activeNodes(): Set<String> = emptySet()
    fun surface(name: String): Boolean = name.startsWith("Door_") && name.endsWith("_Inside") || name == "Central_Control"

    fun footwell(x: Float,y: Float,z: Float): Float {
        val dx=(x+1.02f)/.48f;val dz=(kotlin.math.abs(z)-.44f)/.30f
        val r2=dx*dx+dz*dz
        return exp(-3f*r2)*(1f-smooth(.8f,1f,r2))*smooth(.28f,.31f,y)*(1f-smooth(.44f,.53f,y))
    }

    private data class Point(val x: Float, val y: Float)
    private data class Band(val points: List<Point>, val dashHeight:Float?=null)
    private fun band(vararg xy: Float) = Band(xy.toList().chunked(2).map { Point(it[0], it[1]) })

    fun footwellSurface(name: String): Boolean = name == "Interior_Chair" || name == "Central_Control"

    private val doors = listOf(
        
        band(70f,22.3f,80f,21.9f,156f,19.6f,233f,18.5f,308f,18f),
        band(30f,238.5f,59.3f,235.7f,147.5f,229.8f,199f,227.6f,
            225f,226f,284.7f,222.9f,294f,222.4f),
        band(80f, 57f, 156f, 49f, 233f, 43f, 308f, 40f),
        band(30f, 265f, 106f, 262f, 199f, 258f, 294f, 254f)
    )
    
    private val dash = listOf(
        band(18f,263.4f,35f,262.5f,53f,261.6f).copy(dashHeight=1f),
        band(110f,261.5f,220f,261.5f,260f,262f,290f,262.3f,303f,263f).copy(dashHeight=1f),
        band(18f,287f,53f,287f).copy(dashHeight=.882f),
        band(110f,287f,303f,287f).copy(dashHeight=.882f)
    )

    fun mask(door: Boolean, u: Float, v: Float, px:Float=0f, py:Float=0f, pz:Float=0f): Float {
        if (!u.isFinite() || !v.isFinite()) return 0f
        val x = u * 512f; val y = v * 512f
        val trim = (if (door) doors else dash).maxOf { band ->
            val distance = band.points.zipWithNext().minOf { (a, b) ->
                val dx = b.x - a.x; val dy = b.y - a.y
                val t = (((x - a.x) * dx + (y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
                hypot(x - a.x - t * dx, y - a.y - t * dy)
            }
            val surface = if(band.dashHeight!=null && py!=0f) dashHorizontal(py,pz,band.dashHeight) else 1f
            surface * (1f - smooth(.35f, 1.1f, distance)) *
                smooth(0f,8f,hypot(x-band.points.first().x,y-band.points.first().y)) *
                smooth(0f,8f,hypot(x-band.points.last().x,y-band.points.last().y))
        }
        return if(door) trim else maxOf(trim,islandSide(px,py,pz))
    }

    private val islandSide = listOf(
        floatArrayOf(.20f,.69091f,.11035f),floatArrayOf(.10f,.69100f,.10997f),
        floatArrayOf(0f,.68829f,.11006f),floatArrayOf(-.10f,.68570f,.11060f),
        floatArrayOf(-.20f,.68414f,.11257f),floatArrayOf(-.30f,.69061f,.11711f),
        floatArrayOf(-.40f,.70413f,.12322f),floatArrayOf(-.50f,.71846f,.12935f),
        floatArrayOf(-.60f,.73082f,.13587f))
    private fun islandSide(x:Float,y:Float,z:Float):Float {
        val p=floatArrayOf(x,y,kotlin.math.abs(z))
        val distance=islandSide.zipWithNext().minOf { (a,b) ->
            val ab=FloatArray(3){b[it]-a[it]}
            val t=(ab.indices.sumOf { ((p[it]-a[it])*ab[it]).toDouble() } /
                ab.sumOf { (it*it).toDouble() }).toFloat().coerceIn(0f,1f)
            kotlin.math.sqrt(ab.indices.sumOf { val d=p[it]-a[it]-t*ab[it];(d*d).toDouble() }).toFloat()
        }
        return (1f-smooth(.0012f,.0032f,distance))*smooth(-.60f,-.55f,x)*(1f-smooth(.15f,.20f,x))
    }

    private fun dashHorizontal(y:Float,z:Float,height:Float):Float {
        return (1f-smooth(.006f,.012f,kotlin.math.abs(y-height)))*(1f-smooth(.65f,.69f,kotlin.math.abs(z)))
    }

    private fun smooth(a: Float, b: Float, value: Float): Float {
        val t = ((value - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun shaderFunctions(door:Boolean, dash:Boolean, feet:Boolean): String = buildString {
        if(door||dash) append("""
            float labTrimDistance(vec2 p, vec2 a, vec2 b) {
                vec2 ab = b-a;
                float t = clamp(dot(p-a,ab)/dot(ab,ab),0.0,1.0);
                return length(p-a-ab*t);
            }
        """.trimIndent())
        if(feet) append("""
            float labFootwell(vec3 p) {
                vec2 q=vec2((p.x+1.02)/.48,(abs(p.z)-.44)/.30);
                float r2=dot(q,q);
                return exp(-3.0*r2)*(1.0-smoothstep(.8,1.0,r2))*smoothstep(.28,.31,p.y)*(1.0-smoothstep(.44,.53,p.y));
            }
        """.trimIndent())
        if(dash) {
            append("\nfloat labIslandDistance(vec3 pos) {\nvec3 p=vec3(pos.xy,abs(pos.z));float d=10000.0;\n")
            for((a,b) in islandSide.zipWithNext()) {
                val va="vec3(${a[0].glsl()},${a[1].glsl()},${a[2].glsl()})"
                val vb="vec3(${b[0].glsl()},${b[1].glsl()},${b[2].glsl()})"
                append("{vec3 a=$va,b=$vb;vec3 ab=b-a;float t=clamp(dot(p-a,ab)/dot(ab,ab),0.0,1.0);d=min(d,length(p-a-t*ab));}\n")
            }
            append("return d;}\nfloat labIslandSide(vec3 pos) {return (1.0-smoothstep(.0012,.0032,labIslandDistance(pos)))*smoothstep(-.60,-.55,pos.x)*(1.0-smoothstep(.15,.20,pos.x));}\n")
        }
        val bandsBySurface=buildList {
            if(door)add("Door" to doors)
            if(dash)add("Dash" to this@InteriorLighting.dash)
        }
        for ((name, bands) in bandsBySurface) {
            append("\nfloat lab${name}Field(vec2 uv, vec3 pos,float radius) {\nvec2 p=uv*512.0;\nfloat coverage=0.0;\nfloat d;\n")
            for (band in bands) {
                append("d=10000.0;\n")
                for ((a, b) in band.points.zipWithNext()) {
                    append("d=min(d,labTrimDistance(p,vec2(${a.x.glsl()},${a.y.glsl()}),vec2(${b.x.glsl()},${b.y.glsl()})));\n")
                }
                val a=band.points.first();val b=band.points.last()
                val surface=band.dashHeight?.let { "(1.0-smoothstep(.006+radius*.002,.012+radius*.004,abs(pos.y-${it.glsl()})))*(1.0-smoothstep(.65,.69,abs(pos.z)))*" } ?: ""
                append("coverage=max(coverage,$surface(1.0-smoothstep(.35+radius*.4,1.1+radius,d))*smoothstep(0.0,8.0,length(p-vec2(${a.x.glsl()},${a.y.glsl()})))*smoothstep(0.0,8.0,length(p-vec2(${b.x.glsl()},${b.y.glsl()}))));\n")
            }
            append("return coverage;\n}\nfloat lab${name}Trim(vec2 uv,vec3 pos){return lab${name}Field(uv,pos,0.0); }\n")
        }
    }

    private fun Float.glsl(): String = String.format(Locale.ROOT, "%.4f", this)
}
