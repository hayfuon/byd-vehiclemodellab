package com.haystudio.vehiclelab.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ShaderLibraryTest {
    @Test fun installsHeadersAndRejectsTraversalWithoutDestroyingExistingLibrary() {
        val root = Files.createTempDirectory("shader-library").toFile()
        val destination = File(root, "engine")
        try {
            ShaderLibrary.install(archive(listOf("engine/vs_common.es", "engine/fs_pbr.es", "engine/fragmentOutput.es")), destination)
            assertEquals("void main() {}", File(destination, "fs_pbr.es").readText())
            assertThrows(IllegalArgumentException::class.java) {
                ShaderLibrary.install(archive(listOf("../escape.es")), destination)
            }
            assertEquals("void main() {}", File(destination, "fs_pbr.es").readText())
            assertFalse(File(root, "escape.es").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsIncompleteIncludesWithoutReplacingTheCurrentLibrary() {
        val root=Files.createTempDirectory("shader-dependencies").toFile()
        try {
            val destination=File(root,"engine")
            ShaderLibrary.install(archive(listOf("vs_common.es","fs_pbr.es","fragmentOutput.es")),destination)
            val output=ByteArrayOutputStream()
            ZipOutputStream(output).use {zip->
                listOf("vs_common.es","fs_pbr.es","fragmentOutput.es").forEach {name->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write((if(name=="fs_pbr.es")"#include<missingLighting>\nvoid main(){}" else "void main(){}").toByteArray())
                    zip.closeEntry()
                }
            }
            assertThrows(IllegalArgumentException::class.java){ShaderLibrary.install(ByteArrayInputStream(output.toByteArray()),destination)}
            assertEquals("void main() {}",File(destination,"fs_pbr.es").readText())
        }finally{root.deleteRecursively()}
    }

    private fun archive(names: List<String>): ByteArrayInputStream {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("void main() {}".toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bytes.toByteArray())
    }
}
