package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.MaterialSpec
import com.haystudio.vehiclelab.data.InteriorLighting

internal object SurfaceMaterialVariants {
    private val main = Regex("void\\s+main\\s*\\(\\s*\\)\\s*\\{")

    fun courtesy(spec:MaterialSpec,part:String):MaterialSpec {
        val source=requireNotNull(spec.fsSource)
        val marker="outColor.rgb += emissiveColor.rgb;"
        require(source.contains(marker)){"顶灯照明需要 PBR 表面接口"}
        val lens=part=="Interior_Light"
        val front=part=="Interior_Chair"
        val luggage=part=="Interior_Chair" || part=="Trunk_Inside"
        val helpers="""
            uniform vec4 uLabReading;
            uniform float uLabLuggage,uLabCourtesyEnvironment,uLabCourtesyGlowMask;
            in highp vec3 vLabCourtesyPos,vLabCourtesySurface;
            uniform mat4 uLabCourtesyFrameInverse;
            vec3 labCourtesyEmission=vec3(0.0);
            float labReadingLight(vec3 p,vec3 n,vec3 source,float strength,float range) {
                vec3 d=source-p;float r2=dot(d,d);
                float edge=1.0-smoothstep(range*.7,range,sqrt(r2));
                float down=smoothstep(.45,.82,d.y/max(sqrt(r2),.001));
                return strength*edge*down*max(dot(n,normalize(d)),0.0)/(.24+r2);
            }
        """.trimIndent()
        val body=buildString {
            append(marker).append("\nvec3 labP=vLabCourtesySurface;vec3 lensP=vLabCourtesyPos;vec3 labN=normalize(mat3(uLabCourtesyFrameInverse)*v_wnormal);float labWash=0.0;\n")
            append("""
                if(dot(uLabReading,vec4(1.0))>.0001 && labP.x<1.45 && labP.y<1.47) {
                    labWash+=labReadingLight(labP,labN,vec3(-.2105,1.357,.1625),uLabReading.x,1.2)*(1.0-smoothstep(.12,.36,labP.x));
                    labWash+=labReadingLight(labP,labN,vec3(-.2105,1.357,-.1625),uLabReading.y,1.2)*(1.0-smoothstep(.12,.36,labP.x));
                    labWash+=labReadingLight(labP,labN,vec3(.612,1.398,.48),uLabReading.z,1.15)*smoothstep(.28,.48,labP.x);
                    labWash+=labReadingLight(labP,labN,vec3(.612,1.398,-.48),uLabReading.w,1.15)*smoothstep(.28,.48,labP.x);
                }
            """.trimIndent())
            if(luggage)append("""
                if(uLabLuggage>.0001) {
                float trunkZone=smoothstep(1.35,1.55,labP.x);
                labWash+=trunkZone*labReadingLight(labP,labN,vec3(2.08,1.10,0.0),uLabLuggage,1.3)*3.8;
                }
            """.trimIndent())
            append("\noutColor.rgb+=(.12+.88*baseColor.rgb)*vec3(1.0,.92,.80)*labWash*.30*uLabCourtesyEnvironment;\n")
            if(front)append("""
                float frontMask=(1.0-smoothstep(.069,.074,abs(lensP.x+.2105)))*(1.0-smoothstep(.025,.030,abs(abs(lensP.z)-.1625)))*smoothstep(1.333,1.341,lensP.y)*(1.0-smoothstep(1.377,1.385,lensP.y));
                float frontGain=lensP.z>0.0 ? uLabReading.x : uLabReading.y;
                labCourtesyEmission+=vec3(1.0,.94,.84)*frontMask*frontGain*1.8;
            """.trimIndent())
            if(lens)append("labCourtesyEmission+=vec3(1.0,.94,.84)*(lensP.z>0.0?uLabReading.z:uLabReading.w)*smoothstep(.54,.56,lensP.x)*1.8;\n")
            append("labCourtesyEmission*=mix(.65,1.0,uLabCourtesyEnvironment);outColor.rgb+=labCourtesyEmission;\n")
        }
        val output=Regex("void\\s+OutputFragColor\\s*\\(in vec4 outColor[\\s\\S]*?\\)\\s*\\{")
        val fragment=source.replace(output){helpers+"\n"+it.value+"\nif(uLabCourtesyGlowMask>0.0){FragColor=vec4(labCourtesyEmission*outColor.a,1.0);return;}\n"}
        return spec.copy(vsPath=spec.vsPath+"#顶棚位置",fsPath=spec.fsPath+"#阅读与行李厢:$part",
            vsSource=requireNotNull(spec.vsSource).replace(main,"out highp vec3 vLabCourtesyPos,vLabCourtesySurface;\nuniform mat4 uLabCourtesyFrameInverse;\nvoid main(){vLabCourtesyPos=a_position;vLabCourtesySurface=(uLabCourtesyFrameInverse*u_model*vec4(a_position,1.0)).xyz;"),
            fsSource=fragment.replace(marker,body))
    }

