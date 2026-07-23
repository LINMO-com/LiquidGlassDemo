package com.liquidglass.demo;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class LiquidGlassRenderer implements GLSurfaceView.Renderer {

    private static final String VERTEX_SHADER =
        "attribute vec4 aPosition;\n" +
        "attribute vec2 aTexCoord;\n" +
        "varying vec2 vUV;\n" +
        "void main() {\n" +
        "    vUV = aTexCoord;\n" +
        "    gl_Position = aPosition;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "precision highp float;\n" +
        "varying vec2 vUV;\n" +
        "uniform vec2 uRes;\n" +
        "uniform float uTime;\n" +
        "uniform vec2 uDrag;\n" +
        "uniform float uAlpha;\n" +
        "float hash(vec2 p) { return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }\n" +
        "float noise(vec2 p) { vec2 i=floor(p),f=fract(p); f=f*f*(3.-2.*f); return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y); }\n" +
        "float fbm(vec2 p) { float v=0.,a=.5; vec2 s=vec2(0); mat2 m=mat2(.8,.6,-.6,.8); for(int i=0;i<5;i++){v+=a*noise(p+s);s+=3.7;p=m*p*2.1;a*=.5;} return v; }\n" +
        "float sdRR(vec2 p,vec2 sz,float r){ vec2 d=abs(p)-sz+r; return length(max(d,0.))+min(max(d.x,d.y),0.)-r; }\n" +
        "vec3 sky(vec2 uv) { float h=uv.y; vec3 top=vec3(.02,.04,.18),mid=vec3(.12,.08,.22),low=vec3(.55,.25,.08),hor=vec3(.98,.65,.25); return h>.65?mix(mid,top,(h-.65)/.35):h>.52?mix(low,mid,(h-.52)/.13):mix(hor,low,h/.52); }\n" +
        "vec3 sun(vec2 uv) { vec2 p=vec2(.65,.58); float d=length(uv-p); return vec3(1.,.9,.5)*exp(-d*d/.002)*1.2 + vec3(1.,.6,.2)*exp(-d*d/.008)*.4 + vec3(.9,.35,.05)*exp(-d*d/.025)*.15; }\n" +
        "vec3 stars(vec2 uv) { float s=0.; for(int i=0;i<8;i++){ vec2 p=vec2(hash(vec2(float(i),0.)),hash(vec2(float(i),1.))); float d2=length(uv-p); s+=exp(-d2*d2/.00003)*hash(vec2(p.x,p.y+1.))*.5; } return vec3(1.,.9,.8)*s*step(uv.y,.62); }\n" +
        "vec3 clouds(vec2 uv,float t) { vec2 p=uv*4.; p.x+=t*.02; float c=fbm(p)*fbm(p+2.); c=smoothstep(.35,.55,c); return vec3(.95,.7,.35)*c*.25; }\n" +
        "float mt(vec2 uv,float h,float sc,float sh) { vec2 p=uv*sc; float n=fbm(p); float r=abs(noise(p*.7)-.5)*2.; float m=n*1.1+r*.4; m=smoothstep(.35,.35+sh,m); return (1.-m)*h; }\n" +
        "vec3 mountains(vec2 uv,float t) { uv.y-=.12; float m1=mt(uv,.18,2.5,.08),m2=mt(uv+vec2(.3,0),.13,1.8,.12),m3=mt(uv+vec2(-.2,0),.08,1.2,.15); float h=max(max(m1,m2),m3); float sn=smoothstep(.065,.09,h); vec3 c=vec3(.06,.06,.12); c=mix(c,vec3(.18,.16,.22),smoothstep(0.,.06,h)); c=mix(c,vec3(.8,.85,.95),sn); c=mix(c,vec3(.25,.22,.28),smoothstep(.03,.07,h)*(1.-sn)); return c*h; }\n" +
        "vec3 lake(vec2 uv,float t) { float w=smoothstep(.56,.565,uv.y); vec2 wp=uv; wp.y=.56-(wp.y-.56); wp.x+=sin(wp.y*30.+t*1.5)*.003+sin(wp.y*15.+t*2.)*.005; vec3 ref=vec3(0); ref+=mountains(wp,t)*.5+sky(wp)*.3+sun(wp)*.15; vec3 wc=mix(vec3(.02,.06,.15),vec3(.05,.12,.22),fbm(uv*6.+t*.1)); wc=mix(wc,ref,.45); float rip=sin(uv.x*40.+t*2.5)*sin(uv.y*30.-t*1.8)*.03+sin(uv.x*25.-t*3.2)*sin(uv.y*35.+t*2.2)*.02; return wc*w+rip*w; }\n" +
        "vec3 trees(vec2 uv,float t) { float z=smoothstep(.55,.56,uv.y)*smoothstep(.58,.57,uv.y); float tr=fbm(uv*15.)*z; tr=smoothstep(.45,.55,tr); return vec3(.04,.08,.03)*tr; }\n" +
        "vec3 background(vec2 uv) { float t=uTime; vec3 c=sky(uv); c+=stars(uv); c+=sun(uv); c+=clouds(uv,t); c+=mountains(uv,t); c+=trees(uv,t); c+=lake(uv,t); return clamp(c,0.,1.); }\n" +
        "vec2 refractUV(vec2 uv,vec2 center,float t) { vec2 d=uv-center; float dist=length(d); float wobble=sin(dist*12.-t*2.)*.006+cos(dist*18.+t*1.7)*.004+sin(dist*6.+t*3.1)*.005; float magnify=.06*smoothstep(.35,0.,dist); vec2 normal=vec2(sin(d.x*15.+d.y*8.+t*1.3)*.004,cos(d.y*12.+d.x*6.+t*1.5)*.004); return uv-d*magnify+normal+wobble*d/max(dist,.001); }\n" +
        "void main() {\n" +
        "    vec2 uv=vUV;\n" +
        "    float aspect=uRes.x/uRes.y;\n" +
        "    vec2 center=vec2(.5)+uDrag/uRes;\n" +
        "    float gW=.34,gH=.34/aspect,cr=.04;\n" +
        "    vec2 lp=uv-center;\n" +
        "    float d=sdRR(lp,vec2(gW,gH),cr);\n" +
        "    float mask=1.-smoothstep(0.,.003,d);\n" +
        "    vec2 rUV=refractUV(uv,center,uTime);\n" +
        "    rUV=clamp(rUV,0.,1.);\n" +
        "    vec3 bg=background(uv);\n" +
        "    vec3 refracted=background(rUV);\n" +
        "    vec3 gc=mix(bg,refracted,mask);\n" +
        "    gc=mix(gc,vec3(.04,.06,.13),.25*mask);\n" +
        "    gc=mix(bg,gc*1.08,mask);\n" +
        "    float ed=abs(d);\n" +
        "    float f1=pow(1.-smoothstep(0.,.035,ed),3.)*.6;\n" +
        "    float f2=pow(1.-smoothstep(0.,.07,ed),5.)*.25;\n" +
        "    gc+=f1*vec3(.55,.62,.95)*mask+f2*vec3(.75,.8,1.)*mask;\n" +
        "    float te=smoothstep(gH-.05,gH-.01,lp.y); te*=smoothstep(-gW+.04,-gW+.12,-lp.x); te*=smoothstep(-gW+.04,-gW+.12,lp.x);\n" +
        "    gc+=te*.55*vec3(.7,.78,.98)*mask;\n" +
        "    float be=smoothstep(-gH+.03,-gH+.005,-lp.y); be*=(1.-abs(lp.x)/gW);\n" +
        "    gc+=be*.2*vec3(.3,.35,.55)*mask;\n" +
        "    vec2 sp=vec2(sin(uTime*.55)*gW*.5,cos(uTime*.75+.6)*gH*.4)+uDrag/uRes*.15;\n" +
        "    float sd=length(lp-sp);\n" +
        "    gc+=exp(-sd*sd/.0008)*.35*vec3(.7,.75,.95)*mask+exp(-sd*sd/.004)*.12*vec3(.5,.55,.75)*mask;\n" +
        "    vec2 sp2=vec2(cos(uTime*.65+1.5)*gW*.3,sin(uTime*.85+.3)*gH*.25+gH*.25)+uDrag/uRes*.1;\n" +
        "    float sd2=length(lp-sp2);\n" +
        "    gc+=exp(-sd2*sd2/.0003)*.25*vec3(.8,.82,1.)*mask;\n" +
        "    vec2 cUV=lp/.08;\n" +
        "    float c1=sin(cUV.x*7.+uTime*1.2)*cos(cUV.y*5.-uTime*.9)*.5+.5;\n" +
        "    float c2=sin(cUV.x*4.5-uTime*.7)*cos(cUV.y*6.5+uTime*1.1)*.5+.5;\n" +
        "    float c3=sin(cUV.x*8.5+uTime*1.5)*cos(cUV.y*3.5+uTime*1.3)*.5+.5;\n" +
        "    float caustic=(c1*.5+c2*.3+c3*.2); caustic=smoothstep(.48,.56,caustic)*(1.-abs(lp.x)/gW*.4);\n" +
        "    gc+=caustic*.06*vec3(.6,.7,.95)*mask;\n" +
        "    float cs=f1*.3;\n" +
        "    float cR=1.-smoothstep(0.,.003,sdRR(lp+vec2(.0015,0),vec2(gW,gH),cr));\n" +
        "    float cB=1.-smoothstep(0.,.003,sdRR(lp+vec2(-.0015,0),vec2(gW,gH),cr));\n" +
        "    gc.r+=(cR-mask)*.08*cs; gc.b+=(cB-mask)*.08*cs;\n" +
        "    float rim=smoothstep(0.,.002,ed)-smoothstep(.002,.004,ed); gc+=rim*.3*vec3(.6,.65,.85)*mask;\n" +
        "    float iSh=1.-smoothstep(-.06,-.01,d); gc=mix(gc,gc*.7,iSh*.15*mask);\n" +
        "    float sD=1.-smoothstep(-.015,.025,sdRR(lp+vec2(.004,.008),vec2(gW,gH),cr));\n" +
        "    vec3 fin=mix(bg,gc*.85,mask); fin=mix(fin,vec3(0),sD*.2*(1.-mask));\n" +
        "    gl_FragColor=vec4(fin*uAlpha,1.);\n" +
        "}\n";

    private static final float[] QUAD = {
        -1, -1, 0,   0, 1,
         1, -1, 0,   1, 1,
        -1,  1, 0,   0, 0,
         1,  1, 0,   1, 0,
    };
    private static final int FLOAT_SIZE = 4;
    private static final int STRIDE = 5 * FLOAT_SIZE;

    private FloatBuffer vb;
    private int program;
    private int aPos, aUV;
    private int uRes, uTime, uDrag, uAlpha;

    private float time;
    private float dragX, dragY;
    private float alpha = 1f;
    private long startTime;
    private boolean entryDone;

    private int sw, sh;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0, 0, 0, 1);

        ByteBuffer bb = ByteBuffer.allocateDirect(QUAD.length * FLOAT_SIZE);
        bb.order(ByteOrder.nativeOrder());
        vb = bb.asFloatBuffer();
        vb.put(QUAD);
        vb.position(0);

        int vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("Link: " + GLES20.glGetProgramInfoLog(program));
        }

        aPos = GLES20.glGetAttribLocation(program, "aPosition");
        aUV = GLES20.glGetAttribLocation(program, "aTexCoord");
        uRes = GLES20.glGetUniformLocation(program, "uRes");
        uTime = GLES20.glGetUniformLocation(program, "uTime");
        uDrag = GLES20.glGetUniformLocation(program, "uDrag");
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha");

        startTime = System.nanoTime();
        entryDone = false;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        sw = w;
        sh = h;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glUseProgram(program);

        vb.position(0);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, vb);
        GLES20.glEnableVertexAttribArray(aPos);

        vb.position(3);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, STRIDE, vb);
        GLES20.glEnableVertexAttribArray(aUV);

        GLES20.glUniform2f(uRes, sw, sh);
        GLES20.glUniform1f(uTime, time);
        GLES20.glUniform2f(uDrag, dragX, dragY);
        GLES20.glUniform1f(uAlpha, alpha);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void updateTime() {
        long now = System.nanoTime();
        time = (now - startTime) / 1_000_000_000f;

        if (!entryDone && time < 0.6f) {
            float t = time / 0.6f;
            alpha = 1f - (1f - t) * (1f - t) * (1f - t);
        } else if (!entryDone) {
            alpha = 1f;
            entryDone = true;
        }
    }

    public void setDrag(float dx, float dy) {
        dragX = -dx;
        dragY = -dy;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            throw new RuntimeException("Shader: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }
}
