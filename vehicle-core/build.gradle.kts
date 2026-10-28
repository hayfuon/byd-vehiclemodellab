plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.haystudio.vehiclelab.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("vehicle-core"))
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

val licenseAssets=layout.buildDirectory.dir("generated/license-assets")
val prepareLicenseAssets by tasks.registering(Sync::class) {
    from(rootProject.file("LICENSE")) { into("licenses");rename { "Apache-2.0.txt" } }
    from(rootProject.file("NOTICE")) { into("licenses") }
    into(licenseAssets)
}
android.sourceSets.getByName("main").assets.srcDir(licenseAssets)
tasks.named("preBuild") { dependsOn(prepareLicenseAssets) }
