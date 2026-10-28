package com.haystudio.vehiclelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Properties
import java.util.UUID

class VehicleResourceLibraryTest {
    @Test
    fun importedItemsIgnoreStagingFoldersAndRestorePackIdentity() {
        val root = Files.createTempDirectory("vehicle-library").toFile()
        try {
            val id = UUID.randomUUID().toString()
            writeMetadata(File(root, id), "外部汉 L", "models/hc25.gwm", true, 1234L)
            writeMetadata(File(root, ".pending"), "未完成", "models/broken.gwm", true, 9L)

            val items = VehicleResourceLibrary(root).listImported()

            assertEquals(1, items.size)
            assertEquals("外部汉 L", items.single().label)
            assertEquals(id, items.single().resourcePack)
            assertEquals("models/hc25.gwm", items.single().path)
            assertEquals(1234L, items.single().resourceBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun deleteRemovesImportedPackButRejectsSystemResources() {
        val root = Files.createTempDirectory("vehicle-library-delete").toFile()
        try {
            val id = UUID.randomUUID().toString()
            val importedDirectory = File(root, id)
            writeMetadata(importedDirectory, "待删除", "models/car.gwm", true, 42L)
            val library = VehicleResourceLibrary(root)
            val imported = library.listImported().single()

            library.delete(imported)

            assertFalse(importedDirectory.exists())
            assertThrows(IllegalArgumentException::class.java) {
                library.delete(imported.copy(resourcePack = ""))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeMetadata(
        directory: File,
        label: String,
        model: String,
        fromMyCar: Boolean,
        size: Long
    ) {
        directory.mkdirs()
        Properties().apply {
            setProperty("name", label)
            setProperty("model", model)
            setProperty("mycar", fromMyCar.toString())
            setProperty("vehicle", "测试车")
            setProperty("size", size.toString())
            File(directory, "pack.properties").outputStream().use { store(it, null) }
        }
    }
}
