package com.haystudio.vehiclelab.data

import org.json.JSONArray
import org.json.JSONObject

class LampState(
    val key: String,
    val nodes: Set<String>,
    val notNodes: Set<String>,
    val flickNodes: Set<String>,
    val relateKeys: Set<String> = emptySet(),
    
    val flowing: Boolean = false,
    
    val signTime: Float = 0.5f
)

class LampProfile(private val states: Map<String, LampState>, private val hc25: Boolean = false) {

    var turnFrequencyPerMinute: Float = 90f
        set(value) { field = if(value.isFinite()) value.coerceIn(30f,180f) else 90f }
    private val turnStarts = HashMap<String, Float>()
    private var previousTurnKeys = emptySet<String>()
    fun restartTurns(elapsedSeconds: Float) {
        previousTurnKeys.forEach { turnStarts[it]=elapsedSeconds }
    }
    private fun updateTurns(keys: Set<String>, time: Float) {
        val turns = keys.filter { it == "LightTurnLeft" || it == "LightTurnRight" }.toSet()
        if(turns.size==2 && previousTurnKeys.size!=2) {
            // 第二侧开启即进入双闪，从共同起点开始完整流水。
            turns.forEach {turnStarts[it]=time}
        } else {
            for(key in turns) if(key !in previousTurnKeys || time < (turnStarts[key] ?: time)) turnStarts[key] = time
        }
        previousTurnKeys = turns
    }
    private fun cycle(lamp: LampState) = if(lamp.key.startsWith("LightTurn")) 60f/turnFrequencyPerMinute else lamp.signTime.coerceAtLeast(.05f)
    private fun phase(lamp: LampState, time: Float): Float {
        val cycle = cycle(lamp)
        val elapsed = time - (turnStarts[lamp.key] ?: 0f)
        val phase=((elapsed % cycle) + cycle) % cycle
        return if(cycle-phase<.00001f || phase<.00001f) 0f else phase
    }

    val allNodes: Set<String> = HashSet<String>().apply {
        for (s in states.values) {
            addAll(s.nodes)
            addAll(s.flickNodes)
            addAll(s.notNodes)
        }
    }

    fun state(key: String): LampState? = states.entries.firstOrNull {
        it.key.equals(key, ignoreCase = true)
    }?.value

    fun active(state: Int): Set<String> = active(VehicleLights.preset(state))

    fun active(lights: VehicleLights): Set<String> = active(lights, 0f)

    fun active(lights: VehicleLights, elapsedSeconds: Float): Set<String> {
        val keys = lights.keys()
        updateTurns(keys, elapsedSeconds)
        val active = keys.flatMap { key ->
            val lamp = state(key) ?: return@flatMap emptySet()
            
            if (hc25 && key in setOf("LightAtmoOut","LightAdasLeft","LightAdasRight")) return@flatMap emptySet()
            if (lamp.flowing && lamp.nodes.isNotEmpty()) {
                val cycle = cycle(lamp)
                val phase = phase(lamp, elapsedSeconds)
                
                if (phase < cycle * .55f) lamp.nodes else emptySet()
            } else if (lamp.flickNodes.isEmpty()) lamp.nodes else {
                val cycle = cycle(lamp)
                val flashOn = phase(lamp, elapsedSeconds) < cycle * .55f
                if (flashOn) lamp.nodes else lamp.nodes - lamp.flickNodes
            }
        }.toSet()
        return if (hc25) (active - RearLampState.allNodes) + RearLampState.resolve(lights).nodes else active
    }

    fun flowProgress(ancestors: Set<String>, elapsedSeconds: Float): Float? {
        val lamp = states.values.firstOrNull { lamp ->
            lamp.flowing && lamp.nodes.any { it in ancestors } &&
                lamp.flickNodes.none { it in ancestors }
        } ?: return null
        val cycle = cycle(lamp)
        val phase = phase(lamp, elapsedSeconds)
        return (phase / (cycle * .40f)).coerceIn(0f, 1f)
    }

    fun hidden(state: Int): Set<String> = hidden(VehicleLights.preset(state))

    fun hidden(lights: VehicleLights): Set<String> {
        val keys = lights.keys()
        return keys.flatMap { key ->
            val state = state(key)
            if (state != null && (state.relateKeys.isEmpty() || state.relateKeys.any { it in keys }))
                state.notNodes else emptySet()
        }.toSet()
    }

    fun withReverseFallback(node: String): LampProfile {
        if (state("LightReversing")?.nodes?.isNotEmpty() == true) return this
        return LampProfile(states + ("LightReversing" to LampState("LightReversing",
            setOf(node), emptySet(), emptySet())), hc25)
    }

    fun withHc25DesktopFallbacks(): LampProfile {
        val fallback = mapOf(
            "LightDay" to setOf("Light_09", "Light_10"),
            "LightAround" to setOf("Light_11", "Light_12"),
            "LightRear" to setOf("Light_18_04", "Light_18_05", "Light_18_06"),
            "LightAdjustment" to setOf("Light_13")
        )
        var next = states
        for ((key, nodes) in fallback) {
            val state = next[key] ?: continue
            if (state.nodes.isEmpty()) next = next + (key to LampState(key, nodes,
                state.notNodes, state.flickNodes, state.relateKeys, state.flowing, state.signTime))
        }
        return LampProfile(next, hc25 = true)
    }

    companion object {
        fun load(reader: GpakReader?, modelPath: String): LampProfile? {
            if (reader == null) return null
            val base = modelPath.substringAfterLast('/').substringBeforeLast('.')
            val names = try {
                reader.names()
            } catch (_: Throwable) {
                return null
            }
            val path = names.firstOrNull {
                it.endsWith("/LightControl/${base}_light.json", ignoreCase = true)
            } ?: names.firstOrNull {
                it.contains("/LightControl/", ignoreCase = true) &&
                    it.endsWith("_light.json", ignoreCase = true) &&
                    it.contains("/${base}/", ignoreCase = true)
            } ?: return null
            val raw = try {
                reader.read(path)
            } catch (_: Throwable) {
                null
            } ?: return null
            return parse(String(raw, Charsets.UTF_8)).also {
                android.util.Log.i("CarDemo", "灯语 $path：${it?.states?.keys?.joinToString()}")
            }
        }

        fun parse(text: String): LampProfile? {
            return try {
                val root = JSONObject(text)
                val arr = root.optJSONArray("mCombLamps") ?: return null
                val map = LinkedHashMap<String, LampState>(arr.length() * 2)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val key = o.optString("dsKey", "")
                    if (key.isBlank()) continue
                    map[key] = LampState(
                        key = key,
                        nodes = strings(o.optJSONArray("mLampNodes")),
                        notNodes = strings(o.optJSONArray("mNotLampNodes")),
                        flickNodes = strings(o.optJSONArray("mFlickNodes")),
                        relateKeys = o.optString("mRelateKeys", "").split(',', ';').map { it.trim() }
                            .filter { it.isNotEmpty() }.toSet(),
                        flowing = o.optString("mFlowing", "false").equals("true", ignoreCase = true),
                        signTime = o.optDouble("mSignTime", 0.5).toFloat().takeIf { it.isFinite() && it > 0f }
                            ?: 0.5f
                    )
                }
                if (map.isEmpty()) null else LampProfile(map)
            } catch (_: Throwable) {
                null
            }
        }

        private fun strings(a: JSONArray?): Set<String> {
            if (a == null) return emptySet()
            val out = LinkedHashSet<String>(a.length())
            for (i in 0 until a.length()) {
                val s = a.optString(i, "")
                if (s.isNotBlank()) out.add(s)
            }
            return out
        }
    }
}
