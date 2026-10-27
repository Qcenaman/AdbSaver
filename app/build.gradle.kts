plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.qcenaman.adbsaver"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.qcenaman.adbsaver"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
    packaging { resources { excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES") } }
}

dependencies {
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcpkix-jdk15to18:1.81")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}

// Builds the real TokenSaver (Go) for the phone and ships it as a native lib so it lands executable in nativeLibraryDir.
val tokenSaverDir = rootProject.file((findProperty("tokensaver.dir") as String?) ?: "../TokenSaver")
val goExe = (findProperty("go.exe") as String?) ?: "go"
val tokenSaverOut = file("src/main/jniLibs/arm64-v8a/libtokensaver.so")
val buildTokenSaver by tasks.registering(Exec::class) {
    inputs.dir(tokenSaverDir).withPropertyName("src")
    outputs.file(tokenSaverOut)
    workingDir = tokenSaverDir
    environment("CGO_ENABLED", "0"); environment("GOOS", "android"); environment("GOARCH", "arm64")
    commandLine(goExe, "build", "-trimpath", "-ldflags=-s -w", "-o", tokenSaverOut.absolutePath, ".")
    doFirst { tokenSaverOut.parentFile.mkdirs() }
}
tasks.named("preBuild") { dependsOn(buildTokenSaver) }
