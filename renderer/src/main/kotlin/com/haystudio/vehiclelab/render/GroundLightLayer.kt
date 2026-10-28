package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import com.haystudio.vehiclelab.data.GroundPhotometry
import kotlin.math.cos

data class GroundEmitter(
    val name: String, val position: FloatArray, val direction: FloatArray,
    val color: FloatArray, val power: Float, val range: Float, val angle: Float,
    val halfWidth: Float, val axis: FloatArray,
    val frontBlend: Float = 0f,
    val headlight: Boolean = false,
    
    val exteriorPlane: FloatArray? = null,
    val bodyShadow: Boolean = false,
    val shadowGain: Float = 1f,
    val shadowSoftness: Float = .025f
)

class GroundLightLayer {
    private val localShadows=LocalGroundShadows()
    private var program = 0
    private var vao = 0
    private val uniforms = HashMap<String, Int>()
    private val positions = FloatArray(MAX_LIGHTS * 4)
    private val directions = FloatArray(MAX_LIGHTS * 4)
    private val colors = FloatArray(MAX_LIGHTS * 4)
    private val axes = FloatArray(MAX_LIGHTS * 4)
    private val frontBlend = FloatArray(MAX_LIGHTS)
    private val headlights = FloatArray(MAX_LIGHTS)
    private val shadowGains = FloatArray(MAX_LIGHTS)
    private val shadowSoftness = FloatArray(MAX_LIGHTS)
    private val exteriorPlanes = FloatArray(MAX_LIGHTS * 4)
    private fun uniform(name: String) = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }

    fun onContextCreated() { localShadows.onContextCreated(); program = 0; vao = 0; uniforms.clear() }

    internal fun prepareShadows(sources:List<GroundEmitter>,poseKey:Int,draw:(Int,Int,Int)->Unit) = localShadows.prepare(sources,poseKey,draw)

    fun draw(vp: FloatArray, groundOffset: Float, eye: FloatArray, sources: List<GroundEmitter>, linear: Boolean, reflections: Boolean,
             receiver: () -> Unit) {
        if (sources.isEmpty()) return
        require(sources.size <= MAX_LIGHTS) { "地面光源超过 $MAX_LIGHTS 个" }
        if (program == 0) {
            program = GlUtil.program(VERTEX, FRAGMENT)
            val ids = IntArray(1)
            GLES30.glGenVertexArrays(1, ids, 0)
            vao = ids[0]
        }
        for ((i, source) in sources.withIndex()) {
            val j = i * 4
            for (k in 0..2) {
                positions[j+k] = source.position[k]
                directions[j+k] = source.direction[k]
                colors[j+k] = source.color[k]
                axes[j+k] = source.axis[k]
            }
            positions[j+3] = source.power
            directions[j+3] = cos(Math.toRadians(source.angle.toDouble())).toFloat()
            colors[j+3] = source.range
            axes[j+3] = source.halfWidth
            frontBlend[i] = source.frontBlend
            headlights[i] = if(source.headlight) 1f else 0f
            shadowGains[i] = source.shadowGain.coerceIn(0f, 1f)
            shadowSoftness[i] = source.shadowSoftness.coerceIn(.005f, .15f)
            for (k in 0..3) exteriorPlanes[j+k] = source.exteriorPlane?.get(k) ?: 0f
        }
        GLES30.glUseProgram(program)
        localShadows.bind(program,::uniform)
        GLES30.glBindVertexArray(vao)
        
        GLES30.glUniformMatrix4fv(uniform("uVp"), 1, false, vp, 0)
        GLES30.glUniform1f(uniform("uGroundOffset"), groundOffset)
        GLES30.glUniform3fv(uniform("uEye"), 1, eye, 0)
        GLES30.glUniform1i(uniform("uLinear"), if (linear) 1 else 0)
        GLES30.glUniform1f(uniform("uReflection"), if (reflections) 1f else 0f)
        GLES30.glUniform1i(uniform("uCount"), sources.size)
        GLES30.glUniform4fv(uniform("uPositions[0]"), sources.size, positions, 0)
        GLES30.glUniform4fv(uniform("uDirections[0]"), sources.size, directions, 0)
        GLES30.glUniform4fv(uniform("uColors[0]"), sources.size, colors, 0)
        GLES30.glUniform4fv(uniform("uAxes[0]"), sources.size, axes, 0)
        GLES30.glUniform1fv(uniform("uFrontBlend[0]"), sources.size, frontBlend, 0)
        GLES30.glUniform1fv(uniform("uHeadlights[0]"), sources.size, headlights, 0)
        GLES30.glUniform1fv(uniform("uShadowGains[0]"), sources.size, shadowGains, 0)
        GLES30.glUniform1fv(uniform("uShadowSoftness[0]"), sources.size, shadowSoftness, 0)
        GLES30.glUniform4fv(uniform("uExteriorPlanes[0]"), sources.size, exteriorPlanes, 0)
        GLES30.glUniform1f(uniform("uNearRadius2"), 1f / GroundPhotometry.attenuation(0f, 1f, 1f, .5f))
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
        
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1f, -2f)
        receiver()
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun release() {
        localShadows.release();
        if (program != 0) GLES30.glDeleteProgram(program)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        onContextCreated()
    }

    companion object {
        private const val MAX_LIGHTS = 48
        private const val VERTEX = """#version 300 es
precision highp float;
layout(location=0) in vec3 aPosition;
uniform mat4 uVp;
uniform float uGroundOffset;
out vec3 vWorld;
void main() {
    vWorld = aPosition + vec3(0,uGroundOffset,0);
    gl_Position = uVp*vec4(aPosition,1);
}"""
        private const val FRAGMENT = """#version 300 es
precision highp float;
in vec3 vWorld;
uniform vec3 uEye;
uniform vec4 uPositions[48], uDirections[48], uColors[48], uAxes[48];
uniform float uFrontBlend[48], uHeadlights[48], uShadowGains[48], uShadowSoftness[48];
uniform vec4 uExteriorPlanes[48];
uniform highp sampler2DShadow uLocalShadowAtlas;
uniform mat4 uLocalShadowVp[8];
uniform float uLocalShadowSlots[48];
uniform int uCount;
uniform float uReflection, uNearRadius2;
uniform bool uLinear;
out vec4 fragColor;
float attenuation(vec3 delta, float range, vec4 direction) {
    float distance = length(delta);
    float x = distance / range;
    if (x >= 1.0) return 0.0;
    float a = clamp((dot(delta/max(distance,0.0001),direction.xyz)-direction.w)/max(1.0-direction.w,0.0001),0.0,1.0);
    float edge = a*a*(3.0-2.0*a);
    float window = 1.0-x*x*x*x;
    return edge*window*window/(distance*distance+uNearRadius2);
}
float localVisibility(int source,vec3 position) {
    int slot=int(uLocalShadowSlots[source]);
    if(slot<0)return 1.0;
    // 接收面沿法线微移，避免低位光源与门槛在掠射角发生自遮挡。
    vec4 q=uLocalShadowVp[slot]*vec4(position+vec3(0,.012,0),1);
    vec3 p=q.xyz/q.w*.5+.5;
    if(q.w<=0.0||p.z<=0.0||p.z>=1.0||any(lessThan(p.xy,vec2(0)))||any(greaterThan(p.xy,vec2(1))))return 1.0;
    vec2 tile=vec2(float(slot%4),float(slot/4));
    float visible=0.0;
    // 偏移只由光源与地面夹角决定，避免相机缩放改变阴影边缘。
    float incidence=abs(normalize(uPositions[source].xyz-position).y);
    float bias=.0010+.0008*(1.0-incidence);
    // 以地面厘米尺度过滤半影，避免移动光源的阴影栅格逐像素跳动。
    float softness=uShadowSoftness[source];
    vec4 qx=q+uLocalShadowVp[slot][0]*softness;
    vec4 qz=q+uLocalShadowVp[slot][2]*softness;
    vec2 dx=(qx.xy/qx.w-q.xy/q.w)*.5;
    vec2 dz=(qz.xy/qz.w-q.xy/q.w)*.5;
    for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++) {
        vec2 samplePoint=p.xy+(dx*float(x)+dz*float(y))*.5;
        vec2 uv=(tile+clamp(samplePoint,vec2(.005),vec2(.995)))/vec2(4,2);
        visible+=texture(uLocalShadowAtlas,vec3(uv,p.z-bias));
    }
    return mix(1.0, visible/25.0, clamp(uShadowGains[source], 0.0, 1.0));
}
void main() {
    vec3 V = normalize(uEye-vWorld);
    vec3 R = reflect(-V,vec3(0,1,0));
    vec3 light = vec3(0);
    for (int i=0; i<48; ++i) {
        if (i>=uCount) break;
        vec3 position = uPositions[i].xyz;
        vec3 toLight = position-vWorld;
        float range = uColors[i].w;
        if (length(toLight)>range+uAxes[i].w || toLight.y<=0.0) continue;
        vec3 L = normalize(toLight);
        // 连续椭圆散射：不再沿灯具线段采样，避免把灯具轮廓投成地面色块。
        vec3 horizontal = vec3(uDirections[i].x,0,uDirections[i].z);
        vec3 forward = length(horizontal) > .001 ? normalize(horizontal) : normalize(uAxes[i].xyz);
        vec3 groundDelta = vWorld-position;
        groundDelta.y = 0.0;
        float longitudinal = dot(groundDelta, forward);
        vec3 lateralVec = groundDelta-forward*longitudinal;
        float lateral = length(lateralVec);
        float spread = max(uAxes[i].w*1.7, range*0.45) + max(longitudinal,0.0)*0.42;
        float oval = exp(-(longitudinal*longitudinal/(range*range) + lateral*lateral/(spread*spread))*.65);
        float diffuse = max(L.y,0.0)*0.46*attenuation(-toLight,range,uDirections[i])*oval;
        if (uFrontBlend[i] > 0.0) {
            // 车头近场与远场使用相同连续光瓣，先累加辐照度再压缩峰值。
            // 有限宽度的灯面和路面散射让左右光源自然重叠，不生成两个独立圆斑。
            float width = max(.72,uAxes[i].w*1.7)+max(longitudinal,0.0)*.58;
            float flood = exp(-lateral*lateral/(width*width));
            flood *= smoothstep(-.30,.40,longitudinal);
            flood *= .045/(1.0+max(longitudinal,0.0)*.62);
            // 连续近场只改变光瓣外形，仍需遵守真实灯轴和地面入射角。
            // 近场强度保留方向衰减，防止光照越过灯轴背面。
            float angularWindow = attenuation(-toLight,range,uDirections[i])
                * (dot(toLight,toLight)+uNearRadius2);
            diffuse = flood*angularWindow*max(L.y,0.0);
        }
        if (uHeadlights[i] > .5) {
            // 与空气积分使用同一灯轴、有限灯口、径向光瓣与射程收敛。
    // 入射角衰减仅应用一次，保持掠射光的可见精度。
            vec3 delta = vWorld-position;
            float t = dot(delta,uDirections[i].xyz);
            float slope = sqrt(max(0.0,1.0-uDirections[i].w*uDirections[i].w))/max(.01,uDirections[i].w);
            float radius = max(.01,uAxes[i].w)+max(t,0.0)*slope;
            float radial = length(delta-uDirections[i].xyz*t)/radius;
            float density = exp(-radial*radial*2.0)*(1.0-smoothstep(.45,1.0,radial));
            density *= smoothstep(0.0,.12,t)*(1.0-smoothstep(range*.4,range,t));
            diffuse = .12*density*pow(max(L.y,0.0),.7)/(1.0+t*t*.32);
        }
        float specular = 0.0;
        if (uReflection>0.0) {
            // 宽化高光只由连续光晕中心决定，不复制灯带或灯壳外形。
            vec3 samplePosition = position+forward*clamp(longitudinal,0.0,range*.45);
            vec3 S = normalize(samplePosition-vWorld);
            vec3 H = normalize(S+V);
            float nh = max(H.y,0.0), nv=max(V.y,0.001), nl=max(S.y,0.0);
            float roughness = 0.75;
            float a2 = pow(roughness,4.0);
            float den = nh*nh*(a2-1.0)+1.0;
            float D = a2/(3.14159265*den*den);
            float k = (roughness+1.0)*(roughness+1.0)/8.0;
            float G = nv/(nv*(1.0-k)+k)*nl/(nl*(1.0-k)+k);
            float F = 0.06+0.94*pow(1.0-max(dot(V,H),0.0),5.0);
            specular = D*G*F/(4.0*nv)*attenuation(vWorld-samplePosition,range,uDirections[i])*oval*0.05*(1.0-clamp(uFrontBlend[i],0.0,1.0));
        }
        float exterior = 1.0;
        if (dot(uExteriorPlanes[i].xyz,uExteriorPlanes[i].xyz) > .5) {
            float outside = dot(vWorld,uExteriorPlanes[i].xyz)-uExteriorPlanes[i].w;
            exterior = smoothstep(0.0,.14,outside);
        }
        light += uColors[i].rgb*uPositions[i].w*(diffuse+specular)*exterior*localVisibility(i,vWorld);
    }
    // sRGB 附件负责线性混合；非 sRGB 降级附件输出近似 gamma 增量。
    if (!uLinear) light = light/0.8;
    // 限制叠加峰值，保留连续扩散的层次，避免窄角光锥把地面烧成白色色块。
    light = vec3(.28)*(vec3(1.0)-exp(-max(light,vec3(0))/.28));
    fragColor = vec4(light,0);
}"""
    }
}
