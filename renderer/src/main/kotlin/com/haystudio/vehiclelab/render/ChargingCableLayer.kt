package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import com.haystudio.vehiclelab.data.ChargingCableGeometry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sqrt

class ChargingCableLayer {
    data class Lighting(
        val ambient: Float,
        val portEnergy: Float = 0f,
        val portPosition: FloatArray = floatArrayOf(0f, 0f, 0f)
    )
    data class Flow(val timeSeconds: Float = 0f, val strength: Float = 0f, val scale: Float = 1f, val distance: Float = timeSeconds * .34f)
    private var cachedPath = emptyList<FloatArray>()
    private var cachedRadius = 0f
    private var cachedScale = 0f
    private var cachedTube = FloatArray(0)
    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var shadowVao = 0
    private var shadowVbo = 0
    private var shadowVertexCount = 0
    private var shadowPositions = FloatArray(0)
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(MAX_FLOATS * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun onContextCreated() {
        program = 0; vao = 0; vbo = 0
        shadowVao = 0; shadowVbo = 0
        clearShadowGeometry()
    }

    fun draw(vp: FloatArray, eye: FloatArray, path: List<FloatArray>, radius: Float, lighting: Lighting, opacity: Float = 1f, flow: Flow = Flow()) {
        if (path.size < 2) return
        ensureProgram()
        val safeRadius=radius.coerceIn(.004f,.03f)
        if(cachedRadius!=safeRadius || cachedScale!=flow.scale || cachedPath.size!=path.size ||
            path.indices.any { !path[it].contentEquals(cachedPath[it]) }) {
            cachedTube=ChargingCableGeometry.surfaceTubeVertices(path,safeRadius,16,flow.scale)
            cachedPath=path.map { it.copyOf() }; cachedRadius=safeRadius; cachedScale=flow.scale
        }
        drawTube(vp, eye, cachedTube,
            floatArrayOf(.012f, .015f, .018f), lighting.copy(portEnergy = 0f), opacity,
            faded = true, flow = flow, radius = safeRadius / flow.scale)
    }

    fun drawPlug(vp: FloatArray, eye: FloatArray, center: FloatArray, outward: FloatArray, scale: Float, lighting: Lighting, size: Float = 1f, depth: Float = .085f, insertion: Float = 0f, opacity: Float = 1f) {
        ensureProgram()
        drawTube(vp, eye, ChargingCableGeometry.connectorVertices(center,outward,scale,size,depth,insertion),
            floatArrayOf(.78f,.80f,.82f),lighting,opacity)
    }

    fun prepareShadowGeometry(path: List<FloatArray>, radius: Float, center: FloatArray, outward: FloatArray,
                              scale: Float, size: Float, depth: Float, insertion: Float) {

        val shadowPath = resample(path, 14)
        val cable = ChargingCableGeometry.fadedTubeVertices(shadowPath,
            (radius * .72f).coerceIn(.003f, .022f), 8)
        val plug = ChargingCableGeometry.connectorVertices(center, outward, scale, size, depth, insertion)
        val total = cable.size / 7 + plug.size / 6
        shadowPositions = FloatArray(total * 3)
        var cursor = 0
        for (i in cable.indices step 7) {
            shadowPositions[cursor++] = cable[i]
            shadowPositions[cursor++] = cable[i + 1]
            shadowPositions[cursor++] = cable[i + 2]
        }
        for (i in plug.indices step 6) {
            shadowPositions[cursor++] = plug[i]
            shadowPositions[cursor++] = plug[i + 1]
            shadowPositions[cursor++] = plug[i + 2]
        }
        shadowVertexCount = shadowPositions.size / 3
    }

    private fun resample(path: List<FloatArray>, count: Int): List<FloatArray> {
        if (path.size <= count) return path
        val cumulative = FloatArray(path.size)
        for (i in 1 until path.size) {
            val a = path[i - 1]; val b = path[i]
            val dx = b[0] - a[0]; val dy = b[1] - a[1]; val dz = b[2] - a[2]
            cumulative[i] = cumulative[i - 1] + sqrt(dx * dx + dy * dy + dz * dz)
        }
        val total = cumulative.last().coerceAtLeast(1e-5f)
        var segment = 0
        return List(count) { sample ->
            val distance = total * sample / (count - 1f)
            while (segment < path.lastIndex - 1 && cumulative[segment + 1] < distance) segment++
            val span = (cumulative[segment + 1] - cumulative[segment]).coerceAtLeast(1e-5f)
            val t = ((distance - cumulative[segment]) / span).coerceIn(0f, 1f)
            FloatArray(3) { axis -> path[segment][axis] + (path[segment + 1][axis] - path[segment][axis]) * t }
        }
    }

    fun clearShadowGeometry() {
        shadowPositions = FloatArray(0)
        shadowVertexCount = 0
    }

    fun drawShadow(shadowProgram: Int, viewProjLocation: Int, modelLocation: Int, positionLocation: Int,
                   viewProj: FloatArray) {
        if (shadowVertexCount == 0 || shadowProgram == 0 || positionLocation < 0) return
        ensureShadowBuffer()
        GLES30.glUseProgram(shadowProgram)
        GLES30.glUniformMatrix4fv(viewProjLocation, 1, false, viewProj, 0)
        val identity = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,1f,0f, 0f,0f,0f,1f)
        GLES30.glUniformMatrix4fv(modelLocation, 1, false, identity, 0)
        shadowBuffer.clear()
        shadowBuffer.put(shadowPositions)
        shadowBuffer.flip()
        GLES30.glBindVertexArray(shadowVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shadowVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, shadowBuffer.remaining() * 4, shadowBuffer)
        GLES30.glEnableVertexAttribArray(positionLocation)
        GLES30.glVertexAttribPointer(positionLocation, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, shadowVertexCount)
        GLES30.glDisableVertexAttribArray(positionLocation)
        GLES30.glBindVertexArray(0)
    }