    fun lamp(spec: MaterialSpec): MaterialSpec {
        val source = requireNotNull(spec.fsSource)
        val output = Regex("void\\s+OutputFragColor\\s*\\(in vec4 outColor[\\s\\S]*?\\)\\s*\\{")
        require(output.containsMatchIn(source)) { "灯具缺少片元输出接口" }
        return spec.copy(fsPath = spec.fsPath + "#灯具溢光", fsSource = source.replace(output) { match ->
            "uniform float uLabGlowMask;\nvec3 labEmission=vec3(0.0);\n" + match.value + """
                if (uLabGlowMask > 0.0) {
                    FragColor = vec4(labEmission * outColor.a * uLabGlowMask, 1.0);
                    return;
                }
            """.trimIndent()
        }.replace("outColor.rgb += emissiveColor.rgb;", "labEmission=emissiveColor.rgb;\noutColor.rgb += emissiveColor.rgb;")
            .replace("outColor.rgb = mix(outColor.rgb, emissiveColor.rgb, emissiveColor.a);",
                "labEmission=emissiveColor.rgb*emissiveColor.a;\noutColor.rgb = mix(outColor.rgb, emissiveColor.rgb, emissiveColor.a);"))
    }

    fun interior(spec: MaterialSpec, door:Boolean, dash:Boolean, feet:Boolean): MaterialSpec {
        val source = requireNotNull(spec.fsSource)
        val marker = "outColor.rgb += emissiveColor.rgb;"
        require(source.contains(marker)) { "座舱材质缺少 PBR 表面接口" }
        require(door||dash||feet) { "座舱照明变体至少需要一个表面" }
        val helpers = InteriorLighting.shaderFunctions(door,dash,feet)
        val output=Regex("void\\s+OutputFragColor\\s*\\(in vec4 outColor[\\s\\S]*?\\)\\s*\\{")
        val glowEmission=if(source.contains("vec3 labCourtesyEmission")) "labCabinEmission+labCourtesyEmission" else "labCabinEmission"
        val cabinSource=source.replace(output) { match ->
            "uniform float uLabCabinGlowMask;\nvec3 labCabinEmission=vec3(0.0);\n"+match.value+
                "\nif(uLabCabinGlowMask>0.0){FragColor=vec4(($glowEmission)*outColor.a,1.0);return;}\n"
        }
        val variantName=listOfNotNull(if(door)"门板" else null,if(dash)"仪表岛台" else null,if(feet)"脚窝" else null).joinToString("+")
        val body=buildString {
            append("outColor.rgb += emissiveColor.rgb;\n")
            append("if(uLabInterior>0.0001) {\nfloat lightMask=0.0;\nfloat reflectedMask=0.0;\n")
            if(door) append("""
                float door=labDoorTrim(v_texcoord0,vLabCabinPos);
                lightMask+=door;
                reflectedMask+=.32*labDoorField(v_texcoord0,vLabCabinPos,5.0);
            """.trimIndent()).append('\n')
            if(dash) append("""
                float dash=labDashTrim(v_texcoord0,vLabCabinPos);
                vec3 labN=normalize(vLabCabinNormal);
                float labSide=smoothstep(.5,.8,labN.z*sign(vLabCabinPos.z))*(1.0-smoothstep(.5,.85,abs(labN.y)));
                vec3 labView=normalize(uLabCabinEye-vLabCabinPos);
                float aperture=smoothstep(.35,.65,labView.z*sign(vLabCabinPos.z))*(1.0-smoothstep(.35,.65,labView.y));
                dash=max(dash,labIslandSide(vLabCabinPos)*labSide*aperture);
                lightMask+=dash;
                float islandWash=exp(-pow(labIslandDistance(vLabCabinPos)/.032,2.0))*labSide*
                    smoothstep(-.62,-.55,vLabCabinPos.x)*(1.0-smoothstep(.15,.21,vLabCabinPos.x));
                reflectedMask+=.25*labDashField(v_texcoord0,vLabCabinPos,4.0)+.30*islandWash;
            """.trimIndent()).append('\n')
            if(feet) append("reflectedMask+=.35*labFootwell(vLabCabinPos)*(.25+.75*max(normalize(v_wnormal).y,0.0));\n")
            append("""
                vec3 cabinColor=uLabCabinColor;
                vec3 reflected=(.06+.94*baseColor.rgb)*reflectedMask;
                labCabinEmission=uLabInterior*cabinColor*lightMask*.75;
                outColor.rgb+=uLabInterior*cabinColor*(1.15*lightMask+reflected);
            }
            """.trimIndent())
        }
        return spec.copy(fsPath=spec.fsPath+"#座舱照明-$variantName",
            vsPath=spec.vsPath+"#座舱表面位置",
            vsSource=requireNotNull(spec.vsSource).replace(main,"out highp vec3 vLabCabinPos,vLabCabinNormal;\nvoid main() {\nvLabCabinPos=a_position;vLabCabinNormal=a_normal;"),
            fsSource=cabinSource.replace(main,
                "in highp vec3 vLabCabinPos,vLabCabinNormal;\nuniform float uLabInterior, uLabInteriorDoor, uLabInteriorDash, uLabInteriorFeet;\nuniform vec3 uLabCabinColor;\nuniform vec3 uLabCabinEye;\n" + helpers + "\nvoid main() {")
                .replace(marker,body))
    }

