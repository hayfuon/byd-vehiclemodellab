package com.haystudio.vehiclelab

import com.haystudio.vehiclelab.data.EnvironmentPreset

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.haystudio.vehiclelab.data.CarColor
import com.haystudio.vehiclelab.data.DayPeriod
import com.haystudio.vehiclelab.data.ModelRepository
import com.haystudio.vehiclelab.data.ShaderSource
import com.haystudio.vehiclelab.data.ShaderLibrary
import com.haystudio.vehiclelab.data.VehicleResourceLibrary
import com.haystudio.vehiclelab.render.CarGLView
import com.haystudio.vehiclelab.render.CarRenderer
import com.haystudio.vehiclelab.data.VehicleLights
import java.util.concurrent.Executors
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlin.math.roundToInt
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {

    private lateinit var glView: CarGLView
    private lateinit var modelController: com.haystudio.vehiclelab.api.VehicleModelController
    private lateinit var renderer: CarRenderer
    private lateinit var repo: ModelRepository
    private lateinit var resourceLibrary: VehicleResourceLibrary
    private var repositoryPack: String? = null

    private lateinit var statusView: TextView
    private var palette: List<CarColor> = emptyList()
    private var selectedColor = 0
    private var pendingColor = -1
    private var selectedLights = VehicleLights()
    private val io = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var models: List<ModelRepository.ModelInfo> = emptyList()
    private var current: ModelRepository.Loaded? = null
    private var diagnostics: String = ""
    @Volatile private var generation = 0
    private val loadDeadline=ModelLoadDeadline()
    private var foreground=false
    private var loadFuture:java.util.concurrent.Future<*>?=null
    private lateinit var debugOverlayView: ComposeView
    private val debugHistory = DebugHistory()
    private var composeDebugSamples by mutableStateOf<List<com.haystudio.vehiclelab.render.RenderDebugSnapshot>>(emptyList())
    private var debugSamplePending = false
    private var composeEnvironment by mutableStateOf(EnvironmentPreset.DESKTOP)
    private var composeEnvironmentLoading by mutableStateOf(false)
    private var environmentRequest=0
    private var composePalette by mutableStateOf<List<CarColor>>(emptyList())
    private var composeSelectedColor by mutableIntStateOf(0)
    private var composeSelectedModelKey by mutableStateOf<String?>(null)
    private var composeModelLoading by mutableStateOf(true)
    private var composeResourceBusy by mutableStateOf(false)
    private var composeLights by mutableStateOf(VehicleLights())
    private var composeRevision by mutableIntStateOf(0)
    private var composeModelReady by mutableStateOf(false)
    private var composeLoadingStage by mutableStateOf("正在读取模型…")
    private var composeLoadError by mutableStateOf<String?>(null)
    private var composeFrameRateLimit by mutableIntStateOf(0)
    private val vehicleLabPreferences by lazy { getSharedPreferences("vehicle_model_lab", MODE_PRIVATE) }
    private var pendingExportModel: ModelRepository.ModelInfo? = null
    private val shaderImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            composeResourceBusy = true
            io.execute {
                val result = runCatching {
                    ShaderLibrary.install(
                        requireNotNull(contentResolver.openInputStream(uri)),
                        File(filesDir, "shader-library")
                    )
                }
                runOnUiThread {
                    composeResourceBusy = false
                    result.onSuccess {
                        if (!repo.selfContained) recreate()
                        else android.widget.Toast.makeText(this, "着色器库已保存；当前资源包使用内置着色器", android.widget.Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        android.widget.Toast.makeText(this, "着色器库导入失败：${it.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private val importResourceLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importResourcePack(uri)
    }

    private val exportResourceLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val selected = pendingExportModel
        pendingExportModel = null
        if (uri != null && selected != null) exportResourcePack(selected, uri)
    }

    private var dayPeriod = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 6..9 -> DayPeriod.MORNING
        in 10..17 -> DayPeriod.NOON
        else -> DayPeriod.NIGHT
    }

    private val ticker = object : Runnable {
        override fun run() {
            if(composeModelLoading && loadDeadline.expired(android.os.SystemClock.elapsedRealtime())) {
                val expired=generation
                failModelLoad(expired,"${composeLoadingStage.removeSuffix("…")}超时，请重试或重新导入资源包")
                generation++
                loadFuture?.cancel(true)
            }
            refreshStatus()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureSystemShaderLibrary()
        ShaderSource.init(File(filesDir, "shader-library"))
        renderer = CarRenderer()
        renderer.onBodyTargetsChanged={runOnUiThread {composeRevision++}}
        glView = CarGLView(this, renderer)
        modelController = com.haystudio.vehiclelab.api.VehicleModelController(renderer, java.util.concurrent.Executor { glView.queueEvent(it) }, java.util.concurrent.Executor { runOnUiThread(it) })
        composeFrameRateLimit=vehicleLabPreferences.getInt("frame_rate_limit",0).takeIf {it==0 || it==30 || it==60} ?: 0
        glView.setTargetFrameRate(composeFrameRateLimit)
        resourceLibrary = VehicleResourceLibrary(this)
        repo = resourceLibrary.repository("")

        val root = FrameLayout(this)
        root.addView(glView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        statusView = TextView(this)

        val loadStatus=ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {ModelLoadStatus(composeModelLoading,composeModelReady,composeLoadingStage,composeLoadError) { startLoad(null,true) }}
        }
        root.addView(loadStatus,FrameLayout.LayoutParams(-1,-1))

        val panelParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        panelParams.gravity = Gravity.START or Gravity.TOP
        panelParams.setMargins(dp(16), dp(16), dp(16), dp(16))
        val panel = buildPanel()
        root.addView(panel, panelParams)

        debugOverlayView=ComposeView(this).apply {
            visibility=View.GONE
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        root.addView(debugOverlayView,FrameLayout.LayoutParams(dp(264),ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity=Gravity.TOP or Gravity.END
            setMargins(0,dp(16),dp(16),0)
        })

        setContentView(root)

        setPanelContent(panel)
        debugOverlayView.setContent {
            DebugOverlay(composeDebugSamples,
                {renderer.debugInfoEnabled=false;composeRevision++})
        }
        applyPalette(emptyList())
        startLoad(null, redetect = false)
        handler.postDelayed(ticker, 500L)
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        foreground=true
        loadDeadline.resume(android.os.SystemClock.elapsedRealtime())
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        foreground=false
        loadDeadline.pause(android.os.SystemClock.elapsedRealtime())
        handler.removeCallbacks(ticker)
        glView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        generation++
        loadDeadline.finish()
        loadFuture?.cancel(true)
        renderer.onBodyTargetsChanged=null
        renderer.onModelInitialized=null
        handler.removeCallbacks(ticker)
        io.shutdownNow()
        repo.close()
        super.onDestroy()
    }

    private fun ensureSystemShaderLibrary() {
        val destination = File(filesDir, "shader-library")
        val required = listOf("vs_common.es", "fs_pbr.es", "fragmentOutput.es")
        if (required.all { File(destination, it).isFile }) return
        val source = File(ModelRepository.APK_MYCAR)
        if (!source.isFile) return
        val stage = File(filesDir, ".shader-system")
        runCatching {
            stage.deleteRecursively()
            check(stage.mkdirs())
            var total = 0L
            ZipFile(source).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("assets/Data/GFXResource/shaders/native/gles/") && it.name.endsWith(".es") }
                    .forEach { entry ->
                        val target = File(stage, entry.name.substringAfterLast('/'))
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        total += bytes.size
                        check(total <= 16L * 1024 * 1024)
                        target.writeBytes(bytes)
                    }
            }
            check(required.all { File(stage, it).isFile })
            destination.deleteRecursively()
            check(stage.renameTo(destination))
            android.util.Log.i("CarDemo", "已从车机系统资源恢复着色器库")
        }.onFailure {
            stage.deleteRecursively()
            android.util.Log.w("CarDemo", "系统着色器库恢复失败：${it.message}")
        }
    }

    private fun buildPanel(): View {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    private fun setPanelContent(panel: View) {
        (panel as? ComposeView)?.setContent {
            VehiclePanel(
                renderer = renderer,
                modelItems = models,
                selectedModelKey = composeSelectedModelKey,
                modelLoading = composeModelLoading,
                resourceBusy = composeResourceBusy,
                selectModel = { info -> startLoad(info, redetect = false) },
                importResourcePack = {
                    importResourceLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream")
                    )
                },
                exportCurrentModel = ::requestResourceExport,
                deleteImportedResource = ::deleteImportedResource,
                importShaderLibrary = { shaderImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                thumbnailLoader = resourceLibrary::thumbnailGeometry,
                palette = composePalette,
                selectedColor = composeSelectedColor,
                selectColor = ::selectColor,
                dayPeriod = dayPeriod,
                selectPeriod = ::selectPeriod,
                revision = composeRevision,
                modelReady = composeModelReady,
                modelLoadError = composeLoadError,
                retryModel = { startLoad(null,true) },
                onLightsChanged = ::selectLights,
                lights = composeLights,
                selectedEnvironment = composeEnvironment,
                selectEnvironment = ::selectEnvironment,
                environmentLoading = composeEnvironmentLoading,
                frameRateLimit = composeFrameRateLimit,
                onFrameRateLimitChanged = { rate ->
                    composeFrameRateLimit=rate
                    vehicleLabPreferences.edit().putInt("frame_rate_limit",rate).apply()
                    glView.setTargetFrameRate(rate)
                },

            )
        }
    }

    private fun selectEnvironment(environment:EnvironmentPreset) {
        val request=++environmentRequest
        composeEnvironmentLoading=true
        io.execute {
            try {
                val bytes=com.haystudio.vehiclelab.data.EnvironmentResources.read(assets,environment)
                runOnUiThread {
                    if(request!=environmentRequest)return@runOnUiThread
                    modelController.environment(bytes,environment.label)
                    composeEnvironment=environment
                    composeEnvironmentLoading=false
                    if(environment!=EnvironmentPreset.DESKTOP)selectPeriod(DayPeriod.NOON)
                }
            } catch(t:Throwable) {
                runOnUiThread {
                    if(request==environmentRequest) {
                        composeEnvironmentLoading=false
                        android.widget.Toast.makeText(this,"场景加载失败：${t.message}",android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun startLoad(info: ModelRepository.ModelInfo?, redetect: Boolean) {
        val gen = ++generation
        loadFuture?.cancel(true)
        loadDeadline.begin(android.os.SystemClock.elapsedRealtime(),foreground)
        composeModelReady = false
        composeModelLoading = true
        composeLoadError=null
        composeLoadingStage="正在读取车机模型…"
        composeRevision++
        statusView.text = "正在读取车机模型…"
        loadFuture=io.submit {
            try {
                if (models.isEmpty() || redetect) {
                    models = resourceLibrary.list()
                }
                if(redetect)repositoryPack=null
                if (info == null) switchRepository("")
                val chosen = info ?: repo.autoSelect(models.filter { it.resourcePack.isEmpty() })
                    ?: models.firstOrNull()
                    ?: throw IllegalStateException("未找到兼容的模型资源，请导入 .hcvpack 资源包")
                switchRepository(chosen.resourcePack)
                check(repo.selfContained || ShaderSource.isReady) { "缺少着色器库，请从模型菜单导入着色器库 ZIP 后重试" }
                if (redetect && chosen.resourcePack.isEmpty()) repo.refreshVehicle()
                repo.fascia = "DM"
                val camera = repo.desktopCamera()
                val viewOptions = repo.desktopViewOptions()
                val cameraPhysics = repo.cameraPhysics()
                val loaded = repo.load(chosen)
                val scene = repo.loadScene(dayPeriod)

                val colorIndex =
                    if (pendingColor in loaded.colors.indices) pendingColor else minOf(2, loaded.colors.lastIndex).coerceAtLeast(0)
                runOnUiThread {if(gen==generation) {
                    composeLoadingStage="正在初始化模型与材质…"
                    loadDeadline.begin(android.os.SystemClock.elapsedRealtime(),foreground)
                }}
                glView.queueEvent {
                    if(gen!=generation)return@queueEvent
                    renderer.onModelInitialized={model,error ->
                        runOnUiThread {
                            if(gen==generation && model===loaded.model) {
                                loadDeadline.finish()
                                composeModelReady=error==null
                                composeModelLoading=false
                                composeLoadError=error?.let {"模型初始化失败：${it.message ?: "渲染不可用"}"}
                                composeRevision++
                            }
                        }
                    }
                    runCatching {
                        renderer.setDesktopCamera(camera)
                        renderer.setCameraPhysics(cameraPhysics)
                        renderer.viewOptions = viewOptions
                        renderer.bloom.enabled = viewOptions.bloom
                        renderer.setPalette(loaded.palette, colorIndex)
                        renderer.setLamps(loaded.lamps)
                        renderer.setVehicleBeams(loaded.beams)
                        renderer.setVehicleLights(selectedLights)
                        renderer.setHiddenNodes(loaded.hidden)
                        renderer.setDayPeriod(dayPeriod)
                        renderer.setScene(scene)
                        renderer.setModel(loaded.model)
                    }.onFailure { error -> runOnUiThread { failModelLoad(gen,error.message ?: "渲染初始化失败") } }
                }
                runOnUiThread {
                    if (gen != generation) return@runOnUiThread
                    current = loaded
                    applyPalette(loaded.colors, colorIndex)
                    selectLights(selectedLights)
                    composeSelectedModelKey = chosen.key
                    composeRevision++
                    refreshStatus()
                }
            } catch (t: Throwable) {
                android.util.Log.e("CarDemo", "模型加载失败", t)
                runOnUiThread { failModelLoad(gen,t.message ?: "未找到可用模型") }
            }
        }
    }

    private fun failModelLoad(gen:Int,reason:String) {
        if(gen!=generation)return
        loadDeadline.finish()
        renderer.onModelInitialized=null
        composeModelReady=false
        composeModelLoading=false
        composeLoadError=reason
        statusView.text="加载失败：$reason"
        composeRevision++
    }

    private fun refreshStatus() {
        debugOverlayView.visibility = if(renderer.debugInfoEnabled)View.VISIBLE else View.GONE
        if (!renderer.debugInfoEnabled) {
            debugHistory.clear()
            composeDebugSamples = emptyList()
        } else if (!debugSamplePending) {
            debugSamplePending = true
            io.execute {
                val sample = runCatching { renderer.sampleDebugInfo() }.getOrNull()
                runOnUiThread {
                    debugSamplePending = false
                    if (renderer.debugInfoEnabled && !isFinishing && sample != null) {
                        debugHistory.append(sample)
                        composeDebugSamples = debugHistory.samples
                    }
                }
            }
        }
        val loaded = current ?: run {
            return
        }
        val source = if (loaded.info.fromMyCar) "BydMyCar" else "BydSR"
        statusView.text = buildString {
            append("识别车型：")
            append(repo.vehicleName.ifBlank { "未知" })
            append('\n')
            append("当前模型：")
            append(loaded.info.label)
            append('\n')
            append(source).append(" · ").append(loaded.summary)
            append('\n')
            append("色板：").append(loaded.colors.size)
                .append(" 色（桌面配置） · 灯组：")
                .append(loaded.lamps?.allNodes?.size ?: 0)
                .append(" 个节点")
            append('\n')
            append("贴图：").append(renderer.textureStats)
            append('\n')
            append("帧率：").append(renderer.fps.roundToInt()).append(" FPS")
        }
    }

    private fun switchRepository(pack: String) {
        if (repositoryPack == pack) return
        repo.close()
        repo = resourceLibrary.repository(pack)
        diagnostics = repo.open()
        repositoryPack = pack
        android.util.Log.i("CarDemo", "资源包[$pack]：$diagnostics")
    }

    private fun requestResourceExport() {
        val selected = current?.info ?: return
        pendingExportModel = selected
        val safeName = selected.label.replace(Regex("[\\/:*?\"<>|]"), "_").ifBlank { "车辆模型" }
        exportResourceLauncher.launch("$safeName.${com.haystudio.vehiclelab.data.VehicleResourcePack.EXTENSION}")
    }

    private fun exportResourcePack(selected: ModelRepository.ModelInfo, uri: Uri) {
        composeResourceBusy = true
        composeRevision++
        io.execute {
            val result = runCatching {
                val output = requireNotNull(contentResolver.openOutputStream(uri, "w")) {
                    "无法打开导出位置"
                }
                resourceLibrary.export(
                    selected = selected,
                    output = output,
                    work = File(cacheDir, "pack-export-${UUID.randomUUID()}")
                )
            }
            runOnUiThread {
                composeResourceBusy = false
                composeRevision++
                android.widget.Toast.makeText(
                    this,
                    result.fold(
                        onSuccess = { "已导出 ${selected.label} 完整资源包" },
                        onFailure = { "导出失败：${it.message}" }
                    ),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun importResourcePack(uri: Uri) {
        composeResourceBusy = true
        composeRevision++
        val displayName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty()
        io.execute {
            val result = runCatching {
                val input = requireNotNull(contentResolver.openInputStream(uri)) { "无法读取资源包" }
                resourceLibrary.import(input, displayName)
            }
            runOnUiThread {
                composeResourceBusy = false
                result.onSuccess { imported ->
                    models = (models + imported).distinctBy { it.key }
                    composeRevision++
                    android.widget.Toast.makeText(
                        this,
                        "已导入 ${imported.label}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    startLoad(imported, redetect = false)
                }.onFailure {
                    composeRevision++
                    android.widget.Toast.makeText(
                        this,
                        "导入失败：${it.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun deleteImportedResource(info: ModelRepository.ModelInfo) {
        if (info.resourcePack.isEmpty() || composeResourceBusy) return
        composeResourceBusy = true
        composeRevision++
        val selected = composeSelectedModelKey == info.key
        val fallback = models.firstOrNull { it.resourcePack.isEmpty() }
        io.execute {
            val result = runCatching {
                if (selected) {
                    requireNotNull(fallback) { "未找到可切换的车机模型" }
                    switchRepository("")
                }
                resourceLibrary.delete(info)
            }
            runOnUiThread {
                composeResourceBusy = false
                result.onSuccess {
                    models = models.filterNot { it.resourcePack == info.resourcePack }
                    composeRevision++
                    android.widget.Toast.makeText(
                        this,
                        "已删除 ${info.label}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    if (selected && fallback != null) startLoad(fallback, redetect = false)
                }.onFailure {
                    composeRevision++
                    android.widget.Toast.makeText(
                        this,
                        "删除失败：${it.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun applyPalette(colors: List<CarColor>, defaultIndex: Int = 0) {
        palette = colors
        composePalette = colors
        composeSelectedColor = defaultIndex.coerceIn(0, (colors.size - 1).coerceAtLeast(0))
        if (colors.isEmpty()) return
        val selected = if (pendingColor in colors.indices) pendingColor
        else defaultIndex.coerceIn(0, colors.lastIndex)
        selectColor(selected)
    }

    private fun selectColor(index: Int) {
        if (palette.isEmpty()) return
        selectedColor = index.coerceIn(0, palette.lastIndex)
        composeSelectedColor = selectedColor
        composeRevision++
        renderer.selectColor(selectedColor)
    }

    private fun selectLights(state: VehicleLights) {
        selectedLights = state
        composeLights = state
        composeRevision++
        glView.queueEvent { renderer.setVehicleLights(state) }
    }

    private var periodRequest=0
    private fun selectPeriod(period: DayPeriod) {
        dayPeriod = period
        composeRevision++
        val request=++periodRequest
        val modelGeneration=generation
        io.execute {
            val result=runCatching {repo.loadScene(period)}
            result.onSuccess {scene ->
                glView.queueEvent {
                    if(request==periodRequest && modelGeneration==generation)renderer.transitionDayPeriod(period,scene)
                }
            }.onFailure {error ->runOnUiThread {
                if(request==periodRequest)android.widget.Toast.makeText(this,"时段场景加载失败：${error.message}",android.widget.Toast.LENGTH_SHORT).show()
            }}
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

}
