package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.WheelExposure
import com.haystudio.vehiclelab.data.ModelResourceStats

import com.haystudio.vehiclelab.data.WeatherMode
import com.haystudio.vehiclelab.data.WeatherSimulation
import com.haystudio.vehiclelab.data.WeatherCollision
import com.haystudio.vehiclelab.data.WeatherFrame
import com.haystudio.vehiclelab.data.WeatherVec
import com.haystudio.vehiclelab.data.CabinAirflow
import com.haystudio.vehiclelab.data.AirflowMode
import com.haystudio.vehiclelab.data.ChargingSimulation
import com.haystudio.vehiclelab.data.ChargingSnapshot
import com.haystudio.vehiclelab.data.ChargingState
import com.haystudio.vehiclelab.data.ChargingPortPresentation
import com.haystudio.vehiclelab.data.ChargingConnectionMotion
import com.haystudio.vehiclelab.data.ChargingCableGeometry
import com.haystudio.vehiclelab.data.LightEnvironmentResponse
import com.haystudio.vehiclelab.data.WindowControl
import com.haystudio.vehiclelab.data.WindowTouchRegion
import com.haystudio.vehiclelab.data.ChargingPort
import com.haystudio.vehiclelab.data.RoofCurtain
import com.haystudio.vehiclelab.data.FrontLampState
import com.haystudio.vehiclelab.data.RearLampState
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.haystudio.vehiclelab.data.DesktopCamera
import com.haystudio.vehiclelab.data.CarPalette
import com.haystudio.vehiclelab.data.CarTint
import com.haystudio.vehiclelab.data.PaintSelection
import com.haystudio.vehiclelab.data.PaintStyle
import com.haystudio.vehiclelab.data.PaintRegion
import com.haystudio.vehiclelab.data.VehicleCorner
import com.haystudio.vehiclelab.data.DrivingControls
import com.haystudio.vehiclelab.data.DrivingDynamics
import com.haystudio.vehiclelab.data.DrivingSuspension
import com.haystudio.vehiclelab.data.DayPeriod
import com.haystudio.vehiclelab.data.GenvEnvironment
import com.haystudio.vehiclelab.data.GenvEnvironmentReader
import com.haystudio.vehiclelab.data.MeshData
import com.haystudio.vehiclelab.data.GwmModel
import com.haystudio.vehiclelab.data.GwmNode
import com.haystudio.vehiclelab.data.GwmNodeTree
import com.haystudio.vehiclelab.data.KtxTexture
import com.haystudio.vehiclelab.data.LampProfile
import com.haystudio.vehiclelab.data.VehicleLights
import com.haystudio.vehiclelab.data.VehicleInteractionApi
import com.haystudio.vehiclelab.data.VehicleLamp
import com.haystudio.vehiclelab.data.VehicleBeamRig
import com.haystudio.vehiclelab.data.VehicleMotionChannel
import com.haystudio.vehiclelab.data.VehicleMotionProgress
import com.haystudio.vehiclelab.data.VehicleMotionTransforms
import com.haystudio.vehiclelab.data.VehicleLightPresentation
import com.haystudio.vehiclelab.data.ModelRepository
import com.haystudio.vehiclelab.data.InteriorLighting
import com.haystudio.vehiclelab.data.DrivingKinematics
import com.haystudio.vehiclelab.data.GroundUvProjection
import com.haystudio.vehiclelab.data.SteeringKinematics
import com.haystudio.vehiclelab.data.WheelKinematics
import com.haystudio.vehiclelab.data.CameraPhysics
import com.haystudio.vehiclelab.data.CameraPhysicsConfig
import com.haystudio.vehiclelab.data.CameraPreset
import com.haystudio.vehiclelab.data.LightRig
import com.haystudio.vehiclelab.data.MaterialKind
import com.haystudio.vehiclelab.data.MaterialLibrary
import com.haystudio.vehiclelab.data.MaterialSpec
import com.haystudio.vehiclelab.data.NodeClip
import com.haystudio.vehiclelab.data.NodeActivation
import com.haystudio.vehiclelab.data.PackedLights
import com.haystudio.vehiclelab.data.SceneAsset
import com.haystudio.vehiclelab.data.ShaderSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import kotlin.math.abs
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 管理车辆渲染通道及交互状态；所有 GPU 资源操作必须在所属 GL 线程执行。 */
class CarRenderer : GLSurfaceView.Renderer {

    val interactionApi: VehicleInteractionApi by lazy { RendererInteractionApi(this) }

