package dev.pianopocket.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import dev.pianopocket.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

data class UpdateStatus(val info: UpdateManifest?=null,val checking: Boolean=false,val running: Boolean=false,val bytes: Long=0,
    val phase: String="",val message: String="",val ready: Boolean=false)

object UpdateCoordinator {
    private val mutable=MutableStateFlow(UpdateStatus());val status=mutable.asStateFlow()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val restored=AtomicBoolean()
    val cancelled=AtomicBoolean()
    @Volatile private var network: UpdateHttpSource?=null
    fun home(context: Context)=File(context.filesDir,"updates").apply {mkdirs()}
    fun supported(info: UpdateManifest)=info.available(BuildConfig.VERSION_CODE,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.toList())
    private fun persist(context: Context,info: UpdateManifest){
        val j=JSONObject().put("schema",1).put("packageName",context.packageName).put("abi","arm64-v8a").put("apk",info.fileName)
            .put("versionCode",info.versionCode).put("versionName",info.versionName).put("minSdk",info.minSdk).put("bytes",info.bytes).put("sha256",info.sha256).put("notes",info.notes)
        val folder=home(context);val pending=File(folder,"info.pending");pending.outputStream().use {it.write(j.toString().toByteArray());it.fd.sync()};check(pending.renameTo(File(folder,"info.json")))
    }
    fun restore(context: Context){
        if(!restored.compareAndSet(false,true))return
        scope.launch {runCatching {
            val file=File(home(context),"info.json");if(!file.isFile)return@runCatching
            require(file.length()<=16384);val info=UpdateClient.parse(file.readText());if(!supported(info))return@runCatching
            val apk=File(home(context),info.fileName);val ready=apk.exists() && runCatching {UpdateClient.verifyApk(context,apk,info)}.isSuccess
            synchronized(this@UpdateCoordinator){if(!status.value.running && !status.value.checking && status.value.info==null)mutable.value=UpdateStatus(info=info,ready=ready,message=if(ready)"更新APKを取得済みです"else "更新の取得を再開できます")}
        }}
    }
    @Synchronized fun check(context: Context){
        if(status.value.checking || status.value.running)return
        mutable.value=status.value.copy(checking=true,message="最新版を確認中")
        scope.launch {try {
            val source=UpdateHttpSource();val info=UpdateClient.fetch(source)
            var ready=false
            if(supported(info)){persist(context,info);val apk=File(home(context),info.fileName);ready=apk.exists() && runCatching {UpdateClient.verifyApk(context,apk,info)}.isSuccess}
            synchronized(this@UpdateCoordinator){mutable.value=UpdateStatus(info=info,ready=ready,message=when {
                info.versionCode<=BuildConfig.VERSION_CODE->"最新版を使っています"
                !supported(info)->"この端末には対応していない更新です"
                ready->"更新APKを取得済みです"
                else->"新しいバージョンがあります"
            })}
        }catch(e: Exception){synchronized(this@UpdateCoordinator){mutable.value=status.value.copy(checking=false,message="更新を確認できません: ${e.message}")}}}
    }
    @Synchronized fun download(context: Context){
        val info=status.value.info ?: return;if(!supported(info) || status.value.running || status.value.checking)return
        cancelled.set(false);mutable.value=status.value.copy(running=true,bytes=0,phase="準備",message="APKを取得中",ready=false)
        try {ContextCompat.startForegroundService(context,Intent(context,UpdateService::class.java).setAction("download"))}
        catch(e: Exception){finish("ダウンロードを開始できません: ${e.message}",false)}
    }
    fun receive(context: Context): File {
        val info=checkNotNull(status.value.info);val folder=home(context)
        require(folder.usableSpace>info.bytes+32*1048576){"更新用の空き容量が不足しています"}
        val source=UpdateHttpSource();network=source
        try {
            var last=0L
            val file=UpdateTransfer(source).receive(info,folder,{cancelled.get()}){bytes->val now=System.nanoTime();if(now-last>250000000 || bytes==info.bytes){report(bytes,"ダウンロード");last=now}}
            report(info.bytes,"署名を検証中")
            try {UpdateClient.verifyApk(context,file,info){cancelled.get()}}catch(e: Exception){if(!cancelled.get())file.delete();throw e}
            check(!cancelled.get()){"キャンセルしました"}
            persist(context,info)
            folder.listFiles().orEmpty().filter {it.extension in listOf("apk","part") && it!=file}.forEach {it.delete()}
            return file
        }finally{if(network===source)network=null;source.abort()}
    }
    @Synchronized fun report(bytes: Long,phase: String){if(status.value.running)mutable.value=status.value.copy(bytes=bytes,phase=phase)}
    @Synchronized fun finish(message: String,ready: Boolean){mutable.value=status.value.copy(running=false,phase="",message=message,ready=ready)}
    fun cancel(){cancelled.set(true);scope.launch {network?.abort()}}
    fun install(context: Context){
        val info=status.value.info ?: return;if(!status.value.ready || status.value.checking || status.value.running)return
        mutable.value=status.value.copy(checking=true,message="APKを確認中")
        scope.launch {try {
            val file=File(home(context),info.fileName);UpdateClient.verifyApk(context,file,info)
            val intent=if(context.packageManager.canRequestPackageInstalls())UpdateClient.installerIntent(context,file)else UpdateClient.permissionIntent(context)
            withContext(Dispatchers.Main){context.startActivity(intent)}
            mutable.value=status.value.copy(checking=false,message=if(context.packageManager.canRequestPackageInstalls())"Androidの画面でインストールしてください"else "このアプリからのインストールを許可し、戻って「インストール」を押してください")
        }catch(e: Exception){mutable.value=status.value.copy(checking=false,ready=false,message="インストールを開始できません: ${e.message}")}}
    }
}
