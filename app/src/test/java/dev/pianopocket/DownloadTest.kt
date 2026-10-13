package dev.pianopocket

import dev.pianopocket.download.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest

class DownloadTest {
    private val data="piano pocket".toByteArray()
    private fun hash(bytes: ByteArray)=VerifiedDownloader.hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun pack(sha: String=hash(data))=DownloadPack("piano-v1","Piano","Test","CC0","https://github.com/tstyr/PianoPocket",data.size.toLong(),sha,100,1000,listOf(DownloadPart("piano.part01","https://raw.githubusercontent.com/tstyr/PianoPocket/test/piano.part01",data.size.toLong(),hash(data))))
    private fun response(bytes: ByteArray,status: Int=200,offset: Long?=null,total: Long?=null)=object:ChunkResponse {override val status=status;override val offset=offset;override val total=total;override val input=bytes.inputStream();override fun close(){input.close()}}
    private fun directory(test:(File)->Unit){val dir=Files.createTempDirectory("pocket-download-").toFile();try{test(dir)}finally{dir.deleteRecursively()}}
    @Test fun completeDownloadAndVerifiedCacheAvoidSecondTransfer()=directory {dir->
        var requests=0;val client=VerifiedDownloader {_,_->requests++;response(data)}
        val result=client.receive(pack(),dir,{false}){};assertArrayEquals(data,result.single().readBytes())
        client.receive(pack(),dir,{false}){};assertEquals(1,requests)
    }
    @Test fun resumesAtVerifiedContentRange()=directory {dir->
        File(dir,"piano.part01.pending").writeBytes(data.copyOfRange(0,3))
        val files=VerifiedDownloader {_,offset->assertEquals(3,offset);response(data.copyOfRange(3,data.size),206,3,data.size.toLong())}.receive(pack(),dir,{false}){}
        assertArrayEquals(data,files.single().readBytes())
    }
    @Test fun serverIgnoringRangeRestartsInsteadOfAppending()=directory {dir->
        File(dir,"piano.part01.pending").writeBytes(data.copyOfRange(0,3))
        val files=VerifiedDownloader {_,_->response(data)}.receive(pack(),dir,{false}){}
        assertArrayEquals(data,files.single().readBytes())
    }
    @Test fun cancellationKeepsPartialDataForRetry()=directory {dir->
        var canceled=false
        try{VerifiedDownloader {_,_->response(data)}.receive(pack(),dir,{canceled}){canceled=true};fail("cancel should stop before commit")}catch(_:InterruptedIOException){}
        assertFalse(File(dir,"piano.part01").exists());assertEquals(data.size.toLong(),File(dir,"piano.part01.pending").length())
        val result=VerifiedDownloader {_,_->error("no transfer needed")}.receive(pack(),dir,{false}){}
        assertArrayEquals(data,result.single().readBytes())
    }
    @Test fun corruptTransferRejectedBeforeImport()=directory {dir->
        try{VerifiedDownloader {_,_->response(ByteArray(data.size))}.receive(pack(),dir,{false}){};fail("hash mismatch")}catch(_:IllegalStateException){}
        assertFalse(File(dir,"piano.part01").exists());assertFalse(File(dir,"piano.part01.pending").exists())
    }
    @Test fun wholePackageHashIsAlsoChecked()=directory {dir->
        try{VerifiedDownloader {_,_->response(data)}.receive(pack("0".repeat(64)),dir,{false}){};fail("package hash mismatch")}catch(_:IllegalArgumentException){}
    }
    @Test fun wrongResumePositionRejected()=directory {dir->
        File(dir,"piano.part01.pending").writeBytes(data.copyOfRange(0,3))
        try{VerifiedDownloader {_,_->response(data,206,0,data.size.toLong())}.receive(pack(),dir,{false}){};fail("invalid range")}catch(_:IllegalArgumentException){}
        assertEquals(3,File(dir,"piano.part01.pending").length())
    }
    @Test(expected=IllegalArgumentException::class) fun traversalNameRejected(){DownloadPart("../file","https://github.com/tstyr/PianoPocket",1,"0".repeat(64))}
    @Test(expected=IllegalArgumentException::class) fun insecureUrlRejected(){DownloadPart("file","http://github.com/tstyr/PianoPocket",1,"0".repeat(64))}
}
