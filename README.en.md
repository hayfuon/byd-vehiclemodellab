# BYD VehicleModelLab

[简体中文](README.md) | **English**

Vehicle resource parsing and interactive rendering for BYD DiLink. Built with Kotlin, OpenGL ES 3.0 and Jetpack Compose Material 3, with reusable rendering libraries, portable resource packages and a demo application.

![Application running on a DiLink head unit](docs/images/vehiclelab-overview.jpg)

## Features

- GPAK, GWM, GWA, material, texture and scene parsing.
- Body animations, lighting, paint, driving, charging, weather and cabin airflow simulation.
- Interior and exterior cameras, touch gestures, inertial camera motion and rendering quality settings.
- HCVP package import, export and integrity verification.

## Compatibility

| Requirement | Supported / tested scope |
| --- | --- |
| Platform | Android 7.0 / API 24 or later |
| Graphics | OpenGL ES 3.0 or later |
| Head unit tested | 2025 Han L DM-p, DiLink |
| Phone tested | Redmi K30 Pro, standalone package import and rendering |

Other Android head units have not been tested. Resource formats and interaction support depend on the vehicle and system version. Rendering varies with the GPU, driver and renderer version. The application does not connect to vehicle control interfaces.

## Modules

```text
app/                    Demo application, Material 3 controls, diagnostics UI
vehicle-core/           Parsing, state types, package management, environments
renderer/               OpenGL rendering, animation, gestures, thumbnails, public API
gradle/wrapper/         Gradle Wrapper
.github/workflows/      Build and test workflow
```

Dependency direction: `app → renderer → vehicle-core`. Neither library depends on Compose or the demo application.

## Resource loading

### Discovery

1. Read GPAK indexes from compatible DiLink resource APKs.
2. Validate model declarations, meshes, materials and required scene configuration.
3. Match system vehicle properties to valid models; expose available models when no match exists.
4. Parse geometry, nodes, animation, textures, lighting and camera configuration for rendering.

Discovery runs in the application process without ADB, root or wireless debugging. When system resources are inaccessible, import `.hcvpack` through the Android document picker. Broad storage permission is not required.

### Shaders

Compatible head units can load shaders from readable system resources. External shader libraries are imported as ZIP files containing `.es` files at the root or in one `engine/` directory. Required files include `vs_common.es`, `fs_pbr.es`, `fragmentOutput.es` and their dependencies. Self-contained HCVP v2 packages include the required dependencies.

Vehicle models, head-unit APKs, original vehicle textures and external engine shaders are not distributed with the application. Bundled environment resources use CC0; see [environment attribution](vehicle-core/src/main/assets/environments/NOTICE.txt).

### HCVP format

New exports use HCVP v2, a ZIP container:

```text
manifest.json           Format version, UTC creation time, file sizes, SHA-256
model.json              Model and scene dependency graph
data/<SHA-256>          Content-addressed geometry, textures and expanded shaders
```

Resource references use package-local identifiers. System APKs, original resource paths and device identifiers are excluded. Imports verify the file inventory, sizes and SHA-256 checksums without depending on the source device. Legacy packages are converted to v2 when exported.

Packages store dependencies and default rendering configuration, excluding active animation, weather particles and GPU state. Exports retain author, copyright and license metadata while removing known local path and device identity fields. Format conversion does not change asset licensing.

## Build

- JDK 17
- Android SDK Platform 36
- Android Build Tools 35.0.0
- Gradle 8.11.1, provided by the Wrapper

```bash
export ANDROID_HOME=/path/to/Android/sdk
./gradlew :vehicle-core:testDebugUnitTest :renderer:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:publishRelease
```

Use `gradlew.bat` on Windows. AGP 8.9.2 currently reports a supported SDK range warning for `compileSdk 36`.

| Artifact | Path |
| --- | --- |
| Debug APK | `build/app/outputs/apk/debug/app-debug.apk` |
| Release APK | `build/release/BYD-VehicleModelLab-v0.1.0-universal-release.apk` |
| Core AAR | `build/vehicle-core/outputs/aar/vehicle-core-release.aar` |
| Renderer AAR | `build/renderer/outputs/aar/renderer-release.aar` |

Build libraries with `./gradlew :vehicle-core:assembleRelease :renderer:assembleRelease`.

Application ID: `com.haystudio.vehiclelab`. Configure release signing through environment variables:

