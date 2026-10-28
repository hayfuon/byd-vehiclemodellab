package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.MaterialSpec

/** 自定义参数通过统一材质变体传入，调整色盘只更新 uniform，不重新编译着色器。 */
internal object PaintShader {
    private const val MAIN="void main() {"
    const val HELPERS="""
uniform float uLabPaintEnabled;
uniform vec3 uLabPaintA,uLabPaintB,uLabPaintEye;
uniform vec4 uLabPaintSurface,uLabPaintGradient;
float labPaintWeight(vec3 position) {
    float coordinate=dot(position,uLabPaintGradient.xyz);
    return smoothstep(uLabPaintGradient.w-uLabPaintSurface.w*.5,uLabPaintGradient.w+uLabPaintSurface.w*.5,coordinate);
}
vec3 labPaintColor(vec3 original,vec3 position,vec3 world,vec3 normal) {
    if(uLabPaintEnabled<.5)return original;
    vec3 base=mix(uLabPaintA,uLabPaintB,labPaintWeight(position));
    float facing=abs(dot(normalize(normal),normalize(uLabPaintEye-world)));
    float pearl=pow(1.0-facing,2.4)*uLabPaintSurface.z;
    vec3 edge=mix(base,vec3(.55,.68,.92),.32);
    return mix(base,edge,pearl*.6);
}
"""
    fun apply(spec:MaterialSpec):MaterialSpec {
        val source=spec.fsSource ?: return spec
        val vertex=spec.vsSource ?: return spec
        val engine=source.contains("vec3 baseColor = _BaseCol;")
        val generic=source.contains("float roughness = perceptualRoughnessToRoughness(perceptualRoughness);")
        if(!vertex.contains(MAIN) || !source.contains(MAIN) || !engine && !generic)return spec
        var fragment=source.replace(MAIN,"in highp vec3 vLabPaintPos,vLabPaintWorld,vLabPaintNormal;\n$HELPERS\n$MAIN")
        val color="labPaintColor(baseColor${if(generic)".rgb" else ""},vLabPaintPos,vLabPaintWorld,vLabPaintNormal)"
        fragment=if(engine)fragment.replace("vec3 baseColor = _BaseCol;","vec3 baseColor = _BaseCol;\nbaseColor=$color;")
            .replace("float var_Roughness = var_ORM.g * _Roughness;","float var_Roughness = var_ORM.g * _Roughness;\nif(uLabPaintEnabled>.5){var_Roughness=uLabPaintSurface.x;var_Metallic=uLabPaintSurface.y;}")
        else fragment.replace("float roughness = perceptualRoughnessToRoughness(perceptualRoughness);","if(uLabPaintEnabled>.5){baseColor.rgb=$color;perceptualRoughness=uLabPaintSurface.x;metallic=uLabPaintSurface.y;}\nfloat roughness = perceptualRoughnessToRoughness(perceptualRoughness);")
        return spec.copy(vsPath=spec.vsPath+"#自定义车漆",fsPath=spec.fsPath+"#自定义车漆",
            vsSource=vertex.replace(MAIN,"out highp vec3 vLabPaintPos,vLabPaintWorld,vLabPaintNormal;\n$MAIN\nvLabPaintPos=a_position;vLabPaintWorld=(u_model*vec4(a_position,1.0)).xyz;vLabPaintNormal=mat3(u_model)*a_normal;"),fsSource=fragment)
    }
}