    fun weatherGround(spec:MaterialSpec):MaterialSpec {
        val source=requireNotNull(spec.fsSource)
        val marker="// step2. calculate alpha"
        if(!source.contains(marker))return spec
        var result=source.replace(main,"uniform float uLabWetness,uLabEnvironment,uLabGroundStyle,uLabReflectionOn;\nuniform sampler2D uLabGroundReflection;\nuniform vec2 uLabReflectionScreen;\nuniform vec3 uLabGroundHaze;\nvoid main() {")
            .replace(marker,"""
                if(uLabGroundStyle > .5) {
                    iSurfaceInfo.roughness = uLabGroundStyle < 1.5 ? .72 : (uLabGroundStyle < 2.5 ? .86 : (uLabGroundStyle < 3.5 ? .62 : (uLabGroundStyle < 4.5 ? .44 : .82)));
                }
                iSurfaceInfo.roughness=mix(iSurfaceInfo.roughness,.16,uLabEnvironment);
                iSurfaceInfo.roughness=mix(iSurfaceInfo.roughness,.055,uLabWetness);
                iSurfaceInfo.perceptualRoughness=sqrt(iSurfaceInfo.roughness);
                iSurfaceInfo.prefitleredRoughness=sqrt(iSurfaceInfo.roughness);
                // step2. calculate alpha
            """.trimIndent())
        result=result.replace("outColor.rgb += emissiveColor.rgb;","""
            outColor.rgb += emissiveColor.rgb;
            if(uLabReflectionOn > .5) {
                vec2 reflectionUv=gl_FragCoord.xy/uLabReflectionScreen;
                float spread = uLabGroundStyle > .5 ? .004 : .0015;
                vec3 reflected=texture(uLabGroundReflection,reflectionUv).rgb*.4;
                reflected+=texture(uLabGroundReflection,reflectionUv+vec2(spread,0)).rgb*.15;
                reflected+=texture(uLabGroundReflection,reflectionUv-vec2(spread,0)).rgb*.15;
                reflected+=texture(uLabGroundReflection,reflectionUv+vec2(0,spread)).rgb*.15;
                reflected+=texture(uLabGroundReflection,reflectionUv-vec2(0,spread)).rgb*.15;
                float grazing=pow(1.0-max(dot(normalize(v_wnormal),shading_view),0.0),5.0);
                float surfaceGain = uLabGroundStyle < .5 ? .40 : (uLabGroundStyle < 2.5 ? .09 : .22);
                float reflectionWeight = mix(surfaceGain,.58,grazing);
                reflectionWeight=mix(reflectionWeight,.65,uLabWetness);
                outColor.rgb=mix(outColor.rgb,reflected,reflectionWeight);
            }
            if(uLabGroundStyle>.5) {
                float ambient=clamp(dot(uLabGroundHaze,vec3(.2126,.7152,.0722))*.55,.015,.34);
                outColor.rgb+=baseColor.rgb*ambient;
            }
            outColor.rgb*=1.0-.12*uLabWetness;
            if(uLabGroundStyle>.5) {
                float haze=smoothstep(18.0,100.0,length(eye-v_wpos));
                outColor.rgb=mix(outColor.rgb,uLabGroundHaze,haze);
            }
        """.trimIndent())
        result=result.replace("baseColor *= baseTemp;", "baseColor *= baseTemp;\nif(uLabGroundStyle>.5)baseColor.rgb=baseTemp.rgb;")
            .replace("vec2 baseUV = uv;", "vec2 baseUV = uv*(uLabGroundStyle>.5?3.0:1.0);")
            .replace("float metallic = propertyMetallic(u_metallic_Roughness.x);", "float metallic = uLabGroundStyle>.5 ? .02 : propertyMetallic(u_metallic_Roughness.x);")
            .replace("perturbNormal(s_texBump, v_wpos, tbn, uv, bumpscale)", "perturbNormal(s_texBump, v_wpos, tbn, uv*(uLabGroundStyle>.5?3.0:1.0), bumpscale)")
            .replace("EMISSIVE_COLOR *= emissiveTemp.rgb;", "EMISSIVE_COLOR *= emissiveTemp.rgb;\nif(uLabGroundStyle>.5)EMISSIVE_COLOR=baseColor.rgb*(uLabGroundStyle>1.5 && uLabGroundStyle<2.5?.035:.025);")
        return spec.copy(fsPath=spec.fsPath+"#天气地面",fsSource=result)
    }

