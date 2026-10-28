plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.haystudio.vehiclelab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.haystudio.vehiclelab"
        minSdk = 24
        targetSdk = 33
        versionCode = 100
        versionName = "0.1.0"
    }

    val signingValues = listOf("VEHICLELAB_KEYSTORE", "VEHICLELAB_STORE_PASSWORD", "VEHICLELAB_KEY_ALIAS", "VEHICLELAB_KEY_PASSWORD")
        .map { providers.environmentVariable(it).orNull }
    signingConfigs {
        if (signingValues.all { !it.isNullOrBlank() }) {
            create("publication") {
                storeFile = file(signingValues[0]!!)
                storePassword = signingValues[1]
                keyAlias = signingValues[2]
                keyPassword = signingValues[3]
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("publication")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }
}

tasks.register<Copy>("publishRelease") {
    dependsOn("assembleRelease")
    val publicationVersion = android.defaultConfig.versionName
    val release = layout.buildDirectory.dir("outputs/apk/release")
    from(release) { include("*.apk") }
    from(rootProject.file("LICENSE"))
    from(rootProject.file("NOTICE"))
    into(rootProject.layout.buildDirectory.dir("release"))
    rename { name ->
        if (!name.endsWith(".apk")) name
        else if (name.contains("unsigned")) "BYD-VehicleModelLab-v${publicationVersion}-universal-release-unsigned.apk"
        else "BYD-VehicleModelLab-v${publicationVersion}-universal-release.apk"
    }
}

layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("app"))

dependencies {
    implementation(project(":renderer"))
    implementation("com.google.android.material:material:1.12.0")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
