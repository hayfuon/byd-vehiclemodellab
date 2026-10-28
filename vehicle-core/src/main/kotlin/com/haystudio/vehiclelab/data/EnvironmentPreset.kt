package com.haystudio.vehiclelab.data

enum class EnvironmentPreset(val label:String,val asset:String?) {
    DESKTOP("车机桌面",null),AUTUMN("秋日公园","autumn"),SNOW_PARK("雪景公园","snow"),COURTYARD("阴天庭院","rain");
    companion object { fun of(value:String?)=entries.firstOrNull { it.name.equals(value,true)||it.asset==value } ?: DESKTOP }
}
