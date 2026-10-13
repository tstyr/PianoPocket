package dev.pianopocket.download

import java.net.URI

data class DownloadPart(val name: String,val url: String,val bytes: Long,val sha256: String) {
    init {
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,120}")) && name !in setOf(".",".."))
        require(bytes in 1..50L*1024*1024 && sha256.matches(Regex("[a-f0-9]{64}")))
        allowedDownloadUrl(url)
    }
}
data class DownloadPack(val id: String,val name: String,val description: String,val license: String,val source: String,
    val bytes: Long,val sha256: String,val expandedBytes: Long,val requiredBytes: Long,val parts: List<DownloadPart>) {
    init {
        require(id.matches(Regex("[a-z0-9-]{1,80}")) && name.length in 1..100 && description.length<=500)
        require(bytes in 1..512L*1024*1024 && expandedBytes in 1..1024L*1024*1024 && requiredBytes>=bytes+expandedBytes)
        require(sha256.matches(Regex("[a-f0-9]{64}")) && parts.size in 1..32 && parts.sumOf {it.bytes}==bytes && parts.map {it.name}.distinct().size==parts.size)
    }
}
internal fun allowedDownloadUrl(value: String): URI {
    val uri=URI(value)
    require(uri.scheme=="https" && uri.host in setOf("raw.githubusercontent.com","github.com","objects.githubusercontent.com","release-assets.githubusercontent.com") && uri.userInfo==null && (uri.port==-1 || uri.port==443)) {"許可されていないダウンロード先です"}
    return uri
}