    fun grille(spec: MaterialSpec): MaterialSpec {
        val helper = """
            float labGrilleRoughness(vec3 normal, vec3 viewDirection, float alpha) {
                vec3 reflected=reflect(-viewDirection,normal);
                vec3 dx=dFdx(reflected), dy=dFdy(reflected);
                float variance=min(.035,.25*(dot(dx,dx)+dot(dy,dy)));
                return sqrt(min(1.0,max(alpha*alpha,.007225)+variance));
            }
        """.trimIndent()
        var source=requireNotNull(spec.fsSource)
        if(spec.engineShader) {
            val marker="// step2. calculate alpha"
            require(source.contains(marker)) { "格栅缺少 PBR 粗糙度接口" }
            source=source.replace(marker,"""
                float labGrilleAlpha=labGrilleRoughness(shading_normal,shading_view,iSurfaceInfo.roughness);
                iSurfaceInfo.roughness=labGrilleAlpha;
                iSurfaceInfo.perceptualRoughness=sqrt(labGrilleAlpha);
                iSurfaceInfo.prefitleredRoughness=sqrt(labGrilleAlpha);
                // step2. calculate alpha
            """.trimIndent())
        } else {
            val marker="float roughness2 = roughness * roughness;"
            require(source.contains(marker)) { "格栅缺少车漆粗糙度接口" }
            source=source.replace(marker,"roughness=labGrilleRoughness(nDirWS,vDirWS,roughness);\n$marker")
                .replace("float clear_coat_roughness = min(Remapper(_CarCLParams.t, 0.65, CLRRange), 1.0);",
                    "float clear_coat_roughness = labGrilleRoughness(nDirWSCL,vDirWS,min(Remapper(_CarCLParams.t, 0.65, CLRRange), 1.0));")
        }
        return spec.copy(fsPath=spec.fsPath+"#格栅高光过滤",fsSource=source.replace(main,helper+"\nvoid main() {"))
    }

    fun movingGround(spec: MaterialSpec): MaterialSpec {
        val source=requireNotNull(spec.fsSource)
        val marker="vec2 uv = v_texcoord0 * uvScale;"
        require(source.contains(marker)) { "地面缺少原厂 UV 接口" }
        return spec.copy(fsPath=spec.fsPath+"#原厂地板运动",
            fsSource=source.replace(main,"uniform vec4 uLabGroundPose, uLabGroundAxes;\nuniform float uLabGroundPivot;\nvoid main() {")
                .replace(marker,"""
                    vec2 labP=v_wpos.xz-vec2(uLabGroundPivot,0.0);
                    vec2 labDelta=vec2(uLabGroundPose.x*labP.x+uLabGroundPose.y*labP.y,
                        -uLabGroundPose.y*labP.x+uLabGroundPose.x*labP.y)-labP;
                    vec2 labShift=vec2(dot(uLabGroundAxes.xy,labDelta),dot(uLabGroundAxes.zw,labDelta));
                    vec2 uv=v_texcoord0*uvScale+labShift*uvScale+uLabGroundPose.zw;
                """.trimIndent())
                .replace("u_UVTransformMatrixALPHA * vec3(uv, 1.0)","u_UVTransformMatrixALPHA * vec3(v_texcoord0*uvScale, 1.0)"))
    }

