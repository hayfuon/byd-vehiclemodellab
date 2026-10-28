package com.haystudio.vehiclelab.data

object RoofCurtain {
    const val DIRECTORY = "MyCar/AppCommon/Dynasty/HC2/Common/Models/SunRoof/"
    private const val PREFIX = "RoofCurtain_"
    
    private const val CLOSED_PANEL_FORWARD_OFFSET = -.075f

    fun closedPanelForwardOffset(): Float = CLOSED_PANEL_FORWARD_OFFSET

    fun assemblyRoot(assembly: FloatArray, exported: FloatArray): FloatArray =
        GwmNodeTree.multiply(assembly, GwmNodeTree.invert(exported)).also { it[12] += CLOSED_PANEL_FORWARD_OFFSET }

    fun fitBounds(source: FloatArray, opening: FloatArray): FloatArray = floatArrayOf(
        opening[0]-.012f,source[1],opening[2]-.012f,
        opening[3]+.012f,source[4],opening[5]+.012f)

    fun frontLayerOffset(front:FloatArray,rear:FloatArray):Float = minOf(-.003f,rear[1]-front[4]-.003f)

    fun sharedProgress(progress:Map<NodeClip,Float>):Float = progress.entries
        .filter { VehicleMotionChannel.ofClip(it.key.name)==VehicleMotionChannel.CURTAIN }
        .maxByOrNull { it.key.duration }?.value ?: 0f

    fun retraction(bounds:FloatArray, slot:Float, travel:Float, progress:Float):FloatArray {
        val t=progress.coerceIn(0f,1f)
        val left=minOf(bounds[0]+travel*t,slot+.02f)
        val right=minOf(bounds[3]+travel*t,slot+.02f)
        val scale=((right-left)/(bounds[3]-bounds[0]).coerceAtLeast(.001f)).coerceAtLeast(.0001f)
        return GwmNodeTree.IDENTITY.copyOf().apply { this[0]=scale;this[12]=left-bounds[0]*scale }
    }

    private fun matte(spec:MaterialSpec):MaterialSpec {
        val result=spec.copy(path=spec.path+"#深灰磨砂",base=floatArrayOf(.19f,.20f,.21f),
            metallic=0f,roughness=.82f,coat=0f,baseTex=null,tintTex=null,normalTex=null,
            ormTex=null,alphaMaskTex=null,alphaCutoff=0f,
            uniforms=spec.uniforms.filterKeys { it !in setOf("s_texColor","s_texBump","s_alpha") },
            vsOptions=spec.vsOptions-setOf("BUMP_MAP"),
            fsOptions=spec.fsOptions-setOf("BASE_MAP","BUMP_MAP","NEEDS_TBN","UVTRANSFORM_BUMP","ALPHA_TEST"))
        return result.copy(materialBlock=MaterialLibrary.pbrBlock(result))
    }

    fun bounds(positions:FloatArray):FloatArray = floatArrayOf(
        positions.filterIndexed{i,_->i%3==0}.min(),positions.filterIndexed{i,_->i%3==1}.min(),positions.filterIndexed{i,_->i%3==2}.min(),
        positions.filterIndexed{i,_->i%3==0}.max(),positions.filterIndexed{i,_->i%3==1}.max(),positions.filterIndexed{i,_->i%3==2}.max())

    fun attach(car: GwmModel, curtain: GwmModel, clips: List<NodeClip>): GwmModel {
        val offset = car.nodes.size
        val nodes = car.nodes + curtain.nodes.map { n ->
            GwmNode(PREFIX + n.name, if (n.parent < 0) -1 else n.parent + offset,
                n.local, n.childCount, n.meshes)
        }
        val opening=car.meshes.filter { it.part=="Interior_Chair_sunroof" }
        val source=curtain.meshes.takeIf { it.isNotEmpty() }?.let { bounds(it.flatMap { m->m.positions.toList() }.toFloatArray()) }
        val fitted=if(source!=null && opening.isNotEmpty()) fitBounds(source,bounds(opening.flatMap { it.positions.toList() }.toFloatArray())) else source
        val frontOffset=if(curtain.meshes.size>=2)
            frontLayerOffset(bounds(curtain.meshes[0].positions),bounds(curtain.meshes[1].positions)) else -.003f
        val materials=HashMap<String,MaterialSpec>()
        val meshes = car.meshes + curtain.meshes.mapIndexed { index,m ->
            val p=m.positions.copyOf();val normals=m.normals.copyOf();val tangents=m.tangents.copyOf()
            if(source!=null && fitted!=null) {
                val sx=(fitted[3]-fitted[0])/(source[3]-source[0]);val sz=(fitted[5]-fitted[2])/(source[5]-source[2])
                val local=bounds(p)
                for(i in p.indices step 3) {
                    p[i]=fitted[0]+(p[i]-source[0])*sx
                    
                    if(index==0 && kotlin.math.abs(m.positions[i]-local[3])<.002f) p[i]+=.004f
                    if(index==1 && kotlin.math.abs(m.positions[i]-local[0])<.002f) p[i]-=.004f
                    p[i+1]+=if(index==0) frontOffset else 0f
                    p[i+2]=fitted[2]+(p[i+2]-source[2])*sz
                    normals[i]/=sx;normals[i+2]/=sz
                    val len=kotlin.math.sqrt(normals[i]*normals[i]+normals[i+1]*normals[i+1]+normals[i+2]*normals[i+2]).coerceAtLeast(.0001f)
                    for(k in 0..2) normals[i+k]/=len
                }
            }
            MeshData(PREFIX + m.name, PREFIX + m.part, p, normals, m.uvs,
                m.indices, materials.getOrPut(m.material.path){matte(m.material)}, m.node + offset, tangents)
        }
        val motion = clips.map { it.renamed("Sunshade_Clip", PREFIX + it.target) }.groupBy { it.target }
        return GwmModel(meshes, car.boundsMin, car.boundsMax, car.rot, car.scale, car.baked,
            car.parts + curtain.parts.mapKeys { PREFIX + it.key }, nodes,
            car.nodeWorlds + curtain.nodeWorlds,
            nodes.mapIndexed { i,n -> n.name to i }.toMap(),car.textures,car.clips + motion,car.lights,car.environment)
    }
}
