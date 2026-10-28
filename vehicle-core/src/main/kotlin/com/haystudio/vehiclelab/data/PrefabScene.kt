package com.haystudio.vehiclelab.data

import org.json.JSONObject

object PrefabScene {
    fun resolve(prefab: JSONObject, read: (String) -> JSONObject): List<JSONObject> {
        val result = ArrayList<JSONObject>()
        val patches = prefab.optJSONObject("pref_data")
        fun visit(node: JSONObject) {
            val include = node.optString("\$include")
            if (include.isNotBlank()) {
                val model = JSONObject(read(include).toString())
                val operations = patches?.optJSONArray(node.optString("ID"))
                for (i in 0 until (operations?.length() ?: 0)) {
                    val operation = operations!!.getJSONObject(i)
                    val segments = operation.getString("path").trimStart('/').split('/')
                    var parent: Any = model
                    for (segment in segments.dropLast(1)) {
                        parent = when (val value = parent) {
                            is JSONObject -> value.get(segment)
                            is org.json.JSONArray -> value.get(segment.toInt())
                            else -> error("预制体补丁路径不可达")
                        }
                    }
                    val key = segments.last()
                    when (parent) {
                        is JSONObject -> if (operation.getString("op") == "remove") parent.remove(key)
                            else parent.put(key, operation.get("value"))
                        is org.json.JSONArray -> if (operation.getString("op") == "remove") parent.remove(key.toInt())
                            else parent.put(key.toInt(), operation.get("value"))
                    }
                }
                result.add(model)
            }
            val children = node.optJSONArray("Child")
            for (i in 0 until (children?.length() ?: 0)) visit(children!!.getJSONObject(i))
        }
        visit(prefab)
        return result
    }
}
