package dev.pianopocket.download

import android.content.Context
import org.json.JSONObject

object DownloadCatalog {
    fun load(context: Context): DownloadPack {
        val json=context.assets.open("download-catalog.json").bufferedReader().use {JSONObject(it.readText())}
        val parts=json.getJSONArray("parts")
        return DownloadPack(json.getString("id"),json.getString("name"),json.getString("description"),json.getString("license"),json.getString("source"),json.getLong("bytes"),json.getString("sha256"),json.getLong("expandedBytes"),json.getLong("requiredBytes"),
            (0 until parts.length()).map {i->parts.getJSONObject(i).let {DownloadPart(it.getString("name"),it.getString("url"),it.getLong("bytes"),it.getString("sha256"))}})
    }
}