    fun plate(spec: MaterialSpec): MaterialSpec {
        val source = requireNotNull(spec.fsSource)
        require(source.contains("vec3 baseColor = _BaseCol;")) { "当前牌照底板材质不支持反照率替换" }
        return spec.copy(fsPath = spec.fsPath + "#实验牌照表面",
            fsOptions = (spec.fsOptions - setOf("USE_FLAKES_CL","SPEC_SWITCHING")) + "CAR_CL", carCl = true,
            fsSource = source.replace("outrgb += emitResult;", """outrgb += emitResult;
                    // 牌照灯从牌面上缘向下柔和衰减，受牌面反照率调制，黑字仍保留。
                    float plateFalloff = exp(-2.5 * o.uv0.y);
                    float platePool = .28 + .72 * exp(-pow((o.uv0.x-.5)*1.75, 4.0));
                    outrgb += albedo * uLabPlateLight * plateFalloff * platePool;
                """.trimIndent()).replace(main, "uniform sampler2D uLabPlate;\nuniform float uLabPlateLight;\nvoid main() {")
                .replace("vec3 baseColor = _BaseCol;", "vec3 baseColor = pow(texture(uLabPlate, o.uv0).rgb, vec3(2.2));")
                
                .replace("vec4 var_BaseMap = texture(_BaseMap, o.uv0);", "vec4 var_BaseMap = vec4(1.0);")
                .replace("vec3 var_ORM = texture(_ORM, o.uvBaseRM.pq).rgb;", "vec3 var_ORM = vec3(1.0);")
                .replace("texture(_RoughnessCLTex, o.uvNorRCL.pq).r", "1.0")
                
                .replace("envDiff += (ambientCL + var_EnvDiffMapCL) * kdEnvCL * albedoCL;", "")
                .replace("mix(F0, vec3(grazingTermCL), min(FEnvCL, float_0dot04))",
                    "vec3(.04 + .96 * pow(1.0-NdotVCL, 5.0))")
                .replace("dirDiff *= smoothstep(0.04, 0.95, _EnvSpecMapCLBrightness);", "")
                .replace("dirDiff = dirDiff * 0.8 + albedo * 0.2;", "")
                .replace("float roughness2 = roughness * roughness;", "roughness=mix(min(roughness,.11),roughness,smoothstep(.015,.10,dot(baseColor,vec3(.2126,.7152,.0722))));\nfloat roughness2 = roughness * roughness;")
                .replace("float F90 = 0.5 + 2.0 * roughness2 * VdotH * VdotH;", "float F90 = 1.0;"))
    }

    fun flowing(spec: MaterialSpec): MaterialSpec = spec.copy(
        vsPath = spec.vsPath + "#流水灯",
        fsPath = spec.fsPath + "#流水灯",
        vsSource = requireNotNull(spec.vsSource).replace(main,
            "centroid out highp float vLabFlowZ;\nvoid main() {\nvLabFlowZ = a_position.z;"),
        fsSource = requireNotNull(spec.fsSource).replace(main, """
            centroid in highp float vLabFlowZ;
            uniform vec3 uLabFlow;
            void main() {
                float flowingPosition = (abs(vLabFlowZ) - uLabFlow.y) / max(uLabFlow.z - uLabFlow.y, .0001);
                float labFlowAA = max(fwidth(flowingPosition), .002);
                float labCoverage = uLabFlow.x <= 0.0 ? 0.0 :
                    (uLabFlow.x >= 1.0 ? 1.0 : 1.0-smoothstep(uLabFlow.x-labFlowAA,uLabFlow.x+labFlowAA,flowingPosition));
        """.trimIndent())
            
            .replace("diffuseMatColor *= diffuseTemp;", "diffuseMatColor *= vec4(1.0);")
            .replace("emissiveColor *= emissiveTemp;", "emissiveColor *= vec4(1.0);")
            .replace("OutputFragColor(outColor);", "outColor.a *= labCoverage;\nOutputFragColor(outColor);")
    )
}
