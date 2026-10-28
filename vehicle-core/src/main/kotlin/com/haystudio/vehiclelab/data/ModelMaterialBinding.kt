package com.haystudio.vehiclelab.data

import org.json.JSONObject

object ModelMaterialBinding {
    fun path(model: JSONObject, component: String, meshIndex: Int): String? {
        val names = model.optJSONObject("MeshNameMaterialMap")
        return if (names != null && names.length() > 0) {
            names.optString(component).takeIf { it.isNotBlank() }
        } else {
            model.optJSONArray("MeshMaterialArray")?.optString(meshIndex)?.takeIf { it.isNotBlank() }
        }
    }
}
