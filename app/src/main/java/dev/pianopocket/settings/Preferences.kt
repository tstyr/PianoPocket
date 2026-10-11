package dev.pianopocket.settings

import android.content.Context

class Preferences(context: Context) {
    private val store=context.getSharedPreferences("pianopocket",Context.MODE_PRIVATE)
    var lastInstrument: String
        get()=store.getString("lastInstrument","")!!
        set(value){store.edit().putString("lastInstrument",value).apply()}
    var voiceLimit: Int
        get()=store.getInt("voices",80)
        set(value){store.edit().putInt("voices",value.coerceIn(32,128)).apply()}
    var memoryMiB: Int
        get()=store.getInt("memory",256)
        set(value){store.edit().putInt("memory",value.coerceIn(32,512)).apply()}
    var bursts: Int
        get()=store.getInt("bursts",2)
        set(value){store.edit().putInt("bursts",value.coerceIn(1,8)).apply()}
}
