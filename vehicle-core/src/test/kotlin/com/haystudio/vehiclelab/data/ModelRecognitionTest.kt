package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelRecognitionTest {
    @Test
    fun missingResourcesDoNotIdentifyDeviceAsVehicle() {
        val root = Files.createTempDirectory("model-recognition").toFile()
        try {
            ModelRepository(root, standalone = true, vehicleIdentity = "Redmi K30 Pro").use { repo ->
                repo.open()
                assertTrue(repo.listModels().isEmpty())
                assertEquals("", repo.vehicleName)
                assertEquals("", repo.refreshVehicle())
            }
        } finally { root.deleteRecursively() }
    }

    @Test
    fun invalidResourceArchiveDoesNotProduceCandidates() {
        val root = Files.createTempDirectory("invalid-model").toFile()
        try {
            File(root, "BydMyCar.apk").writeText("invalid archive")
            ModelRepository(root, standalone = true, vehicleIdentity = "汉L").use { repo ->
                repo.open()
                assertTrue(repo.listModels().isEmpty())
                assertEquals("", repo.vehicleName)
            }
        } finally { root.deleteRecursively() }
    }
}
