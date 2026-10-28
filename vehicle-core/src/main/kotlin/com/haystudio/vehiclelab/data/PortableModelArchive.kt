package com.haystudio.vehiclelab.data

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 渲染快照只保存已解析的依赖图，不携带系统包、设备标识或原始资源目录。 */
class PortableModelBundle(
    val model: GwmModel,
    val palette: CarPalette?,
    val lamps: LampProfile?,
    val hidden: Set<String>,
    val beams: VehicleBeamRig?,
    val camera: DesktopCamera,
    val cameraPhysics: CameraPhysicsConfig,
    val viewOptions: DesktopViewOptions,
    val scenes: Map<DayPeriod, SceneAsset>
)

object PortableModelArchive {
    const val MODEL = "model.json"
    const val PLATE_TEXTURE = "textures/license-plate.png"
    private const val MAX_TOTAL = 1024L * 1024 * 1024
    private const val MAX_FILE = 256L * 1024 * 1024
    private const val MAX_JSON = 16L * 1024 * 1024
    private val shaPattern = Regex("[0-9a-f]{64}")
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun write(output: OutputStream, bundle: PortableModelBundle, work: File) {
        require(work.mkdirs() || work.isDirectory)
        try {
            val store = BlobStore(work, true)
            val gson = codec(store)
            val tree = gson.toJsonTree(bundle)
            val aliases = linkedMapOf<String, String>()
            val materials = (bundle.model.meshes + bundle.scenes.values.flatMap { it.officialMeshes }).map { it.material }.distinctBy { it.path }
            for ((index, material) in materials.withIndex()) {
                val semantic = when {
                    material.path.contains("m_env_ground_", true) -> "m_env_ground_"
                    material.path.contains("skybox", true) -> "skybox"
                    material.path.contains("mountain", true) -> "mountain"
                    material.path.contains("m_env_fog_", true) -> "m_env_fog_"
                    material.path.contains("Baked_Paint_Black", true) -> "Baked_Paint_Black"
                    material.path.contains("LightsBloom", true) -> "LightsBloom"
                    material.path.endsWith("/M_Plastics_Black.mat") -> "M_Plastics_Black"
                    bundle.palette?.regionOf(material.path) == PaintRegion.UPPER -> "carpaint_up"
                    bundle.palette?.regionOf(material.path) == PaintRegion.LOWER -> "carpaint"
                    material.light -> "Light"
                    else -> "surface"
                }
                aliases[material.path] = if(semantic=="M_Plastics_Black") "materials/$index/M_Plastics_Black.mat" else "materials/${semantic}_$index.mat"
            }
            aliases[ModelRepository.LICENSE_PLATE_ATLAS] = PLATE_TEXTURE
            val textures=(bundle.model.textures.keys + bundle.scenes.values.flatMap {it.textures.keys}).toSet()
            fun alias(value: String): String {
                val alternative=if(value.contains("-mipmap"))value.replace("-mipmap","") else value.substringBeforeLast('.')+"-mipmap.ktx"
                val canonical=if(value !in textures && value.endsWith(".ktx",true) && alternative in textures)alternative else value
                return aliases.getOrPut(canonical) {
                val extension = value.substringAfterLast('.', "asset").lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "asset"
                "resources/${aliases.size}.$extension"
                }
            }
            fun isPath(value: String): Boolean = (value.contains('/') || value.contains('\\')) &&
                value.none { it=='\n' || it=='\r' } && !value.startsWith("sha256:")
            fun clean(value: JsonElement, field: String = ""): JsonElement = when {
                value.isJsonObject -> JsonObject().also { result -> value.asJsonObject.entrySet().forEach { (key, child) ->
                    if (field == "beams" && key == "source") result.addProperty(key, "")
                    else result.add(if (isPath(key)) alias(key) else key, clean(child, key))
                } }
                value.isJsonArray -> JsonArray().also { result -> value.asJsonArray.forEach { result.add(clean(it, field)) } }
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> {
                    val text = value.asString
                    when {
                        field == "vsSource" || field == "fsSource" || field == "vertex" || field == "fragment" ->
                            JsonPrimitive(text.replace(Regex("/\\*[\\s\\S]*?\\*/"), " ").replace(Regex("(?m)//[^\\n]*"), "").replace(Regex("(?m)^\\s*#line[^\\n]*"), ""))
                        isPath(text) -> JsonPrimitive(alias(text))
                        else -> value
                    }
                }
                else -> value
            }
            val graph = File(work, MODEL)
            graph.writer(Charsets.UTF_8).buffered().use { gson.toJson(clean(tree), it) }
            require(graph.length() <= MAX_JSON) { "模型清单超出大小限制" }
            val rows = listOf(MODEL) + store.names.map { "data/$it" }
            val manifest = JsonObject().apply {
                addProperty("format", "BYD-VehicleModelLab")
                addProperty("version", 2)
                addProperty("createdAt", timestamp().format(Date()))
                add("files", JsonArray().also { files -> rows.forEach { name -> files.add(JsonObject().apply {
                    addProperty("id", name);addProperty("size", File(work,name).length());addProperty("sha256", if(name==MODEL) graph.inputStream().use { input -> val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)};digest.digest().joinToString(""){"%02x".format(it)} } else name.removePrefix("data/"))
                }) } })
            }
            ZipOutputStream(output.buffered()).use { zip ->
                zip.setLevel(6)
                fun entry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L });zip.write(bytes);zip.closeEntry()
                }
                entry("manifest.json", manifest.toString().toByteArray())
                rows.forEach { name ->
                    zip.putNextEntry(ZipEntry(name).apply {time=0L})
                    File(work,name).inputStream().use {it.copyTo(zip)}
                    zip.closeEntry()
                }
            }
        } finally { work.deleteRecursively() }
    }

    fun extract(archive: File, directory: File) {
        require(!directory.exists() || directory.listFiles().isNullOrEmpty()) { "导入目录必须为空" }
        directory.mkdirs()
        try {
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().toList()
                require(entries.size in 2..30002 && entries.map { it.name }.distinct().size == entries.size) { "资源包目录异常" }
                val manifestEntry = requireNotNull(zip.getEntry("manifest.json"))
                require(manifestEntry.size in 1..MAX_JSON)
                val manifest = JsonParser.parseString(zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }).asJsonObject
                require(manifest["format"].asString == "BYD-VehicleModelLab" && manifest["version"].asInt == 2) { "不支持此资源包版本" }
                requireNotNull(timestamp().parse(manifest["createdAt"].asString))
                val rows = manifest.getAsJsonArray("files")
                require(rows.size() in 1..30001)
                val ids = rows.map { it.asJsonObject["id"].asString }
                require(ids.distinct().size == ids.size && MODEL in ids)
                require(entries.map { it.name }.toSet() == ids.toSet() + "manifest.json") { "资源清单与文件不一致" }
                var total = 0L
                for (element in rows) {
                    val row=element.asJsonObject
                    val id=row["id"].asString
                    val size=row["size"].asLong
                    val digest=row["sha256"].asString
                    require(id==MODEL || id.startsWith("data/") && shaPattern.matches(id.removePrefix("data/"))) { "资源标识无效" }
                    require(shaPattern.matches(digest) && size in (if(id==MODEL) 1L else 0L)..(if(id==MODEL) MAX_JSON else MAX_FILE))
                    if (id!=MODEL) require(id=="data/$digest") { "资源内容标识不一致" }
                    total+=size;require(total<=MAX_TOTAL) { "资源包解压体积超限" }
                    val entry=requireNotNull(zip.getEntry(id));require(entry.size==size)
                    val destination=File(directory,id).also { it.parentFile?.mkdirs() }
                    val actual=MessageDigest.getInstance("SHA-256")
                    zip.getInputStream(entry).use { input -> destination.outputStream().use { target ->
                        var copied=0L
                        val buffer=ByteArray(65536)
                        while(true) {
                            val n=input.read(buffer);if(n<0)break
                            copied+=n;require(copied<=size)
                            actual.update(buffer,0,n);target.write(buffer,0,n)
                        }
                        require(copied==size)
                    } }
                    require(actual.digest().joinToString("") { "%02x".format(it) }==digest) { "资源 SHA-256 校验失败" }
                }
                File(directory,"manifest.json").writeText(manifest.toString())
            }
            read(directory)
        } catch (error: Throwable) { directory.deleteRecursively();throw error }
    }

    fun read(directory: File): PortableModelBundle {
        val graph=File(directory,MODEL)
        require(graph.length() in 1..MAX_JSON)
        val bundle=graph.bufferedReader().use { codec(BlobStore(directory,false)).fromJson(it,PortableModelBundle::class.java) }
        require(bundle.model.meshes.isNotEmpty() && bundle.model.nodes.size<=30000 && bundle.model.meshes.size<=30000)
        require(bundle.model.boundsMin.size==3 && bundle.model.boundsMax.size==3 && bundle.model.rot.size==3 && bundle.model.scale.size==3)
        for (mesh in bundle.model.meshes + bundle.scenes.values.flatMap { it.officialMeshes }) {
            require(mesh.positions.size % 3==0 && mesh.normals.size==mesh.positions.size && mesh.indices.size % 3==0)
            require(mesh.uvs.size==mesh.positions.size/3*2)
            require(mesh.indices.all { it in 0 until mesh.positions.size/3 })
            require(mesh.positions.all { it.isFinite() })
        }
        for ((i,node) in bundle.model.nodes.withIndex()) require(node.parent==-1 || node.parent in 0 until i)
        require(bundle.model.nodeWorlds.size==bundle.model.nodes.size)
        require(DayPeriod.entries.all { it in bundle.scenes })
        return bundle
    }

    private class BlobStore(private val root: File, private val writing: Boolean) {
        val names=linkedSetOf<String>()
        private var total=0L
        private val cache=HashMap<String,ByteArray>()
        fun put(bytes: ByteArray): String {
            require(writing && bytes.size<=MAX_FILE)
            val digest=sha(bytes)
            if(names.add(digest)) {
                total+=bytes.size;require(total<=MAX_TOTAL && names.size<=30000)
                File(root,"data/$digest").also { it.parentFile?.mkdirs();it.writeBytes(bytes) }
            }
            return "sha256:$digest"
        }
        fun get(id: String): ByteArray {
            require(!writing && id.startsWith("sha256:") && shaPattern.matches(id.removePrefix("sha256:")))
            return cache.getOrPut(id) {
                val file=File(root,"data/${id.removePrefix("sha256:")}")
                require(file.length() in 0..MAX_FILE)
                file.readBytes().also {require(sha(it)==id.removePrefix("sha256:")) {"资源内容已损坏"}}
            }
        }
    }

    private fun timestamp() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.ROOT).apply {timeZone=TimeZone.getTimeZone("UTC");isLenient=false}

    private fun codec(store: BlobStore): Gson {
        val excluded=object: ExclusionStrategy {
            override fun shouldSkipField(f: FieldAttributes) = f.name.endsWith("\$delegate") ||
                f.declaringClass==MaterialSpec::class.java && f.name=="textureUniforms" ||
                f.declaringClass==EnvMap::class.java && f.name=="uniforms" ||
                f.declaringClass==LampProfile::class.java && f.name in setOf("turnStarts","previousTurnKeys")
            override fun shouldSkipClass(c: Class<*>)=false
        }
        val builder=GsonBuilder().disableHtmlEscaping().addSerializationExclusionStrategy(excluded).addDeserializationExclusionStrategy(excluded)
            .registerTypeAdapter(NodeClip::class.java,InstanceCreator { NodeClip("","",0f,arrayOfNulls(3),arrayOfNulls(3),arrayOfNulls(3)) })
            .registerTypeAdapter(LampProfile::class.java,InstanceCreator { LampProfile(emptyMap()) })
        builder.registerTypeAdapter(String::class.java,object:TypeAdapter<String>() {
            override fun write(out:JsonWriter,value:String?) {
                if(value==null)out.nullValue()
                else if(value.contains('\n') && (value.contains("#version") || value.contains("void main") || value.contains("uniform "))) {
                    val source=value.replace("// step2. calculate alpha","LAB_ALPHA_INSERTION_MARKER").replace(Regex("/\\*[\\s\\S]*?\\*/")," ")
                        .replace(Regex("(?m)//[^\\n]*"),"").replace(Regex("(?m)^\\s*#line[^\\n]*"),"")
                        .replace("LAB_ALPHA_INSERTION_MARKER","// step2. calculate alpha")
                    out.value("utf8:"+store.put(source.toByteArray(Charsets.UTF_8)))
                } else out.value(value)
            }
            override fun read(input:JsonReader):String? {
                if(input.peek()==com.google.gson.stream.JsonToken.NULL){input.nextNull();return null}
                val text=input.nextString()
                return if(text.startsWith("utf8:sha256:")) store.get(text.removePrefix("utf8:")).toString(Charsets.UTF_8) else text
            }
        })
        fun <T> arrayAdapter(type:Class<T>, encode:(T)->ByteArray, decode:(ByteArray)->T) {
            builder.registerTypeAdapter(type,object: TypeAdapter<T>() {
                override fun write(out: JsonWriter,value:T?) { if(value==null)out.nullValue() else out.value(store.put(encode(value))) }
                override fun read(input:JsonReader):T? { if(input.peek()==com.google.gson.stream.JsonToken.NULL){input.nextNull();return null};return decode(store.get(input.nextString())) }
            })
        }
        arrayAdapter(ByteArray::class.java,{TextureMetadata.clean(it)},{it})
        arrayAdapter(FloatArray::class.java,{ values -> ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN).apply {values.forEach {putFloat(it)}}.array() },{ bytes ->
            require(bytes.size%4==0);val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);FloatArray(bytes.size/4){b.float}
        })
        arrayAdapter(IntArray::class.java,{ values -> ByteBuffer.allocate(values.size*4).order(ByteOrder.LITTLE_ENDIAN).apply {values.forEach {putInt(it)}}.array() },{ bytes ->
            require(bytes.size%4==0);val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);IntArray(bytes.size/4){b.int}
        })
        builder.registerTypeAdapterFactory(object: TypeAdapterFactory {
            override fun <T> create(gson:Gson,type:TypeToken<T>):TypeAdapter<T>? {
                if(type.rawType!=MaterialSpec::class.java && type.rawType!=EnvMap::class.java)return null
                val delegate=gson.getDelegateAdapter(this,type)
                return object:TypeAdapter<T>() {
                    override fun write(out:JsonWriter,value:T)=delegate.write(out,value)
                    @Suppress("UNCHECKED_CAST") override fun read(input:JsonReader):T {
                        val value=delegate.read(input)
                        return when(value) {
                            is MaterialSpec -> value.copy(uniforms=value.uniforms.mapValues { (_,v) -> when(v) {
                                is List<*> -> v.map { (it as Number).toFloat() }.toFloatArray()
                                is Number -> v.toFloat()
                                is String -> if(v.startsWith("sha256:")) {
                                    val bytes=store.get(v);require(bytes.size%4==0)
                                    val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                                    FloatArray(bytes.size/4){buffer.float}
                                } else v
                                else -> v
                            } }) as T
                            is EnvMap -> EnvMap(value.band,value.name,value.intensity,value.yaw,value.roughness) as T
                            else -> value
                        }
                    }
                }
            }
        })
        return builder.create()
    }
}
