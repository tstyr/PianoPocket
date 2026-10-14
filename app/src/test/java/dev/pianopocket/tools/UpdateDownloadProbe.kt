package dev.pianopocket.tools

import dev.pianopocket.update.*
import java.io.File

/** Actual HTTPS transfer through the production updater. Android archive checks require a device. */
fun main(args: Array<String>){
    require(args.size==6){"versionCode versionName minSdk bytes sha256 outputDirectory"}
    val info=UpdateManifest(args[0].toInt(),args[1],args[2].toInt(),args[3].toLong(),args[4],"")
    val file=UpdateTransfer(UpdateHttpSource()).receive(info,File(args[5]))
    check(file.length()==info.bytes && UpdateTransfer.digest(file)==info.sha256)
    println("PASS production HTTPS APK transfer / ${file.length()} bytes / ${info.sha256}; Android archive checks not executed")
}
