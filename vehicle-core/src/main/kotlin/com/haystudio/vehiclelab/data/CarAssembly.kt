package com.haystudio.vehiclelab.data

import org.json.JSONArray

data class CarAssembly(val path: String, val transform: FloatArray, val hidden: Set<String>) {
    companion object {
        fun select(texts: List<String>, modelPath: String, fascia: String): CarAssembly? {
            val expected = modelPath.removeSuffix(".gwm") + ".gmdl"
            val assemblies = texts.flatMap { text ->
                val entries = JSONArray(text)
                (0 until entries.length()).mapNotNull { i ->
                    val entry = entries.getJSONObject(i)
                    if (entry.optString("Path") != expected) return@mapNotNull null
                    val hiddenArray = entry.optJSONArray("Hidden") ?: JSONArray()
                    val hidden = (0 until hiddenArray.length()).map { hiddenArray.getString(it) }.toSet()
                    if (hidden.any { it.startsWith("Custom_Headstock_${fascia}_") }) return@mapNotNull null
                    fun vector(name: String, fallback: Float) = entry.optJSONArray(name)?.let { a ->
                        FloatArray(3) { a.getDouble(it).toFloat() }
                    } ?: FloatArray(3) { fallback }
                    val pos = vector("Position", 0f)
                    val rotation = vector("Rotation", 0f).map { Math.toRadians(it.toDouble()).toFloat() }
                    val scale = vector("Scale", 1f)
                    fun constant(v: Float) = AnimTrack(floatArrayOf(0f), floatArrayOf(v))
                    val matrix = NodeClip("assembly", "assembly", 0f,
                        Array(3) { constant(pos[it]) }, Array(3) { constant(rotation[it]) },
                        Array(3) { constant(scale[it]) }).localAt(0f)
                    CarAssembly(expected, matrix, hidden)
                }
            }
            val selected = assemblies.firstOrNull() ?: return null
            require(assemblies.all { it.transform.contentEquals(selected.transform) && it.hidden == selected.hidden }) {
                "车型装配配置存在差异，需要指定准确配置"
            }
            return selected
        }
    }
}
