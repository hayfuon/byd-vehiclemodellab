package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.MeshData
import com.haystudio.vehiclelab.data.DefaultMaterials
import com.haystudio.vehiclelab.data.FaceCulling
import kotlin.math.*

/** 尾门关闭键贴合门板下沿端面，独立于内衬并继承门板节点变换。 */
internal object TrunkButtonGeometry {
    const val PART="Lab_Trunk_Close_Button"
    fun create(shell:MeshData):MeshData? {
        if(shell.part!="Carpaint_BootTrunk.001")return null
        val p=shell.positions
        fun surface(x:Float,z:Float):Float? {
            var y=Float.POSITIVE_INFINITY
            for(t in shell.indices.indices step 3) {
                val a=shell.indices[t]*3;val b=shell.indices[t+1]*3;val c=shell.indices[t+2]*3
                val den=(p[b+2]-p[c+2])*(p[a]-p[c])+(p[c]-p[b])*(p[a+2]-p[c+2])
                if(abs(den)<1e-9f)continue
                val u=((p[b+2]-p[c+2])*(x-p[c])+(p[c]-p[b])*(z-p[c+2]))/den
                val v=((p[c+2]-p[a+2])*(x-p[c])+(p[a]-p[c])*(z-p[c+2]))/den
                if(u>=0f && v>=0f && u+v<=1f)y=minOf(y,u*p[a+1]+v*p[b+1]+(1-u-v)*p[c+1])
            }
            return y.takeIf{it.isFinite()}
        }
        val positions=ArrayList<Float>();val normals=ArrayList<Float>();val uv=ArrayList<Float>()
        val cx=2.535f;val cz=-.17f
        fun vertex(depth:Float,width:Float):Boolean {
            val z=cz+width
            val x=cx+depth+.07f*width
            val y=surface(x,z) ?: return false
            positions.addAll(listOf(x,y-.0008f,z));normals.addAll(listOf(0f,-1f,0f));uv.addAll(listOf(width/.058f+.5f,depth/.028f+.5f));return true
        }
        if(!vertex(0f,0f))return null
        for(corner in 0..3)for(step in 0..6) {
            val a=(-90f+corner*90f+step*15f)*PI.toFloat()/180f
            val depth=(if(corner in 1..2).0125f else -.0125f)+.0015f*sin(a)
            val width=(if(corner in 0..1).0275f else -.0275f)+.0015f*cos(a)
            if(!vertex(depth,width))return null
        }
        val count=positions.size/3-1
        val indices=IntArray(count*3){i->when(i%3){0->0;1->i/3+1;else->if(i/3+1==count)1 else i/3+2}}
        return MeshData(shell.name+"#尾门关闭键",PART,positions.toFloatArray(),normals.toFloatArray(),uv.toFloatArray(),indices,
            DefaultMaterials.OTHER.copy(path="Lab_尾门关闭键",doubleSided=true,faceCulling=FaceCulling.NONE,
                vsPath="lab/trunk-button.vert",fsPath="lab/trunk-button.frag",
                vsSource="""#version 300 es
                    precision highp float;
                    in vec3 a_position;
                    in vec2 a_texcoord0;
                    uniform mat4 u_model,u_viewProj;
                    out vec2 buttonUv;
                    void main(){buttonUv=a_texcoord0;gl_Position=u_viewProj*u_model*vec4(a_position,1.0);}
                """.trimIndent(),
                fsSource="""#version 300 es
                    precision highp float;
                    uniform float uLabCourtesyEnvironment;
                    in vec2 buttonUv;
                    out vec4 FragColor;
                    float stroke(vec2 p,vec2 a,vec2 b){
                        vec2 v=b-a;return length(p-a-v*clamp(dot(p-a,v)/dot(v,v),0.0,1.0));
                    }
                    float symbol(vec2 p,bool lockKey){
                        float d=1.0;
                        if(lockKey){
                            vec2 q=abs(p-vec2(0,.095))-vec2(.17,.12);
                            d=abs(length(max(q,0.0))+min(max(q.x,q.y),0.0));
                            vec2 arc=p-vec2(0,-.065);
                            if(arc.y<0.0)d=min(d,abs(length(arc)-.125));
                            d=min(d,stroke(p,vec2(-.125,-.065),vec2(-.125,-.025)));
                            d=min(d,stroke(p,vec2(.125,-.065),vec2(.125,-.025)));
                            d=min(d,stroke(p,vec2(0,.065),vec2(0,.13)));
                        }else{
                            d=min(d,stroke(p,vec2(-.23,.14),vec2(.23,.14)));
                            d=min(d,stroke(p,vec2(-.23,.14),vec2(-.17,-.13)));
                            d=min(d,stroke(p,vec2(.23,.14),vec2(.17,-.13)));
                            d=min(d,stroke(p,vec2(-.17,-.13),vec2(.17,-.13)));
                            d=min(d,stroke(p,vec2(0,-.07),vec2(0,.065)));
                            d=min(d,stroke(p,vec2(0,.065),vec2(-.055,.01)));
                            d=min(d,stroke(p,vec2(0,.065),vec2(.055,.01)));
                        }
                        return d;
                    }
                    void main(){
                        float aa=max(fwidth(buttonUv.x),.0001);
                        float key=smoothstep(.017-aa,.017+aa,abs(buttonUv.x-.5));
                        vec3 color=mix(vec3(.001),vec3(.065,.0015,.0008)*mix(.3,1.0,uLabCourtesyEnvironment),key);
                        vec2 p=vec2((fract(buttonUv.x*2.0)-.5)*1.035,.5-buttonUv.y);
                        float distanceToIcon=symbol(p,buttonUv.x<.5);
                        float edge=max(fwidth(distanceToIcon),.002);
                        float icon=1.0-smoothstep(.012-edge,.012+edge,distanceToIcon);
                        float night=smoothstep(.25,1.0,uLabCourtesyEnvironment);
                        float glow=exp(-distanceToIcon*70.0)*night;
                        color+=key*vec3(1.0,.13,.055)*(icon*mix(.055,.48,night)+glow*.045);
                        FragColor=vec4(color,1.0);
                    }
                """.trimIndent()),shell.node)
    }
}
