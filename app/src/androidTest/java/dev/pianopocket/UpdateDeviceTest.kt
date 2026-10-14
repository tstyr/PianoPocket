package dev.pianopocket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pianopocket.update.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateDeviceTest {
    @Test fun manifestParserAndArchiveValidationRejectSameVersion(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val apk=File(context.applicationInfo.sourceDir)
        val info=UpdateClient.parse("""{"schema":1,"packageName":"dev.pianopocket","abi":"arm64-v8a","apk":"PianoPocket-${BuildConfig.VERSION_NAME.removeSuffix("-alpha")}-arm64.apk","versionCode":${BuildConfig.VERSION_CODE},"versionName":"${BuildConfig.VERSION_NAME}","minSdk":29,"bytes":${apk.length()},"sha256":"${UpdateTransfer.digest(apk)}","notes":"更新"}""")
        assertEquals(BuildConfig.VERSION_CODE,info.versionCode)
        try {UpdateClient.verifyApk(context,apk,info);fail("same-version APK must not be offered as an update")}catch(_:IllegalArgumentException){}
        try {UpdateClient.parse("""{"schema":1,"packageName":"other.app"}""");fail()}catch(_:Exception){}
        assertEquals("package:${context.packageName}",UpdateClient.permissionIntent(context).data.toString())
    }
}
