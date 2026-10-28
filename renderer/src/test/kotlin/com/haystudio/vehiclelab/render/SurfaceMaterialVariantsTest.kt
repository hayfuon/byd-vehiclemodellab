package com.haystudio.vehiclelab.render

import com.haystudio.vehiclelab.data.DefaultMaterials
import org.junit.Assert.*
import org.junit.Test

class SurfaceMaterialVariantsTest {
    @Test fun plateChangesOnlyAlbedoAndKeepsTheExistingLightingAndShadowCode() {
        val source = "precision highp float; void main() { vec3 baseColor = _BaseCol; applyShadow(); applyLighting(baseColor); }"
        val spec = DefaultMaterials.OTHER.copy(fsPath = "car.es", fsSource = source)
        val variant = SurfaceMaterialVariants.plate(spec)
        assertTrue(variant.fsSource!!.contains("texture(uLabPlate, o.uv0).rgb"))
        assertTrue(variant.fsSource!!.contains("applyShadow(); applyLighting(baseColor);"))
        assertNotEquals(spec.fsPath, variant.fsPath)
    }

    @Test fun flowingMaskUsesOriginalMeshPositionsWithoutReplacingLampTextureOrLighting() {
        val spec = DefaultMaterials.OTHER.copy(vsPath = "v", fsPath = "f",
            vsSource = "precision highp float; void main () { drawOriginalVertex(); }",
            fsSource = "precision highp float; void main () { drawOriginalLampTextureAndLight(); }")
        val variant = SurfaceMaterialVariants.flowing(spec)
        assertTrue(variant.vsSource!!.contains("vLabFlowZ = a_position.z"))
        assertTrue(variant.vsSource!!.contains("drawOriginalVertex();"))
        assertTrue(variant.fsSource!!.contains("smoothstep("))
        assertTrue(variant.fsSource!!.contains("fwidth(flowingPosition)"))
        assertTrue(variant.fsSource!!.contains("drawOriginalLampTextureAndLight();"))
    }
    @Test fun glowExtractsEmissionAndRejectsViewDependentSurfaceHighlights() {
        val spec=DefaultMaterials.OTHER.copy(fsPath="lamp",fsSource="""
            void OutputFragColor(in vec4 outColor) { FragColor=outColor; }
            void main() { vec4 emissiveColor=readLampTexture();
                vec4 outColor=vec4(totalDiffuse+totalSpecular,opacity);
                outColor.rgb += emissiveColor.rgb; OutputFragColor(outColor); }
        """)
        val source=SurfaceMaterialVariants.lamp(spec).fsSource!!
        assertTrue(source.contains("labEmission=emissiveColor.rgb;"))
        assertTrue(source.contains("vec4(labEmission * outColor.a * uLabGlowMask"))
        assertFalse(source.contains("outColor.rgb * outColor.a * uLabGlowMask"))
    }

    @Test fun weatherGroundReceivesReflectionInsteadOfOnlyChangingTint() {
        val source = "precision highp float; void main() { // step2. calculate alpha\n outColor.rgb += emissiveColor.rgb; }"
        val spec = DefaultMaterials.OTHER.copy(fsPath = "ground.es", fsSource = source)
        val variant = SurfaceMaterialVariants.weatherGround(spec)
        assertTrue(variant.fsSource!!.contains("uLabGroundStyle"))
        assertTrue(variant.fsSource!!.contains("texture(uLabGroundReflection"))
        assertFalse(variant.fsSource!!.contains("styleTint"))
    }
    @Test fun readingLightDeclarationsPrecedeTheSharedFragmentOutputAndComposeWithAmbientLighting() {
        val spec=DefaultMaterials.OTHER.copy(vsPath="v",fsPath="f",
            vsSource="void main(){gl_Position=vec4(a_position,1.0);}",
            fsSource="void OutputFragColor(in vec4 outColor){FragColor=outColor;} void main(){outColor.rgb += emissiveColor.rgb;}")
        val courtesy=SurfaceMaterialVariants.courtesy(spec,"Interior_Chair")
        val source=courtesy.fsSource!!
        assertTrue(source.indexOf("vec3 labCourtesyEmission")<source.indexOf("void OutputFragColor"))
        val combined=SurfaceMaterialVariants.interior(courtesy,false,false,true).fsSource!!
        assertTrue(combined.contains("labCabinEmission+labCourtesyEmission"))
    }
}
