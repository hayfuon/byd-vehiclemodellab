package com.haystudio.vehiclelab.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class VehicleResourcePackTest {
    @Test
    fun roundTripPreservesBytesAndDeduplicatesAcrossSources() = temporary { directory ->
        val shared = ByteArray(8192) { (it % 127).toByte() }
        val archive = File(directory, "test.hcvpack")
        VehicleResourcePack.Builder(File(directory, "work")).use { builder ->
            builder.add("mycar", "models/car.gwm", shared)
            builder.add("sr", "Textures/共享贴图.ktx", shared)
            builder.add("mycar", "views/a.view", byteArrayOf(1, 2, 3))
            builder.write(
                archive.outputStream(),
                VehicleResourcePack.Info(
                    name = "测试车",
                    model = "models/car.gwm",
                    fromMyCar = true,
                    vehicle = "汉L",
                    settings = mapOf("paint" to "2")
                )
            )
        }

        ZipFile(archive).use { zip ->
            assertEquals(2, zip.entries().asSequence().count { it.name.startsWith("blobs/") })
        }
        val output = File(directory, "import")
        val info = VehicleResourcePack.extract(archive, output)
        assertEquals("测试车", info.name)
        assertEquals("2", info.settings["paint"])
        GpakReader.open(File(output, "BydMyCar.apk").path, ModelRepository.GPak_ENTRY).use {
            assertArrayEquals(shared, it.read("models/car.gwm"))
            assertArrayEquals(byteArrayOf(1, 2, 3), it.read("views/a.view"))
        }
        GpakReader.open(File(output, "BydSR.apk").path, ModelRepository.GPak_ENTRY).use {
            assertArrayEquals(shared, it.read("Textures/共享贴图.ktx"))
        }
    }

    @Test
    fun corruptionAndInvalidSourcesAreRejected() = temporary { directory ->
        VehicleResourcePack.Builder(File(directory, "work")).use { builder ->
            assertThrows(IllegalArgumentException::class.java) {
                builder.add("../escape", "test", byteArrayOf(1))
            }
            builder.add("mycar", "model", byteArrayOf(5))
            val archive = File(directory, "pack")
            builder.write(
                archive.outputStream(),
                VehicleResourcePack.Info("车", "model", true, "")
            )
            val damaged = File(directory, "damaged")
            ZipOutputStream(damaged.outputStream()).use { output ->
                ZipFile(archive).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        output.putNextEntry(ZipEntry(entry.name))
                        output.write(
                            if (entry.name.startsWith("blobs/")) byteArrayOf(6)
                            else zip.getInputStream(entry).readBytes()
                        )
                        output.closeEntry()
                    }
                }
            }
            assertThrows(Exception::class.java) {
                VehicleResourcePack.extract(damaged, File(directory, "bad"))
            }
        }
    }

    @Test
    fun aliasesInsideOneSourceKeepExactLengths() = temporary { directory ->
        VehicleResourcePack.Builder(File(directory, "work")).use { builder ->
            builder.add("mycar", "a", byteArrayOf(1, 2))
            builder.add("mycar", "b", byteArrayOf(1, 2))
            builder.add("mycar", "c", byteArrayOf(3))
            val archive = File(directory, "pack")
            builder.write(
                archive.outputStream(),
                VehicleResourcePack.Info("车", "a", true, "")
            )
            val output = File(directory, "out")
            VehicleResourcePack.extract(archive, output)
            GpakReader.open(File(output, "BydMyCar.apk").path, ModelRepository.GPak_ENTRY).use { reader ->
                assertArrayEquals(reader.read("a"), reader.read("b"))
                assertArrayEquals(byteArrayOf(3), reader.read("c"))
            }
        }
    }

    @Test
    fun gpakReaderReportsOnlyResourcesActuallyRead() = temporary { directory ->
        VehicleResourcePack.Builder(File(directory, "work")).use { builder ->
            builder.add("mycar", "used", byteArrayOf(1, 2, 3))
            builder.add("mycar", "unused", byteArrayOf(4, 5, 6))
            val archive = File(directory, "pack")
            builder.write(
                archive.outputStream(),
                VehicleResourcePack.Info("车", "used", true, "")
            )
            val output = File(directory, "out")
            VehicleResourcePack.extract(archive, output)
            GpakReader.open(File(output, "BydMyCar.apk").path, ModelRepository.GPak_ENTRY).use { reader ->
                val captured = linkedMapOf<String, ByteArray>()
                reader.onRead = { name, bytes -> captured[name] = bytes }
                assertArrayEquals(byteArrayOf(1, 2, 3), reader.read("used"))
                assertEquals(setOf("used"), captured.keys)
                assertArrayEquals(byteArrayOf(1, 2, 3), captured.getValue("used"))
            }
        }
    }

    @Test
    fun standaloneRepositoryRejectsIncompleteImportedResources() = temporary { directory ->
        val archive = File(directory, "pack")
        VehicleResourcePack.Builder(File(directory, "work")).use { builder ->
            val path = "MyCar/Test/Common/Models/TEST/TEST.gwm"
            builder.add("mycar", path, byteArrayOf(1, 2, 3))
            builder.add(
                "mycar",
                path.removeSuffix(".gwm") + ".gmdl",
                """{"Class":"Model","MeshPath":"$path"}""".toByteArray()
            )
            builder.write(
                archive.outputStream(),
                VehicleResourcePack.Info("导入模型", path, true, "测试车")
            )
        }
        val imported = File(directory, "imported")
        VehicleResourcePack.extract(archive, imported)
        ModelRepository(
            localResourceDirectory = imported,
            standalone = true,
            vehicleIdentity = "测试车"
        ).use { repository ->
            repository.open()
            val models = repository.listModels()
            assertEquals(0, models.size)
            assertEquals("", repository.vehicleName)
        }
    }

    private fun temporary(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("vehicle-pack").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
