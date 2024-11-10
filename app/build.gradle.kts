import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.VariantOutput
import org.apache.tools.ant.taskdefs.condition.Os
import java.util.Locale
import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.application")
    kotlin("android")
}

val flavorRegex = "(assemble|generate)\\w*(Release|Debug)".toRegex()
val currentFlavor get() = gradle.startParameter.taskRequests.toString().let { task ->
    flavorRegex.find(task)?.groupValues?.get(2)?.toLowerCase(Locale.ROOT) ?: "debug".also {
        println("Warning: No match found for $task")
    }
}

android {
    fun getLocalProperty(key: String) = gradleLocalProperties(rootDir).getProperty(key)
    fun String?.toFile() = file(this!!)
    val environment: Map<String, String> = System.getenv()
    val javaVersion = JavaVersion.VERSION_11
    compileSdkVersion(30)
    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    ndkVersion = "27.0.12077973"
    kotlinOptions.jvmTarget = javaVersion.toString()
    defaultConfig {
        applicationId = "com.github.shadowsocks.plugin.v2ray"
        minSdkVersion(23)
        targetSdkVersion(30)
        versionCode = 5022200
        versionName = "5.22.2"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    val storefile = getLocalProperty("signing.storeFile") ?: environment["SIGNING_STORE_FILE"] ?: ""
    if (storefile != ""){
        signingConfigs {
            create("AppSigningConfig") {
                keyAlias = getLocalProperty("signing.keyAlias") ?: environment["SIGNING_KEY_ALIAS"] ?: error("?")
                storeFile = storefile.toFile()
                keyPassword = getLocalProperty("signing.keyPassword") ?: environment["SIGNING_KEY_PASSWORD"] ?: error("?")
                storePassword = getLocalProperty("signing.storePassword") ?: environment["SIGNING_STORE_PASSWORD"] ?: error("?")
                isV1SigningEnabled = true
                isV2SigningEnabled = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            if (storefile != "") {
                signingConfig = signingConfigs["AppSigningConfig"]
            }
        }
    }
    splits {
        abi {
            isEnable = true
            isUniversalApk = true
        }
    }
    sourceSets.getByName("main") {
        jniLibs.setSrcDirs(jniLibs.srcDirs + files("$projectDir/build/go"))
    }
}

tasks.register<Exec>("goBuild") {
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        println("Warning: Building on Windows is not supported")
    } else {
        executable("/bin/bash")
        args("go-build.bash", 23)
        environment("ANDROID_HOME", android.sdkDirectory)
        environment("ANDROID_NDK_HOME", android.ndkDirectory)
    }
}

tasks.whenTaskAdded {
    when (name) {
        "mergeDebugJniLibFolders", "mergeReleaseJniLibFolders" -> dependsOn("goBuild")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8", rootProject.extra.get("kotlinVersion").toString()))
    implementation("androidx.preference:preference:1.1.1")
    implementation("com.github.shadowsocks:plugin:2.0.1")
    implementation("com.takisoft.preferencex:preferencex-simplemenu:1.1.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
if (currentFlavor == "release") android.applicationVariants.all {
    for (output in outputs) {
        abiCodes[(output as ApkVariantOutputImpl).getFilter(VariantOutput.ABI)]?.let { offset ->
            output.versionCodeOverride = versionCode + offset
        }
    }
}
