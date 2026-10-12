package dev.pianopocket.video

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Worker-owned EGL surface; no UI or realtime audio thread is involved. */
internal class EncoderSurface(surface: Surface): AutoCloseable {
    private var display=EGL14.EGL_NO_DISPLAY
    private var context=EGL14.EGL_NO_CONTEXT
    private var window=EGL14.EGL_NO_SURFACE
    private var program=0;private var texture=0;private var imageAllocated=false
    private val vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,1f,1f,-1f,1f,1f,-1f,1f,0f,0f,1f,1f,1f,0f));position(0)
    }
    init {
        try {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);check(display!=EGL14.EGL_NO_DISPLAY)
            val version=IntArray(2);check(EGL14.eglInitialize(display,version,0,version,1))
            val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
            check(EGL14.eglChooseConfig(display,intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,0x3142,1,EGL14.EGL_NONE),0,configs,0,1,count,0) && count[0]>0)
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0);check(context!=EGL14.EGL_NO_CONTEXT)
            window=EGL14.eglCreateWindowSurface(display,configs[0],surface,intArrayOf(EGL14.EGL_NONE),0);check(window!=EGL14.EGL_NO_SURFACE)
            check(EGL14.eglMakeCurrent(display,window,window,context))
            fun shader(type: Int,source: String): Int {
                val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);val ok=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0)
                if(ok[0]==0){val error=GLES20.glGetShaderInfoLog(id);GLES20.glDeleteShader(id);error(error)};return id
            }
            val vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; attribute vec2 t; varying vec2 uv; void main(){gl_Position=vec4(p,0.0,1.0);uv=t;}")
            var fs=0
            try {
                fs=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying vec2 uv; uniform sampler2D image; void main(){gl_FragColor=texture2D(image,uv);}")
                program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);val ok=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GLES20.glGetProgramInfoLog(program)}
            }finally{GLES20.glDeleteShader(vs);if(fs!=0)GLES20.glDeleteShader(fs)}
            val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);texture=ids[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE)
        }catch(e: Throwable){close();throw e}
    }
    fun draw(bitmap: Bitmap,timeNanos: Long) {
        GLES20.glViewport(0,0,bitmap.width,bitmap.height);GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture)
        if(imageAllocated)GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,bitmap)else{GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);imageAllocated=true}
        val p=GLES20.glGetAttribLocation(program,"p");val t=GLES20.glGetAttribLocation(program,"t")
        vertices.position(0);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(p)
        vertices.position(2);GLES20.glVertexAttribPointer(t,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(t)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        check(GLES20.glGetError()==GLES20.GL_NO_ERROR){"Video GL rendering failed"}
        check(EGLExt.eglPresentationTimeANDROID(display,window,timeNanos));check(EGL14.eglSwapBuffers(display,window)){"Video encoder surface failed"}
    }
    override fun close() {
        if(display!=EGL14.EGL_NO_DISPLAY){
            if(context!=EGL14.EGL_NO_CONTEXT && window!=EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display,window,window,context)) {
                if(texture!=0)GLES20.glDeleteTextures(1,intArrayOf(texture),0);if(program!=0)GLES20.glDeleteProgram(program)
            }
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window)
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context)
            EGL14.eglReleaseThread();EGL14.eglTerminate(display)
        }
        display=EGL14.EGL_NO_DISPLAY;context=EGL14.EGL_NO_CONTEXT;window=EGL14.EGL_NO_SURFACE;texture=0;program=0
    }
}