    private companion object {
        val WHITE = floatArrayOf(1f, 1f, 1f)

        const val GROUP_NONE = -1
        const val GROUP_DOOR = 0
        const val GROUP_WINDOW = 1
        const val GROUP_SUNROOF = 2
        const val GROUP_TRUNK = 3

        const val VERTEX_STRIDE = 48

        const val SHADOW_SIZE = 2048

        const val SHADOW_BIAS = 0.005f
        
        const val SHADOW_BLUR = 1f
        
        const val SHADOW_COLOR_R = 0f
        const val SHADOW_COLOR_G = 0f
        const val SHADOW_COLOR_B = 0f
        
        const val SHADOW_DARKNESS = 0.75f
        
        const val SHADOW_RANGE_Z_START = 1f
        const val SHADOW_RANGE_Z_END = 2f
        
        const val SHADOW_LIGHT_SIZE = 0.027f
        
        const val SHADOW_EDGE_FALLOFF = 0.13f

        const val CAR_VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
uniform mat4 uMvp;
uniform mat4 uModel;
uniform mat3 uNormalMat;
out vec3 vNormal;
out vec3 vWorld;
out vec2 vUv;
void main() {
    vec4 world = uModel * vec4(aPos, 1.0);
    vWorld = world.xyz;
    vNormal = normalize(uNormalMat * aNormal);
    vUv = aUv;
    gl_Position = uMvp * vec4(aPos, 1.0);
}"""

        const val CAR_FS = """#version 300 es
precision highp float;
uniform vec3 uBase;
uniform vec3 uEmissive;
uniform vec3 uEye;
uniform float uMetallic;
uniform float uRough;
uniform float uEmissiveInt;
uniform float uAlpha;
uniform float uCoat;
uniform float uCoatRough;
uniform float uDirSpec;
uniform float uEnvSpec;
uniform float uEnvDiff;
uniform float uHasAlbedo;
uniform float uHasEmissive;
uniform float uAlphaCut;
uniform float uUnlit;
uniform float uCarCl;
uniform bool uOutputLinear;
uniform bool uApplyToneMapping;
uniform float uDirLight;
uniform vec4 uUvST;
uniform vec3 uEnvTop;
uniform vec3 uEnvSide;
uniform vec3 uEnvDown;
uniform float uEnvYaw;
uniform float uHasEnvCube;
uniform float uMirrorSurface;
uniform sampler2D uAlbedoTex;
uniform sampler2D uEmissiveTex;
uniform sampler2D uOrmTex;
uniform float uHasOrm;
uniform sampler2D uAlphaMask;
uniform float uHasAlphaMask;
uniform vec2 uOrmST;
uniform samplerCube uEnvTex;
in vec3 vNormal;
in vec3 vWorld;
in vec2 vUv;
out vec4 fragColor;

${PaintShader.HELPERS}

const float INV_PI = 0.31830988618379;
const vec3 LIGHT_DIFF = vec3(0.62, 0.64, 0.70);
const vec3 LIGHT_SPEC = vec3(1.0, 0.99, 0.96);

float pow5(float b) {
    float b2 = b * b;
    return b2 * b2 * b;
}

float brightness(vec3 c) {
    return (c.r + c.g + c.b) / 3.0;
}

vec2 rot2(vec2 v, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

vec3 aces2byd(vec3 x) {
    return clamp((x * (0.026 * x + 0.132)) / (x * (0.010 * x + 0.163) + 0.101),
        vec3(0.0), vec3(1.0));
}

vec3 desaturate(vec3 c, float sat) {
    float b = (c.r + c.g + c.b) / 3.0;
    return mix(vec3(b), c, sat);
}

// 桌面把反射向量 z 取反后再按 _EnvSpecYaw 绕 Y 旋转
vec3 reflectDir(vec3 v, vec3 n) {
    vec3 r = reflect(-v, n);
    r.z *= -1.0;
    r.xz = rot2(r.xz, radians(uEnvYaw));
    return normalize(r);
}

vec3 envSample(vec3 dir, float lod) {
    if (uHasEnvCube > 0.5) {
        return textureLod(uEnvTex, dir, lod).rgb;
    }
    float up = clamp(dir.y, 0.0, 1.0);
    float dn = clamp(-dir.y, 0.0, 1.0);
    return mix(mix(uEnvSide, uEnvTop, up), uEnvDown, dn);
}

void main() {
    vec2 uv = vUv * uUvST.xy + uUvST.zw;
    vec4 texel = uHasAlbedo > 0.5 ? texture(uAlbedoTex, uv) : vec4(1.0);
    // 仪表台、车窗投影这类材质只有发光贴图，镂空形状也在它的 alpha 里
    if (uHasAlbedo < 0.5 && uHasEmissive > 0.5) texel = texture(uEmissiveTex, uv);
    if (uAlphaCut > 0.0 && texel.a < uAlphaCut) discard;
    // 桌面 alphaTexture：灰度图直接当透明度用（轮胎软阴影），不是反照率也不是 alpha 通道
    float maskTex = uHasAlphaMask > 0.5 ? texture(uAlphaMask, uv).r : 1.0;

    // 桌面 unlit（仪表屏底色、投影贴图）：只输出 tint × 贴图
    if (uUnlit > 0.5) {
        vec3 c = clamp(uBase * texel.rgb, 0.0, 1.0);
        if (!uOutputLinear) c = pow(c, vec3(1.0 / 2.2));
        fragColor = vec4(c, uAlpha * texel.a * maskTex);
        return;
    }

    vec3 N = normalize(vNormal);
    vec3 V = normalize(uEye - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    vec3 base = uBase * mix(vec3(1.0), texel.rgb, uHasAlbedo);
    base=labPaintColor(base,vWorld,vWorld,N);
    // 桌面 USE_ORM：r=AO、g=粗糙度、b=金属度，直接乘在材质常量上
    vec3 orm = uHasOrm > 0.5 ? texture(uOrmTex, vUv * uOrmST).rgb : vec3(1.0);

    float met = clamp(orm.b * uMetallic, 0.0, 1.0);
    float rgh = clamp(orm.g * uRough, 0.03, 1.0);
    if(uLabPaintEnabled>.5){met=uLabPaintSurface.y;rgh=uLabPaintSurface.x;}
    float ros = rgh * rgh;
    float ros2 = ros * ros;
    vec3 F0 = mix(vec3(0.04), base, met);
    float oneMinusMet = 1.0 - met;
    float NdotV = max(0.000001, dot(N, V));

    // ---- 方向光（桌面同一个 GGX/Disney 公式，场景本身无灯，这里作补光）----
    vec3 L = normalize(vec3(0.35, 0.85, 0.45));
    vec3 H = normalize(L + V);
    float NdotLw = dot(N, L) * 0.75 + 0.25;
    float NdotL = clamp(NdotLw, 0.0001, 0.9999);
    float NdotH = max(0.000001, dot(N, H));
    float VdotH = max(0.000001, dot(V, H));
    float kd = (1.0 - F0.r) * oneMinusMet;
    vec3 dirDiff = kd * NdotL * LIGHT_DIFF * base * NdotL * INV_PI * uDirLight;

    float a2 = mix(0.002, 1.0, ros);
    a2 *= a2;
    float cos2 = NdotH * NdotH;
    float den = 1.0 + (a2 - 1.0) * cos2;
    float D = INV_PI * a2 / (den * den + 0.000001);
    float k = (rgh + 1.0) * (rgh + 1.0) * 0.125;
    float gv = NdotV / (NdotV * (1.0 - k) + k);
    float gl = NdotL / (NdotL * (1.0 - k) + k);
    vec3 fresnel = F0 + (vec3(1.0) - F0) * exp2((-5.55473 * VdotH - 6.98316) * VdotH);
    vec3 dirSpec = (D * gl * gv * 0.25) / NdotV * LIGHT_SPEC * 3.14159265 * uDirSpec * uDirLight;

    // ---- 环境漫反射：桌面统一用 _EnvTopCol/_SideCol/_DownCol 三段 SH，
    //      带三段色的材质用自己的，BRDF 材质（座椅、内饰）用场景天空色；
    //      反射立方体只参与高光，不当半球光用 ----
    float shTop = max(0.0, N.y);
    float shDown = max(0.0, -N.y);
    float shSide = 1.0 - shTop - shDown;
    vec3 envDiffMap = shTop * uEnvTop + shSide * uEnvSide + shDown * uEnvDown;
    envDiffMap *= uEnvDiff;
    // 桌面 dirDiffBrightness 只累计已启用的光源；关掉方向光的座舱材质取下限 0.04
    float dirBright = clamp(NdotLw * uDirLight, 0.04, 0.82);
    float fEnv = F0.r + (1.0 - F0.r) * exp2((-5.55473 * NdotV - 6.98316) * NdotV);
    float kdEnv = (1.0 - fEnv) * oneMinusMet;
    vec3 ambient = 0.03 * uEnvDiff * base;
    vec3 envDiff = (ambient + envDiffMap) * kdEnv * base;

    // ---- 环境反射：mip 由桌面 roughness 曲线决定，再 pow/去饱和 ----
    float rfm = rgh * (1.7 - 0.7 * rgh);
    vec3 envMap = envSample(reflectDir(V, N), rfm * 6.0);
    envMap = pow(max(envMap, vec3(0.0)), vec3(1.16));
    float satMask = max(1.0 - pow(dirBright, 48.0), pow(1.0 - NdotV, 16.0));
    float sat = satMask * 0.267 + 0.0633;
    envMap = desaturate(envMap, sat) * uEnvSpec;

    float reflectivity = max(max(envMap.r, envMap.g), envMap.b);
    float surfaceReduction = 1.0 / (ros2 + 1.0);
    float grazing = clamp(sqrt(1.0 - rgh) + reflectivity, 0.0, 1.0);
    float envMix = min(fEnv, uMirrorSurface > 0.5 ? 0.72 : (uCarCl > 0.5 ? 0.04 : 0.08));
    vec3 envSpec = envMap * mix(F0, vec3(grazing), envMix) *
        surfaceReduction;

    // ---- 清漆层（CAR_CL）：桌面第二条 cubemap 采样链 ----
    float clBright = 1.0;
    if (uCarCl > 0.5 && uCoat > 0.0) {
        float clRough = min(uCoatRough * 0.65, 1.0);
        vec3 rcl = reflectDir(V, N);
        rcl.y -= 0.01;
        rcl = normalize(rcl);
        float clMip = clRough * (1.7 - 0.7 * clRough) * 6.0;
        vec3 eCl = envSample(rcl, clMip);
        eCl = min(eCl, vec3(0.61)) * 2.0;
        eCl = pow(max(eCl, vec3(0.0)), vec3(1.16 * 2.3));
        eCl = desaturate(eCl, sat);
        clBright = max(max(eCl.r, eCl.g), eCl.b);
        float grazingCl = clamp(sqrt(1.0 - clRough) + reflectivity, 0.0, 1.0);
        envSpec += eCl * (uEnvSpec * 0.96 * uCoat) *
            mix(F0, vec3(grazingCl), min(fEnv, 0.04)) * surfaceReduction;
    }

    // 桌面 CAR_CL：漫反射由清漆反射亮度衰减，并混入 20% 基色（车漆颜色的主要来源）
    if (uCarCl > 0.5) {
        dirDiff *= smoothstep(0.04, 0.95, clBright);
        dirDiff = dirDiff * 0.8 + base * 0.2;
    }

    // 桌面按法线朝向压暗底部与背光面（CAR_CL 与非 CAR_CL 的映射不同）
    float vbao = clamp((N.y * 0.5 + 0.5) * 1.6, 0.0, 1.0);
    vbao = uCarCl > 0.5 ? 0.35 + 0.65 * vbao : 0.04 + 0.96 * vbao;
    // 桌面 aoFactor = ORM.r；CAR_CL 再乘上反照率贴图 alpha 的映射
    float aoFactor = orm.r;
    if (uCarCl > 0.5) aoFactor *= texel.a * 0.65 + 0.35;
    vec3 dirResult = dirDiff + dirSpec;
    vec3 envResult = (envDiff + envSpec) * aoFactor * (vbao * 0.85 + 0.15) *
        (dirBright * 0.4 + 0.6);

    // 桌面 USE_EMISSIVE_TEX：自发光按贴图 alpha 混入，并随漫反射/环境亮度与 AO 衰减。
    // 保留贴图与 AO 对座舱窗沿和遮光材质的局部亮度约束。
    vec3 col = dirResult + envResult;
    if (uMirrorSurface > 0.5) {
        // 镜面反射以环境为主，边缘 Fresnel 更强；保留少量深蓝灰底色，避免变成白色贴片。
        float edge = pow(1.0 - NdotV, 1.45);
        vec3 mirrorColor = envMap * (.60 + .30 * edge) + envDiff * .12 + dirSpec * .08;
        col = mix(mirrorColor, vec3(.028,.048,.072), .10);
    }
    if (uHasEmissive > 0.5) {
        vec4 eTex = texture(uEmissiveTex, uv);
        eTex.rgb = pow(max(eTex.rgb, vec3(0.0)), vec3(1.1));
        vec3 emis = eTex.rgb * uEmissive * uEmissiveInt *
            (pow(max(brightness(dirResult), 0.0), 0.1) +
                pow(max(brightness(envResult), 0.0), 1.2)) *
            pow5(aoFactor);
        col = mix(col, emis, eTex.a);
    }
    if (uCarCl > 0.5) {
        col *= 1.013;
        col.r *= 1.013;
        col = pow(max(col, vec3(0.0)), vec3(1.13));
    } else {
        col *= 1.05;
    }
    if (uApplyToneMapping) col = aces2byd(col);
    if (!uOutputLinear) col = pow(max(col,vec3(0.0)), vec3(1.0 / 2.2));

    // 桌面：CAR_CL 只用 _Opacity；其它透明材质再乘贴图 alpha 与视角项
    float alpha = uCarCl > 0.5
        ? uAlpha
        : uAlpha * texel.a * (pow5(1.0 - NdotV) * 0.15 + 0.85);
    fragColor = vec4(col, alpha * maskTex);
}"""

        const val GRID_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
uniform mat4 uVp;
uniform float uY;
out vec3 vWorld;
void main() {
    vWorld = vec3(aPos.x, uY, aPos.y);
    gl_Position = uVp * vec4(vWorld, 1.0);
}"""

        const val GRID_FS = """#version 300 es
precision highp float;
in vec3 vWorld;
out vec4 fragColor;
void main() {
    vec2 p = vWorld.xz;
    vec2 grid = abs(fract(p - 0.5) - 0.5) / max(fwidth(p), vec2(1e-4));
    float line = 1.0 - min(min(grid.x, grid.y), 1.0);
    float d = length(p);
    float fade = 1.0 - smoothstep(2.5, 10.0, d);
    float a = (line * 0.28 + 0.045) * fade;
    fragColor = vec4(vec3(0.30, 0.55, 0.82) * a, a);
}"""
    }

    private class GpuMesh(
        val vao: Int,
        val vbo: Int,
        val ebo: Int,
        val indexCount: Int,
        val spec: MaterialSpec,
        val alpha: Float,
        val node: Int,
        val albedoTex: Int,
        val emissiveTex: Int,
        val envCube: Int,
        
        val ormTex: Int,
        
        val alphaMaskTex: Int,
        val bloom: Boolean,
        
        val ancestors: Set<String>,
        
        val name: String,
        
        val part: String,
        
        val bounds: FloatArray,
        
        val touchVertices: FloatArray = FloatArray(0),
        val touchIndices: IntArray = IntArray(0)
    ) {
        
        val wheel = part.contains("Wheel", true) || name.contains("Wheel", true) || ancestors.any { it.contains("Wheel", true) }
        val matrix = FloatArray(16)
        var plateAnchor: PlateAnchor? = null
        var flowRange: FloatArray? = null
    }

    private data class CarFrame(
        val lengthAxisX: Boolean,
        val halfLength: Float,
        val halfWidth: Float,
        val bottomY: Float,
        val height: Float
    )

    private val groundLightLayer = GroundLightLayer()
    private val groundTextures = GroundTextures()
    private val headlightBeamLayer = HeadlightBeamLayer()
    private val weatherLayer=WeatherLayer()
    private val chargingCableLayer = ChargingCableLayer()
    private data class ChargingCableFrame(
        val path: List<FloatArray>,
        val plugCenter: FloatArray,
        val outward: FloatArray,
        val scale: Float,
        val insertion: Float,
        val opacity: Float,
        val port: ChargingPort,
        val lighting: ChargingCableLayer.Lighting
    )
    private var chargingCableFrame: ChargingCableFrame? = null
    private var chargingFlowGain = 0f
    private var chargingFlowSpeed = 0f
    private var chargingFlowDistance = 0f
    @Volatile private var fastChargingPowerKw = 60f
    @Volatile private var slowChargingPowerKw = 3.5f
    var chargingPowerKw: Float
        get() = if(selectedChargingPort==ChargingPort.FAST) fastChargingPowerKw else slowChargingPowerKw
        set(value) {
            val safe=if(value.isNaN()) 0f else value.coerceIn(0f,com.haystudio.vehiclelab.data.ChargingFlowProfile.limit(selectedChargingPort))
            if(selectedChargingPort==ChargingPort.FAST) fastChargingPowerKw=safe else slowChargingPowerKw=safe
        }
    fun chargingFlowSnapshot(): FloatArray = floatArrayOf(chargingPowerKw,chargingFlowDistance,chargingFlowSpeed,chargingFlowGain)
    private val panoramaLayer=PanoramaLayer()
    private val cabinAirflow=CabinAirflow()
    @Volatile var airflowMode=AirflowMode.OFF
    @Volatile var airflowFan=.5f
    @Volatile var airflowDirections=setOf(com.haystudio.vehiclelab.data.AirflowDirection.FACE)
    @Volatile var weatherTemperatureC=-4f
    @Volatile private var clearWeatherRequested=false
    fun clearSurfaceWeather(){clearWeatherRequested=true}
    @Volatile var weatherMode=WeatherMode.NONE
    @Volatile var weatherIntensity=.55f
        set(value) { field=if(value.isFinite())value.coerceIn(0f,1f) else 0f }
    private var weatherSimulation=WeatherSimulation()
    private var weatherCollision=WeatherCollision(emptyList())
    private var weatherCollisionDirty=true
    private var uploadedModel:GwmModel?=null
    private var weatherStepMillis=0.0
    @Volatile private var panoramaEnvironment:ByteArray?=null
    @Volatile var environmentName="原厂桌面"
        private set
    fun setPanoramaEnvironment(bytes:ByteArray?,name:String) {
        panoramaEnvironment=bytes;environmentName=name;sceneEnvironmentDirty=true
    }
    fun setWeatherSeed(seed:Long) { weatherSimulation=WeatherSimulation(seed) }
    fun weatherSnapshot()=mapOf("mode" to weatherMode.name,"intensity" to weatherIntensity,
        "particles" to weatherSimulation.particles.size,"contacts" to weatherSimulation.contacts.size,
        "bodyHits" to weatherSimulation.bodyHits,"groundHits" to weatherSimulation.groundHits,
        "collisionTriangles" to weatherCollision.triangleCount,"stepMs" to weatherStepMillis,"drawMs" to weatherLayer.drawMillis,
        "environment" to environmentName,"snowVolume" to weatherSimulation.snowVolume,
        "snowPatches" to weatherSimulation.snow.patches.size,"groundSnowCover" to weatherSimulation.groundSnowCover,
        "released" to weatherSimulation.released,"temperatureC" to weatherTemperatureC,
        "airflow" to airflowMode.name,"airParticles" to cabinAirflow.particles.size)
    private val lampGlowLayer = LampGlowLayer()
    private var drawingLampMask = false
    private val licensePlateLayer = LicensePlateLayer()
    private var platePng: ByteArray? = null
    private var plateAnchors: List<PlateAnchor> = emptyList()
    private val surfaceVariants = HashMap<String, MaterialSpec>()
    @Volatile var licensePlateEnabled = true
    @Volatile var plateAppearance = PlateAppearance()
    @Volatile var turnFrequencyPerMinute = 90f
    private val courtesyGains = HashMap<String,Float>()
    private val doorCourtesyMounts=HashMap<String,FloatArray>()
    private var vehicleBeams: VehicleBeamRig? = null
    private var groundEmitters: List<GroundEmitter> = emptyList()
    @Volatile var groundLightEnabled = true
    @Volatile var lampReflectionEnabled = true
    @Volatile private var requestedVehicleLights=VehicleLights()
    private val emergencyBrakeSignal=com.haystudio.vehiclelab.data.EmergencyBrakeSignal()
    private val vehicleLights:VehicleLights
        get()=emergencyBrakeSignal.apply(requestedVehicleLights).with(VehicleLamp.BRAKE,VehicleLamp.BRAKE in requestedVehicleLights ||
            drivingSimulation && vehicleSpeedKmh==null && drivingControls.brakeLampOn)
            .with(VehicleLamp.REVERSE,VehicleLamp.REVERSE in requestedVehicleLights ||
                drivingSimulation && (drivingControls.gear==com.haystudio.vehiclelab.data.DriveGear.REVERSE || (vehicleSpeedKmh ?: 0f)<0f))
    
    val currentLights: VehicleLights get() = vehicleLights
    val manualLights: VehicleLights get() = requestedVehicleLights
    private val sceneLayer = SceneLayer({ ktx -> createTexture(ktx) }) { mesh, vbo, ibo ->
        val gpu = GpuMesh(0, vbo, ibo, mesh.indices.size, mesh.material, mesh.material.opacity,
            -1, 0, 0, 0, 0, 0, false, emptySet(), mesh.name, mesh.part, FloatArray(6))
        Matrix.setIdentityM(gpu.matrix, 0)
        val ground=mesh.material.path.substringAfterLast('/').startsWith("m_env_ground_")
        val variant=if(ground)SurfaceMaterialVariants.weatherGround(SurfaceMaterialVariants.movingGround(mesh.material)) else mesh.material
        val axes=if(ground)GroundUvProjection.axes(mesh.positions,mesh.uvs,mesh.indices) else FloatArray(4)
        val draw: () -> Unit = {
            val background=mesh.material.path.contains("skybox",true) || mesh.material.path.contains("mountain",true) || mesh.material.path.contains("m_env_fog_",true)
            Matrix.setIdentityM(gpu.matrix,0)
            if(background)Matrix.rotateM(gpu.matrix,0,-Math.toDegrees(drivingPose.heading).toFloat(),0f,1f,0f)
            val shader = officialProgram(variant)
                ?: error("原厂场景着色器编译失败：${mesh.material.path}")
            drawOfficial(gpu, shader, null, if(ground)axes else null)
        }
        draw
    }
    private val sceneVp = FloatArray(16)
    private val shadowVp = FloatArray(16)

    @Volatile private var sceneGroundY = 0f
    @Volatile private var sceneEnvironment: ByteArray? = null
    @Volatile private var sceneEnvironmentDirty = false
    private val meshes = ArrayList<GpuMesh>()
    private val tex2d = HashMap<String, Int>()
    
    private val texDiag = HashSet<String>()
    private val texCube = HashMap<String, Int>()
    private var maxAniso = 0f

    @Volatile private var pendingModel: GwmModel? = null
    private var lastModel: GwmModel? = null
    private var needsUpload = false

    @Volatile var palette: CarPalette? = null
        private set
    @Volatile var lamps: LampProfile? = null
        private set
    @Volatile var colorIndex: Int = 0
        private set
    @Volatile var trimColorIndex: Int = 0
        private set
    @Volatile var paintSelection=PaintSelection()
        private set
    fun customizePaint(trim:Boolean,style:PaintStyle?) {
        paintSelection=paintSelection.customize(trim,style)
        notifyTouch()
    }
    fun customizePaint(region:PaintRegion,style:PaintStyle?) {
        paintSelection=paintSelection.customize(region,style)
        notifyTouch()
    }
    private fun paintFor(path:String):PaintStyle? {
        val p=palette ?: return null
        return p.regionOf(path)?.let(paintSelection::style)
    }

    @Volatile var lampState: Int = 0
        private set

    var checkGl = false
    val glErrors = ArrayList<String>()
    private val validatedPrograms = HashSet<Int>()
    private fun checkGlAt(stage: String) {
        if (!checkGl) return
        var code = GLES30.glGetError()
        while (code != GLES30.GL_NO_ERROR) {
            if (glErrors.size < 40) glErrors.add("$stage:$code")
            code = GLES30.glGetError()
        }
    }

    private var desktopCamera: DesktopCamera? = null
    private val rotation = OrbitRotation()
    private val cabinLook = CabinLook()

    var fixedDeltaSeconds: Float? = null
    
    var poseProgress: Float? = null
    private val nodeProgress = HashMap<String, Float>()

    fun setNodeProgress(node: String, progress: Float?) {
        require(progress == null || progress.isFinite() && progress in 0f..1f)
        if (progress == null) nodeProgress.remove(node) else nodeProgress[node] = progress
    }

    fun nodeProgressSnapshot():Map<String,Float> = nodeProgress.toMap()

    fun cameraSnapshot(): List<Float> = displayedCamera?.let { it.eye.toList()+listOf(it.yaw,it.pitch,it.fov,it.near) } ?: emptyList()
    @Volatile var settingsPanelWidthPixels=0f
    private var settingsPanelFraction=0f
    private var panelFitScale=1f
    private var panelCenterX=0f
    private var panelCenterY=0f
    private var panelLastOccupied=-1f
    private var panelTargetScale=1f
    private var panelTargetCenterX=0f
    private var panelTargetCenterY=0f
    private var tourScaleStart=1f
    private var tourCenterXStart=0f
    private var tourCenterYStart=0f
    private val targetFramingView=FloatArray(16)
    private val targetFramingProj=FloatArray(16)
    private var framingCorners=emptyList<FloatArray>()
    private val framingProjection=FloatArray(16)
    private val framingPoint=FloatArray(4)
    private val cameraTour=CameraTour()
    private var displayedCamera:CameraView?=null
    @Volatile private var cabinCamera=false
    private val curtainBounds=HashMap<Int,FloatArray>()
    private var curtainSlot=0f
    private var curtainTravel=1f

    fun setDesktopCamera(camera: DesktopCamera) {
        val firstCamera = desktopCamera == null
        desktopCamera = camera
        cabinCamera=false
        cameraTour.begin(displayedCamera)
        
        rotation.reset(camera.yaw + 180f,camera.pitch,firstCamera)
        yawDeg = rotation.targetYaw
        pitchDeg = rotation.targetPitch
        distance = camera.armLength
        smoothDistance = distance
        autoRotate = false
        camera.projection(viewportWidth.toFloat() / viewportHeight).copyInto(proj)
        if (lastModel != null) needsUpload = true
    }

    @Volatile var handlesExtended: Boolean = false
    @Volatile var drivingSimulation: Boolean = false
        set(value) {
            if(value)disconnectCharging()
            field = value
        }
    @Volatile var drivingControls=DrivingControls()
        private set
    fun applyDrivingControls(value:DrivingControls) {
        drivingControls=value.normalized()
    }
    private val drivingDynamics=DrivingDynamics()
    private val drivingSuspension=DrivingSuspension()
    private var drivingActive=false
    private val bodyMotionMatrix=FloatArray(16)
    private var roadDistanceMetres=0.0
    private var drivingPose=DrivingKinematics.Pose()
    private val drivingFollow=DrivingFollow()
    private var rearAxleX=1.45f
    private var chassisGeometry=SteeringKinematics.Geometry()
    fun chassisDimensions()=listOf(chassisGeometry.wheelbase,chassisGeometry.track,chassisGeometry.radius)
    fun drivingSnapshot()=listOf(roadDistanceMetres,smoothWheelSpeedKmh.toDouble(),drivingPose.x,drivingPose.z,drivingPose.heading)
    private val doorCornerOverrides=java.util.concurrent.ConcurrentHashMap<VehicleCorner,Boolean>()
    @Volatile var onBodyTargetsChanged:(()->Unit)?=null
    @Volatile var doorsOpen: Boolean = false
        set(value) { field=value;doorCornerOverrides.clear();onBodyTargetsChanged?.invoke() }
    fun doorOpen(corner:VehicleCorner)=doorCornerOverrides[corner] ?: doorsOpen
    fun setDoorOpen(corner:VehicleCorner,open:Boolean) { doorCornerOverrides[corner]=open;notifyTouch();onBodyTargetsChanged?.invoke() }
    fun windowOpen(corner:VehicleCorner)=(windowProgressOverrides[corner.windowClip] ?: if(windowsOpen)1f else 0f)>.001f
    fun setWindowOpen(corner:VehicleCorner,open:Boolean) {
        windowProgressOverrides[corner.windowClip]=if(open)1f else 0f
        notifyTouch()
        onBodyTargetsChanged?.invoke()
    }
    @Volatile var windowsOpen: Boolean = false
        set(value) {
            field = value
            windowProgressOverrides.keys.filter { VehicleMotionChannel.ofClip(it) == VehicleMotionChannel.WINDOW }
                .forEach(windowProgressOverrides::remove)
            onBodyTargetsChanged?.invoke()
        }
    @Volatile var sunroofVentOpen: Boolean = false
        set(value) {
            field = value
            windowProgressOverrides.keys.filter { VehicleMotionChannel.ofClip(it) == VehicleMotionChannel.SUNROOF }
                .forEach(windowProgressOverrides::remove)
            if (value) sunroofOpen = false
        }
    @Volatile var sunroofOpen: Boolean = false
        set(value) {
            field = value
            windowProgressOverrides.keys.filter { VehicleMotionChannel.ofClip(it) == VehicleMotionChannel.SUNROOF }
                .forEach(windowProgressOverrides::remove)
            if (value) sunroofVentOpen = false
        }
    @Volatile var trunkOpen: Boolean = false
    @Volatile var hoodOpen: Boolean = false
    @Volatile var mirrorsExpanded: Boolean = true
    
    @Volatile var leftChargerOpen: Boolean = false
    @Volatile var rightChargerOpen: Boolean = false
    @Volatile var selectedChargingPort: ChargingPort = ChargingPort.SLOW
    
    private var visibleChargingPort: ChargingPort = ChargingPort.SLOW
    private val chargingCapMotion = mutableMapOf(
        ChargingPort.FAST to 0f,
        ChargingPort.SLOW to 0f
    )
    private var chargingPlugMotion = 0f
    private var chargingGroundGlowGain = 0f
    
    private var chargingGroundAnchor: FloatArray? = null
    private var chargingGroundAnchorPort: ChargingPort? = null
    
    private var closeChargeCoverAfterDisconnect = false
    @Volatile private var currentChargingSnapshot = ChargingSnapshot()
    val chargingState: ChargingState get() = currentChargingSnapshot.state
    val simulatedChargePercent: Float get() = currentChargingSnapshot.stateOfChargePercent
    @Volatile var onChargingSnapshot: ((ChargingSnapshot) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPublishedChargePercent = 35
    fun chargingSnapshot() = currentChargingSnapshot
    fun chargingMotionSnapshot() = listOf(
        chargingCapMotion.getValue(ChargingPort.FAST),
        chargingCapMotion.getValue(ChargingPort.SLOW),
        chargingPlugMotion
    )
    @Synchronized
    fun connectCharging(): Boolean {
        // 充电连接与行驶互斥：用户主动连接时先退出行驶模拟，再执行充电口开启动画。
        if (drivingSimulation) drivingSimulation = false
        val current = chargingState
        val connection = ChargingSimulation.openPortAndConnect(current, selectedChargingPort)
        rightChargerOpen = connection.portOpen
        closeChargeCoverAfterDisconnect = true
        if (connection.state == current) return false
        currentChargingSnapshot = currentChargingSnapshot.copy(state = connection.state)
        publishChargingSnapshot()
        return true
    }
    @Synchronized
    fun startCharging(): Boolean {
        val next = ChargingSimulation.start(chargingState)
        if (next == chargingState) return false
        currentChargingSnapshot = currentChargingSnapshot.copy(state = next)
        publishChargingSnapshot()
        return true
    }
    @Synchronized
    fun stopCharging(): Boolean {
        val next = ChargingSimulation.stop(chargingState)
        if (next == chargingState) return false
        currentChargingSnapshot = currentChargingSnapshot.copy(state = next)
        publishChargingSnapshot()
        return true
    }
    @Synchronized
    fun completeCharging(): Boolean {
        val next = ChargingSimulation.complete(chargingState)
        if (next == chargingState) return false
        currentChargingSnapshot = currentChargingSnapshot.copy(state = next, stateOfChargePercent = 100f)
        publishChargingSnapshot()
        return true
    }
    @Synchronized
    fun disconnectCharging(): Boolean {
        val next = ChargingSimulation.disconnect(chargingState)
        if (next == chargingState) return false
        currentChargingSnapshot = currentChargingSnapshot.copy(state = next)
        publishChargingSnapshot()
        return true
    }
    @Synchronized
    private fun advanceCharging(dt: Float) {
        val current = currentChargingSnapshot
        val next = ChargingSimulation.advance(current, dt)
        if (next == current) return
        currentChargingSnapshot = next
        val displayedPercent = next.stateOfChargePercent.toInt()
        if (displayedPercent != lastPublishedChargePercent || next.state != ChargingState.CHARGING) {
            lastPublishedChargePercent = displayedPercent
            publishChargingSnapshot()
        }
    }
    private fun publishChargingSnapshot() {
        val listener = onChargingSnapshot ?: return
        val snapshot = chargingSnapshot()
        mainHandler.post { if (onChargingSnapshot === listener) listener(snapshot) }
    }
    @Volatile var wheelsRolling: Boolean = false
    
    @Volatile var wheelCyclesPerSecond: Float = 0f
    
    /** 外部有符号车速（km/h）：正数前进、负数倒车、零保持停止，null 使用踏板。 */
    @Volatile var vehicleSpeedKmh: Float? = null
        set(value) {field=value?.takeIf(Float::isFinite)?.coerceIn(-180f,180f)}
    private var smoothWheelSpeedKmh = 0f
    @Volatile var wheelsSteered: Boolean = false
    @Volatile var steeringAngleDegrees: Float? = null
    private var smoothSteeringAngle = 0f
    private var steeringGeometryOffset = .25f
    fun steeringAngles() = SteeringKinematics.resolve(smoothSteeringAngle,chassisGeometry)
    @Volatile var curtainOpen: Boolean = false
        set(value) {
            field = value
            windowProgressOverrides.keys.filter { VehicleMotionChannel.ofClip(it) == VehicleMotionChannel.CURTAIN }
                .forEach(windowProgressOverrides::remove)
        }
    private val cabinLightTransition = com.haystudio.vehiclelab.data.CabinLightTransition()
    fun interiorLightSnapshot() = floatArrayOf(cabinLightTransition.brightness, *cabinLightTransition.color)
    @Volatile var readingLamps:Set<com.haystudio.vehiclelab.data.CabinReadingLamp> = emptySet()
    @Volatile var luggageLampEnabled:Boolean = true
    private val courtesyFrameInverse=FloatArray(16).also{Matrix.setIdentityM(it,0)}
    private val readingGains=FloatArray(4)
    private var luggageGain=0f
    fun courtesyCabinSnapshot()=floatArrayOf(*readingGains,luggageGain)
    @Volatile private var trunkButtonRect:FloatArray?=null
    fun tapTrunkButton(x:Float,y:Float):Boolean {
        val rect=trunkButtonRect ?: return false
        if(x !in rect[0]..rect[2] || y !in rect[1]..rect[3])return false
        trunkOpen=!trunkOpen;notifyTouch();onBodyTargetsChanged?.invoke();return true
    }
    private fun updateTrunkButton() {
        trunkButtonRect=null
        val mesh=meshes.firstOrNull{it.part==TrunkButtonGeometry.PART} ?: return
        if(!isVisible(mesh) || isHidden(mesh,frameHidden))return
        val local=floatArrayOf((mesh.bounds[0]+mesh.bounds[3])*.5f,(mesh.bounds[1]+mesh.bounds[4])*.5f,(mesh.bounds[2]+mesh.bounds[5])*.5f,1f)
        val center=FloatArray(4);Matrix.multiplyMV(center,0,mesh.matrix,0,local,0)
        val normal=FloatArray(4)
        Matrix.multiplyMV(normal,0,mesh.matrix,0,floatArrayOf(0f,-1f,0f,0f),0)
        if((eyePos[0]-center[0])*normal[0]+(eyePos[1]-center[1])*normal[1]+(eyePos[2]-center[2])*normal[2]<=0f)return
        val matrix=FloatArray(16);Matrix.multiplyMM(matrix,0,vp,0,mesh.matrix,0)
        val input=FloatArray(4);input[3]=1f;val out=FloatArray(4)
        var left=Float.POSITIVE_INFINITY;var top=Float.POSITIVE_INFINITY;var right=Float.NEGATIVE_INFINITY;var bottom=Float.NEGATIVE_INFINITY
        for(c in 0..7){for(a in 0..2)input[a]=mesh.bounds[a+if(c and (1 shl a)==0)0 else 3]
            Matrix.multiplyMV(out,0,matrix,0,input,0);if(out[3]<=0f)continue
            val x=(out[0]/out[3]*.5f+.5f)*viewportWidth;val y=(.5f-out[1]/out[3]*.5f)*viewportHeight
            left=minOf(left,x);right=maxOf(right,x);top=minOf(top,y);bottom=maxOf(bottom,y)
        }
        if(!left.isFinite() || !right.isFinite() || right<=left || bottom<=top)return
        trunkButtonRect=floatArrayOf(left-8,top-8,right+8,bottom+8)
    }
    @Volatile var interiorAmbientEnabled: Boolean = false
    @Volatile var interiorAmbientBrightness: Float = .5f
        set(value) { field = if(value.isFinite()) value.coerceIn(0f,1f) else 0f }
    @Volatile var interiorAmbientColor: FloatArray = floatArrayOf(.025f,.21f,.66f)
        set(value) { field = FloatArray(3) { i -> value.getOrNull(i)?.takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 0f } }
    @Volatile var yawDeg = 34f
    @Volatile var pitchDeg = 15f

    @Volatile var distance = 4.3f
    @Volatile var autoRotate = false

    fun setCameraPhysics(config: CameraPhysicsConfig) {
        
    }
    fun beginCameraGesture(){
        panelTargetScale=panelFitScale;panelTargetCenterX=panelCenterX;panelTargetCenterY=panelCenterY
        if(cameraTour.isActive) {
            tourScaleStart=panelFitScale;tourCenterXStart=panelCenterX;tourCenterYStart=panelCenterY
            displayedCamera?.let { current ->
                gestureView=current
                if(cabinCamera) {
                    cabinLook.reset(current.yaw,current.pitch)
                    smoothDistance=current.fov/65f
                    minDistance=minOf(.62f,smoothDistance);maxDistance=maxOf(1.25f,smoothDistance)
                } else {
                    freeOrbitAim=current.aim()
                    rotation.reset(current.yaw+180f+drivingFollow.lagDegrees,-current.pitch,snap=true)
                    smoothDistance=current.focusDistance
                }
            }
            pendingCameraPreset=null
            cameraTour.cancel()
        }
        if(cabinCamera) {
            cabinLook.interrupt()
        } else rotation.interrupt()
        distance=smoothDistance;notifyTouch()
    }
    fun dragCameraPixels(dx:Float,dy:Float,height:Float) {
        if(cabinCamera)cabinLook.drag(dx,dy,height,65f*smoothDistance)
        else {val k=(distance/4.3f).coerceIn(.45f,1f);rotation.drag(dx*k,dy*k)}
        notifyTouch()
    }
    fun flingCameraPixels(vx:Float,vy:Float,height:Float) {
        if(cabinCamera) {val k=65f*smoothDistance/height.coerceAtLeast(1f);cabinLook.fling(vx*k,vy*k)}
        else {val k=(distance/4.3f).coerceIn(.45f,1f);rotation.fling(-vx*.2f*k,vy*.2f*k)}
    }
    fun dragCamera(dx: Float, dy: Float) { if(cabinCamera)cabinLook.drag(dx,dy,viewportHeight.toFloat(),65f*smoothDistance) else rotation.drag(dx,dy);notifyTouch() }
    fun flingCamera(yawVelocity:Float,pitchVelocity:Float){rotation.fling(yawVelocity,pitchVelocity)}
    fun setCameraYaw(yaw: Float) { if(cabinCamera)cabinLook.reset(yaw,cabinLook.targetPitch); rotation.reset(yaw, rotation.targetPitch, snap = true); yawDeg = yaw }
    fun setCameraPitch(pitch: Float) { if(cabinCamera)cabinLook.reset(cabinLook.targetYaw,pitch); rotation.reset(rotation.targetYaw, pitch, snap = true); pitchDeg = pitch }
    private fun exteriorOrbitOriginY(): Float = when(selectedCameraPreset) {
        CameraPreset.WINDOW_LEFT_FRONT, CameraPreset.WINDOW_RIGHT_FRONT -> 1.16f
        CameraPreset.WINDOW_LEFT_REAR, CameraPreset.WINDOW_RIGHT_REAR -> 1.18f
        CameraPreset.SUNROOF_WINDOW -> 1.48f
        else -> freeOrbitAim?.getOrNull(1) ?: desktopCamera?.origin?.getOrNull(1) ?: 0f
    }
    private fun safeExteriorPitch(requestedPitch: Float): Float = CameraGroundClearance.safePitchDegrees(
        requestedPitch,
        exteriorOrbitOriginY(),
        CameraDistanceRange.resolve(minDistance, maxDistance).let { range ->
            maxOf(smoothDistance, distance.coerceIn(range.minimum, range.maximum))
        },
        groundY
    )
    @Volatile private var pendingCameraPreset: CameraPreset? = null
    @Volatile var selectedCameraPreset: CameraPreset = CameraPreset.DEFAULT
    fun isWindowPreset(): Boolean = WindowControl.sideClip(selectedCameraPreset) != null
    private var freeOrbitAim: FloatArray? = null
    
    private var gestureView: CameraView? = null
    fun takeOverWindowCamera() {
        if(!isWindowPreset()) return
        val current = displayedCamera ?: return
        pendingCameraPreset = null
        selectedCameraPreset = CameraPreset.DEFAULT
        freeOrbitAim = current.aim()
        rotation.reset(current.yaw + 180f + drivingFollow.lagDegrees, -current.pitch, snap = true)
        yawDeg = current.yaw + 180f + drivingFollow.lagDegrees; pitchDeg = -current.pitch
        distance = current.focusDistance; smoothDistance = distance
        cameraTour.cancel()
        notifyTouch()
    }
    fun selectCameraPreset(preset: CameraPreset) {
        freeOrbitAim = null
        selectedCameraPreset = preset
        pendingCameraPreset = preset
        notifyTouch()
    }
    
    private fun applyCameraPreset(preset: CameraPreset) {
        val camera=desktopCamera ?: return
        gestureView=null
        val wasInside=cabinCamera
        cabinCamera=preset.interior
        val (yaw,pitch,distance)=when(preset) {
            CameraPreset.DEFAULT -> Triple(camera.yaw+180f,camera.pitch,camera.armLength)
            CameraPreset.FRONT -> Triple(-90f,4f,12.5f)
            CameraPreset.REAR -> Triple(90f,4f,12.5f)
            CameraPreset.LEFT -> Triple(180f,4f,12.5f)
            CameraPreset.RIGHT -> Triple(0f,4f,12.5f)
            CameraPreset.FRONT_LEFT -> Triple(-135f,11f,12.5f)
            CameraPreset.FRONT_RIGHT -> Triple(-45f,11f,12.5f)
            CameraPreset.REAR_LEFT -> Triple(135f,11f,12.5f)
            CameraPreset.REAR_RIGHT -> Triple(45f,11f,12.5f)
            CameraPreset.TOP -> Triple(90f,65f,referenceCameraDistance(camera,65f))
            CameraPreset.DRIVER -> Triple(90f,16f,referenceCameraDistance(camera,16f)*1.12f)
            CameraPreset.CABIN, CameraPreset.CABIN_LEFT_FRONT, CameraPreset.CABIN_RIGHT_FRONT, CameraPreset.CABIN_LEFT_REAR, CameraPreset.CABIN_RIGHT_REAR -> Triple(-90f,-4f,1f)
            CameraPreset.ISLAND -> Triple(-90f,-11f,.9f)
            CameraPreset.WINDOW_LEFT_FRONT, CameraPreset.WINDOW_LEFT_REAR -> Triple(0f,2f,2.5f)
            CameraPreset.WINDOW_RIGHT_FRONT, CameraPreset.WINDOW_RIGHT_REAR -> Triple(180f,2f,2.5f)
            CameraPreset.SUNROOF_WINDOW -> Triple(90f,88f,3.2f)
        }
        tourScaleStart=panelFitScale;tourCenterXStart=panelCenterX;tourCenterYStart=panelCenterY
        cameraTour.begin(displayedCamera, exterior=!wasInside && !cabinCamera,cabin=wasInside||cabinCamera)
        rotation.reset(yaw,pitch,snap=true);this.distance=distance
        
        smoothDistance=distance
        if(cabinCamera){cabinLook.reset(yaw,pitch);minDistance=.62f;maxDistance=1.25f}
    }
    private fun referenceCameraDistance(camera: DesktopCamera, pitch: Float): Float {
        val model=lastModel ?: return camera.armLength
        val (lo,hi)=CameraFraming.bodyBounds(model,prefabHidden)
        return CameraFraming.distance(camera,lo,hi,90f,pitch,viewportWidth.toFloat()/viewportHeight)
    }
    fun zoomCamera(delta: Float) { distance=(distance+delta).coerceIn(minDistance,maxDistance);notifyTouch() }
    
    @Volatile var skipMesh = ""
    
    @Volatile var onlyMesh = ""

    @Volatile var noEmissive = false
    @Volatile private var lastTouchAt = 0L

    @Volatile var minDistance = 1.2f
        private set
    @Volatile var maxDistance = 14f
        private set
    private var smoothDistance = 4.3f

    @Volatile var fps = 0f
        private set
    @Volatile var triangleCount = 0
        private set

    @Volatile var debugInfoEnabled = false
    @Volatile private var frameWallMillis = 0.0
    @Volatile private var frameCpuMillis = 0.0
    @Volatile private var sceneDrawMillis = 0.0
    @Volatile private var carDrawMillis = 0.0
    @Volatile private var lightDrawMillis = 0.0
    @Volatile private var weatherDrawMillis = 0.0
    @Volatile private var postProcessMillis = 0.0

    @Volatile var resourceStats:ModelResourceStats?=null
        private set
    @Volatile var textureStats = "贴图未加载"
        private set

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val baseMatrix = FloatArray(16)
    private val normalMat = FloatArray(9)

    private var groundY = -0.55f

    private var reflectFbo = 0
    private var reflectTex = 0
    private var reflectDepth = 0
    private var reflectW = 0
    private var reflectH = 0
    private var hdrFbo = 0
    private var hdrTex = 0
    private var hdrDepth = 0
    private var hdrW = 0
    private var hdrH = 0
    private val reflectVp = FloatArray(16)
    private val reflectSceneVp = FloatArray(16)
    private val reflectSavedVp = FloatArray(16)
    private val mirror = FloatArray(16)

    val bloom = BloomPass()

    @Volatile var hideShadow = false
    @Volatile var hideScene = false
    
    @Volatile var hideReflection = false
    
    @Volatile var debugReflectionOnly = false

    private var viewportHeight = 1080
    private var viewportWidth = 1920
    private var carFrame: CarFrame? = null

    private var nodes: List<GwmNode> = emptyList()
    private var nodeRest: Array<FloatArray> = emptyArray()
    private var nodeLocals: Array<FloatArray> = emptyArray()
    private var nodePoseEvaluator: com.haystudio.vehiclelab.data.NodePoseEvaluator? = null
    private val poseMatrixScratch = FloatArray(16)
    private val previousBodyPose = FloatArray(16)
    private var appliedCurtainProgress = Float.NaN
    private var appliedSteeringAngle = Float.NaN
    private val clipByNode = HashMap<Int, List<NodeClip>>()
    private var groupByNode = IntArray(0)
    private var poseValue = FloatArray(0)
    private var poseTarget = FloatArray(0)
    private val clipProgress = HashMap<NodeClip, Float>()
    private val windowProgressOverrides = java.util.concurrent.ConcurrentHashMap<String, Float>()
    @Volatile private var heldWindowClip: String? = null
    @Volatile private var activeWindowTouchRegions: Map<String, WindowTouchRegion> = emptyMap()
    private var touchProjection = FloatArray(0)
    private var touchTransforms = emptyList<FloatArray>()
    private var touchMeshes = emptyList<GpuMesh>()
    private var touchPreset: CameraPreset? = null
    private var touchViewport = 0L

    fun windowTouchRegion(clipName: String): WindowTouchRegion? =
        if (WindowControl.sideClips(selectedCameraPreset).any { it.equals(clipName, true) })
            activeWindowTouchRegions.entries.firstOrNull { it.key.equals(clipName, true) }?.value else null
    fun windowTouchClipAt(x: Float, y: Float): String? = WindowControl.sideClips(selectedCameraPreset)
        .firstOrNull { clip -> activeWindowTouchRegions.entries.firstOrNull { it.key.equals(clip, true) }?.value?.contains(x, y) == true }
    fun currentWindowProgress(clipName: String): Float = clipByNode.values.asSequence().flatten()
        .firstOrNull { it.name.equals(clipName, true) }?.let { clipProgress[it] ?: 0f } ?: 0f
    fun setWindowDrag(clipName: String?, progress: Float? = null) {
        heldWindowClip = clipName
        if (clipName != null && progress != null) {
            require(progress.isFinite())
            windowProgressOverrides[clipName] = progress.coerceIn(0f, 1f)
            onBodyTargetsChanged?.invoke()
        }
        notifyTouch()
    }
    fun isHoldingWindow(): Boolean = heldWindowClip != null
    private var wheelRollPhase = 0f
    private val corrections = HashMap<Int, FloatArray>()
    
    private val effectMatrices = HashMap<Int, FloatArray>()

    private val officialPrograms = HashMap<String, OfficialProgram>()
    private val brdfLut = BrdfLut()
    private val officialFailed = HashSet<String>()
    private var officialLogged = false
    private var shadowLogged = false
    private var shadowReadback = false
    private var lightsByPeriod: Map<DayPeriod, LightRig> = emptyMap()
    private var packedLights: PackedLights? = null
    
    private var basePackedLights: PackedLights? = null
    private val eyePos = FloatArray(3)
    
    private var viewUbo = 0
    private var lightsUbo = 0
    
    private var lightsNoDirUbo = 0
    private var physicalUbo = 0
    
    private var materialUbo = 0
    
    private var shUbo = 0
    
    private var materialBound: FloatArray? = null
    
    private var shBakedCube = -1
    private var shBakedPeriod = -1
    
    @Volatile private var shDirty = true
    private var lightsDirty = false
    
    private var lightsNoDirBound = false

    @Volatile var hideSelfShadow = false
    @Volatile var viewOptions = com.haystudio.vehiclelab.data.DesktopViewOptions()
    var srgbFramebuffer = false
        private set
    private var shadowProgram = 0
    private var uShViewProj = 0
    private var uShModel = 0
    private var uShLightRel = 0
    private var uShDepthValue = 0
    private var shAttribPos = -1
    private var shFbo = 0
    private var shTex = 0
    private var shReady = false
    
    private var shHasLight = false
    private var packedShadowRequest = false
    private var shPerspective = false
    
    private var shBoundUnit = -1
    
    @Volatile var shadowBias = SHADOW_BIAS
    @Volatile var shadowBlur = SHADOW_BLUR
    private val shLightVp = FloatArray(16)
    private val shView = FloatArray(16)
    private val shProj = FloatArray(16)
    private val shEye = floatArrayOf(0f, 0f, 0f)
    private val shLook = floatArrayOf(0f, 0f, 0f)
    private val shDir = floatArrayOf(0f, -1f, 0f)
    
    private var shRadius = 3f
    private var shDistance = 6f
    
    private val shEncX = SHADOW_RANGE_Z_START
    private val shEncY = SHADOW_RANGE_Z_END

    private var program = 0
    private var uMvp = 0
    private var uModel = 0
    private var uNormal = 0
    private var uBase = 0
    private var uEye = 0
    private var uMetallic = 0
    private var uRough = 0
    private var uEmissive = 0
    private var uEmissiveInt = 0
    private var uAlpha = 0
    private var uCoat = 0
    private var uCoatRough = 0
    private var uCarCl = 0
    private var uOutputLinear = 0
    private var uApplyToneMapping = 0
    private var outputLinear = false
    private var materialToneMapping = true
    private var uDirSpec = 0
    private var uEnvSpec = 0
    private var uEnvDiff = 0
    private var uHasAlbedo = 0
    private var uHasEmissive = 0
    private var uHasOrm = 0
    private var uHasAlphaMask = 0
    private var uOrmST = 0
    private var uAlphaCut = 0
    private var uUnlit = 0
    private var uDirLight = 0
    private var uUvST = 0
    private var uEnvTop = 0
    private var uEnvSide = 0
    private var uEnvDown = 0
    private var uEnvYaw = 0
    private var uHasEnvCube = 0
    private var uMirrorSurface = 0

    private var sceneEnvCube = 0
    
    private var environment: GenvEnvironment? = null
    private var envCube = 0
    
    private val cubeMips = HashMap<Int, Int>()
    private val sceneTop = floatArrayOf(1.5f, 1.45f, 1.35f)
    private val sceneSide = floatArrayOf(1.0f, 0.97f, 0.92f)
    private val sceneDown = floatArrayOf(0.42f, 0.38f, 0.34f)
    private var sceneYaw = 13f

    private var gridProgram = 0
    private var gVp = 0
    private var gY = 0
    private var gridVbo = 0

    private var time = 0f
    private var lastNanos = 0L
    private var frames = 0
    private var fpsWindowStart = 0L
    private val frameIntervals = DoubleArray(120)
    private var frameIntervalCount = 0
    private var frameIntervalIndex = 0
    @Volatile private var renderDebugSnapshot = RenderDebugSnapshot()
    private var processCpuLastMillis = 0L
    private var processCpuLastNanos = 0L

    private var frameActive: Set<String> = emptySet()
    private val sceneBindings = LinkedHashMap<String, String>()
    private var frameHidden: Set<String> = emptySet()

    private var prefabHidden: Set<String> = emptySet()

    fun setModel(model: GwmModel) {
        pendingModel = model
    }

    fun setScene(asset: SceneAsset?) {
        sceneTransition.request()
        sceneGroundY = asset?.groundY ?: 0f
        sceneEnvironment = asset?.environment
        sceneEnvironmentDirty = true
        sceneLayer.setAsset(asset)
    }
    @Volatile var groundMaterialStyle: Int = 0
        set(value) { field=value.coerceIn(0,6); sceneLayer.groundMaterialStyle=field }

    private val shadowXf = floatArrayOf(1f, 1f, 0f, 0f)

    @Volatile private var dayPeriod = DayPeriod.NOON

    private val sceneTransition=SceneTransition()
    var sceneTransitionsEnabled=true
    private data class PeriodChange(val period:DayPeriod,val scene:SceneAsset?)
    @Volatile private var pendingPeriod:PeriodChange?=null
    fun transitionDayPeriod(period:DayPeriod,scene:SceneAsset?) {pendingPeriod=PeriodChange(period,scene)}
    fun setDayPeriod(period: DayPeriod) {
        if(dayPeriod!=period)sceneTransition.request()
        dayPeriod = period
        applySceneEnv()
        rebuildLights()
    }

    fun setHiddenNodes(nodes: Set<String>) {
        prefabHidden = nodes
    }

    fun setPalette(value: CarPalette?, defaultIndex: Int = 0) {
        palette = value
        colorIndex = defaultIndex.coerceIn(0, max(0, (value?.colors?.size ?: 1) - 1))
        trimColorIndex = colorIndex
        paintSelection=PaintSelection().selectBody(colorIndex)
        applySceneEnv()
    }

    fun setLamps(value: LampProfile?) {
        lamps = value
    }

    fun selectColor(index: Int) {
        val size = palette?.colors?.size ?: return
        if (size <= 0) return
        colorIndex = index.coerceIn(0, size - 1)
        trimColorIndex = colorIndex
        paintSelection=paintSelection.selectBody(colorIndex)
    }

    fun selectTrimColor(index: Int) {
        val size = palette?.colors?.size ?: return
        if (size > 0) {
            trimColorIndex = index.coerceIn(0, size - 1)
            paintSelection=paintSelection.selectTrim(trimColorIndex)
        }
    }

    fun setLampState(state: Int) {
        lampState = state.coerceIn(0, 2)
        requestedVehicleLights = VehicleLights.preset(lampState)
    }

    fun setVehicleLights(state: VehicleLights) { requestedVehicleLights = state }
    fun setVehicleBeams(rig: VehicleBeamRig?) { vehicleBeams = rig }

    fun notifyTouch() {
        lastTouchAt = SystemClock.uptimeMillis()
    }

    private fun updateDistanceLimits(dx: Float, dy: Float, dz: Float) {
        val f = carFrame ?: return
        val bounds=framingCorners
        val low=FloatArray(3){axis->bounds.minOfOrNull {it[axis]} ?: 0f}
        val high=FloatArray(3){axis->bounds.maxOfOrNull {it[axis]} ?: 0f}
        val origin=when(selectedCameraPreset) {
            CameraPreset.WINDOW_LEFT_FRONT -> floatArrayOf(-.17f,1.16f,.75f)
            CameraPreset.WINDOW_RIGHT_FRONT -> floatArrayOf(-.17f,1.16f,-.75f)
            CameraPreset.WINDOW_LEFT_REAR -> floatArrayOf(.69f,1.18f,.74f)
            CameraPreset.WINDOW_RIGHT_REAR -> floatArrayOf(.69f,1.18f,-.74f)
            CameraPreset.SUNROOF_WINDOW -> floatArrayOf(.18f,1.48f,0f)
            else -> freeOrbitAim ?: desktopCamera?.origin ?: floatArrayOf(0f,0f,0f)
        }
        val rawMinimum=com.haystudio.vehiclelab.data.CameraExteriorClearance.minimumDistance(low,high,origin,
            floatArrayOf(dx,dy,dz),gestureView?.near ?: diagnosticNear)
        val rawMaximum = (maxOf(f.halfLength, f.halfWidth) * 14f).coerceIn(12f, 24f)
        CameraDistanceRange.resolve(rawMinimum, rawMaximum).let { range ->
            minDistance = range.minimum
            maxDistance = range.maximum
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        sceneTransition.reset()
        wheelExposureLayer.onContextCreated()
        
        val encoding = IntArray(1)
        GLES30.glGetFramebufferAttachmentParameteriv(GLES30.GL_FRAMEBUFFER, GLES30.GL_BACK,
            GLES30.GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING, encoding, 0)
        check(GLES30.glGetError() == GLES30.GL_NO_ERROR) { "读取默认帧缓冲颜色编码失败" }
        srgbFramebuffer = encoding[0] == GLES30.GL_SRGB
        
        sceneLayer.onContextCreated()
        groundLightLayer.onContextCreated()
        groundTextures.onContextCreated()
        headlightBeamLayer.onContextCreated()
        lampGlowLayer.onContextCreated()
        weatherLayer.onContextCreated()
        chargingCableLayer.onContextCreated()
        panoramaLayer.onContextCreated()
        licensePlateLayer.onContextCreated()
        bloom.onContextCreated()
        brdfLut.onContextCreated()
        EnvSh.onContextCreated()
        cubeMips.clear()
        shBoundUnit = -1
        reflectFbo = 0
        reflectTex = 0
        reflectDepth = 0
        reflectW = 0
        reflectH = 0
        hdrFbo = 0
        hdrTex = 0
        hdrDepth = 0
        hdrW = 0
        hdrH = 0
        sceneEnvironmentDirty = true
        meshes.clear()
        doorCourtesyMounts.clear()
        tex2d.clear()
        texDiag.clear()
        texCube.clear()
        clipByNode.clear()
        poseValue = FloatArray(0)
        poseTarget = FloatArray(0)
        corrections.clear()
        needsUpload = lastModel != null
        sceneEnvCube = 0
        environment = null
        envCube = 0
        lastNanos = 0L
        fpsWindowStart = 0L
        frames = 0
        frameIntervalCount = 0

        program = GlUtil.program(CAR_VS, CAR_FS)
        uMvp = GLES30.glGetUniformLocation(program, "uMvp")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uNormal = GLES30.glGetUniformLocation(program, "uNormalMat")
        uBase = GLES30.glGetUniformLocation(program, "uBase")
        uEye = GLES30.glGetUniformLocation(program, "uEye")
        uMetallic = GLES30.glGetUniformLocation(program, "uMetallic")
        uRough = GLES30.glGetUniformLocation(program, "uRough")
        uEmissive = GLES30.glGetUniformLocation(program, "uEmissive")
        uEmissiveInt = GLES30.glGetUniformLocation(program, "uEmissiveInt")
        uAlpha = GLES30.glGetUniformLocation(program, "uAlpha")
        uCoat = GLES30.glGetUniformLocation(program, "uCoat")
        uCoatRough = GLES30.glGetUniformLocation(program, "uCoatRough")
        uCarCl = GLES30.glGetUniformLocation(program, "uCarCl")
        uOutputLinear = GLES30.glGetUniformLocation(program, "uOutputLinear")
        uApplyToneMapping = GLES30.glGetUniformLocation(program, "uApplyToneMapping")
        uDirSpec = GLES30.glGetUniformLocation(program, "uDirSpec")
        uEnvSpec = GLES30.glGetUniformLocation(program, "uEnvSpec")
        uEnvDiff = GLES30.glGetUniformLocation(program, "uEnvDiff")
        uHasAlbedo = GLES30.glGetUniformLocation(program, "uHasAlbedo")
        uHasEmissive = GLES30.glGetUniformLocation(program, "uHasEmissive")
        uHasOrm = GLES30.glGetUniformLocation(program, "uHasOrm")
        uHasAlphaMask = GLES30.glGetUniformLocation(program, "uHasAlphaMask")
        uOrmST = GLES30.glGetUniformLocation(program, "uOrmST")
        uAlphaCut = GLES30.glGetUniformLocation(program, "uAlphaCut")
        uUnlit = GLES30.glGetUniformLocation(program, "uUnlit")
        uDirLight = GLES30.glGetUniformLocation(program, "uDirLight")
        uUvST = GLES30.glGetUniformLocation(program, "uUvST")
        uEnvTop = GLES30.glGetUniformLocation(program, "uEnvTop")
        uEnvSide = GLES30.glGetUniformLocation(program, "uEnvSide")
        uEnvDown = GLES30.glGetUniformLocation(program, "uEnvDown")
        uEnvYaw = GLES30.glGetUniformLocation(program, "uEnvYaw")
        uMirrorSurface = GLES30.glGetUniformLocation(program, "uMirrorSurface")
        uHasEnvCube = GLES30.glGetUniformLocation(program, "uHasEnvCube")
        GLES30.glUseProgram(program)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uAlbedoTex"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uEmissiveTex"), 1)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uEnvTex"), 2)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uOrmTex"), 3)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uAlphaMask"), 4)

        gridProgram = GlUtil.program(GRID_VS, GRID_FS)
        gVp = GLES30.glGetUniformLocation(gridProgram, "uVp")
        gY = GLES30.glGetUniformLocation(gridProgram, "uY")

        val grid = floatArrayOf(-20f, -20f, 20f, -20f, -20f, 20f, 20f, 20f)
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        gridVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo)
        val fb = ByteBuffer.allocateDirect(grid.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(grid)
        fb.position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, grid.size * 4, fb, GLES30.GL_STATIC_DRAW)

