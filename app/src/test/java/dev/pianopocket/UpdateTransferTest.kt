package dev.pianopocket

import dev.pianopocket.update.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.MessageDigest

class UpdateTransferTest {
    private val bytes=ByteArray(120000){(it*7).toByte()}
    private val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private val info=UpdateManifest(44,"0.28.0-alpha",29,bytes.size.toLong(),sha,"変更点")
    private fun home()=java.nio.file.Files.createTempDirectory("pocket-update-").toFile()
    private fun response(data: ByteArray,status: Int=200,start: Long?=null,total: Long?=bytes.size.toLong())=object: UpdateResponse {
        override val status=status;override val offset=start;override val total=total;override val input=data.inputStream();override fun close(){input.close()}
    }
    @Test fun manifestUsesOnlyPublisherUrlsAndChecksDeviceCompatibility(){
        assertTrue(info.available(43,36,listOf("arm64-v8a")));assertFalse(info.available(44,36,listOf("arm64-v8a")));assertFalse(info.available(43,28,listOf("arm64-v8a")));assertFalse(info.available(43,36,listOf("x86_64")))
        assertEquals("PianoPocket-0.28.0-arm64.apk",info.fileName);UpdateManifest.allowed(info.apkUrl);UpdateManifest.allowed(UpdateManifest.LATEST)
        for(url in listOf("http://raw.githubusercontent.com/tstyr/PianoPocket/release-artifacts/latest.json",info.apkUrl+"?x=1",info.apkUrl.replace("tstyr","someone"),info.apkUrl.replace("raw.githubusercontent.com","raw.githubusercontent.com.evil.test"),UpdateManifest.BASE+"/../file.apk")){
            try {UpdateManifest.allowed(url);fail(url)}catch(_:IllegalArgumentException){}
        }
        try {info.copy(bytes=UpdateManifest.MAX_APK_BYTES+1);fail()}catch(_:IllegalArgumentException){}
        try {info.copy(versionName="../../bad");fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun completeDownloadIsHashedAndReusedWithoutNetwork(){
        val home=home();var calls=0
        try {val transfer=UpdateTransfer {_,offset->calls++;assertEquals(0L,offset);response(bytes)}
            val file=transfer.receive(info,home);assertArrayEquals(bytes,file.readBytes());assertEquals(sha,UpdateTransfer.digest(file));transfer.receive(info,home);assertEquals(1,calls)
        }finally{home.deleteRecursively()}
    }
    @Test fun interruptedTransferResumesAtVerifiedRangeAndServerCanRestart(){
        for(restart in listOf(false,true)){
            val home=home();var calls=0
            try {val transfer=UpdateTransfer {_,offset->calls++;if(calls==1)response(bytes.copyOf(30000))else {
                assertEquals(30000L,offset);if(restart)response(bytes)else response(bytes.copyOfRange(30000,bytes.size),206,30000,bytes.size.toLong())
            }}
                try {transfer.receive(info,home);fail()}catch(_:IllegalArgumentException){}
                assertArrayEquals(bytes,transfer.receive(info,home).readBytes());assertEquals(2,calls)
            }finally{home.deleteRecursively()}
        }
    }
    @Test fun hashMismatchOversizeAndBadRangeNeverBecomeInstallable(){
        val home=home()
        try {val transfer=UpdateTransfer {_,_->response(ByteArray(bytes.size))}
            try {transfer.receive(info,home);fail()}catch(_:IllegalStateException){};assertFalse(File(home,info.fileName).exists());assertFalse(home.listFiles()!!.any {it.extension=="part"})
            try {UpdateTransfer {_,_->response(bytes+byteArrayOf(1),total=null)}.receive(info,home);fail()}catch(_:IllegalArgumentException){};assertFalse(File(home,info.fileName).exists())
            home.listFiles()!!.forEach {it.delete()};File(home,"${info.versionCode}-${sha.take(12)}.part").writeBytes(bytes.copyOf(1000))
            try {UpdateTransfer {_,_->response(bytes,206,0,bytes.size.toLong())}.receive(info,home);fail()}catch(_:IllegalArgumentException){};assertFalse(File(home,info.fileName).exists())
        }finally{home.deleteRecursively()}
    }
    @Test fun cancellationPreservesOnlyResumableData(){
        val home=home();var done=0L
        try {
            try {UpdateTransfer {_,_->response(bytes)}.receive(info,home,{done>0}){done=it};fail()}catch(_:InterruptedIOException){}
            assertFalse(File(home,info.fileName).exists());assertTrue(home.listFiles()!!.single().length() in 1 until bytes.size.toLong())
        }finally{home.deleteRecursively()}
    }
}
