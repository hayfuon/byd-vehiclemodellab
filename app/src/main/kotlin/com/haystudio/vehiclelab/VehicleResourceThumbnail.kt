package com.haystudio.vehiclelab

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** 串行生成缩略图并限制磁盘缓存数量，避免并发解析挤占渲染内存。 */
internal val vehicleThumbnailMutex = Mutex()

@Composable
internal fun VehicleResourceThumbnail(cacheKey: String, generate: suspend () -> Bitmap) {
    val context = LocalContext.current.applicationContext
    var failed by remember(cacheKey) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, cacheKey) {
        try {
            value = withContext(Dispatchers.IO) {
                vehicleThumbnailMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    val digest = MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    val folder = File(context.cacheDir, "vehicle-thumbnails").apply { mkdirs() }
                    val file = File(folder, "$digest.png")
                    BitmapFactory.decodeFile(file.path) ?: generate().also { image ->
                        try {
                            currentCoroutineContext().ensureActive()
                            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            folder.listFiles()?.sortedByDescending { it.lastModified() }?.drop(80)?.forEach { it.delete() }
                        } catch (error: Exception) {
                            image.recycle()
                            throw error
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: if (failed) Text("预览不可用", style = MaterialTheme.typography.labelSmall)
            else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