| Variable | Purpose |
| --- | --- |
| `VEHICLELAB_KEYSTORE` | Keystore path |
| `VEHICLELAB_STORE_PASSWORD` | Keystore password |
| `VEHICLELAB_KEY_ALIAS` | Key alias |
| `VEHICLELAB_KEY_PASSWORD` | Key password |

Without signing configuration, the output uses the `-unsigned.apk` suffix and requires signing before installation.

## Integration

Source module integration:

```kotlin
// settings.gradle.kts
include(":vehicle-core", ":renderer")
project(":vehicle-core").projectDir = file("../BYD-VehicleModelLab/vehicle-core")
project(":renderer").projectDir = file("../BYD-VehicleModelLab/renderer")

// Application build.gradle.kts
implementation(project(":renderer"))
```

For resource management alone, depend on `:vehicle-core`. Raw AAR integration requires both AARs and Gson 2.11.0. The core AAR includes consumer shrinker rules for HCVP reflection serialization and license files in `assets/licenses/`.

### Rendering

```kotlin
import com.haystudio.vehiclelab.api.VehicleModelView

val view = VehicleModelView(context)
container.addView(view)
val api = view.controller
view.setFrameRateLimit(60)
```

Forward `onResume` and `onPause` to `view.resume()` and `view.pause()`. Compose hosts use `AndroidView`.

Commands execute on the GL queue; callbacks return to the main thread. Commands submitted while paused execute after rendering resumes. Resource parsing and file operations require serialized background execution.

### Models and packages

```kotlin
import com.haystudio.vehiclelab.data.*

val library = VehicleResourceLibrary(context)
val selected = library.list().firstOrNull()
if (selected != null) {
    library.repository(selected.resourcePack).use { repo ->
        repo.open()
        api.load(
            repo.load(selected), repo.desktopCamera(), repo.desktopViewOptions(),
            DayPeriod.NOON, repo.loadScene(DayPeriod.NOON)
        ) { result ->
            result.onFailure { error -> showError(error.message) }
        }
    }
    library.export(selected, outputStream, workDirectory)
}
val imported = library.import(inputStream, "Vehicle resources")
library.delete(imported)
```

The host supplies `showError`, streams and a cache directory. Initialize external system shaders with `ShaderSource.init(shaderDirectory)` before loading system resources. Self-contained HCVP v2 packages require no separate shader initialization.

The `load` callback reports GPU initialization. A newer load cancels a pending older callback. The library does not request permissions or display document pickers. System resources are read-only; only imported packages can be deleted.

### Interaction API

```kotlin
import com.haystudio.vehiclelab.api.*
import com.haystudio.vehiclelab.data.*

api.apply(VehicleInteractionPatch(
    interiorAmbientEnabled = true,
    interiorAmbientBrightness = .5f,
    vehicleSpeedKmh = 30f,
    steeringAngleDegrees = 90f
))
api.door(VehicleCorner.LEFT_FRONT, true)
api.body(VehicleBodyState(progress = mapOf("SunRoof_Clip" to .5f)))
api.camera(CameraPreset.REAR)
api.visuals(VehicleVisualState(weather = WeatherMode.SNOW))
api.charging(ChargingPort.SLOW, 7f, ChargingCommand.CONNECT)
```

| API | Responsibility |
| --- | --- |
| `VehicleInteractionPatch` | Incremental body, lighting and driving updates; omitted fields remain unchanged |
| `VehicleBodyState` | Individual doors, windows and node progress; 0 closed, 1 open, null clears the override |
| `VehicleVisualState` | Complete visual configuration; use snapshot `copy` for partial updates |
| `bodyColor` / `trimColor` / `paint` | Preset colors and regional materials |
| `scene` / `environment` | Time of day, scenes and environment lighting |
| `rasterizeThumbnail` | Thumbnail generation independent of Compose |
| `diagnostics` | On-demand telemetry without UI or scheduled sampling |
| `edit` | Renderer extension access on the GL queue |

`vehicleSpeedKmh` is signed km/h; negative values indicate reverse and override pedal input. `clearVehicleSpeed = true` restores pedal control. Node names depend on the selected model. Command completion does not indicate animation completion.

## License

Source code is licensed under [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for third-party dependencies and environment attribution. External models, textures, animations and shaders retain their respective licenses.

An independent project for learning and research, not affiliated with BYD.
