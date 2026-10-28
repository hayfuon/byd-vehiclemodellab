package com.haystudio.vehiclelab.data

import android.util.Log
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.IOException

/** 从只读车机资源或独立资源目录装配模型、材质、动画、相机及场景依赖。 */
class ModelRepository(
    private val localResourceDirectory: File? = null,
    private val standalone: Boolean = false,
    private val vehicleIdentity: String? = null
) : Closeable {

    companion object {
        const val LICENSE_PLATE_ATLAS = "MyCar/AppCommon/Common/Textures/LicensePlate/signs.png"
        const val APK_MYCAR = "/system/app/BydMyCar/BydMyCar.apk"
        const val APK_SR = "/system/app/BydSR/BydSR.apk"
        
        const val GPak_ENTRY = "assets/Data/pack_data/app.gpak"

        const val ENV_PATH = "textures/t_skybox_01.genv"

        const val GENERIC_PATH =
            "MyCar/AppCommon/PublicCar/SedanCar_new/Common/Models/SedanCar/SedanCar.gwm"

        private val CHINESE_NAMES = mapOf(
            "HC25" to "汉L",
            "HT_Z9GT" to "腾势 Z9GT",
            "HT_Z9" to "腾势 Z9",
            "SFHE" to "方程豹",
            "SR26" to "宋",
            "SedanCar" to "通用轿车"
        )

        private val VEHICLE_ALIASES = listOf(
            "汉L" to "HC25",
            "汉" to "HC25",
            "Z9GT" to "HT_Z9GT",
            "Z9" to "HT_Z9",
            "方程豹" to "SFHE",
            "腾势" to "HT_Z9GT",
            "宋" to "SR26"
        )
    }

    data class ModelInfo(
        val label: String,
        val path: String,
        val fromMyCar: Boolean,
        
        val resourcePack: String = "",
        val resourceBytes: Long = 0L
    ) {
        val key: String get() = "$resourcePack:$fromMyCar:$path"
    }

    class Loaded(
        val info: ModelInfo,
        val model: GwmModel,
        val vehicle: String,
        val palette: CarPalette?,
        val lamps: LampProfile?,
        
        val hidden: Set<String> = emptySet(),
        val beams: VehicleBeamRig? = null
    ) {
        val colors: List<CarColor> get() = palette?.colors ?: emptyList()
        val defaultColor: Int get() = palette?.defaultIndex ?: 0

        val summary: String
            get() = "${model.meshes.size} 段 / ${model.vertexCount} 顶点 / ${model.triangleCount} 三角形" +
                " / ${model.textures.size} 贴图 / ${model.clips.size} 动作"
    }

    private var portable: PortableModelBundle? = null
    val selfContained: Boolean get() = portable != null
    private var myCar: GpakReader? = null
    private var sr: GpakReader? = null
    private var nativeResourcesReady = false

    @Volatile
    var vehicleName: String = ""
        private set

    @Volatile
    var colors: List<CarColor> = emptyList()
        private set

    fun refreshVehicle(): String {
        val name = if (nativeResourcesReady) vehicleIdentity ?: detectVehicle() else ""
        vehicleName = name
        return name
    }

    fun open(): String {
        close()
        vehicleName = ""
        colors = emptyList()
        if (standalone && localResourceDirectory?.resolve(PortableModelArchive.MODEL)?.isFile == true) {
            portable = PortableModelArchive.read(localResourceDirectory)
            vehicleName = ""
            return "独立模型资源已载入"
        }
        val notes = StringBuilder()
        myCar = try {
            val path = listOfNotNull(
                localResourceDirectory?.resolve("BydMyCar.apk")?.path,
                APK_MYCAR.takeUnless { standalone }
            ).firstOrNull { File(it).let { file -> file.isFile && file.canRead() && file.length() > 0L } } ?: throw IOException("未找到桌面资源包")
            GpakReader.open(path, GPak_ENTRY).also {
                notes.append("BydMyCar ").append(it.recordCount)
                    .append(" 条 / ").append(it.entryCount).append(" 项")
            }
        } catch (t: Throwable) {
            notes.append("BydMyCar 不可用：").append(t.message)
            null
        }
        notes.append("；")
        sr = try {
            val path = listOfNotNull(
                localResourceDirectory?.resolve("BydSR.apk")?.path,
                APK_SR.takeUnless { standalone }
            ).firstOrNull { File(it).let { file -> file.isFile && file.canRead() && file.length() > 0L } } ?: throw IOException("未找到备用资源包")
            GpakReader.open(path, GPak_ENTRY).also {
                notes.append("BydSR ").append(it.recordCount)
                    .append(" 条 / ").append(it.entryCount).append(" 项")
            }
        } catch (t: Throwable) {
            notes.append("BydSR 不可用：").append(t.message)
            null
        }
        nativeResourcesReady = myCar != null && runCatching {
            desktopCamera()
            desktopViewOptions()
            cameraPhysics()
            val reader = requireNotNull(myCar)
            require(reader.names().any { it.endsWith(".gwm", true) && it.contains("/Common/Models/", true) && !it.contains("_LOD", true) && declaresModel(reader, it) }) { "未找到有效模型声明与材质" }
            for (name in listOf("Sky", "Ground_morn", "Ground_noon", "Ground_night")) {
                require(reader.has("MyCar/CarDesk/Dynasty/Common/Prefabs/NS15/$name.gpref")) { "缺少场景配置：$name" }
            }
        }.onFailure { notes.append("；资源配置不兼容：").append(it.message) }.isSuccess
        vehicleName = if (nativeResourcesReady) vehicleIdentity ?: detectVehicle() else ""
        return notes.toString()
    }

    fun desktopViewOptions(): DesktopViewOptions {
        portable?.let { return it.viewOptions }
        val source = myCar?.read("views/MyCarView.view") ?: throw IOException("缺少原厂主视图配置")
        return DesktopViewOptions.parse(JSONObject(source.toString(Charsets.UTF_8)))
    }

    fun desktopCamera(preset: String = "Default"): DesktopCamera {
        portable?.let { return it.camera }
        val reader = myCar ?: throw IOException("桌面资源包未打开")
        val source = reader.read(DesktopCamera.PATH) ?: throw IOException("缺少原厂相机配置")
        return DesktopCamera.parse(source.toString(Charsets.UTF_8), preset)
    }

    fun cameraPhysics(): CameraPhysicsConfig {
        portable?.let { return it.cameraPhysics }
        val reader = myCar ?: throw IOException("桌面资源包未打开")
        val path = "UserResources/Configs/MyCar/CarDesk/Dynasty/HC2/Common/CameraControl/CameraRotAndVelocity.json"
        return CameraPhysicsConfig.parse(String(requireNotNull(reader.read(path)), Charsets.UTF_8))
    }

    fun loadScene(period: DayPeriod): SceneAsset? {
        portable?.let { return it.scenes.getValue(period) }
        val reader = myCar ?: return null
        return try {
            SceneLoader.load(reader, period)
        } catch (t: Throwable) {
            throw IOException("背景场景读取失败：${t.message}", t)
        }
    }

    fun exportResources(
        info: ModelInfo,
        output: java.io.OutputStream,
        work: File
    ) {
        val loaded = load(info)
        val model = if (loaded.model.auxiliaryShaders.isNotEmpty()) loaded.model else loaded.model.withAuxiliaryShaders(
            listOf("vertex" to "vs_depth","fragment" to "fs_depth").mapNotNull { (id,name) -> ShaderSource.assembleEngine(name)?.let { id to it } }.toMap())
        val bundle = PortableModelBundle(model, loaded.palette, loaded.lamps, loaded.hidden, loaded.beams,
            desktopCamera(), cameraPhysics(), desktopViewOptions(),
            DayPeriod.entries.associateWith { requireNotNull(loadScene(it)) { "场景数据不完整：$it" } })
        PortableModelArchive.write(output,bundle,work)
    }

    fun validateResources(info: ModelInfo) {
        load(info)
        desktopCamera()
        desktopViewOptions()
        cameraPhysics()
        DayPeriod.values().forEach { period ->
            requireNotNull(loadScene(period)) { "场景数据不完整：$period" }
        }
    }

    fun listModels(): List<ModelInfo> {
        if (portable != null) return listOf(ModelInfo("车辆模型","model",true))
        if (!nativeResourcesReady) return emptyList()
        val out = ArrayList<ModelInfo>()
        myCar?.let { reader ->
            for (name in reader.names()) {
                if (!name.endsWith(".gwm", ignoreCase = true)) continue
                if (!name.contains("/Common/Models/", ignoreCase = true)) continue
                if (name.contains("_LOD", ignoreCase = true)) continue
                if (!declaresModel(reader, name)) continue
                out.add(ModelInfo(labelOf(name), name, true))
            }
        }
        out.sortBy { it.label }
        if (sr?.let { it.has(GENERIC_PATH) && declaresModel(it, GENERIC_PATH) } == true) {
            out.add(ModelInfo("通用轿车", GENERIC_PATH, false))
        }
        return out
    }

    fun thumbnailGeometry(info: ModelInfo): GwmModel {
        portable?.let { return it.model }
        val reader = (if (info.fromMyCar) myCar else sr)
            ?: throw IOException("对应资源包未打开")
        val bytes = reader.read(info.path) ?: throw IOException("模型不存在：${info.path}")
        var rot: FloatArray? = null
        var scale: FloatArray? = null
        reader.read(info.path.removeSuffix(".gwm") + ".gmdl")?.let { raw ->
            runCatching {
                val json = JSONObject(String(raw, Charsets.UTF_8))
                rot = readVec3(json, "Rot")
                scale = readVec3(json, "Scale")
            }
        }
        val assembly = loadAssembly(reader, info.path)
        return GwmParser.parse(bytes, rot, scale, null, assembly?.transform)
    }

    private fun declaresModel(reader: GpakReader, path: String): Boolean {
        return try {
            if ((reader.sizeOf(path) ?: 0) < 64) return false
            val gmdl = reader.read(path.removeSuffix(".gwm") + ".gmdl") ?: return false
            val json = JSONObject(String(gmdl, Charsets.UTF_8))
            val cls = json.optString("Class", "")
            val mesh = json.optString("MeshPath", "")
            cls.equals("Model", ignoreCase = true) &&
                (mesh.isBlank() || mesh.equals(path, ignoreCase = true)) &&
                readMaterials(json)?.let { materials -> materials.isNotEmpty() && materials.all { it.isNotBlank() && reader.has(it) } } == true
        } catch (_: Throwable) {
            false
        }
    }

    fun shortlist(models: List<ModelInfo>): List<ModelInfo> {
        val vehicle = vehicleName
        if (vehicle.isNotBlank()) {
            for ((alias, code) in VEHICLE_ALIASES) {
                if (!vehicle.contains(alias, ignoreCase = true)) continue
                val hit = models.filter { it.path.contains("/Models/$code/", ignoreCase = true) }
                if (hit.isNotEmpty()) return hit + models.filter { !it.fromMyCar }
            }
        }
        return models
    }

    fun autoSelect(models: List<ModelInfo>): ModelInfo? {
        val vehicle = vehicleName
        if (vehicle.isNotBlank()) {
            for ((alias, code) in VEHICLE_ALIASES) {
                if (!vehicle.contains(alias, ignoreCase = true)) continue
                models.firstOrNull { it.path.contains("/Models/$code/", ignoreCase = true) }
                    ?.let { return it }
            }
        }
        return models.firstOrNull { it.path.contains("SedanCar", ignoreCase = true) }
            ?: models.firstOrNull { !it.fromMyCar }
            ?: models.firstOrNull()
    }

    fun load(info: ModelInfo): Loaded {
        portable?.let { return Loaded(info,it.model,"",it.palette,it.lamps,it.hidden,it.beams) }
        val reader = (if (info.fromMyCar) myCar else sr)
            ?: throw IOException("对应资源包未打开")
        val bytes = reader.read(info.path) ?: throw IOException("模型不存在：${info.path}")

        var rot: FloatArray? = null
        var scale: FloatArray? = null
        var materialPaths: List<String>? = null
        val gmdlPath = info.path.removeSuffix(".gwm") + ".gmdl"
        reader.read(gmdlPath)?.let { raw ->
            try {
                val json = JSONObject(String(raw, Charsets.UTF_8))
                rot = readVec3(json, "Rot")
                scale = readVec3(json, "Scale")
                materialPaths = readMaterials(json)
            } catch (_: Throwable) {
            }
        }

        val specs = materialPaths?.let {
            MaterialLibrary.load(reader, it)
        } ?: emptyMap()
        val ordered = materialPaths?.map { specs[it] ?: DefaultMaterials.OTHER }

        val assembly = loadAssembly(reader, info.path)
        var model = GwmParser.parse(bytes, rot, scale, ordered, assembly?.transform)

        if (info.path.endsWith("/HC25.gwm")) {
            val declaration = JSONObject(String(requireNotNull(reader.read(gmdlPath)), Charsets.UTF_8))
            fun constantVector(name: String, fallback: Float): Array<AnimTrack?> {
                val a = declaration.optJSONArray(name)
                return Array(3) { i -> AnimTrack(floatArrayOf(0f), floatArrayOf(a?.optDouble(i, fallback.toDouble())?.toFloat() ?: fallback)) }
            }
            val exportedRoot = NodeClip("export", "export", 0f, constantVector("Pos", 0f),
                constantVector("Rot", 0f), constantVector("Scale", 1f)).localAt(0f)
            
            val directory = RoofCurtain.DIRECTORY
            val curtainDeclaration = JSONObject(String(requireNotNull(reader.read(directory + "SunRoof.gmdl")), Charsets.UTF_8))
            val curtainPaths = readMaterials(curtainDeclaration).orEmpty()
            val curtainSpecs = MaterialLibrary.load(reader, curtainPaths)
            val curtain = GwmParser.parse(requireNotNull(reader.read(directory + "SunRoof.gwm")), null, null,
                curtainPaths.map { curtainSpecs.getValue(it) },
                assembly?.let { RoofCurtain.assemblyRoot(it.transform, exportedRoot) })
            val curtainClips = GwaParser.parse(requireNotNull(reader.read(directory + "SunRoof.gwa")))
            require(curtainClips.size == 2) { "原厂双片遮阳帘动作不完整" }
            model = RoofCurtain.attach(model, curtain, curtainClips)
        }

        val texturePaths = LinkedHashSet<String>()
        for (spec in model.meshes.map { it.material }.distinctBy { it.path }) {
            spec.albedoTexture?.let { texturePaths.add(it) }
            spec.alphaMaskTex?.let { texturePaths.add(it) }
            spec.effectiveEmissiveTexture?.let { texturePaths.add(it) }
            spec.envTex?.let { texturePaths.add(it) }
            spec.ormTex?.let { texturePaths.add(it) }
            
            for (path in spec.textureUniforms.values) texturePaths.add(path)
        }
        
        if (info.path.endsWith("/HC25.gwm")) texturePaths.add(LICENSE_PLATE_ATLAS)

        val palette = ColorConfig.load(myCar, listOfNotNull(sr), info.path)
            ?: ColorConfig.load(sr, listOfNotNull(myCar), info.path)
        android.util.Log.i(
            "CarDemo",
            "色板 ${palette?.colors?.size ?: 0}：${palette?.colors?.joinToString { it.label }}" +
                "；默认 ${palette?.defaultIndex}；环境图 ${palette?.envMaps?.joinToString { it.name }}"
        )
        if (palette != null) {
            for (env in palette.envMaps) texturePaths.add(env.name)
        }

        val textures = LinkedHashMap<String, ByteArray>(texturePaths.size * 2)
        for (path in texturePaths) {
            val data = readAny(path) ?: continue
            textures[path] = data
        }
        android.util.Log.i(
            "CarDemo",
            "贴图 ${textures.size}/${texturePaths.size}" +
                texturePaths.filterNot { textures.containsKey(it) }
                    .joinToString(prefix = "；未读到 ", separator = "、")
        )
        android.util.Log.i(
            "CarDemo",
            "贴图清单：${texturePaths.joinToString { it.substringAfterLast('/') }}"
        )
        for (missing in texturePaths) {
            if (textures.containsKey(missing)) continue
            val base = missing.substringAfterLast('/')
            val alt = myCar?.names()?.firstOrNull { it.endsWith(base, ignoreCase = true) }
                ?: sr?.names()?.firstOrNull { it.endsWith(base, ignoreCase = true) }
            android.util.Log.w("CarDemo", "缺贴图 $missing → 同文件名 $alt")
        }

        val clips = loadClips(reader, info.path, model.nodeIndex) + model.clips

        val lights = LinkedHashMap<DayPeriod, LightRig>()
        for (period in DayPeriod.values()) {
            val rig = LightRig.load(reader, info.path, period)
                ?: LightRig.load(myCar, info.path, period)
            if (rig != null) lights[period] = rig
        }
        android.util.Log.i(
            "CarDemo",
            "灯光组：" + lights.entries.joinToString { (period, rig) ->
                "$period ${rig.lights.size} 盏（${rig.lights.joinToString { it.name }}）"
            }
        )

        val environment = myCar?.read(ENV_PATH) ?: reader.read(ENV_PATH) ?: sr?.read(ENV_PATH)
        android.util.Log.i(
            "CarDemo",
            "场景环境 ${ENV_PATH}：${environment?.size ?: 0} 字节"
        )

        model = model.withScene(textures, clips, lights, environment)

        colors = palette?.colors ?: emptyList()
        var lamps = LampProfile.load(myCar, info.path) ?: LampProfile.load(sr, info.path)
        val isHc25 = info.path.substringAfterLast('/').substringBeforeLast('.') == "HC25"
        if (isHc25) lamps = lamps?.withHc25DesktopFallbacks()?.withReverseFallback("Light_20")
        val hidden = assembly?.hidden ?: emptySet()
        return Loaded(info, model, vehicleName, palette, lamps, hidden,
            if (isHc25) VehicleBeamRig.load(myCar) else null)
    }

    @Volatile
    var fascia: String = "EV"

    private fun loadAssembly(reader: GpakReader, modelPath: String): CarAssembly? {
        val root = modelPath.substringBefore("/Common/", modelPath)
        val paths = reader.names().filter {
            it.startsWith(root) && it.endsWith("/CarPrefab.json") && !it.contains("_lod", true)
        }.sorted()
        val texts = paths.mapNotNull { reader.read(it)?.toString(Charsets.UTF_8) }
        return CarAssembly.select(texts, modelPath, fascia)?.also {
            Log.i("CarDemo", "原厂装配 ${it.path} 前脸=$fascia 位置=${it.transform.slice(12..14)} 隐藏=${it.hidden.size}")
        }
    }

    private fun loadClips(
        reader: GpakReader,
        modelPath: String,
        nodeIndex: Map<String, Int>
    ): Map<String, List<NodeClip>> {
        val dir = modelPath.substringBeforeLast('/')
        val out = LinkedHashMap<String, MutableList<NodeClip>>()
        val names = reader.names().filter { it.startsWith("$dir/") && it.endsWith(".gwa") }
        for (path in names.sorted()) {
            val raw = reader.read(path) ?: continue
            val clips = try {
                GwaParser.parse(raw)
            } catch (_: Throwable) {
                emptyList()
            }
            for (clip in clips) {
                if (!nodeIndex.containsKey(clip.target)) continue
                out.getOrPut(clip.target) { ArrayList(2) }.add(clip)
            }
        }
        return out.mapValues { (_, clips) -> clips.toList() }
    }

    private fun readAny(path: String): ByteArray? {
        myCar?.read(path)?.let { return it }
        sr?.read(path)?.let { return it }
        val base = path.substringAfterLast('/')
        val alt = myCar?.names()?.firstOrNull {
            it.substringAfterLast('/').equals(base, ignoreCase = true)
        } ?: sr?.names()?.firstOrNull {
            it.substringAfterLast('/').equals(base, ignoreCase = true)
        } ?: return null
        android.util.Log.i("CarDemo", "贴图跨车系复用：$path → $alt")
        return myCar?.read(alt) ?: sr?.read(alt)
    }

    override fun close() {
        myCar?.close()
        sr?.close()
        myCar = null
        sr = null
        portable = null
        nativeResourcesReady = false
        vehicleName = ""
        colors = emptyList()
    }

    private fun readMaterials(json: JSONObject): List<String>? {
        val arr = json.optJSONArray("MeshMaterialArray") ?: return null
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optString(i, ""))
        return out
    }

    private fun readVec3(json: JSONObject, key: String): FloatArray? {
        val arr = json.optJSONArray(key) ?: return null
        if (arr.length() < 3) return null
        return floatArrayOf(
            arr.getDouble(0).toFloat(),
            arr.getDouble(1).toFloat(),
            arr.getDouble(2).toFloat()
        )
    }

    private fun labelOf(path: String): String {
        val parts = path.split('/')
        val code = parts.last().substringBeforeLast('.').ifBlank { "?" }
        val family = parts.getOrNull(3) ?: parts.getOrNull(2) ?: ""
        val cn = CHINESE_NAMES[code]
        return if (cn != null) "$cn · $code（$family）" else "$code（$family）"
    }

    private fun detectVehicle(): String {
        val keys = listOf(
            "persist.sys.byd.default_name",
            "persist.sys.byd.bluetooth_name",
            "ro.product.model"
        )
        for (key in keys) {
            val value = getProp(key)
            if (!value.isNullOrBlank()) return value
        }
        return ""
    }

    private fun getProp(key: String): String? {
        return try {
            val process = ProcessBuilder("/system/bin/getprop", key)
                .redirectErrorStream(true)
                .start()
            try {
                val deadline=System.nanoTime()+1_000_000_000L
                while(runCatching { process.exitValue() }.isFailure) {
                    if(System.nanoTime()>=deadline)return null
                    Thread.sleep(10)
                }
                process.inputStream.bufferedReader().use { it.readLine() }
                    ?.trim()?.takeIf { it.isNotEmpty() && it != "unknown" }
            } finally { process.destroy() }
        } catch (_: Throwable) {
            null
        }
    }
}
