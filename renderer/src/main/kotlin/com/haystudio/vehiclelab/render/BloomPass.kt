package com.haystudio.vehiclelab.render

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BloomPass {

    var enabled = false
    var strength = 0.5f
    var threshold = 1.5f
    var radius = 1f

    private var programBright = 0
    private var programDown = 0
    private var programComposite = 0
    private var programPresent = 0
    private var quad = 0

    private var blackTex = 0
    private var brightTex = 0
    private var brightFbo = 0
    private val mipTex = IntArray(MIPS)
    private val mipFbo = IntArray(MIPS)
    private var bloomTex = 0
    private var bloomFbo = 0
    private var width = 0
    private var height = 0

    fun onContextCreated() {
        programBright = 0
        programDown = 0
        programComposite = 0
        programPresent = 0
        quad = 0
        blackTex = 0
        brightTex = 0
        brightFbo = 0
        mipTex.fill(0)
        mipFbo.fill(0)
        bloomTex = 0
        bloomFbo = 0
        width = 0
        height = 0
    }

    fun release() {
        releaseTargets()
        if (blackTex != 0) GLES30.glDeleteTextures(1, intArrayOf(blackTex), 0)
        if (quad != 0) GLES30.glDeleteBuffers(1, intArrayOf(quad), 0)
        for (id in intArrayOf(programBright, programDown, programComposite, programPresent)) {
            if (id != 0) GLES30.glDeleteProgram(id)
        }
        onContextCreated()
    }

    fun present(hdrTex: Int, w: Int, h: Int, toneMapping: Boolean = true, srgbFramebuffer: Boolean = false, antiAliasing: Boolean = false, exposure: Float = 1f) {
        if (hdrTex == 0 || w < 8 || h < 8) return
        ensure(w, h)
        val prevBlend = GLES30.glIsEnabled(GLES30.GL_BLEND)
        val prevDepth = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)

        if (enabled) {
            draw(programBright, brightFbo, w, h) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hdrTex)
                GLES30.glUniform2f(
                    GLES30.glGetUniformLocation(programBright, "u_luminosityThresholdSmoothWidth"),
                    threshold, SMOOTH
                )
            }
            var src = brightTex
            for (i in 0 until MIPS) {
                val mw = (w shr (i + 1)).coerceAtLeast(1)
                val mh = (h shr (i + 1)).coerceAtLeast(1)
                draw(programDown, mipFbo[i], mw, mh) {
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src)
                }
                src = mipTex[i]
            }
            draw(programComposite, bloomFbo, w, h) {
                for (i in 0 until MIPS) {
                    GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + i)
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mipTex[i])
                }
                GLES30.glUniform2f(
                    GLES30.glGetUniformLocation(programComposite, "u_bloomStrengthRadius"),
                    strength, radius
                )
                GLES30.glUniform3f(
                    GLES30.glGetUniformLocation(programComposite, "u_bloomTintColor"),
                    1f, 1f, 1f
                )
                GLES30.glUniform1f(
                    GLES30.glGetUniformLocation(programComposite, "u_bloomMipNumber"),
                    MIPS.toFloat()
                )
            }
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(programPresent)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hdrTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (enabled) bloomTex else blackTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programPresent, "s_hdr"), 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programPresent, "s_bloom"), 1)
        GLES30.glUniform1i(
            GLES30.glGetUniformLocation(programPresent, "u_enableToneMapping"),
            if (toneMapping) 1 else 0
        )
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programPresent, "u_srgbFramebuffer"), if (srgbFramebuffer) 1 else 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programPresent, "u_antialiasing"), if (antiAliasing) 1 else 0)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programPresent, "u_exposure"), exposure)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        if (prevBlend) GLES30.glEnable(GLES30.GL_BLEND) else GLES30.glDisable(GLES30.GL_BLEND)
        if (prevDepth) GLES30.glEnable(GLES30.GL_DEPTH_TEST) else GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glUseProgram(0)
    }

    private fun draw(program: Int, fbo: Int, w: Int, h: Int, bind: () -> Unit) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(program)
        bind()
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun ensure(w: Int, h: Int) {
        if (programBright == 0) {
            programBright = GlUtil.program(QUAD.trimIndent(), BRIGHT.trimIndent())
            programDown = GlUtil.program(QUAD.trimIndent(), DOWN.trimIndent())
            programComposite = GlUtil.program(QUAD.trimIndent(), COMPOSITE.trimIndent())
            programPresent = GlUtil.program(QUAD.trimIndent(), PRESENT.trimIndent())
            GLES30.glUseProgram(programBright)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programBright, "s_texColor"), 0)
            GLES30.glUseProgram(programDown)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programDown, "s_texColor"), 0)
            GLES30.glUseProgram(programComposite)
            for (i in 0 until MIPS) {
                GLES30.glUniform1i(
                    GLES30.glGetUniformLocation(programComposite, "s_bloomMip$i"), i
                )
            }
            GLES30.glUseProgram(0)
            val buf = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            buf.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            buf.position(0)
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            quad = ids[0]
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 8 * 4, buf, GLES30.GL_STATIC_DRAW)
        }
        if (w == width && h == height) return
        releaseTargets()
        width = w
        height = h
        if (blackTex == 0) {
            blackTex = tex(1, 1)
            // 关闭泛光时仍参与加法合成，必须写入零值，不能采样未初始化显存。
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, 1, 1,
                GLES30.GL_RGBA, GLES30.GL_FLOAT, ByteBuffer.allocateDirect(16))
        }
        val bf = fboTex(w, h)
        brightTex = bf[0]
        brightFbo = bf[1]
        for (i in 0 until MIPS) {
            val pair = fboTex((w shr (i + 1)).coerceAtLeast(1), (h shr (i + 1)).coerceAtLeast(1))
            mipTex[i] = pair[0]
            mipFbo[i] = pair[1]
        }
        val bloom = fboTex(w, h)
        bloomTex = bloom[0]
        bloomFbo = bloom[1]
    }

    private fun tex(w: Int, h: Int): Int {
        val id = IntArray(1)
        GLES30.glGenTextures(1, id, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0,
            GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null
        )
        return id[0]
    }

    private fun fboTex(w: Int, h: Int): IntArray {
        val color = tex(w, h)
        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, color, 0
        )
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return intArrayOf(color, fbo[0])
    }

    private fun releaseTargets() {
        if (brightTex != 0) {
            val texs = intArrayOf(brightTex, bloomTex) + mipTex
            GLES30.glDeleteTextures(texs.size, texs, 0)
            val fbos = intArrayOf(brightFbo, bloomFbo) + mipFbo
            GLES30.glDeleteFramebuffers(fbos.size, fbos, 0)
        }
        brightTex = 0
        brightFbo = 0
        bloomTex = 0
        bloomFbo = 0
    }

    private companion object {
        const val MIPS = 5

        const val SMOOTH = 1.0e-4f

        const val QUAD = """
            #version 300 es
            layout(location = 0) in vec2 aPos;
            out vec2 v_texcoord0;
            void main() {
                v_texcoord0 = aPos * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        const val BRIGHT = """
            #version 300 es
            precision highp float;
            uniform sampler2D s_texColor;
            uniform vec2 u_luminosityThresholdSmoothWidth;
            in vec2 v_texcoord0;
            out vec4 fragColor;
            float luminance(vec3 c) { return dot(c, vec3(0.22, 0.707, 0.071)); }
            void main() {
                vec4 outColor = texture(s_texColor, v_texcoord0);
                float v = luminance(outColor.xyz);
                float t = u_luminosityThresholdSmoothWidth.x;
                float alpha = smoothstep(t, t + u_luminosityThresholdSmoothWidth.y, v);
                fragColor = mix(vec4(0.0), outColor, alpha);
            }
        """

        const val DOWN = """
            #version 300 es
            precision highp float;
            uniform sampler2D s_texColor;
            in vec2 v_texcoord0;
            out vec4 fragColor;
            void main() {
                vec2 invSize = 1.0 / vec2(textureSize(s_texColor, 0));
                vec2 halfpixel = invSize * 0.5;
                vec4 sum = texture(s_texColor, v_texcoord0 + vec2(-halfpixel.x * 2.0, 0.0));
                sum += texture(s_texColor, v_texcoord0 + vec2(-halfpixel.x, halfpixel.y)) * 2.0;
                sum += texture(s_texColor, v_texcoord0 + vec2(0.0, halfpixel.y * 2.0));
                sum += texture(s_texColor, v_texcoord0 + vec2(halfpixel.x, halfpixel.y)) * 2.0;
                sum += texture(s_texColor, v_texcoord0 + vec2(halfpixel.x * 2.0, 0.0));
                sum += texture(s_texColor, v_texcoord0 + vec2(halfpixel.x, -halfpixel.y)) * 2.0;
                sum += texture(s_texColor, v_texcoord0 + vec2(0.0, -halfpixel.y * 2.0));
                sum += texture(s_texColor, v_texcoord0 + vec2(-halfpixel.x, -halfpixel.y)) * 2.0;
                fragColor = sum / 12.0;
            }
        """

        const val COMPOSITE = """
            #version 300 es
            precision highp float;
            uniform sampler2D s_bloomMip0;
            uniform sampler2D s_bloomMip1;
            uniform sampler2D s_bloomMip2;
            uniform sampler2D s_bloomMip3;
            uniform sampler2D s_bloomMip4;
            uniform vec2 u_bloomStrengthRadius;
            uniform vec3 u_bloomTintColor;
            uniform float u_bloomMipNumber;
            in vec2 v_texcoord0;
            out vec4 fragColor;
            float lerpBloomFactor(float factor) {
                float mirrorFactor = 1.2 - factor;
                return mix(factor, mirrorFactor, u_bloomStrengthRadius.y);
            }
            void main() {
                const float bloomFactors[5] = float[5](1.0, 0.8, 0.6, 0.4, 0.2);
                vec4 outColor = vec4(0.0);
                if (u_bloomMipNumber > 0.0)
                    outColor += lerpBloomFactor(bloomFactors[0]) * vec4(u_bloomTintColor, 1.0) * texture(s_bloomMip0, v_texcoord0);
                if (u_bloomMipNumber > 1.0)
                    outColor += lerpBloomFactor(bloomFactors[1]) * vec4(u_bloomTintColor, 1.0) * texture(s_bloomMip1, v_texcoord0);
                if (u_bloomMipNumber > 2.0)
                    outColor += lerpBloomFactor(bloomFactors[2]) * vec4(u_bloomTintColor, 1.0) * texture(s_bloomMip2, v_texcoord0);
                if (u_bloomMipNumber > 3.0)
                    outColor += lerpBloomFactor(bloomFactors[3]) * vec4(u_bloomTintColor, 1.0) * texture(s_bloomMip3, v_texcoord0);
                if (u_bloomMipNumber > 4.0)
                    outColor += lerpBloomFactor(bloomFactors[4]) * vec4(u_bloomTintColor, 1.0) * texture(s_bloomMip4, v_texcoord0);
                fragColor = u_bloomStrengthRadius.x * outColor;
            }
        """

        // 在色调映射后的显示亮度域检测边缘，避免 HDR 高光改变边缘判定或产生全局模糊。
        const val PRESENT = """
            #version 300 es
            precision highp float;
            uniform sampler2D s_hdr;
            uniform sampler2D s_bloom;
            uniform int u_enableToneMapping;
            uniform float u_exposure;
            uniform bool u_srgbFramebuffer,u_antialiasing;
            in vec2 v_texcoord0;
            out vec4 fragColor;
            vec3 displaySample(vec2 uv) {
                vec3 x = (texture(s_hdr, uv).rgb + texture(s_bloom, uv).rgb) * u_exposure;
                vec3 mapped = x;
                if (u_enableToneMapping != 0) {
                    mapped=(x*(2.51*x+.03))/(x*(2.43*x+.59)+.14);
                }
                mapped=clamp(mapped,0.0,1.0);
                return mix(12.92*mapped,1.055*pow(mapped,vec3(1.0/2.4))-.055,step(vec3(.0031308),mapped));
            }
            vec3 antialias(vec2 uv) {
                vec2 pixel=1.0/vec2(textureSize(s_hdr,0));
                vec3 center=displaySample(uv);
                vec3 nw=displaySample(uv+vec2(-1,-1)*pixel),ne=displaySample(uv+vec2(1,-1)*pixel);
                vec3 sw=displaySample(uv+vec2(-1,1)*pixel),se=displaySample(uv+vec2(1,1)*pixel);
                const vec3 luma=vec3(.299,.587,.114);
                float m=dot(center,luma),a=dot(nw,luma),b=dot(ne,luma),c=dot(sw,luma),d=dot(se,luma);
                float lo=min(m,min(min(a,b),min(c,d))),hi=max(m,max(max(a,b),max(c,d)));
                if(hi-lo<max(.03125,hi*.125))return center;
                vec2 direction=vec2(-((a+b)-(c+d)),(a+c)-(b+d));
                float reduction=max((a+b+c+d)*.03125,.0078125);
                direction=clamp(direction/(min(abs(direction.x),abs(direction.y))+reduction),vec2(-8),vec2(8))*pixel;
                vec3 first=.5*(displaySample(uv+direction*(-1.0/6.0))+displaySample(uv+direction*(1.0/6.0)));
                vec3 second=first*.5+.25*(displaySample(uv-direction*.5)+displaySample(uv+direction*.5));
                float lum=dot(second,luma);
                return lum<lo||lum>hi?first:second;
            }
            void main() {
                vec3 mapped=u_antialiasing?antialias(v_texcoord0):displaySample(v_texcoord0);
                if(u_srgbFramebuffer) {
                    mapped=mix(mapped/12.92,pow((mapped+.055)/1.055,vec3(2.4)),step(vec3(.04045),mapped));
                }
                fragColor=vec4(mapped,1.0);
            }
        """
    }
}
