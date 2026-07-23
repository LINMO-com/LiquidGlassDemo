package com.liquidglass.demo;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class LiquidGlassRenderer implements GLSurfaceView.Renderer {

    private static final String VERTEX_SHADER =
        "attribute vec4 aPosition;\n" +
        "attribute vec2 aTexCoord;\n" +
        "varying vec2 vTexCoord;\n" +
        "void main() {\n" +
        "    vTexCoord = aTexCoord;\n" +
        "    gl_Position = aPosition;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "precision highp float;\n" +
        "varying vec2 vTexCoord;\n" +
        "uniform vec2 uResolution;\n" +
        "uniform float uTime;\n" +
        "uniform vec2 uOffset;\n" +
        "uniform float uAlpha;\n" +

        "float sdRoundedRect(vec2 p, vec2 halfSize, float r) {\n" +
        "    vec2 d = abs(p) - halfSize + r;\n" +
        "    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;\n" +
        "}\n" +

        "float noise2D(vec2 p) {\n" +
        "    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n" +
        "}\n" +

        "float smoothNoise(vec2 p) {\n" +
        "    vec2 i = floor(p);\n" +
        "    vec2 f = fract(p);\n" +
        "    f = f * f * (3.0 - 2.0 * f);\n" +
        "    return mix(\n" +
        "        mix(noise2D(i), noise2D(i + vec2(1.0, 0.0)), f.x),\n" +
        "        mix(noise2D(i + vec2(0.0, 1.0)), noise2D(i + vec2(1.0, 1.0)), f.x),\n" +
        "        f.y);\n" +
        "}\n" +

        "void main() {\n" +
        "    vec2 uv = vTexCoord;\n" +
        "    vec2 resolution = uResolution;\n" +
        "    float aspect = resolution.x / resolution.y;\n" +

        "    vec2 center = (uv - 0.5) * resolution;\n" +
        "    center.x -= uOffset.x;\n" +
        "    center.y += uOffset.y;\n" +

        "    float minDim = min(resolution.x, resolution.y);\n" +
        "    float glassW = minDim * 0.42;\n" +
        "    float glassH = minDim * 0.30;\n" +
        "    float cornerR = 40.0;\n" +

        "    float d = sdRoundedRect(center, vec2(glassW, glassH), cornerR);\n" +
        "    float edgeSoftness = 1.5;\n" +
        "    float mask = 1.0 - smoothstep(-edgeSoftness, edgeSoftness, d);\n" +

        "    // ---- Shadow ----\n" +
        "    float shadowD = sdRoundedRect(center + vec2(3.0, 6.0), vec2(glassW, glassH), cornerR);\n" +
        "    float shadow = 1.0 - smoothstep(-15.0, 20.0, shadowD);\n" +

        "    // ---- Base glass color ----\n" +
        "    vec3 base = vec3(0.03, 0.05, 0.10);\n" +

        "    // ---- Fresnel edge glow ----\n" +
        "    float edgeDist = abs(d);\n" +
        "    float fresnel = pow(1.0 - smoothstep(0.0, 28.0, edgeDist), 2.8) * 0.55;\n" +
        "    float fresnel2 = pow(1.0 - smoothstep(0.0, 60.0, edgeDist), 4.0) * 0.3;\n" +
        "    vec3 fresnelCol = fresnel * vec3(0.30, 0.35, 0.55);\n" +
        "    fresnelCol += fresnel2 * vec3(0.50, 0.55, 0.80);\n" +

        "    // ---- Top edge intense reflection ----\n" +
        "    float topEdge = smoothstep(glassH - 35.0, glassH - 8.0, center.y);\n" +
        "    topEdge *= smoothstep(-glassW + 30.0, -glassW + 80.0, -center.x);\n" +
        "    topEdge *= smoothstep(-glassW + 30.0, -glassW + 80.0, center.x);\n" +
        "    topEdge *= 0.35;\n" +
        "    vec3 topCol = topEdge * vec3(0.55, 0.62, 0.90);\n" +

        "    // ---- Bottom edge subtle glow ----\n" +
        "    float bottomEdge = smoothstep(-glassH + 20.0, -glassH + 5.0, -center.y);\n" +
        "    bottomEdge *= (1.0 - abs(center.x) / glassW) * 0.15;\n" +
        "    vec3 bottomCol = bottomEdge * vec3(0.20, 0.25, 0.40);\n" +

        "    // ---- Side edges ----\n" +
        "    float sideEdge = smoothstep(glassW - 25.0, glassW - 5.0, abs(center.x));\n" +
        "    sideEdge *= (1.0 - abs(center.y) / glassH) * 0.18;\n" +
        "    vec3 sideCol = sideEdge * vec3(0.22, 0.28, 0.45);\n" +

        "    // ---- Caustic highlights (flowing refractions) ----\n" +
        "    vec2 flowUV = center / minDim * 3.5;\n" +
        "    float t = uTime;\n" +
        "    float c1 = sin(flowUV.x * 7.5 + t * 1.3) * cos(flowUV.y * 5.5 - t * 1.1) * 0.5 + 0.5;\n" +
        "    float c2 = sin(flowUV.x * 4.2 - t * 0.7) * cos(flowUV.y * 6.8 + t * 0.9) * 0.5 + 0.5;\n" +
        "    float c3 = sin(flowUV.x * 9.1 + t * 1.6) * cos(flowUV.y * 3.3 + t * 1.4) * 0.5 + 0.5;\n" +
        "    float caustic = (c1 * 0.5 + c2 * 0.3 + c3 * 0.2);\n" +
        "    caustic = smoothstep(0.48, 0.58, caustic);\n" +
        "    caustic *= (1.0 - abs(center.x) / glassW * 0.5);\n" +
        "    vec3 causticCol = caustic * 0.09 * vec3(0.4, 0.5, 0.8);\n" +

        "    // ---- Specular highlight (moving) ----\n" +
        "    vec2 specPos = vec2(\n" +
        "        sin(t * 0.65) * glassW * 0.45,\n" +
        "        cos(t * 0.85 + 0.8) * glassH * 0.35\n" +
        "    );\n" +
        "    specPos.x += uOffset.x * 0.15;\n" +
        "    specPos.y -= uOffset.y * 0.15;\n" +
        "    float specDist = length(center - specPos);\n" +
        "    float spec1 = exp(-specDist * specDist / 1800.0) * 0.28;\n" +
        "    float spec2 = exp(-specDist * specDist / 8000.0) * 0.12;\n" +
        "    vec3 specCol = spec1 * vec3(0.55, 0.60, 0.82) + spec2 * vec3(0.35, 0.40, 0.60);\n" +

        "    // ---- Second specular reflection (top area) ----\n" +
        "    vec2 spec2Pos = vec2(\n" +
        "        cos(t * 0.55 + 1.2) * glassW * 0.25,\n" +
        "        sin(t * 0.75 + 0.5) * glassH * 0.20 + glassH * 0.30\n" +
        "    );\n" +
        "    float spec2Dist = length(center - spec2Pos);\n" +
        "    float spec3 = exp(-spec2Dist * spec2Dist / 900.0) * 0.20;\n" +
        "    vec3 spec2Col = spec3 * vec3(0.60, 0.65, 0.88);\n" +

        "    // ---- Micro-texture (glass material feel) ----\n" +
        "    vec2 texUV = center / minDim * 25.0;\n" +
        "    float microTex = smoothNoise(texUV);\n" +
        "    microTex = (microTex - 0.5) * 0.04;\n" +

        "    // ---- Refraction distortion lines ----\n" +
        "    vec2 refractUV = center / minDim * 6.0;\n" +
        "    float refract1 = sin(refractUV.x * 10.0 + refractUV.y * 3.0 + t * 0.6) * 0.02;\n" +
        "    float refract2 = sin(refractUV.y * 8.0 - refractUV.x * 4.0 + t * 0.8) * 0.02;\n" +
        "    float refractLines = (refract1 + refract2) * mask * 0.3;\n" +
        "    vec3 refractCol = refractLines * vec3(1.0, 1.0, 1.0);\n" +

        "    // ---- Chromatic dispersion at edges ----\n" +
        "    float chromaStrength = fresnel * 0.25;\n" +
        "    float chromaR = sdRoundedRect(center + vec2(1.5, 0.0), vec2(glassW, glassH), cornerR);\n" +
        "    float chromaB = sdRoundedRect(center + vec2(-1.5, 0.0), vec2(glassW, glassH), cornerR);\n" +
        "    float edgeR = 1.0 - smoothstep(-edgeSoftness, edgeSoftness, chromaR);\n" +
        "    float edgeB = 1.0 - smoothstep(-edgeSoftness, edgeSoftness, chromaB);\n" +
        "    vec3 chromaCol = vec3(\n" +
        "        (edgeR - mask) * 0.15 * chromaStrength,\n" +
        "        0.0,\n" +
        "        (edgeB - mask) * 0.15 * chromaStrength\n" +
        "    );\n" +

        "    // ---- Assemble ----\n" +
        "    vec3 color = base;\n" +
        "    color = mix(color, vec3(0.0), shadow * 0.12);\n" +
        "    color += microTex;\n" +
        "    color += fresnelCol;\n" +
        "    color += topCol;\n" +
        "    color += bottomCol;\n" +
        "    color += sideCol;\n" +
        "    color += causticCol;\n" +
        "    color += specCol;\n" +
        "    color += spec2Col;\n" +
        "    color += refractCol;\n" +
        "    color += chromaCol;\n" +

        "    float alpha = mask * uAlpha * 0.88;\n" +
        "    alpha = clamp(alpha, 0.0, 1.0);\n" +

        "    // ---- Subtle inner shadow for depth ----\n" +
        "    float innerShadow = 1.0 - smoothstep(-45.0, -10.0, d);\n" +
        "    color = mix(color, base * 0.6, innerShadow * 0.2);\n" +

        "    gl_FragColor = vec4(color, alpha);\n" +
        "}\n";

    private static final float[] QUAD_VERTICES = {
        -1f, -1f, 0f, 0f, 1f,
         1f, -1f, 0f, 1f, 1f,
        -1f,  1f, 0f, 0f, 0f,
         1f,  1f, 0f, 1f, 0f,
    };

    private static final int COORDS_PER_VERTEX = 3;
    private static final int TEXCOORDS_PER_VERTEX = 2;
    private static final int STRIDE = (COORDS_PER_VERTEX + TEXCOORDS_PER_VERTEX) * 4;

    private FloatBuffer vertexBuffer;
    private int program;
    private int aPosition;
    private int aTexCoord;
    private int uResolution;
    private int uTime;
    private int uOffset;
    private int uAlpha;

    private float time;
    private float offsetX, offsetY;
    private float alpha = 1.0f;
    private long startTime;
    private boolean entryAnimDone;

    private int screenWidth, screenHeight;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        ByteBuffer bb = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(QUAD_VERTICES);
        vertexBuffer.position(0);

        int vs = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("Shader link failed: " + log);
        }

        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord");
        uResolution = GLES20.glGetUniformLocation(program, "uResolution");
        uTime = GLES20.glGetUniformLocation(program, "uTime");
        uOffset = GLES20.glGetUniformLocation(program, "uOffset");
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha");

        startTime = System.nanoTime();
        entryAnimDone = false;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        screenWidth = width;
        screenHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glUseProgram(program);

        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aPosition, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, STRIDE, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aPosition);

        vertexBuffer.position(COORDS_PER_VERTEX);
        GLES20.glVertexAttribPointer(aTexCoord, TEXCOORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, STRIDE, vertexBuffer);
        GLES20.glEnableVertexAttribArray(aTexCoord);

        GLES20.glUniform2f(uResolution, screenWidth, screenHeight);
        GLES20.glUniform1f(uTime, time);
        GLES20.glUniform2f(uOffset, offsetX, offsetY);
        GLES20.glUniform1f(uAlpha, alpha);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aTexCoord);

        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void updateTime() {
        long now = System.nanoTime();
        time = (now - startTime) / 1_000_000_000f;

        if (!entryAnimDone && time < 0.6f) {
            float t = time / 0.6f;
            alpha = easeOutCubic(t);
        } else if (!entryAnimDone) {
            alpha = 1.0f;
            entryAnimDone = true;
        }
    }

    public void setOffset(float x, float y) {
        offsetX = -x;
        offsetY = -y;
    }

    private static float easeOutCubic(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return shader;
    }
}