        initEngineBlocks()
        initShadow()

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        GLES30.glFrontFace(GLES30.GL_CW)
        applyClearColor()
    }

    private fun applyClearColor() {
        val c = when (dayPeriod) {
            DayPeriod.MORNING -> floatArrayOf(0.78f, 0.72f, 0.66f)
            DayPeriod.NOON -> floatArrayOf(0.72f, 0.76f, 0.80f)
            DayPeriod.NIGHT -> floatArrayOf(0.03f, 0.04f, 0.07f)
        }
        GLES30.glClearColor(c[0], c[1], c[2], 1f)
    }

    private fun initShadow() {
        shReady = false
        val shaders=(pendingModel ?: lastModel)?.auxiliaryShaders.orEmpty()
        val vs = shaders["vertex"] ?: ShaderSource.assembleEngine("vs_depth")
        val fs = shaders["fragment"] ?: ShaderSource.assembleEngine("fs_depth")
        if (vs == null || fs == null) {
            Log.w("CarDemo", "自阴影：引擎深度着色器缺失，跳过")
            return
        }
        shadowProgram = try {
            GlUtil.program(vs, fs)
        } catch (t: Throwable) {
            Log.e("CarDemo", "自阴影：引擎深度着色器编译失败 ${t.message}")
            0
        }
        if (shadowProgram == 0) return
        uShViewProj = GLES30.glGetUniformLocation(shadowProgram, "u_viewProj")
        uShModel = GLES30.glGetUniformLocation(shadowProgram, "u_model")
        uShLightRel =
            GLES30.glGetUniformLocation(shadowProgram, "u_lightCameraRelativeTranslation")
        uShDepthValue =
            GLES30.glGetUniformLocation(shadowProgram, "u_lightSizeDepthValueFrustumEdgeFalloff")
        shAttribPos = GLES30.glGetAttribLocation(shadowProgram, "a_position")

        val ids = IntArray(2)
        GLES30.glGenTextures(1, ids, 0)
        shTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shTex)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24,
            SHADOW_SIZE, SHADOW_SIZE, 0,
            GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null
        )

        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE
        )

        GLES30.glGenFramebuffers(1, ids, 0)
        shFbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shFbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_TEXTURE_2D, shTex, 0
        )
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) ==
            GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        shReady = ok
        if (ok) {
            Log.i("CarDemo", "自阴影已启用：引擎深度通道 ${SHADOW_SIZE}x$SHADOW_SIZE")
        } else {
            Log.e("CarDemo", "自阴影：深度图帧缓冲不完整")
        }
    }

    private fun updateShadowMatrix(): Boolean {
        shHasLight = false
        val frame = carFrame ?: return false
        val packed = packedLights ?: return false
        val block = packed.block
        var base = -1
        for (i in 0 until packed.count) {
            if (block[i * LightRig.VEC_PER_LIGHT * 4 + 24] > 0.5f) {
                base = i * LightRig.VEC_PER_LIGHT * 4
                break
            }
        }
        if (base < 0) return false
        var dx = block[base + 12]
        var dy = block[base + 13]
        var dz = block[base + 14]
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1.0e-6f) return false
        dx /= len; dy /= len; dz /= len
        shDir[0] = dx; shDir[1] = dy; shDir[2] = dz

        shLook[0] = 0f
        shLook[1] = frame.bottomY + frame.height * 0.5f
        shLook[2] = 0f
        shRadius = max(1.2f, max(frame.halfLength, frame.height * 0.7f) * 1.4f)
        shDistance = shRadius * 2.5f
        shEye[0] = shLook[0] - dx * shDistance
        shEye[1] = shLook[1] - dy * shDistance
        shEye[2] = shLook[2] - dz * shDistance

        shPerspective = block[base + 18] > 1.5f
        if(shPerspective) {
            shEye[0]=block[base+8];shEye[1]=block[base+9];shEye[2]=block[base+10]
            shLook[0]=shEye[0]+dx;shLook[1]=shEye[1]+dy;shLook[2]=shEye[2]+dz
        }
        
        if (abs(dy) > 0.99f) {
            Matrix.setLookAtM(
                shView, 0, shEye[0], shEye[1], shEye[2], shLook[0], shLook[1], shLook[2], 0f, 0f, 1f
            )
        } else {
            Matrix.setLookAtM(
                shView, 0, shEye[0], shEye[1], shEye[2], shLook[0], shLook[1], shLook[2], 0f, 1f, 0f
            )
        }
        if(shPerspective) {
            val angle=Math.toDegrees(kotlin.math.acos(block[base+17].coerceIn(-.999f,.999f)).toDouble()).toFloat()*2f
            Matrix.perspectiveM(shProj,0,angle.coerceIn(5f,160f),1f,.05f,max(block[base+19],shDistance*4f))
        } else Matrix.orthoM(
            shProj, 0, -shRadius, shRadius, -shRadius, shRadius, 0.05f, shDistance * 2f
        )
        Matrix.multiplyMM(shLightVp, 0, shProj, 0, shView, 0)
        shHasLight = true
        return true
    }

    private fun drawShadowMap(hidden: Set<String>) {
        // 后处理可能关闭深度测试，阴影通道独立建立所需状态，避免继承上一帧状态。
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        
        GLES30.glBindVertexArray(0)
        
        if (shBoundUnit >= 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + shBoundUnit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            shBoundUnit = -1
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shFbo)
        GLES30.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glColorMask(false, false, false, false)
        GLES30.glUseProgram(shadowProgram)
        GLES30.glUniformMatrix4fv(uShViewProj, 1, false, shLightVp, 0)
        GLES30.glUniform3f(uShLightRel, 0f, 0f, 0f)
        GLES30.glUniform4f(
            uShDepthValue,
            shEncX, shEncY, SHADOW_LIGHT_SIZE, SHADOW_EDGE_FALLOFF
        )
        for (m in meshes) {
            if (m.bloom || m.spec.blended || m.spec.opacity <= 0.001f || chargingCoverVisibility(m) < .5f) continue
            if (!isVisible(m) || isHidden(m, hidden)) continue
            GLES30.glUniformMatrix4fv(uShModel, 1, false, m.matrix, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, m.vbo)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.ebo)
            if (shAttribPos >= 0) {
                GLES30.glEnableVertexAttribArray(shAttribPos)
                GLES30.glVertexAttribPointer(
                    shAttribPos, 3, GLES30.GL_FLOAT, false, VERTEX_STRIDE, 0
                )
            }
            GLES30.glDrawElements(
                GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, 0
            )
        }
        chargingCableLayer.drawShadow(shadowProgram, uShViewProj, uShModel, shAttribPos, shLightVp)
        if (shAttribPos >= 0) GLES30.glDisableVertexAttribArray(shAttribPos)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glColorMask(true, true, true, true)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    var diagnosticNear = .1f
    var diagnosticNoSpecular = false

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportHeight = max(1, height)
        viewportWidth = max(1, width)
        val aspect = if (height == 0) 1f else width.toFloat() / height
        
        desktopCamera?.projection(aspect, diagnosticNear)?.copyInto(proj)
            ?: Matrix.perspectiveM(proj, 0, 42f, aspect, 0.1f, 300f)
    }

    @Volatile var onModelInitialized:((GwmModel,Throwable?)->Unit)?=null
    private var initializingModel:GwmModel?=null
    private var renderingFailed=false
    override fun onDrawFrame(gl: GL10?) {
        if(renderingFailed && pendingModel==null)return
        try {drawFrame(gl)} catch(t:Throwable) {
            renderingFailed=true
            (initializingModel ?: lastModel)?.let {onModelInitialized?.invoke(it,t)}
            initializingModel=null
            android.util.Log.e("VehicleModelLab","模型渲染初始化失败",t)
        }
    }
    private fun drawFrame(gl: GL10?) {
        val frameStartNanos = System.nanoTime()
        val cpuStartNanos = Debug.threadCpuTimeNanos().takeIf { it >= 0L }
        
        pendingPeriod?.let {change ->
            pendingPeriod=null
            setDayPeriod(change.period)
            setScene(change.scene)
        }
        sceneTransition.capture(hdrFbo,hdrW,hdrH,sceneTransitionsEnabled)
        GLES30.glDepthMask(true)
        val now = System.nanoTime()
        val frameInterval = if (lastNanos > 0L) (now - lastNanos) / 1e6 else 0.0
        if (frameInterval > 1000.0 || fpsWindowStart == 0L) {
            fpsWindowStart = now
            frames = 0
            frameIntervalCount = 0
            frameIntervalIndex = 0
        } else if (frameInterval > 0.0) {
            frameIntervals[frameIntervalIndex] = frameInterval
            frameIntervalIndex = (frameIntervalIndex + 1) % frameIntervals.size
            frameIntervalCount = minOf(frameIntervalCount + 1, frameIntervals.size)
        }
        val dt = fixedDeltaSeconds ?: if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1e9f).coerceIn(0f, 0.1f)
        wheelExposureSeconds=(fixedDeltaSeconds ?: (frameInterval.toFloat()/1000f)).coerceIn(1f/120f,1f/30f)
        val courtesyResponse=1f-kotlin.math.exp(-dt/.15f)
        for(i in readingGains.indices)readingGains[i]+=((if(com.haystudio.vehiclelab.data.CabinReadingLamp.entries[i] in readingLamps)1f else 0f)-readingGains[i])*courtesyResponse
        val trunkProgress=clipProgress.filterKeys{VehicleMotionChannel.ofClip(it.name)==VehicleMotionChannel.TRUNK}.values.maxOrNull() ?: 0f
        luggageGain+=(com.haystudio.vehiclelab.data.CourtesyCabinLights.trunkTarget(trunkProgress,luggageLampEnabled)-luggageGain)*courtesyResponse
        lastNanos = now
        time += dt
        cabinLightTransition.advance(interiorAmbientEnabled, interiorAmbientBrightness, interiorAmbientColor, dt)
        for(part in listOf(MirrorCourtesyGeometry.LEFT,MirrorCourtesyGeometry.RIGHT)) {
            val target = if(MirrorCourtesyGeometry.enabled(part,vehicleLights)) 1f else 0f
            val current = courtesyGains[part] ?: 0f
            courtesyGains[part] = (current + (target-current)*(1f-kotlin.math.exp(-dt*10f))).let { if(kotlin.math.abs(it-target)<.001f)target else it }
        }
        advanceCharging(dt)
        val flowProfile=com.haystudio.vehiclelab.data.ChargingFlowProfile.atPower(chargingPowerKw)
        val flowing=chargingState==ChargingState.CHARGING && chargingPlugMotion>=.99f
        val response=1f-kotlin.math.exp(-dt*7f)
        chargingFlowGain+=((if(flowing) flowProfile.intensity else 0f)-chargingFlowGain)*response
        chargingFlowSpeed+=((if(flowing) flowProfile.speed else 0f)-chargingFlowSpeed)*response
        chargingFlowDistance+=chargingFlowSpeed*dt
        pendingModel?.let {
            pendingModel = null
            lastModel = it
            initializingModel=it
            renderingFailed=false
            needsUpload = true
        }
        if (needsUpload) {
            lastModel?.let { if(!shReady && it.auxiliaryShaders.isNotEmpty())initShadow();upload(it) }
            needsUpload = false
        }

        checkGlAt("上传与帧准备")
        advancePose(dt)
        updateChargingLampLighting()
        advanceChargingGroundGlow(dt)

        if (lastModel != null && desktopCamera != null) pendingCameraPreset?.let {
            pendingCameraPreset = null
            applyCameraPreset(it)
        }

        rotation.advance(dt)
        if(cabinCamera)cabinLook.advance(dt)
        yawDeg = if(cabinCamera)cabinLook.targetYaw else rotation.targetYaw
        if(!cabinCamera) {

            rotation.targetPitch = safeExteriorPitch(rotation.targetPitch)
        }
        pitchDeg = if(cabinCamera)cabinLook.targetPitch else rotation.targetPitch

        if (!cabinCamera && !cameraTour.isActive && autoRotate && SystemClock.uptimeMillis() - lastTouchAt > 2500L) {
            
            rotation.targetYaw += dt * 6.0f * (smoothDistance / 4.3f).coerceIn(0.35f, 1f)
        }

        val yaw = Math.toRadians((rotation.yaw-if(!cabinCamera && !isWindowPreset())drivingFollow.lagDegrees else 0f).toDouble())
        val displayedPitch = if(cabinCamera) rotation.pitch else safeExteriorPitch(rotation.pitch)
        val pitch = Math.toRadians(displayedPitch.toDouble())
        val cp = cos(pitch)
        val dirX = (sin(yaw) * cp).toFloat()
        val dirY = sin(pitch).toFloat()
        val dirZ = (cos(yaw) * cp).toFloat()
        if(!cabinCamera) {

            updateDistanceLimits(dirX, dirY, dirZ)
        }
        
        val distanceRange = CameraDistanceRange.resolve(minDistance, maxDistance)
        minDistance = distanceRange.minimum
        maxDistance = distanceRange.maximum
        val target = distance.coerceIn(distanceRange.minimum, distanceRange.maximum)
        distance = target
        smoothDistance = CameraResponse.followDistance(smoothDistance,target,dt)
        smoothDistance = smoothDistance.coerceIn(minDistance, maxDistance)
        val windowAim = when (selectedCameraPreset) {
            CameraPreset.WINDOW_LEFT_FRONT -> floatArrayOf(-.17f,1.16f,.75f)
            CameraPreset.WINDOW_RIGHT_FRONT -> floatArrayOf(-.17f,1.16f,-.75f)
            CameraPreset.WINDOW_LEFT_REAR -> floatArrayOf(.69f,1.18f,.74f)
            CameraPreset.WINDOW_RIGHT_REAR -> floatArrayOf(.69f,1.18f,-.74f)
            CameraPreset.SUNROOF_WINDOW -> floatArrayOf(.18f,1.48f,0f)
            else -> null
        }
        val aim = windowAim ?: freeOrbitAim ?: desktopCamera?.target ?: floatArrayOf(0f, 0f, 0f)
        val origin = windowAim ?: freeOrbitAim ?: desktopCamera?.origin ?: aim
        val eye = floatArrayOf(
            origin[0] + dirX * smoothDistance,
            origin[1] + dirY * smoothDistance,
            origin[2] + dirZ * smoothDistance
        )
        val camera=desktopCamera
        val desired=if(cabinCamera) {
            
            CameraView(gestureView?.eye?:selectedCameraPreset.cabinEye(),cabinLook.yaw,cabinLook.pitch,
                65f*smoothDistance,gestureView?.near?:.025f,gestureView?.shiftX?:0f,gestureView?.shiftY?:0f,1f)
        } else CameraView.looking(eye,aim,gestureView?.fov?:camera?.verticalFovDegrees?:42f,gestureView?.near?:diagnosticNear,
            gestureView?.shiftX?:if(camera?.sensorMode==0) camera.shiftX else (camera?.shiftX?:0f)/(camera?.sensorAspect?:1f),
            gestureView?.shiftY?:if(camera?.sensorMode==0) camera.shiftY*camera.sensorAspect else camera?.shiftY?:0f)
        val touring=cameraTour.isActive
        val actual=cameraTour.advance(desired,dt);displayedCamera=actual
        val look=actual.aim()
        Matrix.perspectiveM(proj,0,actual.fov,viewportWidth.toFloat()/viewportHeight,actual.near,300f)
        proj[8]=2f*actual.shiftX;proj[9]=2f*actual.shiftY
        val occupied=(settingsPanelWidthPixels/viewportWidth.coerceAtLeast(1)).coerceIn(0f,.72f)
        settingsPanelFraction+=(occupied-settingsPanelFraction)*(1f-kotlin.math.exp(-dt/.12f))
        if(kotlin.math.abs(occupied-settingsPanelFraction)<.0001f)settingsPanelFraction=occupied
        Matrix.setLookAtM(view, 0, actual.eye[0], actual.eye[1], actual.eye[2],look[0],look[1],look[2],0f,1f,0f)
        
        if(touring) {
            val targetAim=desired.aim()
            Matrix.setLookAtM(targetFramingView,0,desired.eye[0],desired.eye[1],desired.eye[2],targetAim[0],targetAim[1],targetAim[2],0f,1f,0f)
            Matrix.perspectiveM(targetFramingProj,0,desired.fov,viewportWidth.toFloat()/viewportHeight,desired.near,300f)
            targetFramingProj[8]=2f*desired.shiftX;targetFramingProj[9]=2f*desired.shiftY
            Matrix.multiplyMM(framingProjection,0,targetFramingProj,0,targetFramingView,0)
        } else Matrix.multiplyMM(framingProjection,0,proj,0,view,0)
        var minX=Float.POSITIVE_INFINITY;var maxX=Float.NEGATIVE_INFINITY
        var minY=Float.POSITIVE_INFINITY;var maxY=Float.NEGATIVE_INFINITY
        var valid=!cabinCamera && framingCorners.isNotEmpty()
        if(valid) for(corner in framingCorners) {
            Matrix.multiplyMV(framingPoint,0,framingProjection,0,corner,0)
            if(framingPoint[3]<=.01f){valid=false;break}
            val x=framingPoint[0]/framingPoint[3];val y=framingPoint[1]/framingPoint[3]
            minX=minOf(minX,x);maxX=maxOf(maxX,x);minY=minOf(minY,y);maxY=maxOf(maxY,y)
        }
        
        if(kotlin.math.abs(occupied-panelLastOccupied)>.0001f) {
            panelLastOccupied=occupied
            panelTargetScale=if(valid && occupied>.001f)
                minOf(1f,1.8f*(1f-occupied)/(maxX-minX).coerceAtLeast(.001f),
                    1.8f/(maxY-minY).coerceAtLeast(.001f)) else 1f
            panelTargetCenterX=if(valid && occupied>.001f)(minX+maxX)*.5f*panelTargetScale else 0f
            panelTargetCenterY=if(valid && occupied>.001f)(minY+maxY)*.5f*panelTargetScale else 0f
        }
        val centerResponse=1f-kotlin.math.exp(-dt/.12f)
        panelFitScale+=(panelTargetScale-panelFitScale)*centerResponse
        panelCenterX+=(panelTargetCenterX-panelCenterX)*centerResponse
        panelCenterY+=(panelTargetCenterY-panelCenterY)*centerResponse
        proj[0]*=panelFitScale;proj[5]*=panelFitScale
        proj[8]+=panelCenterX;proj[9]+=panelCenterY
        proj[8]-=settingsPanelFraction
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        updateWindowTouchRegion(vp)
        updateTrunkButton()
        actual.eye.copyInto(eyePos)
        if (sceneEnvironmentDirty) {
            sceneEnvironmentDirty = false
            loadEnvironment(panoramaEnvironment ?: sceneEnvironment ?: lastModel?.environment)
            shBakedCube = Int.MIN_VALUE
        }
        if(packedShadowRequest != viewOptions.selfShadow)rebuildLights()
        if (lightsDirty) uploadLights()
        if (shDirty) uploadEnvironmentSh()

        val active = (lamps?.active(vehicleLights, time) ?: emptySet()) +
            emptySet<String>()
        val hidden = lamps?.hidden(vehicleLights) ?: emptySet()
        frameActive = active
        frameHidden = hidden
        sceneLayer.externalBackground=panoramaEnvironment!=null
        sceneLayer.headingRadians=drivingPose.heading.toFloat()
        panoramaLayer.headingRadians=drivingPose.heading.toFloat()
        updateWeather(dt,hidden)
        
        prepareChargingCable()

        if (viewOptions.selfShadow && shReady && !hideSelfShadow && updateShadowMatrix()) {
            drawShadowMap(hidden)
        }

        applyClearColor()

        val sceneStartNanos = System.nanoTime()
        sceneLayer.uploadPending(tex2d)
        sceneLayer.debugReflectionOnly = debugReflectionOnly

        if (sceneLayer.visible && !hideScene) {
            
            System.arraycopy(vp, 0, sceneVp, 0, 16)
            Matrix.translateM(sceneVp, 0, 0f, groundY - sceneGroundY, 0f)
            
            System.arraycopy(vp, 0, shadowVp, 0, 16)
            Matrix.translateM(shadowVp, 0, 0f, 0.002f, 0f)
        }

        val reflectTex = if (viewOptions.planarReflection && sceneLayer.visible && !hideScene && !hideReflection &&
            meshes.isNotEmpty()
        ) {
            renderReflection(hidden, active)
        } else {
            0
        }

        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)

        val frameOptions = viewOptions
        bloom.enabled = frameOptions.bloom
        bloom.threshold = if(frameOptions.hdr) 1.5f else .72f
        val postProcess = (frameOptions.antiAliasing || frameOptions.hdr || frameOptions.bloom || sceneTransitionsEnabled) && ensureHdr()
        val hdr = postProcess && frameOptions.hdr
        uploadView(hdr, offscreen = postProcess)
        sceneLayer.linearOutput = postProcess || srgbFramebuffer
        if (postProcess) {
            sceneLayer.linearOutput = true
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, hdrFbo)
        }
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (sceneLayer.visible && !hideScene) {
            if(panoramaEnvironment!=null)panoramaLayer.draw(vp,eyePos,envCube,postProcess||srgbFramebuffer)
            sceneLayer.draw(
                sceneVp, reflectTex,
                floatArrayOf(viewportWidth.toFloat(), viewportHeight.toFloat())
            )
        } else {
            drawGrid(vp, eye, groundY)
        }
        sceneDrawMillis = (System.nanoTime() - sceneStartNanos) / 1e6

        checkGlAt("主场景")
        val carStartNanos = System.nanoTime()
        drawCarMeshes(hidden, active)
        checkGlAt("车身绘制")
        carDrawMillis = (System.nanoTime() - carStartNanos) / 1e6

        val lightStartNanos = System.nanoTime()
        if (VehicleLightPresentation.atmosphereGain(dayPeriod, VehicleLamp.LOW) > 0f) {
            fun worldCone(cone: com.haystudio.vehiclelab.data.VehicleCone): HeadlightBeamLayer.AirCone {
                fun transform(v: FloatArray, vector: Boolean): FloatArray {
                    val r = FloatArray(4)
                    Matrix.multiplyMV(r, 0, baseMatrix, 0, floatArrayOf(v[0],v[1],v[2],if (vector) 0f else 1f),0)
                    val n = sqrt(r[0]*r[0]+r[1]*r[1]+r[2]*r[2]).coerceAtLeast(1e-6f)
                    return if (vector) floatArrayOf(r[0]/n,r[1]/n,r[2]/n) else r.copyOf(3)
                }
                val layer=VehicleLightPresentation.headlightLayer(cone.lamp,dayPeriod)
                val scale=sqrt(baseMatrix[0]*baseMatrix[0]+baseMatrix[1]*baseMatrix[1]+baseMatrix[2]*baseMatrix[2])
                return HeadlightBeamLayer.AirCone(headlightPosition(cone), transform(cone.direction,true),
                    cone.distance*scale*layer.rangeMultiplier, cone.angle,layer.gain,layer.spreadMultiplier,cone.radius*scale,if(cone.lamp==VehicleLamp.LOW).36f else .58f,cone.lamp==VehicleLamp.LOW)
            }
            val cones = vehicleBeams?.cones.orEmpty().filter { it.lamp in vehicleLights }.map(::worldCone).toMutableList()
            
            for ((lamp,node) in listOf(VehicleLamp.CORNER_LEFT to "Light_05",VehicleLamp.CORNER_RIGHT to "Light_06")) {
                if(lamp !in vehicleLights) continue
                val mesh=meshes.firstOrNull { !it.bloom && node in it.ancestors } ?: continue
                val b=mesh.bounds;val side=if(b[2]+b[5]>=0f)1f else -1f
                val p=FloatArray(4);val d=FloatArray(4)
                Matrix.multiplyMV(p,0,mesh.matrix,0,floatArrayOf((b[0]+b[3])*.5f-.025f,(b[1]+b[4])*.5f,(b[2]+b[5])*.5f,1f),0)
                Matrix.multiplyMV(d,0,mesh.matrix,0,floatArrayOf(-.62f,-.42f,side*.90f,0f),0)
                val n=sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2]);val scale=sqrt(baseMatrix[0]*baseMatrix[0]+baseMatrix[1]*baseMatrix[1]+baseMatrix[2]*baseMatrix[2])
                cones.add(HeadlightBeamLayer.AirCone(p.copyOf(3),FloatArray(3){d[it]/n},2.8f*scale,42f,
                    if(dayPeriod==DayPeriod.NIGHT).06f else .018f,1f,.04f*scale))
            }
            headlightBeamLayer.draw(vp, eyePos, groundY, cones)
            checkGlAt("前照灯空气散射")
        }

        if (sceneLayer.visible && !hideScene && !hideShadow) {
            sceneLayer.drawShadow(shadowVp, shadowXf, baseMatrix)
        }

        groundEmitters = collectGroundEmitters()
        if (groundLightEnabled && !hideScene && sceneLayer.visible) {
            
            var localShadowPose=System.identityHashCode(lastModel)
            val localCasters=meshes.filter { !it.bloom && !it.spec.blended && it.spec.opacity>.5f &&
                !it.spec.light && !MirrorCourtesyGeometry.isLens(it.part) && isVisible(it) && !isHidden(it,hidden) &&
                chargingCoverVisibility(it)>.001f }
            for(mesh in localCasters) { localShadowPose=31*localShadowPose+mesh.matrix.contentHashCode();localShadowPose=31*localShadowPose+mesh.vao }
            val shadowSources=groundEmitters.filter {it.bodyShadow && it.power>.00001f}.take(8)
            chargingCableFrame?.let {frame->
                for(point in frame.path) localShadowPose=31*localShadowPose+point.contentHashCode()
                localShadowPose=31*localShadowPose+frame.insertion.toBits()
            }
            groundLightLayer.prepareShadows(groundEmitters,localShadowPose) { program, modelLocation, slot ->
                val ownDoor=shadowSources.getOrNull(slot)?.name?.takeIf {it.startsWith("门底:")}?.removePrefix("门底:")?.removeSuffix("_Inside")
                for(mesh in localCasters) {
                    if(ownDoor!=null && com.haystudio.vehiclelab.data.CourtesyLightMount.belongsToDoor(mesh.part,mesh.ancestors,ownDoor))continue
                    GLES30.glUniformMatrix4fv(modelLocation,1,false,mesh.matrix,0)
                    GLES30.glBindVertexArray(mesh.vao)
                    GLES30.glDrawElements(GLES30.GL_TRIANGLES,mesh.indexCount,GLES30.GL_UNSIGNED_INT,0)
                }
                if(shadowSources.getOrNull(slot)?.name=="充电口指示灯" && chargingCableFrame!=null) {
                    chargingCableLayer.drawShadow(program,-1,modelLocation,0,GwmNodeTree.IDENTITY)
                }
            }
            groundLightLayer.draw(sceneVp, groundY-sceneGroundY, eyePos, groundEmitters, postProcess || srgbFramebuffer,
                lampReflectionEnabled, sceneLayer::drawGroundReceiver)
        }
        checkGlAt("车辆地面灯光")

        if (!noEmissive && (vehicleLights.mask != 0L || courtesyGains.values.any { it>.001f } || cabinLightTransition.brightness > .0001f || readingGains.any{it>.001f} || rightChargeEmitterActive())) {
            val glowGain = when(dayPeriod) { DayPeriod.NIGHT -> .82f; DayPeriod.MORNING -> .48f; DayPeriod.NOON -> .24f }
            lampGlowLayer.draw(viewportWidth, viewportHeight, glowGain) {
                drawingLampMask = true
                try {
                    for (mesh in meshes) {
                        val cabin=cabinLightTransition.brightness>.0001f&&InteriorLighting.surface(mesh.part) || readingGains.any{it>.001f} && mesh.part in setOf("Interior_Chair","Interior_Light")
                        val lens=MirrorCourtesyGeometry.isLens(mesh.part)
                        val chargeEmitter = isRightChargeEmitter(mesh)
                        if (chargeEmitter && !rightChargeEmitterActive()) continue
                        if (mesh.bloom || (!cabin&&!lens&&!chargeEmitter && (!mesh.spec.light || !mesh.spec.official)) || mesh.spec.restingLamp || (!cabin&&!chargeEmitter&&!lampLit(mesh)) ||
                            !isVisible(mesh) || isHidden(mesh, hidden)) continue
                        drawMesh(mesh)
                    }
                } finally { drawingLampMask = false }
            }
            checkGlAt("灯具边缘溢光")
        }
        
        drawChargingCable(vp)
        checkGlAt("充电线缆与表面输送粒子")
        lightDrawMillis = (System.nanoTime() - lightStartNanos) / 1e6

        val weatherStartNanos = System.nanoTime()
        if(!hideScene) {
            weatherLayer.draw(vp,eyePos,weatherSimulation,weatherMode,WeatherFrame(drivingPose,rearAxleX),weatherCollision,dayPeriod,cabinAirflow,airflowMode)
        }
        weatherDrawMillis = (System.nanoTime() - weatherStartNanos) / 1e6
        checkGlAt("天气粒子与表面接触")
        val postStartNanos = System.nanoTime()
        if (postProcess) {
            sceneTransition.blend(viewportWidth,viewportHeight,dt)
            sceneLayer.linearOutput = false
            uploadView(hdr = false)
            bloom.present(hdrTex, viewportWidth, viewportHeight, hdr && frameOptions.toneMapping, srgbFramebuffer, frameOptions.antiAliasing,
                exposure = if (dayPeriod == DayPeriod.NIGHT) 1.16f else 1f)
        }
        postProcessMillis = (System.nanoTime() - postStartNanos) / 1e6

        checkGlAt("帧完成")
        initializingModel?.let {initializingModel=null;onModelInitialized?.invoke(it,null)}
        frameWallMillis = (System.nanoTime() - frameStartNanos) / 1e6
        frameCpuMillis = cpuStartNanos?.let { (Debug.threadCpuTimeNanos() - it) / 1e6 } ?: 0.0
        frames++
        val elapsed = (now - fpsWindowStart) / 1e9
        if (elapsed >= .5) {
            fps = (frames / elapsed).toFloat()
            frames = 0
            fpsWindowStart = now
            if (debugInfoEnabled) {
                val ordered = frameIntervals.copyOf(frameIntervalCount).apply { sort() }
                renderDebugSnapshot = RenderDebugSnapshot(
                    sampleNanos = now, fps = fps,
                    frameP95Millis = ordered.getOrNull(((ordered.size - 1) * .95).toInt()) ?: 0.0,
                    submissionMillis = frameWallMillis, renderCpuMillis = frameCpuMillis,
                    sceneMillis = sceneDrawMillis, carMillis = carDrawMillis,
                    lightMillis = lightDrawMillis, weatherMillis = weatherDrawMillis, postMillis = postProcessMillis,
                    meshes = resourceStats?.uploadedMeshes ?: meshes.size, triangles = triangleCount,
                    textures = tex2d.size + texCube.size, shaders = officialPrograms.size,
                    geometryMiB = (resourceStats?.geometryBufferBytes ?: 0L) / 1048576.0
                )
            }
        }
    }

    private fun updateWindowTouchRegion(viewProjection: FloatArray) {
        val clips = WindowControl.sideClips(selectedCameraPreset)
        if (clips.isEmpty() || viewportWidth <= 0 || viewportHeight <= 0) {
            activeWindowTouchRegions = emptyMap()
            touchPreset = null
            return
        }
        val blockers = meshes.filter { it.touchVertices.isNotEmpty() && !it.spec.blended && isVisible(it) }
        val targets = meshes.filter { it.touchVertices.isNotEmpty() && clips.any { clip -> it.part.equals(clip, true) } }
        val apertureMatrices = targets.map { mesh ->
            var node = mesh.node
            while (node in nodes.indices && !nodes[node].name.equals(mesh.part, true)) node = nodes[node].parent
            effectMatrices[nodes.getOrNull(node)?.parent ?: -1] ?: baseMatrix
        }
        val relevantMeshes = blockers + targets
        val matrices = blockers.map { it.matrix } + apertureMatrices
        val viewport = (viewportWidth.toLong() shl 32) or viewportHeight.toLong()
        if (touchPreset == selectedCameraPreset && touchViewport == viewport &&
            touchProjection.contentEquals(viewProjection) && touchMeshes == relevantMeshes &&
            touchTransforms.size == matrices.size && matrices.indices.all { touchTransforms[it].contentEquals(matrices[it]) }) return
        touchPreset = selectedCameraPreset
        touchViewport = viewport
        touchProjection = viewProjection.copyOf()
        touchMeshes = relevantMeshes
        touchTransforms = matrices.map { it.copyOf() }
        
        val frozenProjection = touchProjection
        val frozenMatrices = touchTransforms
        val width = viewportWidth
        val height = viewportHeight
        fun project(mesh: GpuMesh, modelMatrix: FloatArray): List<FloatArray> {
            val transform = FloatArray(16)
            Matrix.multiplyMM(transform, 0, frozenProjection, 0, modelMatrix, 0)
            val projected = FloatArray(4)
            val vertex = floatArrayOf(0f, 0f, 0f, 1f)
            val result = ArrayList<FloatArray>()
            val vertices = mesh.touchVertices
            val indices = mesh.touchIndices
            var i = 0
            while (i + 2 < indices.size) {
                val tri = FloatArray(9)
                var valid = true
                for (corner in 0..2) {
                    val v = indices[i + corner] * 3
                    if (v + 2 >= vertices.size) { valid = false; break }
                    vertex[0] = vertices[v]; vertex[1] = vertices[v + 1]; vertex[2] = vertices[v + 2]
                    Matrix.multiplyMV(projected, 0, transform, 0, vertex, 0)
                    val w = projected[3]
                    if (w <= .001f || projected[2] !in -w..w) { valid = false; break }
                    tri[corner * 2] = (projected[0] / w * .5f + .5f) * width
                    tri[corner * 2 + 1] = (.5f - projected[1] / w * .5f) * height
                    tri[6 + corner] = projected[2] / w
                }
                if (valid) result += tri
                i += 3
            }
            return result
        }
        val occluders by lazy {
            blockers.flatMapIndexed { index, mesh -> project(mesh, frozenMatrices[index]) }
        }
        val regions = LinkedHashMap<String, WindowTouchRegion>()
        for (clip in clips) {
            val projectedTriangles = ArrayList<FloatArray>()
            targets.forEachIndexed { index, mesh ->
                if (mesh.part.equals(clip, true))
                    projectedTriangles += project(mesh, frozenMatrices[blockers.size + index])
            }
            if (projectedTriangles.isEmpty()) continue
            
            val previewMeshes = targets.filter { it.part.equals(clip, true) }.mapNotNull { mesh ->
                var node = mesh.node
                while (node in nodes.indices && !nodes[node].name.equals(mesh.part, true)) node = nodes[node].parent
                val motion = clipByNode[node]?.firstOrNull { it.name.equals(clip, true) } ?: return@mapNotNull null
                val parent = nodes[node].parent
                val parentMatrix = (effectMatrices[parent] ?: baseMatrix).copyOf()
                val parentRest = nodeRest.getOrNull(parent)?.copyOf() ?: FloatArray(16).also { Matrix.setIdentityM(it, 0) }
                val prefix = multiply(parentMatrix, parentRest)
                val inverseRest = GwmNodeTree.invert(nodeRest[node])
                Triple(mesh, motion, Triple(prefix,inverseRest,nodeLocals[node].copyOf()))
            }
            val region = WindowTouchRegion(projectedTriangles, { occluders }, { progress ->
                previewMeshes.flatMap { (mesh, motion, matrices) ->
                    val local = if(progress<=0f) matrices.third else if(clip.startsWith("Window_") || clip=="SunRoof_Window") motion.localAtTravel(progress) else motion.localAt(progress * max(motion.duration, .15f))
                    project(mesh, multiply(multiply(matrices.first, local), matrices.second))
                }
            })
            if (region.right - region.left >= 2f && region.bottom - region.top >= 2f) regions[clip] = region
        }
        activeWindowTouchRegions = regions
    }

    /** 在宿主低频后台采样中读取进程内存，避免阻塞界面和渲染线程。 */
    @Synchronized fun sampleDebugInfo(): RenderDebugSnapshot {
        val memory = Debug.MemoryInfo()
        Debug.getMemoryInfo(memory)
        val deviceMemory = readDeviceMemoryMb()
        val now = System.nanoTime()
        val cpu = android.os.Process.getElapsedCpuTime()
        val cpuPercent = if (processCpuLastNanos > 0L && now > processCpuLastNanos)
            ((cpu - processCpuLastMillis) * 100_000_000.0 / (now - processCpuLastNanos)).coerceAtLeast(0.0) else null
        processCpuLastMillis = cpu
        processCpuLastNanos = now
        val runtime = Runtime.getRuntime()
        return renderDebugSnapshot.copy(
            sampleNanos = now, appPssMiB = memory.totalPss / 1024.0,
            javaHeapMiB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0,
            nativeHeapMiB = Debug.getNativeHeapAllocatedSize() / 1048576.0,
            graphicsPssMiB = memory.getMemoryStat("summary.graphics")?.toDoubleOrNull()?.div(1024.0),
            deviceUsedMiB = deviceMemory.first, deviceTotalMiB = deviceMemory.second,
            processCpuPercent = cpuPercent
        )
    }

    private fun readDeviceMemoryMb(): Pair<Double, Double> {
        var totalKb = 0L
        var availableKb = 0L
        try {
            java.io.File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("MemTotal:") -> totalKb = line.filter { it.isDigit() }.toLongOrNull() ?: totalKb
                    line.startsWith("MemAvailable:") -> availableKb = line.filter { it.isDigit() }.toLongOrNull() ?: availableKb
                }
            }
        } catch (_: Throwable) {
            
        }
        val total = totalKb / 1024.0
        val available = availableKb / 1024.0
        return (total - available).coerceAtLeast(0.0) to total
    }

    private val wheelExposureLayer=WheelExposureLayer()
    private var wheelExposureSeconds=1f/60f
    private var reflectionPass=false

    private fun wheelRollNode(node:Int):Int? {
        var axis=node
        while(axis in nodes.indices) {
            if(clipByNode[axis].orEmpty().any{VehicleMotionChannel.ofClip(it.name)==VehicleMotionChannel.WHEEL_ROLL})return axis
            axis=nodes[axis].parent
        }
        return null
    }

    private fun drawWheelExposure(rims:List<GpuMesh>,plan:WheelExposure.Plan):Boolean {
        val evaluator=nodePoseEvaluator ?: return false
        val originals=rims.associateWith {it.matrix.copyOf()}
        val transforms=rims.map {it.node}.distinct().associateWith {node->
            val axis=wheelRollNode(node) ?: return false
            GwmNodeTree.multiply(baseMatrix,evaluator.world(axis))
        }
        val inverses=transforms.mapValues {GwmNodeTree.invert(it.value)}
        var loX=viewportWidth.toFloat();var loY=viewportHeight.toFloat();var hiX=0f;var hiY=0f
        val v=FloatArray(4);val p=FloatArray(4);val matrix=FloatArray(16)
        for(m in rims) {
            Matrix.multiplyMM(matrix,0,vp,0,m.matrix,0)
            for(c in 0..7) {
                for(a in 0..2)v[a]=m.bounds[a+if(c and (1 shl a)==0)0 else 3]
                v[3]=1f;Matrix.multiplyMV(p,0,matrix,0,v,0)
                if(p[3]<=.001f)return false
                val x=(p[0]/p[3]*.5f+.5f)*viewportWidth
                val y=(p[1]/p[3]*.5f+.5f)*viewportHeight
                loX=minOf(loX,x);hiX=maxOf(hiX,x);loY=minOf(loY,y);hiY=maxOf(hiY,y)
            }
        }
        val left=((loX-4).toInt()/64*64).coerceIn(0,viewportWidth);val bottom=((loY-4).toInt()/64*64).coerceIn(0,viewportHeight)
        val right=((hiX+67).toInt()/64*64).coerceIn(0,viewportWidth);val top=((hiY+67).toInt()/64*64).coerceIn(0,viewportHeight)
        wheelExposureLayer.diagnostics=if(checkGl)::checkGlAt else null
        if(!wheelExposureLayer.prepare(intArrayOf(left,bottom,right-left,top-bottom),outputLinear && viewOptions.hdr))return false
        try {
            wheelExposureLayer.draw(plan.samples) {sample->
                for(m in rims) {
                    val axis=transforms.getValue(m.node)
                    val rotation=VehicleMotionTransforms.roll(GwmNodeTree.IDENTITY,sample*plan.radians/(2f*Math.PI.toFloat()))
                    val delta=GwmNodeTree.multiply(GwmNodeTree.multiply(axis,rotation),inverses.getValue(m.node))
                    multiplyInto(m.matrix,delta,originals.getValue(m))
                    drawMesh(m)
                }
            }
        } finally {for(m in rims)originals.getValue(m).copyInto(m.matrix)}
        materialBound=null;shBoundUnit=-1
        return true
    }

    private fun drawCarMeshes(hidden: Set<String>, active: Set<String>) {
        if (meshes.isEmpty()) return

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(program)
        GLES30.glUniform3fv(uEye, 1, eyePos, 0)

        val exposure=WheelExposure.plan(smoothWheelSpeedKmh,chassisGeometry.radius.toFloat(),wheelExposureSeconds)
        val rims=if(viewOptions.wheelMotionBlur && !reflectionPass && exposure.samples>1) meshes.filter {
            it.wheel && wheelRollNode(it.node)!=null && it.spec.kind !in setOf(MaterialKind.TIRE,MaterialKind.SHADOW,MaterialKind.CALIPER) && !it.bloom && !it.spec.blended && isVisible(it) && !isHidden(it,hidden)
        } else emptyList()
        for (m in meshes) {
            if(m in rims)continue
            if (m.bloom || m.spec.blended) continue
            if (m.spec.opacity <= 0.001f) continue
            if (chargingCoverVisibility(m) < .999f) continue
            if (isRightChargeEmitter(m)) continue
            if (!isVisible(m)) continue
            if (isHidden(m, hidden)) continue
            drawMesh(m)
        }
        if(rims.isNotEmpty() && !drawWheelExposure(rims,exposure))rims.forEach(::drawMesh)
        // 充电盖半开阶段先写入深度，再绘制发光体，确保泛光和发光体都受盖板开度遮挡。
        val chargingOccluders = meshes.filter {
            !it.bloom && ChargingPortGeometry.isCover(it.part) &&
                chargingCoverVisibility(it) > .001f && it.spec.opacity > .001f &&
                isVisible(it) && !isHidden(it, hidden)
        }
        if (chargingOccluders.isNotEmpty()) {
            GLES30.glColorMask(false, false, false, false)
            GLES30.glDepthMask(true)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            try { chargingOccluders.forEach(::drawMesh) }
            finally { GLES30.glColorMask(true, true, true, true) }
        }
        val chargingEmitterMesh = meshes.firstOrNull { isRightChargeEmitter(it) && !it.bloom && it.spec.opacity > .001f && isVisible(it) && !isHidden(it, hidden) && rightChargeEmitterActive() }
        if (chargingEmitterMesh != null) {
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(1f, 2f)
            try { drawMesh(chargingEmitterMesh) } finally { GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL) }
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        for (m in meshes) {
            if (!m.bloom) continue
            if (isHidden(m, hidden)) continue
            val on = if (lamps == null) lampState > 0
            else m.ancestors.any { it in active }
            if (!on) continue
            drawMesh(m)
        }

        val blended = meshes.filter {
            it.bloom.not() && (it.spec.blended || chargingCoverVisibility(it) < .999f) &&
                chargingCoverVisibility(it) > .001f && it.spec.opacity > 0.001f &&
                isVisible(it) && !isHidden(it, hidden)
        }.sortedBy { it.spec.renderOrder }
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        for (m in blended) drawMesh(m)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    private fun chargingCoverVisibility(m: GpuMesh): Float {
        if (!ChargingPortGeometry.isCover(m.part)) return 1f
        val port = if (m.part == ChargingPortGeometry.FAST_COVER) ChargingPort.FAST else ChargingPort.SLOW
        return ChargingConnectionMotion.coverVisibility(chargingCapMotion.getValue(port))
    }

    private fun updateWeather(dt:Float,hidden:Set<String>) {
        if(clearWeatherRequested){weatherSimulation.clear();clearWeatherRequested=false}
        val airflowStopped=airflowMode==AirflowMode.OFF||airflowDirections.isEmpty()||!airflowFan.isFinite()||airflowFan<=0f
        if(weatherMode==WeatherMode.NONE&&weatherSimulation.contacts.isEmpty()&&weatherSimulation.meltingSnow.isEmpty()&&weatherSimulation.groundSnowCover<=0f&&airflowStopped) {
            cabinAirflow.step(dt,AirflowMode.OFF,airflowFan,weatherCollision,airflowDirections)
            weatherStepMillis=0.0;return
        }
        val start=System.nanoTime()
        if(weatherCollisionDirty) {
            weatherCollisionDirty=false
            val parts=lastModel?.meshes.orEmpty().filter { mesh ->
                !mesh.material.light && !mesh.part.contains("Shadow",true) && !mesh.part.contains("Bloom",true) &&
                    !mesh.part.startsWith("RoofCurtain_") && !mesh.part.startsWith("Custom_Headstock_EV")
            }
            val tree=lastModel?.nodes.orEmpty()
            weatherCollision=WeatherCollision(parts.map { mesh ->
                val tags=buildString {
                    append(mesh.part)
                    var node=mesh.node
                    while(node in tree.indices) {append(' ').append(tree[node].name);node=tree[node].parent}
                }
                WeatherCollision.Surface(mesh.node,mesh.positions,mesh.indices,tags)
            })
        }
        val matrices=HashMap<Int,FloatArray>();val visible=HashSet<Int>()
        for(mesh in meshes) {
            matrices[mesh.node]=mesh.matrix
            if(!mesh.bloom&&isVisible(mesh)&&!isHidden(mesh,hidden))visible.add(mesh.node)
        }
        weatherCollision.setTransforms(matrices,visible)
        weatherSimulation.step(dt,weatherMode,weatherIntensity,WeatherFrame(drivingPose,rearAxleX),groundY,weatherCollision,
            if(drivingActive)smoothWheelSpeedKmh/3.6f else 0f,weatherTemperatureC,WeatherVec(eyePos[0],groundY,eyePos[2]))
        cabinAirflow.step(dt,airflowMode,airflowFan,weatherCollision,airflowDirections)
        weatherStepMillis=(System.nanoTime()-start)/1e6
    }

    private fun renderReflection(hidden: Set<String>, active: Set<String>): Int {
        if (!ensureReflection()) return 0
        uploadView(hdr = false, offscreen = true)
        sceneLayer.linearOutput = true

        Matrix.setIdentityM(mirror, 0)
        mirror[5] = -1f
        mirror[13] = 2f * groundY
        Matrix.multiplyMM(reflectVp, 0, vp, 0, mirror, 0)
        Matrix.multiplyMM(reflectSceneVp, 0, sceneVp, 0, mirror, 0)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, reflectFbo)
        GLES30.glViewport(0, 0, reflectW, reflectH)
        applyClearColor()
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        System.arraycopy(vp, 0, reflectSavedVp, 0, 16)
        System.arraycopy(reflectVp, 0, vp, 0, 16)
        if(panoramaEnvironment!=null)panoramaLayer.draw(reflectVp,floatArrayOf(eyePos[0],2f*groundY-eyePos[1],eyePos[2]),sceneEnvCube,true)
        else sceneLayer.drawSky(reflectSceneVp)
        checkGlAt("主场景")
        GLES30.glFrontFace(GLES30.GL_CCW)
        reflectionPass=true
        try {drawCarMeshes(hidden, active)} finally {reflectionPass=false}
        GLES30.glFrontFace(GLES30.GL_CW)
        checkGlAt("车身绘制")
        System.arraycopy(reflectSavedVp, 0, vp, 0, 16)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
        return reflectTex
    }

    private fun ensureReflection(): Boolean {
        val w = max(1, viewportWidth / 2)
        val h = max(1, viewportHeight / 2)
        if (reflectFbo != 0 && reflectW == w && reflectH == h) return true
        releaseReflection()
        reflectW = w
        reflectH = h

        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        reflectTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, reflectTex)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glGenRenderbuffers(1, ids, 0)
        reflectDepth = ids[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, reflectDepth)
        GLES30.glRenderbufferStorage(
            GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, w, h
        )

        GLES30.glGenFramebuffers(1, ids, 0)
        reflectFbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, reflectFbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, reflectTex, 0
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER, reflectDepth
        )
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) ==
            GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (!ok) {
            android.util.Log.w("CarDemo", "平面反射帧缓冲不完整，地面退回近似着色")
            releaseReflection()
            return false
        }
        android.util.Log.i("CarDemo", "地面平面反射已启用：${w}x$h")
        return true
    }

    private fun releaseReflection() {
        if (reflectFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(reflectFbo), 0)
        if (reflectDepth != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(reflectDepth), 0)
        if (reflectTex != 0) GLES30.glDeleteTextures(1, intArrayOf(reflectTex), 0)
        reflectFbo = 0
        reflectDepth = 0
        reflectTex = 0
        reflectW = 0
        reflectH = 0
    }

    private fun isHidden(m: GpuMesh, hidden: Set<String>): Boolean =
        NodeActivation.isHidden(m.ancestors, prefabHidden, frameActive, hidden)

    private fun lampLit(m: GpuMesh): Boolean {
        if (MirrorCourtesyGeometry.isLens(m.part)) return (courtesyGains[m.part] ?: 0f) > .001f
        val s = m.spec
        if (!s.light) return true

        if (ChargingPortPresentation.OUTER_EMITTER_NODE in m.ancestors) {
            return rightChargeEmitterActive()
        }
        return if (lamps == null) lampState > 0 else m.ancestors.any { it in frameActive }
    }

    private fun headlightPosition(cone: com.haystudio.vehiclelab.data.VehicleCone): FloatArray {
        val names=if(cone.lamp==VehicleLamp.HIGH) listOf("Light_01","Light_02") else listOf("Light_03","Light_04")
        val side=cone.position[2]>=0f
        val candidates=meshes.filter{ m -> !m.bloom && names.any{it in m.ancestors} }
        for(m in candidates){
            val b=m.bounds
            val center=floatArrayOf((b[0]+b[3])*.5f,(b[1]+b[4])*.5f,(b[2]+b[5])*.5f,1f)
            val result=FloatArray(4)
            if((center[2]>=0f)==side){
                center[0]-=.02f
                Matrix.multiplyMV(result,0,m.matrix,0,center,0)
                
                return result.copyOf(3)
            }
        }
        val result=FloatArray(4)
        Matrix.multiplyMV(result,0,baseMatrix,0,floatArrayOf(cone.position[0],cone.position[1],cone.position[2],1f),0)
        return result.copyOf(3)
    }

    private fun collectGroundEmitters(): List<GroundEmitter> {
        if (vehicleLights.mask == 0L && !doorsOpen && doorCornerOverrides.values.none { it } && chargingState == ChargingState.DISCONNECTED &&
            clipProgress.values.none { it > .001f } && courtesyGains.values.none { it > .001f }
        ) return emptyList()
        fun transform(matrix: FloatArray, v: FloatArray, vector: Boolean = false): FloatArray {
            val out = FloatArray(4)
            Matrix.multiplyMV(out, 0, matrix, 0, floatArrayOf(v[0],v[1],v[2],if (vector) 0f else 1f), 0)
            if (vector) {
                val len = sqrt(out[0]*out[0]+out[1]*out[1]+out[2]*out[2]).coerceAtLeast(1e-6f)
                for (i in 0..2) out[i] /= len
            }
            return out.copyOf(3)
        }
        val scale = sqrt(baseMatrix[0]*baseMatrix[0]+baseMatrix[1]*baseMatrix[1]+baseMatrix[2]*baseMatrix[2])
        val out = ArrayList<GroundEmitter>()
        for (cone in vehicleBeams?.cones.orEmpty()) {
            if (cone.lamp !in vehicleLights) continue

            val layer=VehicleLightPresentation.headlightLayer(cone.lamp,dayPeriod)
            val groundGain = if (cone.lamp == VehicleLamp.HIGH) 350f else 100f
            out.add(GroundEmitter(cone.name, headlightPosition(cone),
                transform(baseMatrix,cone.direction,true),cone.color,cone.nativeIntensity*groundGain*scale*scale,
                cone.distance*scale*layer.rangeMultiplier,cone.angle*layer.spreadMultiplier*.5f,cone.radius*scale,
                transform(baseMatrix,floatArrayOf(0f,0f,1f),true),frontBlend=1f,headlight=true))
        }
        fun strip(lamp: VehicleLamp, names: List<String>, power: Float, range: Float, angle: Float, color: FloatArray) {
            val front = FrontLampState.resolve(vehicleLights)
            for (name in names) {
                if (name !in frameActive) continue
                val mesh = meshes.firstOrNull { name in it.ancestors && !it.bloom &&
                    !isHidden(it,frameHidden) } ?: continue
                val b = mesh.bounds
                val center = FloatArray(3) { (b[it]+b[it+3])*.5f }
                val corner = lamp == VehicleLamp.CORNER_LEFT || lamp == VehicleLamp.CORNER_RIGHT
                val side = if(center[2] >= 0f) 1f else -1f
                val direction = if (corner) floatArrayOf(-.62f,-.42f,side*.90f)
                    else floatArrayOf(if (center[0] < 0f) -1f else 1f,-.32f,0f)
                val rearGain = when {
                    name in FrontLampState.allNodes -> front.emission(setOf(name))
                    lamp == VehicleLamp.DRL || corner -> 1f
                    else -> VehicleLightPresentation.rearGroundGain(lamp)
                }
                out.add(GroundEmitter(name,transform(mesh.matrix,center),transform(mesh.matrix,direction,true),
                    color,power*rearGain*scale*scale,range*scale,angle,((b[5]-b[2])*.5f).coerceAtLeast(.04f)*scale,
                    transform(mesh.matrix,floatArrayOf(0f,0f,1f),true),
                    frontBlend=if(name in FrontLampState.allNodes) 1f else 0f))
            }
        }
        val white = floatArrayOf(.82f,.91f,1f)
        val red = floatArrayOf(1f,.008f,.003f)
        
        val drl = VehicleLightPresentation.groundResponse(VehicleLamp.DRL)
        strip(VehicleLamp.DRL,listOf("Light_09","Light_10"),drl.power,drl.range,drl.angle,white)
        strip(VehicleLamp.DRL,listOf("Light_11","Light_12","Light_13"),.35f,1.25f,92f,white)
        strip(VehicleLamp.DRL,listOf("Light_18_04","Light_18_05","Light_18_06"),.55f,1.65f,92f,red)
        strip(VehicleLamp.BRAKE,listOf("Light_16","Light_17","Light_18_01","Light_18_02","Light_18_03","Light_19"),1.25f,2.5f,86f,red)
        strip(VehicleLamp.FOG,listOf("Light_21"),.48f,1.8f,84f,red)
        strip(VehicleLamp.LOGO,listOf("Light_22"),.28f,1.25f,87f,red)
        strip(VehicleLamp.REVERSE,listOf("Light_20"),.52f,1.7f,80f,white)
        strip(VehicleLamp.CORNER_LEFT,listOf("Light_05"),1.1f,2.8f,50f,white)
        strip(VehicleLamp.CORNER_RIGHT,listOf("Light_06"),1.1f,2.8f,50f,white)
        val periodGain = 1f
        
        fun courtesy(mesh: GpuMesh, name: String, door: Boolean, progress: Float) {
            val b = mesh.bounds
            val side = if(b[2]+b[5]>=0f) 1f else -1f
            val p = if(door) doorCourtesyMounts.getOrPut(mesh.part) {
                val root=mesh.part.removeSuffix("_Inside")
                com.haystudio.vehiclelab.data.CourtesyLightMount.belowDoor(b,meshes.filter {
                    !it.bloom && !it.spec.blended && com.haystudio.vehiclelab.data.CourtesyLightMount.belongsToDoor(it.part,it.ancestors,root)
                }.map {it.bounds})
            } else floatArrayOf((b[0]+b[3])*.5f,(b[1]+b[4])*.5f,(b[2]+b[5])*.5f)
            val dir = floatArrayOf(0f,-1f,side*(if(door) .5f else .32f))
            out.add(GroundEmitter(name,transform(mesh.matrix,p),transform(mesh.matrix,dir,true),
                white,(if(door) .82f else 1.7f)*periodGain*progress*scale*scale,
                (if(door) 1.15f else 2.3f)*scale,65f,.15f*scale,
                transform(mesh.matrix,floatArrayOf(1f,0f,0f),true),
                bodyShadow = true))
        }
        for(mesh in meshes) {
            if(MirrorCourtesyGeometry.isLens(mesh.part))
                courtesy(mesh,"镜下:"+mesh.part,false,courtesyGains[mesh.part] ?: 0f)
        }
        for (mesh in meshes) {
            if (!mesh.part.startsWith("Door_") || !mesh.part.endsWith("_Inside") || mesh.bloom) continue
            val amount = clipByNode.entries.filter { (node,_) -> nodes[node].name in mesh.ancestors }
                .flatMap { it.value }.filter { VehicleMotionChannel.ofClip(it.name) == VehicleMotionChannel.DOOR }
                .maxOfOrNull { clipProgress[it] ?: 0f } ?: (if(doorsOpen) 1f else 0f)
            val fade = com.haystudio.vehiclelab.data.CourtesyLightMount.brightness(amount)
            if (fade>.001f) courtesy(mesh,"门底:"+mesh.part,true,fade)
        }
        
        val active=lamps?.active(vehicleLights,time).orEmpty()
        val gain=1f
        for(mesh in meshes) {
            val bounds=mesh.bounds
            val flow=mesh.flowRange ?: continue
            if(mesh.bloom || isHidden(mesh,frameHidden) || mesh.ancestors.none{it in active}) continue
            val progress=lamps?.flowProgress(mesh.ancestors,time) ?: continue
            val lo=minOf(abs(bounds[2]),abs(bounds[5]));val hi=maxOf(abs(bounds[2]),abs(bounds[5]))
            val interval=TurnSignalSweep.interval(lo,hi,flow[0],flow[1],progress) ?: continue
            val length=interval.endInclusive-interval.start
            val center=FloatArray(3){(bounds[it]+bounds[it+3])*.5f}
            center[2]=(if(center[2]<0f)-1f else 1f)*(interval.start+interval.endInclusive)*.5f
            val direction=floatArrayOf(if(center[0]<0f)-1f else 1f,-.45f,0f)
            out.add(GroundEmitter("流水:"+mesh.name,transform(mesh.matrix,center),
                transform(mesh.matrix,direction,true),floatArrayOf(1f,.29f,.025f),
                .58f*gain*(length/(hi-lo).coerceAtLeast(.001f))*scale*scale,1.65f*scale,92f,
                length*.5f*scale,transform(mesh.matrix,floatArrayOf(0f,0f,1f),true)))
        }

        rightChargeEmitterMesh()?.let { lens ->
            val emitter = if (noEmissive) ChargingPortPresentation.Emitter(floatArrayOf(0f, 0f, 0f), 0f)
            else chargingEmitter()
            if (chargingGroundGlowGain > .001f) {

                val (_, outward) = chargingSocket(visibleChargingPort)
                // 从朝外灯面发光，避免灯体厚度使光源埋入凹槽、扩大近车身阴影。
                val source = ChargingCableGeometry.emitterSurfacePoint(lens.bounds, lens.matrix, outward)
                val frame = ChargingCableGeometry.groundLightFrame(source, outward, groundY, scale)
                val period = ChargingPortPresentation.groundAperture(chargingCoverOpeningProgress())
                if (period > 0f) out.add(GroundEmitter("充电口指示灯", frame.position,
                    frame.direction, emitter.color,
                    .12f * chargingGroundGlowGain * period * scale * scale, frame.range,
                    18f + 70f * period, .05f * scale,
                    // 盖板、轮胎和车壳统一由真实深度投影遮挡，不叠加向车外偏移的裁剪平面。
                    floatArrayOf(1f, 0f, 0f),
                    bodyShadow = true,
                    // 灯面具有面积，轮胎到地面的投影应形成柔和半影，而非硬切出暗带。
                    shadowSoftness = .10f * scale))
            }
        }
        val sceneGain = LightEnvironmentResponse.forPeriod(dayPeriod).ground
        return out.map { emitter -> emitter.copy(power = emitter.power * sceneGain) }
    }

    private fun drawChargingCable(vp: FloatArray) {
        val frame = chargingCableFrame ?: return
        // 先绘制不透明枪体，再绘制透明线缆，避免尾端封面覆盖前方的线缆接头。
        chargingCableLayer.drawPlug(vp, eyePos, frame.plugCenter, frame.outward, frame.scale,
            frame.lighting, frame.port.plugScale, frame.port.plugDepth,
            frame.insertion, frame.opacity)
        chargingCableLayer.draw(vp, eyePos, frame.path, frame.port.cableRadius * frame.scale,
            frame.lighting, frame.opacity,
            ChargingCableLayer.Flow(time,chargingFlowGain*frame.insertion*frame.opacity,frame.scale,chargingFlowDistance))
    }

    private fun prepareChargingCable() {
        if (!rightChargeCoverFullyOpen() || chargingPlugMotion <= .001f) {
            chargingCableFrame = null
            chargingCableLayer.clearShadowGeometry()
            return
        }
        val opacity = ChargingConnectionMotion.opacity(chargingPlugMotion)
        if (opacity <= .001f) {
            chargingCableFrame = null
            chargingCableLayer.clearShadowGeometry()
            return
        }
        val port = visibleChargingPort
        val (socket, outward) = chargingSocket(port)
        val scale = modelScale()
        val insertion = ChargingConnectionMotion.insertion(chargingPlugMotion)
        val approach = .135f * scale * port.plugScale * (1f - insertion)
        val plugCenter = FloatArray(3) { socket[it] + outward[it] * approach }
        val cableStart = ChargingCableGeometry.plugCableJoin(plugCenter, outward, scale,
            port.plugScale, port.plugDepth)
        if (chargingGroundAnchor == null || chargingGroundAnchorPort != port) {
            chargingGroundAnchor = ChargingCableGeometry.groundAnchor(socket, outward,
                groundY + (port.cableRadius - .018f) * scale, scale)
            chargingGroundAnchorPort = port
        }
        val anchor = chargingGroundAnchor ?: return
        val path = ChargingCableGeometry.dragPath(cableStart, outward, anchor, scale, segments = 40)
        val emitter = if (noEmissive) ChargingPortPresentation.Emitter(floatArrayOf(0f, 0f, 0f), 0f)
        else chargingEmitter()
        val lowLight = ChargingPortPresentation.receiverGain(dayPeriod)
        val lighting = ChargingCableLayer.Lighting(
            ambient = when (dayPeriod) {
                DayPeriod.NIGHT -> .34f
                DayPeriod.MORNING -> .58f
                DayPeriod.NOON -> .82f
            },
            portEnergy = emitter.intensity * lowLight * .78f,
            portPosition = socket
        )
        chargingCableLayer.prepareShadowGeometry(path, port.cableRadius * scale, plugCenter, outward,
            scale, port.plugScale, port.plugDepth, insertion)
        chargingCableFrame = ChargingCableFrame(path, plugCenter, outward, scale, insertion,
            opacity, port, lighting)
    }

    private fun rightChargeCoverFullyOpen(): Boolean = rightChargerOpen && clipProgress.entries
        .filter { it.key.name.equals("Charger_Right_Clip", ignoreCase = true) }
        .all { it.value >= .96f }

    private fun isRightChargeEmitter(mesh: GpuMesh): Boolean =
        ChargingPortPresentation.OUTER_EMITTER_NODE in mesh.ancestors

    private fun rightChargeEmitterMesh(): GpuMesh? = meshes.firstOrNull {
        !it.bloom && isRightChargeEmitter(it) && !isHidden(it, frameHidden)
    }

    private fun rightChargeEmitterActive(): Boolean = !noEmissive && chargingEmitter().intensity > .001f

    private fun chargingCoverOpeningProgress(): Float {
        val progress = clipProgress.entries.filter { it.key.name.equals("Charger_Right_Clip", ignoreCase = true) }
            .maxOfOrNull { it.value }
        return progress ?: if (rightChargerOpen) 1f else 0f
    }

    private fun advanceChargingGroundGlow(dt: Float) {
        val target = if (noEmissive) 0f else chargingEmitter().intensity.coerceAtLeast(0f)
        val response = 1f - kotlin.math.exp(-dt.coerceIn(0f, .1f) * 18f)
        chargingGroundGlowGain += (target - chargingGroundGlowGain) * response
        if (kotlin.math.abs(chargingGroundGlowGain - target) < .0005f) chargingGroundGlowGain = target
    }

    private fun chargingEmitter(): ChargingPortPresentation.Emitter {
        val state = if (chargingPlugMotion <= .001f) ChargingState.DISCONNECTED else chargingState
        val base = ChargingPortPresentation.emitter(rightChargerOpen || chargingCoverOpeningProgress() > .001f, state, time)
        val gain = ChargingPortPresentation.coverGain(chargingCoverOpeningProgress())
        return base.copy(intensity = base.intensity * gain)
    }

    private fun chargingSocket(port: ChargingPort): Pair<FloatArray, FloatArray> =
        ChargingCableGeometry.stationarySocketFrame(port.socketLocal, port.outwardLocal, baseMatrix)

    private fun modelScale() = sqrt(baseMatrix[0] * baseMatrix[0] + baseMatrix[1] * baseMatrix[1] +
        baseMatrix[2] * baseMatrix[2]).coerceAtLeast(.001f)

    fun wheelRollPhases(): Map<String, Float> = clipProgress.entries.filter {
        VehicleMotionChannel.ofClip(it.key.name) == VehicleMotionChannel.WHEEL_ROLL
    }.associate { it.key.name to it.value }

    fun groundLightingDiagnostics(): String = "灯光=${vehicleLights.tokens()} 地面=$groundLightEnabled 反射=$lampReflectionEnabled\n" +
        "方向盘=${steeringAngles()} 运动尺寸=$chassisGeometry\n车轮目标km/h=$vehicleSpeedKmh 平滑km/h=$smoothWheelSpeedKmh\n接地高度=$groundY 场景地面=$sceneGroundY\n原厂光锥=${vehicleBeams?.source.orEmpty()}\n" + groundEmitters.joinToString("\n") {
            "${it.name} 位置=${it.position.joinToString()} 方向=${it.direction.joinToString()} 射程=${it.range} 模拟强度=${it.power}"
        }

    private fun isVisible(m: GpuMesh): Boolean {
        val s = m.spec
        if (onlyMesh.isNotEmpty() &&
            !(s.path.contains(onlyMesh, true) || m.name.contains(onlyMesh, true) ||
                m.ancestors.any { it.contains(onlyMesh, true) })
        ) {
            return false
        }
        if (skipMesh.isNotEmpty() &&
            (s.path.contains(skipMesh, true) || m.name.contains(skipMesh, true) ||
                m.ancestors.any { it.contains(skipMesh, true) })
        ) {
            return false
        }
        return true
    }

    private fun advancePose(dt: Float) {

        val speed = vehicleSpeedKmh ?: run {
            val duration=clipByNode.values.asSequence().flatten().firstOrNull {
                VehicleMotionChannel.ofClip(it.name)==VehicleMotionChannel.WHEEL_ROLL
            }?.duration ?: 1f
            val cycles=if(wheelCyclesPerSecond!=0f) wheelCyclesPerSecond else if(wheelsRolling) 1f/max(duration,.15f) else 0f
            cycles*(2f*Math.PI.toFloat()*chassisGeometry.radius.toFloat())*3.6f
        }
        if(drivingSimulation && !drivingActive) {
            drivingDynamics.reset(if(chargingState!=ChargingState.DISCONNECTED || chargingPlugMotion>.001f || closeChargeCoverAfterDisconnect)0f else smoothWheelSpeedKmh)
            drivingActive=true
        }
        val chargingInterlock=chargingState!=ChargingState.DISCONNECTED || chargingPlugMotion>.001f || closeChargeCoverAfterDisconnect
        val pedalMode=vehicleSpeedKmh==null
        val physical=if(drivingActive || pedalMode) {
            if(drivingActive && !drivingSimulation)drivingDynamics.advance(DrivingControls(),dt,chassisGeometry.radius.toFloat())
            else if(pedalMode)drivingDynamics.advance(if(chargingInterlock)DrivingControls() else drivingControls,dt,chassisGeometry.radius.toFloat())
            else drivingDynamics.advance(if(chargingInterlock)0f else speed,dt,chassisGeometry.radius.toFloat())
        } else null
        val wheelStep=physical?.let { WheelKinematics.Step(it.speedKmh,it.cycles,it.distanceMetres) }
            ?: WheelKinematics.advance(smoothWheelSpeedKmh,speed.coerceIn(-180f,180f),dt,chassisGeometry.radius.toFloat())
        smoothWheelSpeedKmh=wheelStep.speedKmh
        val wasEmergency=emergencyBrakeSignal.active
        val deceleration=physical?.acceleration ?: 0f
        val brakeDemand=if(pedalMode)drivingControls.brake else if(deceleration < -6f)1f else 0f
        emergencyBrakeSignal.advance(smoothWheelSpeedKmh,deceleration,brakeDemand,drivingSimulation,dt)
        if(wasEmergency!=emergencyBrakeSignal.active)lamps?.restartTurns(time)
        lamps?.turnFrequencyPerMinute=if(emergencyBrakeSignal.active)emergencyBrakeSignal.frequencyPerMinute else turnFrequencyPerMinute
        val oldSteering=smoothSteeringAngle
        smoothSteeringAngle=SteeringKinematics.advance(oldSteering,steeringAngleDegrees ?: if(wheelsSteered) 420f else 0f,dt,smoothWheelSpeedKmh)
        val travelSteering=(oldSteering+smoothSteeringAngle)*.5f
        val previousHeading=drivingPose.heading
        if(drivingActive && !chargingInterlock) {
            roadDistanceMetres+=wheelStep.distanceMetres.toDouble()
            drivingPose=DrivingKinematics.advance(drivingPose,wheelStep.distanceMetres.toDouble(),travelSteering,chassisGeometry)
        }
        val oldPitch=drivingSuspension.pitchDegrees
        val oldRoll=drivingSuspension.rollDegrees
        val lateral=if(dt>0f)(smoothWheelSpeedKmh/3.6f*(drivingPose.heading-previousHeading)/dt).toFloat() else 0f
        drivingSuspension.advance(physical?.acceleration ?: 0f,lateral,dt)
        drivingFollow.advance(drivingPose.heading-previousHeading,dt,drivingActive && drivingSimulation)
        if(!drivingSimulation && physical?.speedKmh==0f)drivingActive=false
        var changed = oldSteering != smoothSteeringAngle || oldPitch!=drivingSuspension.pitchDegrees || oldRoll!=drivingSuspension.rollDegrees
        for ((node, clips) in clipByNode) {
            val manual = nodeProgress[nodes[node].name] ?: poseProgress
            for (clip in clips) {
                val channel = VehicleMotionChannel.ofClip(clip.name) ?: continue
                val old = clipProgress[clip] ?: 0f
                val windowOverride = if (channel == VehicleMotionChannel.WINDOW || channel == VehicleMotionChannel.SUNROOF || channel == VehicleMotionChannel.CURTAIN)
                    windowProgressOverrides[clip.name] else null
                val next = when {
                    manual != null -> VehicleMotionProgress.clamp(manual)
                    heldWindowClip?.equals(clip.name, true) == true -> old
                    windowOverride != null -> movePose(old, windowOverride, dt, .65f)
                    channel == VehicleMotionChannel.WHEEL_ROLL ->
                        VehicleMotionProgress.advanceRoll(old, (DrivingKinematics.wheelDistance(
                            wheelStep.distanceMetres.toDouble(),travelSteering,
                            nodes[node].name.contains("Front"),nodes[node].name.contains("Left"),chassisGeometry
                        )/(2.0*Math.PI*chassisGeometry.radius)).toFloat())
                    else -> {
                        val target = when (channel) {
                            VehicleMotionChannel.SUNROOF -> when {
                                sunroofOpen -> 1f
                                sunroofVentOpen -> .18f
                                else -> 0f
                            }
                            else -> if (motionOpen(channel, clip.name)) 1f else 0f
                        }
                        movePose(old, target, dt, clip.duration)
                    }
                }
                if (next != old) {
                    clipProgress[clip] = next
                    changed = true
                }
            }
        }

        if (chargingState != ChargingState.DISCONNECTED && chargingPlugMotion <= .001f &&
            chargingCapMotion.values.all { it <= .001f }) visibleChargingPort = selectedChargingPort
        val activePort = visibleChargingPort
        for (port in ChargingPort.entries) {
            val previous = chargingCapMotion.getValue(port)
            val target = chargingState != ChargingState.DISCONNECTED && rightChargeCoverFullyOpen() && port == activePort
            val next = ChargingConnectionMotion.advance(
                ChargingConnectionMotion.State(previous, if (port == activePort) chargingPlugMotion else 0f),
                target, rightChargeCoverFullyOpen(), dt
            )
            chargingCapMotion[port] = next.cap
            if (port == activePort) chargingPlugMotion = next.plug
            if (next.cap != previous) changed = true
        }
        if (chargingState == ChargingState.DISCONNECTED && closeChargeCoverAfterDisconnect &&
            ChargingConnectionMotion.canCloseOuterCover(
                ChargingConnectionMotion.State(chargingCapMotion.getValue(visibleChargingPort), chargingPlugMotion)
            )
        ) {
            rightChargerOpen = false
            closeChargeCoverAfterDisconnect = false
            chargingGroundAnchor = null
            chargingGroundAnchorPort = null
            changed = true
        }
        if (changed) applyPose()
    }

    private fun updateChargingLampLighting() {
        val base = basePackedLights ?: return
        val emitter = if (noEmissive) ChargingPortPresentation.Emitter(floatArrayOf(0f, 0f, 0f), 0f)
        else chargingEmitter()
        val nightGain = ChargingPortPresentation.receiverGain(dayPeriod)
        val localEmitterGain = LightEnvironmentResponse.forPeriod(dayPeriod).emitter
        if (emitter.intensity <= .001f || nightGain <= 0f || base.count >= LightRig.MAX_LIGHTS) {
            if (packedLights !== base) { packedLights = base; lightsDirty = true }
            return
        }
        val block = base.block.copyOf()
        val noDir = base.blockNoDir.copyOf()
        val index = base.count
        val offset = index * LightRig.VEC_PER_LIGHT * 4
        val p = FloatArray(4)
        Matrix.multiplyMV(p, 0, baseMatrix, 0, floatArrayOf(1.70686f, .977f, -.915f, 1f), 0)
        val scale = modelScale()
        fun write(target: FloatArray) {
            
            target[offset] = emitter.color[0] * .60f * emitter.intensity * nightGain * localEmitterGain
            target[offset + 1] = emitter.color[1] * .60f * emitter.intensity * nightGain * localEmitterGain
            target[offset + 2] = emitter.color[2] * .60f * emitter.intensity * nightGain * localEmitterGain
            target[offset + 4] = target[offset]; target[offset + 5] = target[offset + 1]; target[offset + 6] = target[offset + 2]
            target[offset + 8] = p[0]; target[offset + 9] = p[1]; target[offset + 10] = p[2]
            target[offset + 16] = 1f; target[offset + 17] = 1f
            target[offset + 18] = 1f; target[offset + 19] = .52f * scale
            target[offset + 21] = 1f; target[offset + 22] = 1.8f; target[offset + 23] = 2.4f
            target[offset + 24] = -1f
            target[offset + 25] = 1f; target[offset + 26] = .32f * scale; target[offset + 27] = -1f
        }
        write(block); write(noDir)
        packedLights = PackedLights(index + 1, block, noDir, base.physical.copyOf())
        lightsDirty = true
    }

    private fun movePose(current: Float, target: Float, dt: Float, duration: Float): Float =
        VehicleMotionProgress.advance(current, target, dt, duration)

    private fun motionOpen(channel: VehicleMotionChannel, clipName: String): Boolean = when (channel) {
        VehicleMotionChannel.DOOR -> VehicleCorner.fromClip(clipName)?.let(::doorOpen) ?: doorsOpen
        VehicleMotionChannel.HANDLE -> handlesExtended
        VehicleMotionChannel.WINDOW -> windowsOpen
        VehicleMotionChannel.SUNROOF -> sunroofOpen
        VehicleMotionChannel.CURTAIN -> curtainOpen
        VehicleMotionChannel.TRUNK -> trunkOpen
        VehicleMotionChannel.HOOD -> hoodOpen
        VehicleMotionChannel.MIRROR -> !mirrorsExpanded
        VehicleMotionChannel.CHARGER -> when {
            clipName.contains("Left", ignoreCase = true) -> leftChargerOpen
            clipName.contains("Right", ignoreCase = true) -> rightChargerOpen
            else -> false
        }
        VehicleMotionChannel.WHEEL_STEER -> wheelsSteered
        VehicleMotionChannel.WHEEL_ROLL -> wheelsRolling
    }

    private fun applyPose() {
        if (nodes.isEmpty()) {
            effectMatrices.clear()
            for (m in meshes) System.arraycopy(baseMatrix, 0, m.matrix, 0, 16)
            return
        }
        val evaluator = nodePoseEvaluator ?: return
        val locals = evaluator.locals
        for (i in locals.indices) nodeLocals[i].copyInto(locals[i])
        for ((i, clips) in clipByNode) {
            var local = locals[i]

            for (clip in clips) {
                val v = clipProgress[clip] ?: 0f
                val channel = VehicleMotionChannel.ofClip(clip.name)
                if(channel == VehicleMotionChannel.HANDLE) {
                    local=com.haystudio.vehiclelab.data.HandleMotion.localAt(clip,v)
                } else if (channel != null && channel != VehicleMotionChannel.WHEEL_ROLL && channel != VehicleMotionChannel.WHEEL_STEER && channel != VehicleMotionChannel.CURTAIN && v > 0f) {
                    local = if(clip.name.startsWith("Window_") || clip.name=="SunRoof_Window") clip.localAtTravel(v) else clip.localAt(v * max(clip.duration, 0.15f))
                }
            }
            if (clips.any { VehicleMotionChannel.ofClip(it.name)==VehicleMotionChannel.WHEEL_STEER }) {
                val angles=steeringAngles()
                val degrees=if(nodes[i].name.contains("Left"))angles.left else angles.right
                val rotation=FloatArray(16);Matrix.setIdentityM(rotation,0)
                Matrix.rotateM(rotation,0,-degrees,0f,0f,1f)
                local=GwmNodeTree.multiply(local,rotation)
            }
            
            for (clip in clips) {
                if (VehicleMotionChannel.ofClip(clip.name) == VehicleMotionChannel.WHEEL_ROLL) {
                    local = VehicleMotionTransforms.roll(local, clipProgress[clip] ?: 0f)
                }
            }
            if (locals[i] !== local) local.copyInto(locals[i])
        }
        val poseCorrections = evaluator.evaluate()
        val curtainProgress = RoofCurtain.sharedProgress(clipProgress)
        val force = effectMatrices.isEmpty()
        Matrix.setIdentityM(bodyMotionMatrix, 0)
        val pivot = rearAxleX - chassisGeometry.wheelbase.toFloat() * .5f
        // 以接地后的轮毂高度作为姿态支点，车轮保持接地，不参与车壳俯仰。
        val bodyPivotY = groundY + chassisGeometry.radius.toFloat().coerceAtLeast(.28f)
        Matrix.translateM(bodyMotionMatrix, 0, pivot, bodyPivotY, 0f)
        Matrix.rotateM(bodyMotionMatrix, 0, drivingSuspension.pitchDegrees, 0f, 0f, 1f)
        Matrix.rotateM(bodyMotionMatrix, 0, drivingSuspension.rollDegrees, 1f, 0f, 0f)
        Matrix.translateM(bodyMotionMatrix, 0, -pivot, -bodyPivotY, 0f)
        multiplyInto(poseMatrixScratch,bodyMotionMatrix,baseMatrix)
        Matrix.invertM(courtesyFrameInverse,0,poseMatrixScratch,0)
        val bodyChanged = !bodyMotionMatrix.contentEquals(previousBodyPose)
        val curtainChanged = curtainProgress != appliedCurtainProgress
        val steeringChanged = smoothSteeringAngle != appliedSteeringAngle
        for (i in poseCorrections.indices) {
            if (!force && !evaluator.changed[i] && !bodyChanged) continue
            val corr = poseCorrections[i]
            corrections[i] = corr
            val matrix = effectMatrices.getOrPut(i) { FloatArray(16) }
            multiplyInto(matrix, baseMatrix, corr)
            if (!nodes[i].name.contains("Wheel", true)) {
                matrix.copyInto(poseMatrixScratch)
                multiplyInto(matrix, bodyMotionMatrix, poseMatrixScratch)
            }
        }
        for (m in meshes) {
            val curtain = curtainBounds[m.node]
            val steeringWheel = m.part == SteeringWheelGeometry.PART
            if (!force && evaluator.changed.getOrNull(m.node) != true &&
                !(bodyChanged && !m.wheel) && !(curtainChanged && curtain != null) &&
                !(steeringChanged && steeringWheel)) continue
            val corr = corrections[m.node]
            if (corr == null) baseMatrix.copyInto(m.matrix)
            else multiplyInto(m.matrix, baseMatrix, corr)
            if (curtain != null) {
                multiplyInto(m.matrix, baseMatrix, RoofCurtain.retraction(curtain, curtainSlot, curtainTravel, curtainProgress))
            }
            if (steeringWheel) {
                val rotation = SteeringWheelGeometry.rotation(smoothSteeringAngle, steeringGeometryOffset)
                m.matrix.copyInto(poseMatrixScratch)
                multiplyInto(m.matrix, poseMatrixScratch, rotation)
            }
            if (!m.wheel) {
                m.matrix.copyInto(poseMatrixScratch)
                multiplyInto(m.matrix, bodyMotionMatrix, poseMatrixScratch)
            }
        }
        bodyMotionMatrix.copyInto(previousBodyPose)
        appliedCurtainProgress = curtainProgress
        appliedSteeringAngle = smoothSteeringAngle
    }

    private fun groupOf(target: String): Int {
        if (target.contains("_Dash", ignoreCase = true)) return GROUP_NONE
        return when {
            target.contains("SunRoof", ignoreCase = true) -> GROUP_SUNROOF
            target.contains("Window", ignoreCase = true) -> GROUP_WINDOW
            target.contains("Trunk", ignoreCase = true) -> GROUP_TRUNK
            target.contains("Handle", ignoreCase = true) -> GROUP_DOOR
            target.contains("Door", ignoreCase = true) &&
                !target.contains("Hood", ignoreCase = true) -> GROUP_DOOR
            else -> GROUP_NONE
        }
    }

    private fun drawMesh(m: GpuMesh) {
        val s = m.spec
        GlUtil.faceCulling(s.faceCulling)
        val bloom = m.bloom
        val mirrorSurface = m.ancestors.any { it.contains("GlassRearview", ignoreCase = true) } ||
            m.part.contains("GlassRearview", ignoreCase = true)
        
        val lit = lampLit(m) || s.restingLamp
        val off = s.light && !lit
        val offLook = off && s.offBase != null

        val tint = palette?.let { p ->
            p.colors.getOrNull(colorIndex)?.let { c -> p.tintOf(c, s.path, p.colors.getOrNull(trimColorIndex)) }
        }

        if (s.official && !bloom && !mirrorSurface) {
            var variant = when {
                licensePlateEnabled && m.plateAnchor != null && platePng != null ->
                    surfaceVariants.getOrPut(s.path + "#牌照") { SurfaceMaterialVariants.plate(s) }
                m.part.startsWith("Custom_Headstock_") && (m.part.endsWith("_02") || m.part.endsWith("_03")) ->
                    surfaceVariants.getOrPut(s.path + "#格栅") { SurfaceMaterialVariants.grille(s) }
                m.flowRange != null ->
                    surfaceVariants.getOrPut(s.path + "#流水") { SurfaceMaterialVariants.flowing(s) }
                else -> s
            }
            if(s.engineShader && (m.part in setOf("Interior_Chair","Interior_Light","Trunk_Inside","Central_Control") || InteriorLighting.surface(m.part))) {
                val key=s.path+"#阅读与行李厢:"+m.part
                variant=surfaceVariants.getOrPut(key){SurfaceMaterialVariants.courtesy(s,m.part)}
            }
            if ((InteriorLighting.surface(m.part) || InteriorLighting.footwellSurface(m.part)) && s.engineShader) {
                val interiorDoor=m.part.startsWith("Door_")
                val interiorDash=m.part=="Central_Control"
                val interiorFeet=InteriorLighting.footwellSurface(m.part)
                val key=s.path+"#座舱照明:$interiorDoor:$interiorDash:$interiorFeet"
                variant=surfaceVariants.getOrPut(key) {
                    SurfaceMaterialVariants.interior(variant,interiorDoor,interiorDash,interiorFeet)
                }
            }
            if (s.light && !s.restingLamp) variant = surfaceVariants.getOrPut(s.path + variant.fsPath + "#溢光") { SurfaceMaterialVariants.lamp(variant) }
            if(tint!=null)variant=surfaceVariants.getOrPut(variant.fsPath+"#自定义漆") {PaintShader.apply(variant)}
            val prog = officialProgram(variant)
            if (prog != null) {
                drawOfficial(m, prog, tint)
                return
            }
        }

        GLES30.glUseProgram(program)
        GLES30.glUniform1i(uOutputLinear,if(outputLinear)1 else 0)
        GLES30.glUniform1i(uApplyToneMapping,if(materialToneMapping)1 else 0)
        bindPaint(::fallbackPaintUniform,paintFor(s.path))
        GLES30.glUniform3fv(uEye, 1, eyePos, 0)

        val base = when {
            mirrorSurface -> floatArrayOf(.028f, .048f, .072f)
            bloom -> WHITE
            m.ancestors.any { it == "Light_23_02" || it == "Light_24_02" } -> floatArrayOf(.86f,.92f,1f)
            tint != null -> tint.base
            offLook -> s.offBase!!
            else -> s.base
        }
        val metallic = if (mirrorSurface) .80f else if (offLook) s.offMetallic else s.metallic
        val roughness = if (mirrorSurface) .14f else if (offLook) s.offRoughness else s.roughness
        
        val coat = when {
            tint != null -> if (s.carCl) tint.coat[0] else 0f
            s.paint -> if (s.carCl) 1.2f else 0f
            else -> s.coat
        }
        val coatRough = when {
            tint != null -> tint.coat.getOrElse(1) { s.coatRough }
            else -> s.coatRough
        }
        val envSpec = when {
            mirrorSurface -> .86f
            tint != null -> tint.envSpecInt
            else -> s.envSpecInt
        }
        
        val lens=MirrorCourtesyGeometry.isLens(m.part)
        val emissive = if (if(lens)lit else lit || bloom || !s.light) s.emissive else floatArrayOf(0f, 0f, 0f)
        val frontGain = if (s.light && m.ancestors.any { it in FrontLampState.allNodes })
            FrontLampState.resolve(vehicleLights).emission(m.ancestors) else 1f
        val environmentEmitterGain = LightEnvironmentResponse.forPeriod(dayPeriod).emitter
        val emissiveInt = if (if(lens)lit else lit || bloom || !s.light)
            s.emissiveInt * frontGain * (if(lens) courtesyGains[m.part] ?: 0f else 1f) * if (s.light && !bloom) environmentEmitterGain else 1f else 0f
        
        val hasAlbedo = if (mirrorSurface || bloom || off || m.albedoTex == 0) 0f else 1f

        val hasEmissive = if (bloom) {
            if (m.albedoTex != 0) 1f else 0f
        } else if (m.emissiveTex == 0 || emissiveInt <= 0.001f) {
            0f
        } else {
            1f
        }

        Matrix.multiplyMM(mvp, 0, vp, 0, m.matrix, 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, m.matrix, 0)
        normalMat[0] = m.matrix[0]; normalMat[1] = m.matrix[1]; normalMat[2] = m.matrix[2]
        normalMat[3] = m.matrix[4]; normalMat[4] = m.matrix[5]; normalMat[5] = m.matrix[6]
        normalMat[6] = m.matrix[8]; normalMat[7] = m.matrix[9]; normalMat[8] = m.matrix[10]
        GLES30.glUniformMatrix3fv(uNormal, 1, false, normalMat, 0)

        GLES30.glUniform3fv(uBase, 1, base, 0)
        GLES30.glUniform1f(uMetallic, metallic)
        GLES30.glUniform1f(uRough, roughness)
        GLES30.glUniform3fv(uEmissive, 1, emissive, 0)
        GLES30.glUniform1f(uEmissiveInt, emissiveInt)
        GLES30.glUniform1f(uAlpha, m.alpha * chargingCoverVisibility(m))
        GLES30.glUniform1f(uCoat, coat)
        GLES30.glUniform1f(uCoatRough, coatRough)
        GLES30.glUniform1f(uCarCl, if (s.carCl && !bloom && !off) 1f else 0f)
        GLES30.glUniform1f(uDirSpec, s.dirSpecInt)
        GLES30.glUniform1f(uEnvSpec, envSpec)
        GLES30.glUniform1f(uMirrorSurface, if (mirrorSurface) 1f else 0f)
        GLES30.glUniform1f(uEnvDiff, s.envDiffInt)
        GLES30.glUniform1f(uHasAlbedo, hasAlbedo)
        GLES30.glUniform1f(uHasEmissive, hasEmissive)
        GLES30.glUniform1f(uHasOrm, if (m.ormTex != 0) 1f else 0f)
        GLES30.glUniform1f(uHasAlphaMask, if (m.alphaMaskTex != 0) 1f else 0f)
        GLES30.glUniform2f(uOrmST, s.ormScale[0], s.ormScale[1])
        GLES30.glUniform1f(uAlphaCut, if (bloom) 0f else s.alphaCutoff)
        GLES30.glUniform1f(uUnlit, if (s.unlit) 1f else 0f)
        
        GLES30.glUniform1f(uDirLight, if (s.unlit || !s.dirLight) 0f else 1f)
        GLES30.glUniform4f(
            uUvST,
            s.uvScale[0], s.uvScale[1], s.uvOffset[0], s.uvOffset[1]
        )
        GLES30.glUniform3fv(uEnvTop, 1, s.envTop ?: sceneTop, 0)
        GLES30.glUniform3fv(uEnvSide, 1, s.envSide ?: sceneSide, 0)
        GLES30.glUniform3fv(uEnvDown, 1, s.envDown ?: sceneDown, 0)

        val scene = if(panoramaEnvironment!=null)envCube else sceneEnvCube
        val own = m.envCube
        val useScene = scene != 0 && (panoramaEnvironment!=null || tint != null || s.envTex == null || own == 0)
        GLES30.glUniform1f(uEnvYaw, (if(panoramaEnvironment!=null)0f else if (useScene) sceneYaw else s.envYaw)+Math.toDegrees(drivingPose.heading).toFloat())
        val cube = if (useScene) scene else own
        GLES30.glUniform1f(uHasEnvCube, if (cube != 0) 1f else 0f)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, m.albedoTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(
            GLES30.GL_TEXTURE_2D,
            if (bloom) m.albedoTex else m.emissiveTex
        )
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, cube)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, m.ormTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, m.alphaMaskTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        GLES30.glBindVertexArray(m.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    private var paintBounds=floatArrayOf(-2.6f,0f,-1f,2.6f,1.8f,1f)
    private fun fallbackPaintUniform(name:String,value:Any) {
        val location=GLES30.glGetUniformLocation(program,name)
        if(location<0)return
        when(value) {
            is Float -> GLES30.glUniform1f(location,value)
            is FloatArray -> when(value.size) {
                3 -> GLES30.glUniform3fv(location,1,value,0)
                4 -> GLES30.glUniform4fv(location,1,value,0)
            }
        }
    }
    private fun bindPaint(bind:(String,Any)->Unit,style:PaintStyle?) {
        bind("uLabPaintEnabled",if(style!=null)1f else 0f)
        if(style==null)return
        val p=style.normalized()
        bind("uLabPaintA",p.primary.linear())
        bind("uLabPaintB",if(p.gradient)p.secondary.linear() else p.primary.linear())
        bind("uLabPaintEye",eyePos)
        val size=(paintBounds[p.axis+3]-paintBounds[p.axis]).coerceAtLeast(.1f)
        val axis=FloatArray(4)
        axis[p.axis]=1f/size
        axis[3]=paintBounds[p.axis]/size+p.gradientCenter
        bind("uLabPaintGradient",axis)
        bind("uLabPaintSurface",floatArrayOf(p.roughness,p.metallic,p.pearl,p.gradientWidth))
        bind("_Roughness",p.roughness)
        bind("_Metallic",p.metallic)
        bind("_CarCLParams",floatArrayOf(p.finish.coat,p.roughness))
    }

    private fun officialProgram(
        spec: MaterialSpec,
        shadow: Boolean = viewOptions.selfShadow && shReady && !hideSelfShadow && shHasLight
    ): OfficialProgram? {
        val key = OfficialProgram.cacheKey(spec, shadow)
        officialPrograms[key]?.let { return it }
        if (officialFailed.contains(key)) return null
        val created = OfficialProgram.create(spec, shadow)
        if (created == null) {
            officialFailed.add(key)
        } else {
            officialPrograms[key] = created
        }
        return created
    }

    private fun drawOfficial(m: GpuMesh, prog: OfficialProgram, tint: CarTint?, groundAxes:FloatArray?=null) {
        val s = m.spec
        val rearState = RearLampState.resolve(vehicleLights)
        val rawEmissionGain = if (MirrorCourtesyGeometry.isLens(m.part)) courtesyGains[m.part] ?: 0f
        else if (m.ancestors.any { it in FrontLampState.allNodes }) {
            FrontLampState.resolve(vehicleLights).emission(m.ancestors)
        } else rearState.emission(m.ancestors).takeIf { it > 0f } ?: 1f
        val emissionGain = rawEmissionGain * LightEnvironmentResponse.forPeriod(dayPeriod).emitter
        if (!officialLogged) {
            officialLogged = true
            android.util.Log.i("CarDemo", "官方着色器管线已启用：${s.path}")
        }
        prog.use()
        
        GLES30.glUniform1f(prog.uniform("u_visibility"), chargingCoverVisibility(m))
        bindLightsFor(s)
        GLES30.glUniformMatrix4fv(prog.uniform("u_viewProj"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(prog.uniform("u_view"), 1, false, view, 0)
        GLES30.glUniformMatrix4fv(prog.uniform("u_proj"), 1, false, proj, 0)
        GLES30.glUniformMatrix4fv(prog.uniform("u_model"), 1, false, m.matrix, 0)
        GLES30.glUniform4f(
            prog.uniform("u_eyePosLightCount"),
            eyePos[0], eyePos[1], eyePos[2],
            (if (s.directLighting) packedLights?.count ?: 0 else 0).toFloat()
        )
        GLES30.glUniform1f(prog.uniform("u_timeInSecond"), time)

        if (s.engineShader) {
            
            val block = s.materialBlock?.copyOf()
            if (block != null) {

                val cube = if (envCube != 0) envCube else sceneEnvCube
                val mip = ((cubeMips[cube] ?: 9) - 1).toFloat()
                if (s.brdfType == "blinnPhong") block[20] = mip else block[14] = mip
                if (s.light) {
                    val offset = if (s.brdfType == "blinnPhong") 16 else 4
                    if (noEmissive) block[offset+3] = 0f
                    else if (s.restingLamp) {  }
                    else if (!lampLit(m)) block[offset+3] = 0f
                    else {
                        if (m.ancestors.any { it in RearLampState.allNodes }) {
                            block[offset]=1f;block[offset+1]=.002f;block[offset+2]=.0008f
                        }
                        block[offset+3] = maxOf(block[offset+3],1.5f) * emissionGain
                    }
                }
                if (isRightChargeEmitter(m)) {
                    
                    val emitter = if (noEmissive) ChargingPortPresentation.Emitter(floatArrayOf(0f, 0f, 0f), 0f)
                    else chargingEmitter()
                    val offset = if (s.brdfType == "blinnPhong") 16 else 4

                    ChargingPortPresentation.stabilizeLensMaterial(block, s.brdfType == "blinnPhong")
                    block[offset] = emitter.color[0]
                    block[offset + 1] = emitter.color[1]
                    block[offset + 2] = emitter.color[2]
                    block[offset + 3] = emitter.intensity * LightEnvironmentResponse.forPeriod(dayPeriod).emitter
                }
                uploadBlock(materialUbo, block, OfficialProgram.BLOCK_MATERIAL)
                materialBound = block
            }
            GLES30.glUniformMatrix4fv(prog.uniform("u_view"), 1, false, view, 0)
            GLES30.glUniformMatrix4fv(prog.uniform("u_proj"), 1, false, proj, 0)
            GLES30.glUniform2f(prog.uniform("u_environmentLightingIntensity"), 1f, 1f)
            GLES30.glUniform3f(prog.uniform("u_environmentLightingColorMultiplier"), 1f, 1f, 1f)
            
            val st = floatArrayOf(
                1f, 0f, 0f,
                0f, 1f, 0f,
                s.uvOffset[0], s.uvOffset[1], 1f
            )
            GLES30.glUniformMatrix3fv(prog.uniform("u_UVTransformMatrixBASE"), 1, false, st, 0)
            if (s.emissiveTex != null) {
                GLES30.glUniformMatrix3fv(prog.uniform("u_UVTransformMatrixEMISSIVE"), 1, false, st, 0)
            }
            if (s.normalTex != null) {
                
                val normalSt = floatArrayOf(
                    s.normalUvScale[0] / s.uvScale[0], 0f, 0f,
                    0f, s.normalUvScale[1] / s.uvScale[1], 0f,
                    s.normalUvOffset[0], s.normalUvOffset[1], 1f
                )
                GLES30.glUniformMatrix3fv(prog.uniform("u_UVTransformMatrixBUMP"), 1, false, normalSt, 0)
            }
            if (s.alphaMaskTex != null) {
                val alphaSt = st.copyOf()
                alphaSt[0] = s.alphaUvScale[0] / s.uvScale[0]
                alphaSt[4] = s.alphaUvScale[1] / s.uvScale[1]
                GLES30.glUniformMatrix3fv(prog.uniform("u_UVTransformMatrixALPHA"), 1, false, alphaSt, 0)
            }
        }

        for ((name, value) in s.uniforms) prog.bindRaw(name, value)
        if (tint != null) {
            prog.bindRaw("_BaseCol", tint.base)
            prog.bindRaw("_CarCLParams", tint.coat)
            prog.bindRaw("_EnvSpecInt", tint.envSpecInt)
        }
        bindPaint(prog::bindRaw,paintFor(s.path))
        
        val plateSurface = licensePlateEnabled && m.plateAnchor != null
        val useScene = (panoramaEnvironment!=null || tint != null || plateSurface) && sceneEnvCube != 0
        if (useScene) {
            val hour = when (dayPeriod) { DayPeriod.MORNING -> 8; DayPeriod.NOON -> 12; DayPeriod.NIGHT -> 21 }
            palette?.envFor(hour)?.uniforms?.forEach { (name, value) -> prog.bindRaw(name, value) }
        }
        if (s.light) {
            val intensity = when {
                noEmissive -> 0f
                s.restingLamp -> s.emissiveInt
                !lampLit(m) -> 0f
                else -> maxOf(s.emissiveInt, 1.5f)*emissionGain
            }
            prog.bindRaw("_EmissiveInt", intensity)
        } else if (noEmissive) prog.bindRaw("_EmissiveInt", 0f)
        if (isRightChargeEmitter(m)) {
            val emitter = if (noEmissive) ChargingPortPresentation.Emitter(floatArrayOf(0f, 0f, 0f), 0f)
            else chargingEmitter()
            prog.bindRaw("_EmissiveCol", floatArrayOf(emitter.color[0], emitter.color[1], emitter.color[2], 1f))
            prog.bindRaw("_EmissiveInt", emitter.intensity)
        }
        if(groundAxes!=null) {
            val pose=drivingPose
            val shiftU=(groundAxes[0]*pose.x+groundAxes[1]*pose.z)*s.uvScale[0]
            val shiftV=(groundAxes[2]*pose.x+groundAxes[3]*pose.z)*s.uvScale[1]
            
            prog.bindRaw("uLabGroundPose",floatArrayOf(kotlin.math.cos(pose.heading).toFloat(),kotlin.math.sin(pose.heading).toFloat(),
                (shiftU-kotlin.math.floor(shiftU)).toFloat(),(shiftV-kotlin.math.floor(shiftV)).toFloat()))
            prog.bindRaw("uLabGroundAxes",groundAxes)
            prog.bindRaw("uLabGroundPivot",rearAxleX)
            prog.bindRaw("uLabGroundStyle",sceneLayer.groundMaterialStyle.toFloat())
            prog.bindRaw("uLabGroundHaze",when(dayPeriod) {
                DayPeriod.NIGHT -> floatArrayOf(.005f,.006f,.011f)
                DayPeriod.MORNING -> floatArrayOf(.52f,.46f,.40f)
                DayPeriod.NOON -> floatArrayOf(.48f,.56f,.64f)
            })
            prog.bindRaw("uLabReflectionOn",if(sceneLayer.groundReflectionTexture != 0)1f else 0f)
            prog.bindRaw("uLabReflectionScreen",floatArrayOf(viewportWidth.toFloat(),viewportHeight.toFloat()))
            prog.bindRaw("uLabEnvironment",if(panoramaEnvironment!=null)1f else 0f)
            prog.bindRaw("uLabWetness",if(weatherMode==WeatherMode.RAIN)weatherIntensity.coerceIn(0f,1f) else 0f)
        }
        prog.bindRaw("uLabCabinColor", cabinLightTransition.color)
        prog.bindRaw("uLabInterior", 2f * cabinLightTransition.brightness * when(dayPeriod) {
            DayPeriod.NIGHT -> 1f; DayPeriod.MORNING -> .65f; DayPeriod.NOON -> .4f
        })
        if(InteriorLighting.surface(m.part)) {
            val cabinEye=WeatherVec(eyePos[0],eyePos[1],eyePos[2]).transform(GwmNodeTree.invert(m.matrix))
            prog.bindRaw("uLabCabinEye",floatArrayOf(cabinEye.x,cabinEye.y,cabinEye.z))
        }
        prog.bindRaw("uLabInteriorDoor", if (m.part.startsWith("Door_")) 1f else 0f)
        prog.bindRaw("uLabInteriorDash", if (m.part=="Central_Control") 1f else 0f)
        prog.bindRaw("uLabInteriorFeet", if (InteriorLighting.footwellSurface(m.part)) 1f else 0f)
        prog.bindRaw("uLabCabinGlowMask",if(drawingLampMask)1f else 0f)
        prog.bindRaw("uLabGlowMask", if (drawingLampMask) emissionGain.coerceAtMost(6f) else 0f)
        if (plateSurface) {
            val plateLight = PlateLighting.surface()
            
            prog.bindRaw("uLabPlateLight", if (rearState.plate && m.plateAnchor!!.normal[0] > 0f) 1.25f else 0f)
            prog.bindRaw("_Metallic", plateLight.metallic)
            prog.bindRaw("_Roughness", plateLight.roughness)
            prog.bindRaw("_EnvDiffInt", .92f)

            prog.bindRaw("_EnvTopCol", floatArrayOf(.60f,.60f,.60f))
            prog.bindRaw("_EnvSideCol", floatArrayOf(.42f,.42f,.42f))
            prog.bindRaw("_EnvDownCol", floatArrayOf(.06f,.06f,.06f))
            prog.bindRaw("_EnvSpecInt", plateLight.envSpecular)
            prog.bindRaw("_DirSpecInt", .9f)
            prog.bindRaw("_CarCLParams", plateLight.clearCoat)
        }
        if (diagnosticNoSpecular) { prog.bindRaw("_EnvSpecInt",0f); prog.bindRaw("_DirSpecInt",0f) }
        m.flowRange?.let { range ->
            val progress = lamps?.flowProgress(m.ancestors, time) ?: 1f
            GLES30.glUniform3f(prog.uniform("uLabFlow"), progress, range[0], range[1])
        }

        if (texDiag.add(s.path)) {
            android.util.Log.i(
                "CarDemo",
                "材质贴图 ${s.path.substringAfterLast('/')}：" + s.textureUniforms.entries.joinToString {
                    val ok = texEx(it.value) != 0
                    "${it.key}=${it.value.substringAfterLast('/')}${if (ok) "" else "(缺)"}"
                }
            )
        }
        checkGlAt("材质参数:${s.path.substringAfterLast('/')}")
        GLES30.glUniformMatrix4fv(prog.uniform("uLabCourtesyFrameInverse"),1,false,courtesyFrameInverse,0)
        GLES30.glUniform4fv(prog.uniform("uLabReading"),1,readingGains,0)
        GLES30.glUniform1f(prog.uniform("uLabLuggage"),luggageGain)
        GLES30.glUniform1f(prog.uniform("uLabCourtesyEnvironment"),com.haystudio.vehiclelab.data.CourtesyCabinLights.sceneGain(dayPeriod))
        GLES30.glUniform1f(prog.uniform("uLabCourtesyGlowMask"),if(drawingLampMask)1f else 0f)
        val nextUnit = prog.bindTextures(s, 0) { name, path ->
            if(groundAxes != null && name == "uLabGroundReflection") {
                GLES30.GL_TEXTURE_2D to sceneLayer.groundReflectionTexture
            } else if(groundAxes != null && sceneLayer.groundMaterialStyle > 0 && name == "s_texBump") {
                GLES30.GL_TEXTURE_2D to groundTextures.texture(sceneLayer.groundMaterialStyle, normal = true)
            } else if(groundAxes != null && sceneLayer.groundMaterialStyle > 0 && name in setOf("s_texColor", "s_emissive")) {
                GLES30.GL_TEXTURE_2D to groundTextures.texture(sceneLayer.groundMaterialStyle)
            } else if (name == "_EnvSpecMap" && useScene) {
                GLES30.GL_TEXTURE_CUBE_MAP to (if(panoramaEnvironment!=null)envCube else sceneEnvCube)
            } else {
                val cube = texCube[path]
                if (cube != null) {
                    GLES30.GL_TEXTURE_CUBE_MAP to cube
                } else {
                    GLES30.GL_TEXTURE_2D to texEx(path)
                }
            }
        }
        var unit = nextUnit
        if (plateSurface) {
            
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            val plateTexture = licensePlateLayer.texture(platePng, plateAppearance)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, plateTexture)
            GLES30.glUniform1i(prog.uniform("uLabPlate"), unit)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            unit++
        }
        if (s.engineShader) {
            
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(
                GLES30.GL_TEXTURE_CUBE_MAP,
                if (envCube != 0) envCube else sceneEnvCube
            )
            GLES30.glUniform1i(prog.uniform("s_reflection"), unit)
            unit++
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, brdfLut.texture())
            GLES30.glUniform1i(prog.uniform("s_brdf"), unit)
            unit++
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }
        if(panoramaEnvironment!=null)prog.bindRaw("_EnvSpecYaw",0f)
        
        if (s.genericShader) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, if(panoramaEnvironment!=null)envCube else sceneEnvCube)
            GLES30.glUniform1i(prog.uniform("_EnvSpecMap"), unit)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            prog.bindRaw("_EnvSpecYaw", if(panoramaEnvironment!=null)0f else sceneYaw)
            unit++
        }
        
        prog.bindRaw("_EnvSpecYaw",(if(panoramaEnvironment!=null)0f else if(useScene)sceneYaw else s.envYaw)+Math.toDegrees(drivingPose.heading).toFloat())
        val depthLoc = prog.uniform("s_depthMap")
        if (depthLoc >= 0 && !shadowLogged) {
            shadowLogged = true
            Log.i(
                "CarDemo",
                "阴影接口 ${s.path.substringAfterLast('/')}：" +
                    "depth=$depthLoc mtx=${prog.uniform("u_lightViewMtx")} " +
                    "rel=${prog.uniform("u_lightCameraRelativeTranslation")} " +
                    "size=${prog.uniform("u_lightSizeDepthValueFrustumEdgeFalloff")} " +
                    "color=${prog.uniform("u_shadowColorDarkness")} " +
                    "bias=${prog.uniform("u_shadowBiasBlurSize")}"
            )
        }
        if (depthLoc >= 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shTex)
            GLES30.glUniform1i(depthLoc, unit)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            shBoundUnit = unit
            GLES30.glUniformMatrix4fv(prog.uniform("u_lightViewMtx"), 1, false, shLightVp, 0)
            GLES30.glUniform3f(prog.uniform("u_lightCameraRelativeTranslation"), 0f, 0f, 0f)
            GLES30.glUniform4f(
                prog.uniform("u_lightSizeDepthValueFrustumEdgeFalloff"),
                shEncX, shEncY, SHADOW_LIGHT_SIZE, SHADOW_EDGE_FALLOFF
            )
            
            GLES30.glUniform4f(
                prog.uniform("u_shadowColorDarkness"),
                SHADOW_COLOR_R, SHADOW_COLOR_G, SHADOW_COLOR_B, SHADOW_DARKNESS
            )
            
            GLES30.glUniform4f(
                prog.uniform("u_shadowBiasBlurSize"),
                if(shPerspective)shadowBias*.02f else shadowBias, shadowBlur, 0f, 0f
            )
            if (!shadowReadback) {
                shadowReadback = true
                val biasLoc = prog.uniform("u_shadowBiasBlurSize")
                val darkLoc = prog.uniform("u_shadowColorDarkness")
                val rb = FloatArray(4)
                val rd = FloatArray(4)
                GLES30.glGetUniformfv(prog.id, biasLoc, rb, 0)
                GLES30.glGetUniformfv(prog.id, darkLoc, rd, 0)
                Log.i(
                    "CarDemo",
                    "阴影回读 loc=$biasLoc/$darkLoc bias=${rb.toList()} dark=${rd.toList()}"
                )
            }
        }

        if (checkGl && s.path.contains("m_env_ground") && s.path !in sceneBindings) {
            val bindings = ArrayList<String>()
            for (name in listOf("s_texColor", "s_alpha", "s_emissive", "s_reflection")) {
                val loc = prog.uniform(name)
                val value = IntArray(1)
                if (loc >= 0) {
                    GLES30.glGetUniformiv(prog.id, loc, value, 0)
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + value[0])
                    val texture = IntArray(1)
                    GLES30.glGetIntegerv(if (name == "s_reflection") GLES30.GL_TEXTURE_BINDING_CUBE_MAP else GLES30.GL_TEXTURE_BINDING_2D, texture, 0)
                    bindings.add("$name=${value[0]}/${texture[0]}")
                }
            }
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            sceneBindings[s.path] = bindings.joinToString() + " uv=${s.uvScale.toList()} base=${s.base.toList()}"
        }

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, m.vbo)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.ebo)
        val aPos = prog.attrib("a_position")
        val aUv = prog.attrib("a_texcoord0")
        val aNrm = prog.attrib("a_normal")
        val aTan = prog.attrib("a_tangent")
        attachAttrib(aPos, 3, 0)
        attachAttrib(aUv, 2, 24)
        attachAttrib(aNrm, 3, 12)
        attachAttrib(aTan, 4, 32)
        checkGlAt("材质绑定:${s.path.substringAfterLast('/')}")
        if (checkGl && validatedPrograms.add(prog.id)) {
            GLES30.glValidateProgram(prog.id)
            val valid = IntArray(1)
            GLES30.glGetProgramiv(prog.id, GLES30.GL_VALIDATE_STATUS, valid, 0)
            if (valid[0] == 0) {
                val samplers = ArrayList<String>()
                val count = IntArray(1)
                GLES30.glGetProgramiv(prog.id, GLES30.GL_ACTIVE_UNIFORMS, count, 0)
                for (i in 0 until count[0]) {
                    val size = IntArray(1); val type = IntArray(1)
                    val name = GLES30.glGetActiveUniform(prog.id, i, size, 0, type, 0)
                    if (type[0] !in intArrayOf(GLES30.GL_SAMPLER_2D, GLES30.GL_SAMPLER_CUBE,
                            GLES30.GL_SAMPLER_2D_SHADOW)) continue
                    val value = IntArray(1)
                    GLES30.glGetUniformiv(prog.id, prog.uniform(name), value, 0)
                    samplers.add("$name=${value[0]}/${type[0]}")
                }
                glErrors.add("校验:${s.path.substringAfterLast('/')}:${GLES30.glGetProgramInfoLog(prog.id)}:$samplers")
            }
        }
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, 0)
        checkGlAt("绘制:${s.path.substringAfterLast('/')}")
        for (loc in intArrayOf(aPos, aUv, aNrm, aTan)) {
            if (loc >= 0) GLES30.glDisableVertexAttribArray(loc)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun attachAttrib(loc: Int, size: Int, offset: Int) {
        if (loc < 0) return
        GLES30.glEnableVertexAttribArray(loc)
        GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, VERTEX_STRIDE, offset)
    }

    private fun drawGrid(vp: FloatArray, eye: FloatArray, y: Float) {
        if (gridProgram == 0) return
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(gridProgram)
        GLES30.glUniformMatrix4fv(gVp, 1, false, vp, 0)
        GLES30.glUniform1f(gY, y)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisableVertexAttribArray(0)
    }

    private fun upload(model: GwmModel) {
        resourceStats=null
        releaseMeshes()
        for(axis in 0..2) {
            paintBounds[axis]=model.meshes.minOfOrNull {mesh -> mesh.positions.indices.step(3).minOfOrNull {mesh.positions[it+axis]} ?: 0f} ?: 0f
            paintBounds[axis+3]=model.meshes.maxOfOrNull {mesh -> mesh.positions.indices.step(3).maxOfOrNull {mesh.positions[it+axis]} ?: 1f} ?: 1f
        }
        triangleCount = model.triangleCount

        curtainBounds.clear()
        model.meshes.filter { it.part.startsWith("RoofCurtain_") }.forEach { curtainBounds[it.node]=RoofCurtain.bounds(it.positions) }
        if(curtainBounds.isNotEmpty()) {
            curtainSlot=curtainBounds.values.maxOf{it[3]}
            curtainTravel=curtainSlot-curtainBounds.values.minOf{it[0]}+.04f
        }
        val matrix = buildModelMatrix(model)
        System.arraycopy(matrix, 0, baseMatrix, 0, 16)
        val (framingLo,framingHi)=CameraFraming.bodyBounds(model,prefabHidden)
        framingCorners=(0..7).map { index ->
            val point=FloatArray(4) { axis -> if(axis==3)1f else if(index and (1 shl axis)==0)framingLo[axis] else framingHi[axis] }
            FloatArray(4).also { Matrix.multiplyMV(it,0,baseMatrix,0,point,0) }
        }
        platePng = model.textures[ModelRepository.LICENSE_PLATE_ATLAS] ?: model.textures[com.haystudio.vehiclelab.data.PortableModelArchive.PLATE_TEXTURE]
        plateAnchors = plateAnchors(model)
        surfaceVariants.clear()
        val changedModel=uploadedModel !== model
        if(changedModel) {
            uploadedModel=model
            weatherCollisionDirty=true
            weatherSimulation.clear()
        }

        nodes = model.nodes
        nodeRest = model.nodeWorlds
        nodePoseEvaluator = com.haystudio.vehiclelab.data.NodePoseEvaluator(model.nodes, nodeRest)
        nodeLocals = Array(model.nodes.size) { model.nodes[it].local.copyOf() }
        poseValue = FloatArray(nodeLocals.size)
        poseTarget = FloatArray(nodeLocals.size)
        groupByNode = IntArray(nodeLocals.size) { GROUP_NONE }
        clipByNode.clear()
        if(changedModel)clipProgress.clear()
        corrections.clear()
        effectMatrices.clear()
        for ((target, clips) in model.clips) {
            val idx = model.nodeIndex[target] ?: continue
            clipByNode[idx] = clips
        }

        val chains = Array(nodes.size) { HashSet<String>(8) }
        for (i in nodes.indices) {
            var p = i
            while (p in nodes.indices) {
                chains[i].add(nodes[p].name)
                p = nodes[p].parent
            }
        }

        loadTextures(model)
        loadEnvironment(model.environment)
        applySceneEnv()
        lightsByPeriod = model.lights
        rebuildLights()

        val plateFaces = HashMap<String, PlateAnchor>()
        val uploadMeshes = GrilleGeometry.clean(model.meshes).flatMap(ChargingPortGeometry::split).flatMap { source ->
            val rear = source.name == "wangge.063-0"
            val frameName = if (rear) "wangge.062" else "wangge.049-0"
            val anchor = plateAnchors.firstOrNull { (it.normal[0] > 0f) == rear }
            if (source.name !in setOf("wangge.046-0", "wangge.063-0") || anchor == null) listOf(source)
            else {
                val split = PlateSurfaceGeometry.split(source, anchor)
                if (split == null) listOf(source) else {
                    val frameSpec = model.meshes.first { it.name == frameName }.material
                    val face = split.second
                    plateFaces[face.name] = anchor
                    listOf(split.first, MeshData(face.name,face.part,face.positions,face.normals,face.uvs,
                        face.indices,frameSpec,face.node,face.tangents))
                }
            }
        }
        val articulatedMeshes=uploadMeshes.flatMap { source ->
            val split=SteeringWheelGeometry.split(source)
            if(split==null)listOf(source) else {
                steeringGeometryOffset=SteeringWheelGeometry.offset(source)
                listOf(split.first,split.second)
            }
        }
        val litMeshes=articulatedMeshes.flatMap { source ->
            val added=listOfNotNull(MirrorCourtesyGeometry.create(source),TrunkButtonGeometry.create(source))
            listOf(source)+added
        }
        var geometryBufferBytes=0L
        for (mesh in litMeshes) {
            val n = mesh.positions.size / 3
            if (n < 3 || mesh.indices.size < 3) continue

            geometryBufferBytes+=n*48L+mesh.indices.size*4L
            val inter = FloatArray(n * 12)
            val plateAnchor = when (mesh.name) {
                "wangge.049-0" -> plateAnchors.firstOrNull { it.normal[0] < 0f }
                "wangge.062" -> plateAnchors.firstOrNull { it.normal[0] > 0f }
                else -> plateFaces[mesh.name]
            }
            val tan = mesh.tangents
            var p = 0
            for (i in 0 until n) {
                inter[p++] = mesh.positions[i * 3]
                inter[p++] = mesh.positions[i * 3 + 1]
                inter[p++] = mesh.positions[i * 3 + 2]
                inter[p++] = mesh.normals[i * 3]
                inter[p++] = mesh.normals[i * 3 + 1]
                inter[p++] = mesh.normals[i * 3 + 2]
                val plateUv = plateAnchor?.uv(mesh.positions[i * 3], mesh.positions[i * 3 + 1], mesh.positions[i * 3 + 2])
                inter[p++] = plateUv?.get(0) ?: mesh.uvs[i * 2]
                inter[p++] = plateUv?.get(1) ?: mesh.uvs[i * 2 + 1]
                var tx = 0f
                var ty = 0f
                var tz = 0f
                var hand = 1f
                if (tan.isNotEmpty()) {
                    tx = tan[i * 4]
                    ty = tan[i * 4 + 1]
                    tz = tan[i * 4 + 2]
                    hand = if (tan[i * 4 + 3] < 0f) 0f else 1f
                } else {
                    
                    val nx = mesh.normals[i * 3]
                    val ny = mesh.normals[i * 3 + 1]
                    val nz = mesh.normals[i * 3 + 2]
                    val ax = if (abs(nx) < 0.9f) 1f else 0f
                    val ay = if (abs(nx) < 0.9f) 0f else 1f
                    tx = ny * 0f - nz * ay
                    ty = nz * ax - nx * 0f
                    tz = nx * ay - ny * ax
                    val l = sqrt(tx * tx + ty * ty + tz * tz)
                    if (l > 1e-6f) {
                        tx /= l; ty /= l; tz /= l
                    } else {
                        tx = 1f; ty = 0f; tz = 0f
                    }
                }
                inter[p++] = tx * 0.5f + 0.5f
                inter[p++] = ty * 0.5f + 0.5f
                inter[p++] = tz * 0.5f + 0.5f
                inter[p++] = hand
            }

            val vaoIds = IntArray(1)
            val vboIds = IntArray(1)
            val eboIds = IntArray(1)
            GLES30.glGenVertexArrays(1, vaoIds, 0)
            GLES30.glGenBuffers(1, vboIds, 0)
            GLES30.glGenBuffers(1, eboIds, 0)
            GLES30.glBindVertexArray(vaoIds[0])

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboIds[0])
            val fb = ByteBuffer.allocateDirect(inter.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            fb.put(inter)
            fb.position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, inter.size * 4, fb, GLES30.GL_STATIC_DRAW)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboIds[0])
            val ib = ByteBuffer.allocateDirect(mesh.indices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
            ib.put(mesh.indices)
            ib.position(0)
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4, ib, GLES30.GL_STATIC_DRAW
            )

            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 48, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 48, 12)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 48, 24)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, 48, 32)
            GLES30.glBindVertexArray(0)

            val spec = mesh.material
            val bloom = mesh.part.contains("bloom", ignoreCase = true) ||
                spec.path.contains("LightsBloom", ignoreCase = true)
            val albedo = if (spec.paint) 0 else texOf(spec.albedoTexture)
            val emissive = texOf(spec.effectiveEmissiveTexture)
            val env = spec.envTex?.let { texCube[it] } ?: 0
            val orm = texOf(spec.ormTex)
            val mask = texOf(spec.alphaMaskTex)
            val bounds = FloatArray(6)
            for (i in 0 until 3) {
                bounds[i] = Float.MAX_VALUE
                bounds[i + 3] = -Float.MAX_VALUE
            }
            val pos = mesh.positions
            var v = 0
            while (v + 2 < pos.size) {
                for (i in 0 until 3) {
                    val c = pos[v + i]
                    if (c < bounds[i]) bounds[i] = c
                    if (c > bounds[i + 3]) bounds[i + 3] = c
                }
                v += 3
            }
            val retainTouchGeometry = CameraPreset.entries.any { WindowControl.sideClip(it)?.equals(mesh.part, true) == true } ||
                (!spec.blended && chains.getOrElse(mesh.node) { emptySet() }.any {
                    it.startsWith("Door_") && !it.contains("Hood") && !it.contains("Trunk")
                })
            val m = GpuMesh(
                vaoIds[0], vboIds[0], eboIds[0], mesh.indices.size,
                spec, spec.opacity, mesh.node, albedo, emissive, env, orm, mask, bloom,
                chains.getOrElse(mesh.node) { emptySet() }, mesh.name, mesh.part, bounds,
                if (retainTouchGeometry)
                    mesh.positions.copyOf() else FloatArray(0),
                if (retainTouchGeometry)
                    mesh.indices.copyOf() else IntArray(0)
            )
            Log.i(
                "CarDemo",
                "网格 ${mesh.name} 节点=${mesh.node} ${spec.path.substringAfterLast('/')} " +
                    "三角形=${mesh.indices.size / 3} " +
                    "包围盒=(%.2f,%.2f,%.2f)..(%.2f,%.2f,%.2f)".format(
                        bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]
                    )
            )
            m.plateAnchor = plateAnchor
            System.arraycopy(baseMatrix, 0, m.matrix, 0, 16)
            meshes.add(m)
        }

        resourceStats=ModelResourceStats.from(model,meshes.size,geometryBufferBytes)
        for (front in listOf(true, false)) {
            val flowing = meshes.filter { m ->
                lamps?.flowProgress(m.ancestors, 0f) != null && ((m.bounds[0] + m.bounds[3]) < 0f) == front
            }
            val lo = flowing.minOfOrNull { minOf(abs(it.bounds[2]), abs(it.bounds[5])) } ?: continue
            val hi = flowing.maxOf { maxOf(abs(it.bounds[2]), abs(it.bounds[5])) }
            for (mesh in flowing) mesh.flowRange = floatArrayOf(lo, hi)
        }
        val frame = makeFrame(model)
        carFrame = frame
        rearAxleX=1.45f
        chassisGeometry=SteeringKinematics.Geometry()
        val centers=listOf("Wheel_LeftFront","Wheel_RightFront","Wheel_LeftBack","Wheel_RightBack").map { name ->
            model.worldOf(name)?.let { world -> FloatArray(3) { axis ->
                baseMatrix[axis]*world[12]+baseMatrix[4+axis]*world[13]+baseMatrix[8+axis]*world[14]+baseMatrix[12+axis]
            } }
        }
        if(centers.all {it!=null}) {
            val c=centers.map {it!!}
            val front=(c[0][0]+c[1][0])*.5
            val rear=(c[2][0]+c[3][0])*.5
            chassisGeometry=SteeringKinematics.Geometry(abs(rear-front),
                (abs(c[0][2]-c[1][2])+abs(c[2][2]-c[3][2]))*.5,
                0.32)
            rearAxleX=rear.toFloat()
        }
        
        groundY = com.haystudio.vehiclelab.data.TireGroundContact.height(
            model.meshes.filter {it.material.kind==MaterialKind.TIRE}.map {it.positions},baseMatrix) ?: frame.bottomY
        
        applyPose()
    }

    private fun plateAnchors(model: GwmModel): List<PlateAnchor> {

        if (model.boundsMin[0] < -2.3f && model.boundsMax[0] > 2.5f) {
            fun hc25EndFace(name: String, rear: Boolean): PlateAnchor? {
                val mesh = model.meshes.firstOrNull { it.name == name } ?: return null
                val positions = ArrayList<Float>()
                val normals = ArrayList<Float>()
                for (i in mesh.positions.indices step 3) {
                    val nx = mesh.normals[i]
                    val nz = mesh.normals[i + 2]
                    val outward = if (rear) nx > .9f else nx < -.9f
                    if (!outward || abs(nz) > .08f) continue
                    positions.add(mesh.positions[i]); positions.add(mesh.positions[i + 1]); positions.add(mesh.positions[i + 2])
                    normals.add(nx); normals.add(mesh.normals[i + 1]); normals.add(nz)
                }
                return PlateAnchor.fromEndFace(positions.toFloatArray(), normals.toFloatArray(), rear)
            }
            return listOfNotNull(hc25EndFace("wangge.049-0", false), hc25EndFace("wangge.062", true))
        }
        fun anchor(front: Boolean): PlateAnchor? {
            val candidates = model.meshes.filter { mesh ->
                if (mesh.kind == MaterialKind.SHADOW || !mesh.material.path.contains("Baked_Paint_Black", true)) return@filter false
                val p=mesh.positions
                var minX=Float.MAX_VALUE;var maxX=-Float.MAX_VALUE;var minZ=Float.MAX_VALUE;var maxZ=-Float.MAX_VALUE
                var i=0;while(i+2<p.size){minX=min(minX,p[i]);maxX=max(maxX,p[i]);minZ=min(minZ,p[i+2]);maxZ=max(maxZ,p[i+2]);i+=3}
                val end=if(front) minX < -2.15f else maxX > 2.15f
                end && (maxZ-minZ) in .15f..1.0f
            }
            val mesh = candidates.minByOrNull { mesh ->
                val p=mesh.positions;var minX=Float.MAX_VALUE;var maxX=-Float.MAX_VALUE;var minZ=Float.MAX_VALUE;var maxZ=-Float.MAX_VALUE
                var i=0;while(i+2<p.size){minX=min(minX,p[i]);maxX=max(maxX,p[i]);minZ=min(minZ,p[i+2]);maxZ=max(maxZ,p[i+2]);i+=3}
                
                val endScore=if(front) minX else -maxX
                endScore*10f+(maxZ-minZ)*2f
            } ?: return null
            val p=mesh.positions;val n=mesh.normals
            var cx=0f;var cy=0f;var cz=0f;var nx=0f;var ny=0f;var nz=0f;val count=p.size/3
            for(i in 0 until count){cx+=p[i*3];cy+=p[i*3+1];cz+=p[i*3+2];nx+=n[i*3];ny+=n[i*3+1];nz+=n[i*3+2]}
            val inv=1f/count.coerceAtLeast(1);cx*=inv;cy*=inv;cz*=inv;nx*=inv;ny*=inv;nz*=inv
            val len=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(1e-5f);val normal=floatArrayOf(nx/len,ny/len,nz/len)
            val lateral=floatArrayOf(0f,0f,1f);val upRaw=floatArrayOf(normal[1]*lateral[2]-normal[2]*lateral[1],normal[2]*lateral[0]-normal[0]*lateral[2],normal[0]*lateral[1]-normal[1]*lateral[0]);val ul=sqrt(upRaw[0]*upRaw[0]+upRaw[1]*upRaw[1]+upRaw[2]*upRaw[2]).coerceAtLeast(1e-5f)
            return PlateAnchor(floatArrayOf(cx,cy,cz),normal,lateral,PlateAnchor.upFromNormal(normal),.48f,.15f)
        }
        return listOfNotNull(anchor(true),anchor(false))
    }

    private fun applySceneEnv() {
        val p = palette ?: return
        val hour = when (dayPeriod) {
            DayPeriod.MORNING -> 8
            DayPeriod.NOON -> 12
            DayPeriod.NIGHT -> 21
        }
        val env = p.envFor(hour) ?: return
        sceneEnvCube = texCube[env.name] ?: 0
        sceneYaw = env.yaw
        shDirty = true
    }

    private fun rebuildLights() {
        packedShadowRequest = viewOptions.selfShadow
        basePackedLights = lightsByPeriod[dayPeriod]?.pack(baseMatrix, packedShadowRequest)
        packedLights = basePackedLights
        lightsDirty = true
    }

    private fun uploadLights() {
        if (lightsUbo == 0) return
        val empty = FloatArray(LightRig.MAX_LIGHTS * LightRig.VEC_PER_LIGHT * 4 + 4)
        val packed = packedLights
        uploadBlock(lightsUbo, packed?.block ?: empty, OfficialProgram.BLOCK_LIGHTS)
        if (lightsNoDirUbo != 0) {
            uploadBlock(
                lightsNoDirUbo,
                packed?.blockNoDir ?: empty,
                OfficialProgram.BLOCK_LIGHTS
            )
            
            GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, OfficialProgram.BLOCK_LIGHTS, lightsUbo)
            lightsNoDirBound = false
        }
        uploadBlock(
            physicalUbo,
            packed?.physical ?: FloatArray(LightRig.MAX_LIGHTS * 4),
            OfficialProgram.BLOCK_PHYSICAL
        )
        lightsDirty = false
    }

    private fun bindLightsFor(spec: MaterialSpec) {
        val alt = !spec.dirLight && lightsNoDirUbo != 0 && packedLights != null
        if (alt == lightsNoDirBound) return
        GLES30.glBindBufferBase(
            GLES30.GL_UNIFORM_BUFFER,
            OfficialProgram.BLOCK_LIGHTS,
            if (alt) lightsNoDirUbo else lightsUbo
        )
        lightsNoDirBound = alt
    }

    private fun uploadBlock(id: Int, data: FloatArray, point: Int) {
        val buf = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(data)
        buf.position(0)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, id)
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, data.size * 4, buf, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, point, id)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0)
    }

    private fun initEngineBlocks() {
        officialPrograms.clear()
        officialFailed.clear()
        officialLogged = false
        val ids = IntArray(6)
        GLES30.glGenBuffers(6, ids, 0)
        viewUbo = ids[0]
        lightsUbo = ids[1]
        physicalUbo = ids[2]
        lightsNoDirUbo = ids[3]
        materialUbo = ids[4]
        shUbo = ids[5]
        materialBound = null
        shBakedCube = -1
        shBakedPeriod = -1
        lightsNoDirBound = false
        uploadBlock(
            viewUbo,
            floatArrayOf(1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f),
            OfficialProgram.BLOCK_VIEW
        )
        uploadBlock(materialUbo, FloatArray(MaterialLibrary.PBR_BLOCK_FLOATS), OfficialProgram.BLOCK_MATERIAL)
        uploadBlock(shUbo, FloatArray(36), OfficialProgram.BLOCK_SH)
        lightsDirty = true
    }

    private fun uploadView(hdr: Boolean, offscreen: Boolean = false) {
        outputLinear = hdr || offscreen || srgbFramebuffer
        materialToneMapping = !hdr && viewOptions.toneMapping
        if (viewUbo == 0) return
        uploadBlock(
            viewUbo,
            floatArrayOf(
                1f,
                if (!hdr && viewOptions.toneMapping) 1f else 0f,
                if (hdr) 1f else 0f,
                if (hdr || offscreen) 0f else 1f,
                0f, 0f, 0f, if (!hdr && !offscreen && srgbFramebuffer) 1f else 0f
            ),
            OfficialProgram.BLOCK_VIEW
        )
    }

    private fun ensureHdr(): Boolean {
        val w = viewportWidth
        val h = viewportHeight
        if (w < 2 || h < 2) return false
        if (hdrFbo != 0 && hdrW == w && hdrH == h) return true
        releaseHdr()
        hdrW = w
        hdrH = h
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        hdrTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hdrTex)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenRenderbuffers(1, ids, 0)
        hdrDepth = ids[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, hdrDepth)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, w, h)
        GLES30.glGenFramebuffers(1, ids, 0)
        hdrFbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, hdrFbo)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, hdrTex, 0
        )
        GLES30.glFramebufferRenderbuffer(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_RENDERBUFFER, hdrDepth
        )
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) ==
            GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (!ok) {
            android.util.Log.w("CarDemo", "HDR 帧缓冲不完整，退回 8 位直接输出")
            releaseHdr()
            return false
        }
        return true
    }

    private fun releaseHdr() {
        if (hdrFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(hdrFbo), 0)
        if (hdrDepth != 0) GLES30.glDeleteRenderbuffers(1, intArrayOf(hdrDepth), 0)
        if (hdrTex != 0) GLES30.glDeleteTextures(1, intArrayOf(hdrTex), 0)
        hdrFbo = 0
        hdrDepth = 0
        hdrTex = 0
        hdrW = 0
        hdrH = 0
    }

    private fun uploadEnvironmentSh() {
        shDirty = false
        if (shUbo == 0) return
        val env = environment
        
        val key = if (env != null) -1 else sceneEnvCube
        val period = if (env != null) -1 else dayPeriod.ordinal
        if (key == shBakedCube && period == shBakedPeriod) return
        val data = env?.harmonics ?: EnvSh.coefficients(sceneEnvCube, dayPeriod.ordinal)
        uploadBlock(shUbo, data, OfficialProgram.BLOCK_SH)
        shBakedCube = key
        shBakedPeriod = period
        
        materialBound = null
    }

    private fun loadEnvironment(bytes: ByteArray?) {
        environment = null
        if (envCube != 0) GLES30.glDeleteTextures(1, intArrayOf(envCube), 0)
        envCube = 0
        if (bytes == null) {
            Log.w("CarDemo", "场景环境缺失：环境光退回时段反射图")
            return
        }
        val env = try {
            GenvEnvironmentReader.parse(bytes)
        } catch (t: Throwable) {
            Log.w("CarDemo", "场景环境解析失败：${t.message}")
            null
        }
        if (env == null) {
            Log.w("CarDemo", "场景环境不可用：环境光退回时段反射图")
            return
        }
        val id = createCubeTexture(env.cube)
        if (id == 0) {
            Log.w("CarDemo", "场景环境立方图上传失败：环境光退回时段反射图")
            return
        }
        environment = env
        envCube = id
        shDirty = true
        val sh = env.harmonics
        Log.i(
            "CarDemo",
            "场景环境 %d² × %d 级 mip；球谐 L00=(%.3f,%.3f,%.3f) L1_1=%+.3f L10=%+.3f L11=%+.3f".format(
                env.cube.width, env.cube.mipCount, sh[0], sh[1], sh[2], sh[3], sh[6], sh[9]
            )
        )
    }

    private fun texOf(path: String?): Int {
        if (path == null) return 0
        return tex2d[path] ?: 0
    }

    private fun texEx(path: String): Int {
        tex2d[path]?.let { return it }
        val alt = if (path.contains("-mipmap")) {
            path.replace("-mipmap", "")
        } else {
            path.substringBeforeLast('.').plus("-mipmap").plus(".ktx")
        }
        return tex2d[alt] ?: 0
    }

    private fun loadTextures(model: GwmModel) {
        if (model.textures.isEmpty()) return
        detectAnisotropy()
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val failed = ArrayList<String>(4)
        for ((path, bytes) in model.textures) {
            
            if (path.endsWith(".png", ignoreCase = true)) continue
            val ktx = try {
                KtxTexture.parse(bytes)
            } catch (_: Throwable) {
                null
            }
            if (ktx == null) {
                Log.w("CarDemo", "KTX 解析失败：$path（${bytes.size} 字节）")
                failed.add(path.substringAfterLast('/'))
                continue
            }
            val id = if (ktx.cubemap) createCubeTexture(ktx) else createTexture(ktx)
            if (id == 0) {
                Log.w("CarDemo", "贴图上传失败：$path（${ktx.width}x${ktx.height}×${ktx.faces}）")
                failed.add(path.substringAfterLast('/'))
                continue
            }
            if (ktx.cubemap) texCube[path] = id else tex2d[path] = id
        }
        textureStats = buildString {
            append("2D ").append(tex2d.size).append(" 张 · 立方图 ").append(texCube.size).append(" 张")
            if (failed.isNotEmpty()) {
                append(" · 失败 ").append(failed.joinToString(","))
            }
        }
        Log.i("CarDemo", "贴图：$textureStats")
    }

    private fun detectAnisotropy() {
        if (maxAniso > 0f) return
        val exts = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: return
        if (!exts.contains("GL_EXT_texture_filter_anisotropic")) return
        val value = FloatArray(1)
        GLES30.glGetFloatv(0x84FF, value, 0)
        maxAniso = value[0].coerceIn(1f, 8f)
    }

    private fun createTexture(ktx: KtxTexture): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        if (ids[0] == 0) return 0
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
            
        }
        for (level in 0 until ktx.mipCount) {
            val data = ktx.image(0, level)
            val w = ktx.levelWidth(level)
            val h = ktx.levelHeight(level)
            val buf = ByteBuffer.allocateDirect(data.size)
                .order(ByteOrder.nativeOrder())
                .put(data)
            buf.position(0)
            if (ktx.compressed) {
                GLES30.glCompressedTexImage2D(
                    GLES30.GL_TEXTURE_2D, level, ktx.internalFormat, w, h, 0, data.size, buf
                )
            } else {
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, level, ktx.internalFormat, w, h, 0,
                    ktx.glFormat, ktx.glType, buf
                )
            }
        }
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) {
            GLES30.glDeleteTextures(1, ids, 0)
            return 0
        }
        var mip = ktx.mipCount > 1
        if (!mip) {
            
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            mip = GLES30.glGetError() == GLES30.GL_NO_ERROR
        }
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
            if (mip) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT
        )
        if (maxAniso > 0f) {
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, 0x84FE, maxAniso)
        }
        return ids[0]
    }

    private fun createCubeTexture(ktx: KtxTexture): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        if (ids[0] == 0) return 0
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, ids[0])
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
        }
        for (face in 0 until 6) {
            for (level in 0 until ktx.mipCount) {
                val data = ktx.image(face, level)
                val w = ktx.levelWidth(level)
                val h = ktx.levelHeight(level)
                val buf = ByteBuffer.allocateDirect(data.size)
                    .order(ByteOrder.nativeOrder())
                    .put(data)
                buf.position(0)
                val target = GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face
                if (ktx.compressed) {
                    GLES30.glCompressedTexImage2D(
                        target, level, ktx.internalFormat, w, h, 0, data.size, buf
                    )
                } else {
                    GLES30.glTexImage2D(
                        target, level, ktx.internalFormat, w, h, 0,
                        ktx.glFormat, ktx.glType, buf
                    )
                }
            }
        }
        if (GLES30.glGetError() != GLES30.GL_NO_ERROR) {
            GLES30.glDeleteTextures(1, ids, 0)
            return 0
        }
        var mip = ktx.mipCount > 1
        if (!mip) {

            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP)
            mip = GLES30.glGetError() == GLES30.GL_NO_ERROR
        }
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER,
            if (mip) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE
        )
        cubeMips[ids[0]] = if (ktx.mipCount > 1) ktx.mipCount else fullMipCount(ktx.width)
        return ids[0]
    }

    private fun fullMipCount(size: Int): Int {
        var n = 1
        var s = size
        while (s > 1) {
            s /= 2
            n++
        }
        return n
    }

    private fun makeFrame(model: GwmModel): CarFrame {
        val robust = RobustVehicleBounds.fromPositionArrays(model.meshes.filterNot { mesh ->
            val name = (mesh.name + mesh.part).lowercase()
            mesh.kind == MaterialKind.SHADOW || "controller" in name || name.startsWith("light_") || "_clip" in name
        }.map { it.positions })
        val mn = robust?.first ?: model.boundsMin
        val mx = robust?.second ?: model.boundsMax
        val v = FloatArray(4)
        val out = FloatArray(4)
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (ix in 0..1) for (iy in 0..1) for (iz in 0..1) {
            v[0] = if (ix == 0) mn[0] else mx[0]
            v[1] = if (iy == 0) mn[1] else mx[1]
            v[2] = if (iz == 0) mn[2] else mx[2]
            v[3] = 1f
            Matrix.multiplyMV(out, 0, baseMatrix, 0, v, 0)
            minX = min(minX, out[0]); maxX = max(maxX, out[0])
            minY = min(minY, out[1]); maxY = max(maxY, out[1])
            minZ = min(minZ, out[2]); maxZ = max(maxZ, out[2])
        }
        val ex = maxX - minX
        val ey = maxY - minY
        val ez = maxZ - minZ
        return CarFrame(ex >= ez, max(ex, ez) * .5f, min(ex, ez) * .5f, minY, ey)
    }

    private fun buildModelMatrix(model: GwmModel): FloatArray {
        if (desktopCamera != null) return FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        val r = FloatArray(16)
        Matrix.setIdentityM(r, 0)
        Matrix.rotateM(r, 0, Math.toDegrees(model.rot[0].toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(r, 0, Math.toDegrees(model.rot[1].toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(r, 0, Math.toDegrees(model.rot[2].toDouble()).toFloat(), 0f, 0f, 1f)

        val mn = model.boundsMin
        val mx = model.boundsMax
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        val v = FloatArray(4)
        val out = FloatArray(4)
        for (ix in 0..1) for (iy in 0..1) for (iz in 0..1) {
            v[0] = if (ix == 0) mn[0] else mx[0]
            v[1] = if (iy == 0) mn[1] else mx[1]
            v[2] = if (iz == 0) mn[2] else mx[2]
            v[3] = 1f
            Matrix.multiplyMV(out, 0, r, 0, v, 0)
            if (out[0] < minX) minX = out[0]
            if (out[1] < minY) minY = out[1]
            if (out[2] < minZ) minZ = out[2]
            if (out[0] > maxX) maxX = out[0]
            if (out[1] > maxY) maxY = out[1]
            if (out[2] > maxZ) maxZ = out[2]
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val maxExt = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(1.0e-4f)
        val s = 2f / maxExt

        val t = FloatArray(16)
        Matrix.setIdentityM(t, 0)
        Matrix.translateM(t, 0, -cx, -cy, -cz)
        val tr = FloatArray(16)
        Matrix.multiplyMM(tr, 0, t, 0, r, 0)
        val sm = FloatArray(16)
        Matrix.setIdentityM(sm, 0)
        Matrix.scaleM(sm, 0, s, s, s)
        val m = FloatArray(16)
        Matrix.multiplyMM(m, 0, sm, 0, tr, 0)
        return m
    }

    private fun multiply(a: FloatArray, b: FloatArray): FloatArray =
        FloatArray(16).also { multiplyInto(it, a, b) }

    private fun multiplyInto(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (c in 0 until 4) {
            for (row in 0 until 4) {
                out[c * 4 + row] =
                    a[row] * b[c * 4] +
                        a[4 + row] * b[c * 4 + 1] +
                        a[8 + row] * b[c * 4 + 2] +
                        a[12 + row] * b[c * 4 + 3]
            }
        }
    }

    private fun releaseMeshes() {
        for (m in meshes) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(m.vao), 0)
            GLES30.glDeleteBuffers(1, intArrayOf(m.vbo), 0)
            GLES30.glDeleteBuffers(1, intArrayOf(m.ebo), 0)
        }
        meshes.clear()
        doorCourtesyMounts.clear()
        if (tex2d.isNotEmpty()) {
            for (id in tex2d.values) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
            tex2d.clear()
        }
        if (texCube.isNotEmpty()) {
            for (id in texCube.values) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
            texCube.clear()
        }
        sceneEnvCube = 0
        if (envCube != 0) GLES30.glDeleteTextures(1, intArrayOf(envCube), 0)
        environment = null
        envCube = 0
    }

    fun release() {
        wheelExposureLayer.release()
        sceneTransition.release()
        releaseMeshes()
        sceneLayer.dispose()
        groundLightLayer.release()
        groundTextures.release()
        headlightBeamLayer.release()
        lampGlowLayer.release()
        weatherLayer.release()
        chargingCableLayer.release()
        panoramaLayer.release()
        licensePlateLayer.release()
        bloom.release()
        brdfLut.release()
        releaseReflection()
        releaseHdr()
        if (shFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(shFbo), 0)
        if (shTex != 0) GLES30.glDeleteTextures(1, intArrayOf(shTex), 0)
        if (shadowProgram != 0) GLES30.glDeleteProgram(shadowProgram)
        if (gridVbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(gridVbo), 0)
        shFbo = 0
        shTex = 0
        shadowProgram = 0
        gridVbo = 0
        shReady = false
        for (prog in officialPrograms.values) prog.release()
        officialPrograms.clear()
        officialFailed.clear()
        val blocks = intArrayOf(viewUbo, lightsUbo, physicalUbo, lightsNoDirUbo, materialUbo, shUbo)
        if (blocks.any { it != 0 }) GLES30.glDeleteBuffers(6, blocks, 0)
        viewUbo = 0
        lightsUbo = 0
        physicalUbo = 0
        lightsNoDirUbo = 0
        materialUbo = 0
        shUbo = 0
        materialBound = null
        lightsNoDirBound = false
        if (program != 0) GLES30.glDeleteProgram(program)
        if (gridProgram != 0) GLES30.glDeleteProgram(gridProgram)
        program = 0
        gridProgram = 0
    }
}
