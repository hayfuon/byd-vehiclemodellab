package com.haystudio.vehiclelab

internal data class OpenSourceItem(
    val name:String,
    val license:String,
    val source:String,
    val licenseAsset:String?="licenses/Apache-2.0.txt"
)

/** 运行、构建、测试与素材分别声明，避免将外部模型归入源码许可。 */
internal object OpenSourceCatalog {
    val runtime=listOf(
        OpenSourceItem("Kotlin 与 Kotlin Coroutines","Apache-2.0","https://github.com/JetBrains/kotlin"),
        OpenSourceItem("AndroidX 与 Jetpack Compose","Apache-2.0","https://android.googlesource.com/platform/frameworks/support/"),
        OpenSourceItem("Material Components 与 Material Icons","Apache-2.0","https://github.com/material-components/material-components-android"),
        OpenSourceItem("Gson 2.11.0","Apache-2.0","https://github.com/google/gson"),
        OpenSourceItem("Guava ListenableFuture","Apache-2.0","https://github.com/google/guava"),
        OpenSourceItem("Error Prone Annotations","Apache-2.0","https://github.com/google/error-prone")
    )
    val development=listOf(
        OpenSourceItem("Gradle 与 Android Gradle Plugin","Apache-2.0","https://github.com/gradle/gradle"),
        OpenSourceItem("JUnit 4.13.2","EPL-1.0","https://github.com/junit-team/junit4","licenses/EPL-1.0.txt"),
        OpenSourceItem("JSON-java 20240303","Public Domain","https://github.com/stleary/JSON-java/tree/20240303",null)
    )
    val environments=listOf(
        OpenSourceItem("Autumn Park","CC0-1.0","https://polyhaven.com/a/autumn_park",null),
        OpenSourceItem("Snowy Park 01","CC0-1.0","https://polyhaven.com/a/snowy_park_01",null),
        OpenSourceItem("Overcast Industrial Courtyard","CC0-1.0","https://polyhaven.com/a/overcast_industrial_courtyard",null)
    )
}
