package com.haystudio.vehiclelab

import androidx.compose.runtime.Composable
import com.haystudio.vehiclelab.data.GwmModel
import com.haystudio.vehiclelab.data.ModelRepository
import com.haystudio.vehiclelab.data.ThumbnailOrientation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.haystudio.vehiclelab.render.rasterizeThumbnail

@Composable
internal fun VehicleModelThumbnail(
    item: ModelRepository.ModelInfo,
    loader: suspend (ModelRepository.ModelInfo) -> GwmModel?
) {
    VehicleResourceThumbnail("model-v15-depth:${item.key}") {
        currentCoroutineContext().ensureActive()
        val model = requireNotNull(loader(item)) { "模型缩略图不可用" }
        val context=currentCoroutineContext()
        rasterizeThumbnail(ThumbnailOrientation.normalize(model)){context.ensureActive()}
    }
}

