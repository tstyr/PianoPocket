package dev.pianopocket.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.security.MessageDigest

class UpdateHttpSource: UpdateSource {
    @Volatile private var active: HttpURLConnection?=null
    fun abort(){active?.disconnect()}
    override fun open(url: String,offset: Long): UpdateResponse {
        val connection=UpdateManifest.allowed(url).toURL().openConnection() as HttpURLConnection
        active=connection;connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=10000
        connection.setRequestProperty("User-Agent","PianoPocket");connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("Cache-Control","no-cache")
        if(offset>0)connection.setRequestProperty("Range","bytes=$offset-")
        try {
            val status=connection.responseCode;require(status==200 || status==206){"GitHubに接続できません: HTTP $status"}
            val range=connection.getHeaderField("Content-Range")?.let {Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(it)}
            val input=connection.inputStream
            return object: UpdateResponse {
                override val status=status
                override val offset=range?.groupValues?.get(1)?.toLong()
                override val total=range?.groupValues?.get(3)?.toLong() ?: connection.contentLengthLong.takeIf {it>=0 && status==200}
                override val input=input
                override fun close(){try {input.close()}finally{connection.disconnect();if(active===connection)active=null}}
            }
        }catch(e: Throwable){connection.disconnect();if(active===connection)active=null;throw e}
    }
}

object UpdateClient {
    fun parse(text: String): UpdateManifest {
        require(text.toByteArray().size<=16384);val j=JSONObject(text)
        require(j.getInt("schema")==1 && j.getString("packageName")=="dev.pianopocket" && j.getString("abi")=="arm64-v8a") {"このアプリ用の更新情報ではありません"}
        return UpdateManifest(j.getInt("versionCode"),j.getString("versionName"),j.getInt("minSdk"),j.getLong("bytes"),j.getString("sha256"),j.getString("notes")).also {require(j.getString("apk")==it.fileName)}
    }
    fun fetch(source: UpdateSource): UpdateManifest = source.open(UpdateManifest.LATEST,0).use {response->
        require(response.status==200 && (response.total ?: 0)<=16384)
        val out=java.io.ByteArrayOutputStream();val block=ByteArray(4096)
        while(true){val n=response.input.read(block);if(n<0)break;require(out.size()+n<=16384){"更新情報が大きすぎます"};out.write(block,0,n)}
        parse(out.toString("UTF-8"))
    }
    private fun certificates(info: android.content.pm.PackageInfo): Set<String> {
        val signing=checkNotNull(info.signingInfo){"APKの署名を確認できません"}
        return signing.apkContentsSigners.map {s->MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}}.toSet()
    }
    fun verifyApk(context: Context,file: File,info: UpdateManifest,cancel:()->Boolean={false}) {
        require(file.length()==info.bytes && UpdateTransfer.digest(file,cancel)==info.sha256){"APKの検証に失敗しました"}
        val pm=context.packageManager
        val candidate=pm.getPackageArchiveInfo(file.path,PackageManager.GET_SIGNING_CERTIFICATES) ?: error("APKを読み取れません")
        val installed=pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        require(candidate.packageName==context.packageName && candidate.longVersionCode==info.versionCode.toLong() && candidate.versionName==info.versionName && candidate.longVersionCode>installed.longVersionCode){"APKのアプリ名またはバージョンが一致しません"}
        val actual=certificates(candidate);require(actual.isNotEmpty() && actual==certificates(installed)){"APKの署名が現在のアプリと一致しません。インストールを中止しました"}
        require(candidate.applicationInfo?.minSdkVersion==info.minSdk && info.minSdk<=android.os.Build.VERSION.SDK_INT)
    }
    fun installerIntent(context: Context,file: File): Intent {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.preview",file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    fun permissionIntent(context: Context)=Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
