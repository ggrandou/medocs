plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

// Short SHA-1 of the commit being built ("-dirty" if the working tree has changes); "unknown" outside a git checkout.
fun git(vararg args: String): String? = try {
    providers.exec { commandLine("git", *args); isIgnoreExitValue = true }.standardOutput.asText.get().trim().ifEmpty { null }
} catch (e: Exception) { null }
val gitSha: String = git("rev-parse", "--short=7", "HEAD")?.let { it + if (git("status", "--porcelain") != null) "-dirty" else "" } ?: "unknown"

android {
    namespace = "net.grandou.rappels"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.grandou.rappels"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

room {
    // Exported, versioned schemas: basis of future Room migrations.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Pinned BOM: recent Compose versions require platform 37 (not installed)
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    // The SDK's org.json is a non-executable stub in local JVM tests
    testImplementation("org.json:json:20240303")
}
