plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "net.grandou.medocs"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.grandou.medocs"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
    }
    // La base BDPM est générée par scripts/build_bdpm.py (non versionnée) puis embarquée en assets.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/bdpm-assets"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val bdpmSqlite = rootProject.layout.projectDirectory.file("../data/build/bdpm.sqlite").asFile
val copierBdpm = tasks.register<Copy>("copierBdpm") {
    from(bdpmSqlite)
    into(layout.buildDirectory.dir("generated/bdpm-assets"))
    doFirst {
        if (!bdpmSqlite.exists()) {
            throw GradleException("Base BDPM absente : lancer d'abord `python3 -I scripts/build_bdpm.py --download` à la racine du dépôt.")
        }
    }
}
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(copierBdpm)
}

room {
    // Schémas exportés et versionnés : base des futures migrations Room.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // org.json du SDK est un stub non exécutable dans les tests JVM
    testImplementation("org.json:json:20250517")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