    private fun drawTube(
        vp: FloatArray,
        eye: FloatArray?,
        tube: FloatArray,
        color: FloatArray,
        lighting: Lighting,
        opacity: Float,
        faded: Boolean = false,
        flow: Flow = Flow(),
        radius: Float = 0f
    ) {
        if (tube.isEmpty()) return
        vertices.clear()
        if (faded) vertices.put(tube) else {
            for (i in tube.indices step 6) {
                for (component in 0 until 6) vertices.put(tube[i + component])
                vertices.put(1f)
                vertices.put(0f); vertices.put(0f)
            }
        }
        vertices.flip()
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "uVp"), 1, false, vp, 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uColor"), color[0], color[1], color[2])
        val alpha = opacity.coerceIn(0f, 1f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uOpacity"), alpha)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFlowDistance"), flow.distance)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uFlowStrength"), flow.strength.coerceIn(0f,1f))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uCableRadius"), radius)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uAmbient"), lighting.ambient.coerceIn(.05f, 1.2f))
        GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uPortEnergy"), lighting.portEnergy.coerceAtLeast(0f))
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uPortPosition"), lighting.portPosition[0], lighting.portPosition[1], lighting.portPosition[2])
        val view = eye ?: floatArrayOf(0f, 0f, 4f)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uEye"), view[0], view[1], view[2])
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertices.remaining() * 4, vertices)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        GLES30.glDepthMask(!faded && alpha>=.999f)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertices.remaining() / FLOATS_PER_VERTEX)
        GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
    }

    private fun ensureProgram() {
        if (program != 0) return
        program = GlUtil.program(VERTEX, FRAGMENT)
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0]
        GLES30.glGenBuffers(1, ids, 0); vbo = ids[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, MAX_FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 2, GLES30.GL_FLOAT, false, FLOATS_PER_VERTEX * 4, 28)
        GLES30.glBindVertexArray(0)
    }

    private val shadowBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_SHADOW_FLOATS * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun ensureShadowBuffer() {
        if (shadowVao != 0 && shadowVbo != 0) return
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0); shadowVao = ids[0]
        GLES30.glGenBuffers(1, ids, 0); shadowVbo = ids[0]
        GLES30.glBindVertexArray(shadowVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shadowVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, MAX_SHADOW_FLOATS * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindVertexArray(0)
    }

    private fun normalized(v: FloatArray): FloatArray {
        val length = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f)
        return floatArrayOf(v[0] / length, v[1] / length, v[2] / length)
    }

    fun release() {
        if (program != 0) GLES30.glDeleteProgram(program)
        if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
        if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        if (shadowVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(shadowVbo), 0)
        if (shadowVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(shadowVao), 0)
        onContextCreated()
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 9
        private const val MAX_FLOATS = 128 * 16 * 36 * FLOATS_PER_VERTEX
        private const val MAX_SHADOW_FLOATS = 128 * 16 * 36 * 3
        private const val VERTEX = """#version 300 es
precision highp float;
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
layout(location=2) in float aFade;
layout(location=3) in vec2 aSurface;
uniform mat4 uVp;
out vec3 vWorld;
out vec3 vNormal;
out float vFade;
out vec2 vSurface;
void main(){vWorld=aPosition;vNormal=aNormal;vFade=aFade;vSurface=aSurface;gl_Position=uVp*vec4(aPosition,1.0);}
"""
        private const val FRAGMENT = """#version 300 es
precision highp float;
in vec3 vWorld;
in vec3 vNormal;
in float vFade;
in vec2 vSurface;
uniform float uFlowDistance;
uniform float uFlowStrength;
uniform float uCableRadius;
uniform vec3 uColor;
uniform vec3 uEye;
uniform float uOpacity;
uniform float uAmbient;
uniform float uPortEnergy;
uniform vec3 uPortPosition;
out vec4 fragColor;
void main(){
  vec3 n=normalize(vNormal);
  vec3 l=normalize(vec3(-.38,.82,.42));
  vec3 v=normalize(uEye-vWorld);
  // 不把透明管道背面的光照再次叠到正面，消除移动视角时的亮度翻倍。
  if(dot(n,v)<=0.0 || uOpacity*vFade<=.001) discard;
  float diffuse=max(dot(n,l),0.0);
  float spec=pow(max(dot(n,normalize(l+v)),0.0),28.0);
  float edge=pow(1.0-max(dot(n,v),0.0),3.0);
  vec3 portDir=normalize(uPortPosition-vWorld);
  float portDistance2=dot(vWorld-uPortPosition,vWorld-uPortPosition);
  float portAtt=uPortEnergy*exp(-portDistance2*90.0)*max(dot(n,portDir),0.0);
  // 插头外壳保持中性白色，仅在插口附近接收有限的间接光。
  vec3 rubber=uColor*(uAmbient+diffuse*.62)+vec3(spec*.22+edge*.06)+vec3(1.0)*portAtt*.22;
  if(uFlowStrength>.0001){
    float core=0.0;
    float halo=0.0;
    // 每束电流使用不同间距和种子；每颗的周向位置、长度、亮度不同，不形成整齐灯带。
    // 距离由功率积分推进，变速时同一颗电流继续行进，不重排或瞬移。
    for(int lane=0;lane<8;lane++){
      float seed=float(lane)*17.17+3.71;
      float laneHash=fract(sin(seed*7.13)*43758.5453);
      float spacing=.38+laneHash*.64;
      float travel=vSurface.x+uFlowDistance+laneHash*2.3;
      float cell=floor(travel/spacing);
      float q=fract(travel/spacing)*spacing;
      float h=fract(sin(cell*12.9898+seed)*43758.5453);
      float h2=fract(sin(cell*7.231+seed*3.7)*19283.173);
      float delta=q-spacing*(.25+h*.45);
      float head=.008+.009*h2;
      float tail=.023+.055*h;
      float footprint=max(fwidth(vSurface.x)*.65,.001);
      float along=exp(-pow(delta/max(delta<0.0 ? head : tail,footprint),2.0));
      float angle=6.2831853*fract(laneHash+h2*.71)+.16*sin(delta*28.0+seed);
      float theta=atan(sin(vSurface.y-angle),cos(vSurface.y-angle));
      float across=abs(theta)*uCableRadius;
      float pixelWidth=max(fwidth(across)*.65,.0006);
      float width=max(.0014+.0012*h2,pixelWidth);
      float energy=.48+.52*h2;
      core=max(core,energy*along*exp(-pow(across/width,2.0)));
      halo=max(halo,energy*along*exp(-pow(across/max(width*2.5,.005),2.0)));
    }
    // 靠地端随线缆一起渐隐，插头端短距离平滑收束，避免亮点在接缝处骤然消失。
    float envelope=smoothstep(.005,.065,vSurface.x)*uFlowStrength;
    float c=clamp(core*envelope,0.0,1.0);
    rubber=mix(rubber,vec3(.28,.89,.79),c*.82)+vec3(.025,.10,.10)*min(halo*envelope,1.0);
  }
  fragColor=vec4(rubber,uOpacity*vFade);
}
"""
    }
}
