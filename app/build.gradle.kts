plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "dev.pianopocket"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "dev.pianopocket"
        minSdk = 29
        targetSdk = 36
        versionCode = 39
        versionName = "0.24.0-alpha"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { cppFlags += "-std=c++17"; arguments += "-DANDROID_STL=c++_shared" } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    externalNativeBuild { cmake { path = file("../native/CMakeLists.txt"); version = "3.22.1" } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}
tasks.register<JavaExec>("inspectOwnedPresets") {
    dependsOn("compileDebugUnitTestKotlin")
    classpath=files(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),layout.buildDirectory.dir("tmp/kotlin-classes/debug")) + configurations.getByName("debugUnitTestRuntimeClasspath").filter { it.extension=="jar" }
    mainClass.set("dev.pianopocket.tools.PresetProbeKt")
    doFirst { args(project.property("instrumentDir").toString(),project.property("reportDir").toString());project.findProperty("ownedArchive")?.let { args(it.toString()) } }
}
tasks.register<JavaExec>("verifyMovieMuxer") {
    dependsOn("compileDebugUnitTestKotlin")
    classpath=files(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),layout.buildDirectory.dir("tmp/kotlin-classes/debug")) + configurations.getByName("debugUnitTestRuntimeClasspath").filter { it.extension=="jar" }
    mainClass.set("dev.pianopocket.tools.VideoMuxerProbeKt")
    doFirst { args(project.property("packetFixture").toString(),project.property("movieOutput").toString()) }
}
tasks.register<JavaExec>("inspectSoundFont") {
    dependsOn("compileDebugUnitTestKotlin")
    classpath=files(layout.buildDirectory.dir("tmp/kotlin-classes/debugUnitTest"),layout.buildDirectory.dir("tmp/kotlin-classes/debug")) + configurations.getByName("debugUnitTestRuntimeClasspath").filter { it.extension=="jar" }
    mainClass.set("dev.pianopocket.tools.SoundFontProbeKt")
    doFirst { args(project.property("soundFontFile").toString(),project.property("soundFontOutput").toString());project.findProperty("sf3Decoder")?.let {args(it.toString())} }
}
