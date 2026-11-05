import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val localPropFile = rootProject.file("local.properties")
    if (localPropFile.exists()) {
        localPropFile.inputStream().use { load(it) }
    }
}

// Configuration-cache safe branch detection via direct .git/HEAD inspection
val gitBranch = runCatching {
    val headFile = rootProject.file(".git/HEAD")
    if (headFile.exists()) {
        val line = headFile.readText().lines().firstOrNull()?.trim() ?: ""
        if (line.startsWith("ref: refs/heads/")) {
            line.removePrefix("ref: refs/heads/")
        } else {
            line
        }
    } else {
        ""
    }
}.getOrDefault("")

val betaProp = providers.gradleProperty("betaBuild")
val isBeta = if (betaProp.isPresent) {
    betaProp.get().toBoolean()
} else {
    gitBranch.equals("beta", ignoreCase = true)
}

val releaseKeystorePath: String? = System.getenv("KEYSTORE_PATH") ?: localProperties.getProperty("release.keystore.path")
val releaseKeystorePassword: String? = System.getenv("KEYSTORE_PASSWORD") ?: localProperties.getProperty("release.keystore.password")
val releaseKeyAlias: String? = System.getenv("KEY_ALIAS") ?: localProperties.getProperty("release.key.alias")
val releaseKeyPassword: String? = System.getenv("KEY_PASSWORD") ?: localProperties.getProperty("release.key.password")
val resolvedKeystoreFile = releaseKeystorePath?.let { path ->
    val f = File(path)
    if (f.isAbsolute) f else rootProject.file(path)
}

android {
    namespace = "app.overbay.orbit.android"
    compileSdk = 35

    defaultConfig {
        applicationId = if (isBeta) "app.overbay.orbit.beta" else "app.overbay.orbit"
        minSdk = 24
        targetSdk = 35
        versionCode = 70
        versionName = if (isBeta) "0.7.0-beta" else "0.7.0"
        manifestPlaceholders["appName"] = if (isBeta) "Orbit Beta" else "Orbit Money"
        manifestPlaceholders["appScheme"] = if (isBeta) "orbitmoneybeta" else "orbitmoney"
    }

    sourceSets {
        getByName("debug") {
            if (isBeta) {
                res.srcDirs("src/beta/res")
            }
        }
        getByName("release") {
            if (isBeta) {
                res.srcDirs("src/beta/res")
            }
        }
    }

    signingConfigs {
        create("release") {
            if (resolvedKeystoreFile != null && resolvedKeystoreFile.exists()) {
                storeFile = resolvedKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        debug {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
}

abstract class CopyDebugApkTask : DefaultTask() {
    @get:InputFile
    abstract val sourceApk: RegularFileProperty

    @get:Input
    abstract val betaBuild: Property<Boolean>

    @get:Internal
    abstract val projectRootDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val src = sourceApk.get().asFile
        if (src.exists()) {
            val root = projectRootDir.get().asFile
            val betaApk = File(root, "OrbitBeta-debug.apk")
            val standardApk = File(root, "OrbitMoney-debug.apk")
            if (betaBuild.get()) {
                src.copyTo(betaApk, overwrite = true)
                if (standardApk.exists()) standardApk.delete()
                println("Copied ${src.name} -> OrbitBeta-debug.apk")
            } else {
                src.copyTo(standardApk, overwrite = true)
                if (betaApk.exists()) betaApk.delete()
                println("Copied ${src.name} -> OrbitMoney-debug.apk")
            }
        }
    }
}

tasks.register<CopyDebugApkTask>("copyDebugApkToRoot") {
    sourceApk.set(layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk"))
    betaBuild.set(isBeta)
    projectRootDir.set(layout.projectDirectory.dir(".."))
}

afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("copyDebugApkToRoot")
}
