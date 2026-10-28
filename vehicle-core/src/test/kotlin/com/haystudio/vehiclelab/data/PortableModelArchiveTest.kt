package com.haystudio.vehiclelab.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class PortableModelArchiveTest {
    @Test fun portableSnapshotRemovesSourcePathsAndRestoresGeometryMaterialsAndAnimation() {
        val directory=Files.createTempDirectory("portable-model").toFile()
        try {
            val path="MyCar/private/user/Vehicle/Material/Light_carpaint.mat"
            val texture="MyCar/private/user/Vehicle/Textures/body.ktx"
            val material=DefaultMaterials.OTHER.copy(path=path,baseTex=texture,uniforms=mapOf("s_texColor" to texture,"u_test" to floatArrayOf(1f,2f)))
            val clip=NodeClip("Door_LeftFront","Door_LeftFront",1f,Array(3){AnimTrack(floatArrayOf(0f,1f),floatArrayOf(0f,1f))},arrayOfNulls(3),arrayOfNulls(3))
            val shader="#version 300 es\n// private/user/source.glsl\n// step2. calculate alpha\nvoid main(){}\n"
            val model=GwmModel(listOf(MeshData("body","Body",floatArrayOf(0f,0f,0f,1f,0f,0f,0f,1f,0f),FloatArray(9),FloatArray(6),intArrayOf(0,1,2),material)),
                floatArrayOf(0f,0f,0f),floatArrayOf(1f,1f,1f),FloatArray(3),floatArrayOf(1f,1f,1f),true,
                textures=mapOf(texture.replace(".ktx","-mipmap.ktx") to byteArrayOf(1,2,3)),clips=mapOf("Door_LeftFront" to listOf(clip)),auxiliaryShaders=mapOf("vertex" to shader))
            val bundle=PortableModelBundle(model,null,null,emptySet(),null,
                DesktopCamera("Default",4f,20f,45f,FloatArray(3),FloatArray(3),50f,36f,1.7f,0,0f,0f,1f),
                CameraPhysicsConfig(),DesktopViewOptions(),DayPeriod.entries.associateWith{SceneAsset(emptyList())})
            val archive=File(directory,"model.hcvpack")
            PortableModelArchive.write(archive.outputStream(),bundle,File(directory,"work"))
            ZipFile(archive).use { zip ->
                val text=zip.getInputStream(zip.getEntry("model.json")).reader().readText()
                assertFalse(text.contains("MyCar"));assertFalse(text.contains("private/user"))
                val manifest=zip.getInputStream(zip.getEntry("manifest.json")).reader().readText()
                assertTrue(manifest.contains("createdAt"));assertTrue(manifest.contains("sha256"))
                assertFalse(manifest.contains("vehicle"));assertFalse(manifest.contains("apk"))
            }
            val restored=File(directory,"restored")
            PortableModelArchive.extract(archive,restored)
            val loaded=PortableModelArchive.read(restored)
            assertArrayEquals(model.meshes.single().positions,loaded.model.meshes.single().positions,0f)
            val mat=loaded.model.meshes.single().material
            assertTrue(mat.light)
            assertArrayEquals(byteArrayOf(1,2,3),loaded.model.textures.getValue(mat.baseTex!!))
            assertArrayEquals(floatArrayOf(1f,2f),mat.uniforms["u_test"] as FloatArray,0f)
            assertArrayEquals(clip.localAt(.6f),loaded.model.clips.getValue("Door_LeftFront").single().localAt(.6f),0f)
            assertTrue(loaded.model.auxiliaryShaders.getValue("vertex").contains("// step2. calculate alpha"))
            assertFalse(loaded.model.auxiliaryShaders.getValue("vertex").contains("private/user"))
            val corrupt=File(directory,"corrupt.hcvpack")
            fun rewrite(extra:Boolean) {
                java.util.zip.ZipOutputStream(corrupt.outputStream()).use {output -> ZipFile(archive).use {zip ->
                    zip.entries().asSequence().forEach {entry ->
                        output.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        val bytes=zip.getInputStream(entry).readBytes()
                        if(!extra && entry.name=="model.json")bytes[0]='!'.code.toByte()
                        output.write(bytes);output.closeEntry()
                    }
                    if(extra){output.putNextEntry(java.util.zip.ZipEntry("../outside"));output.write(1);output.closeEntry()}
                }}
            }
            rewrite(false)
            val bad=File(directory,"bad")
            assertThrows(IllegalArgumentException::class.java){PortableModelArchive.extract(corrupt,bad)}
            assertFalse(bad.exists())
            rewrite(true)
            assertThrows(IllegalArgumentException::class.java){PortableModelArchive.extract(corrupt,bad)}
            assertFalse(File(directory,"outside").exists())
            assertTrue(restored.walk().none{it.name.endsWith(".apk")})
        } finally { directory.deleteRecursively() }
    }
}
