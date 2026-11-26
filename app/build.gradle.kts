import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// Reads a secret from an environment variable (GitHub Actions), then local.properties, then gradle.properties
fun secret(env: String, prop: String): String =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop) ?: (project.findProperty(prop) as String?) ?: ""

android {
    namespace = "com.atlas.sonora"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    defaultConfig {
        applicationId = "com.atlas.sonora"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0.0"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${secret("SPOTIFY_CLIENT_ID", "spotifyClientId")}\"")
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", "\"${secret("SPOTIFY_CLIENT_SECRET", "spotifyClientSecret")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("GEMINI_API_KEY", "geminiApiKey")}\"")
        buildConfigField("String", "GEMINI_MODEL", "\"${secret("GEMINI_MODEL", "geminiModel").ifBlank { "gemini-2.5-flash" }}\"")
    }
    // Fixed debug key so the SHA-1 registered in the Spotify dashboard stays the same on every CI build
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    // Spotify App Remote SDK: download the .aar and put it in app/libs/ (see README)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
