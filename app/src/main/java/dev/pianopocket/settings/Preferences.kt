package dev.pianopocket.settings

import android.content.Context

class Preferences(context: Context) {
    private val store=context.getSharedPreferences("pianopocket",Context.MODE_PRIVATE)
    var targetFps: Int
        get()=store.getInt("targetFps",60).let { if(it in listOf(30,60,90,120))it else 60 }
        set(value){store.edit().putInt("targetFps",value).apply()}
    var showFps: Boolean
        get()=store.getBoolean("showFps",false)
        set(value){store.edit().putBoolean("showFps",value).apply()}
    var landscapePerformance: Boolean
        get()=store.getBoolean("landscapePerformance",true)
        set(value){store.edit().putBoolean("landscapePerformance",value).apply()}
    var lastInstrument: String
        get()=store.getString("lastInstrument","")!!
        set(value){store.edit().putString("lastInstrument",value).apply()}
    var voiceLimit: Int
        get(){
            if(!store.getBoolean("polyphonyV2",false))store.edit().putInt("voices",maxOf(512,store.getInt("voices",512))).putBoolean("polyphonyV2",true).apply()
            return store.getInt("voices",512)
        }
        set(value){store.edit().putInt("voices",value.coerceIn(32,1024)).apply()}
    var memoryMiB: Int
        get()=store.getInt("memory",256)
        set(value){store.edit().putInt("memory",value.coerceIn(32,512)).apply()}
    var bursts: Int
        get()=store.getInt("bursts",2)
        set(value){store.edit().putInt("bursts",value.coerceIn(1,8)).apply()}
    var lastMidi: String
        get()=store.getString("lastMidi","")!!
        set(value){store.edit().putString("lastMidi",value).apply()}
    var noteLabels: Boolean
        get()=store.getBoolean("noteLabels",true)
        set(value){store.edit().putBoolean("noteLabels",value).apply()}
    var rollVisible: Boolean
        get()=store.getBoolean("rollVisible",true)
        set(value){store.edit().putBoolean("rollVisible",value).apply()}
    var lookAhead: Float
        get()=store.getFloat("lookAhead",5f)
        set(value){store.edit().putFloat("lookAhead",value.coerceIn(2f,10f)).apply()}
}
