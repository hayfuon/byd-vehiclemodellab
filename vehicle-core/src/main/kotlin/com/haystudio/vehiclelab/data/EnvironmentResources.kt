package com.haystudio.vehiclelab.data

import android.content.res.AssetManager

/** 内置环境随核心模块打包；读取应在 IO 线程执行，桌面环境使用模型自己的场景。 */
object EnvironmentResources {
    fun read(assets:AssetManager,preset:EnvironmentPreset):ByteArray? =
        preset.asset?.let{assets.open("environments/$it.genv").use{input->input.readBytes()}}
}
