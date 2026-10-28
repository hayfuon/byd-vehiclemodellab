package com.haystudio.vehiclelab.api

import com.haystudio.vehiclelab.data.*
import com.haystudio.vehiclelab.render.*
import java.util.concurrent.Executor

/** 控制命令在宿主提供的渲染队列执行；结果由宿主回调执行器交付。资源解析和文件 IO 应在后台执行。 */
class VehicleModelController(
    private val renderer: CarRenderer,
    private val renderExecutor: Executor,
    private val callbackExecutor: Executor
) {
    private var pendingLoad: ((Result<Unit>)->Unit)? = null
    private fun <T> submit(callback: ((Result<T>)->Unit)? = null, action:()->T) {
        renderExecutor.execute {
            val result=runCatching(action)
            if(callback!=null)callbackExecutor.execute{callback(result)}
        }
    }

    /** 高级扩展入口，与普通命令使用同一 GL 队列；回调报告执行失败。 */
    fun edit(callback: ((Result<Unit>)->Unit)? = null, action: CarRenderer.()->Unit) =
        submit(callback){renderer.action();renderer.notifyTouch()}

    fun apply(patch:VehicleInteractionPatch, callback:((Result<VehicleInteractionState>)->Unit)?=null) =
        submit(callback){renderer.interactionApi.apply(patch)}

    fun interaction(callback:(Result<VehicleInteractionState>)->Unit)=submit(callback){renderer.interactionApi.snapshot()}

    fun visuals(state:VehicleVisualState,callback:((Result<Unit>)->Unit)?=null) {
        val value=state.normalized()
        edit(callback) {
            interiorAmbientColor=value.ambientColor.toFloatArray()
            airflowMode=value.airflowMode;airflowFan=value.airflowFan;airflowDirections=value.airflowDirections
            weatherMode=value.weather;weatherIntensity=value.weatherIntensity;weatherTemperatureC=value.temperatureC
            groundMaterialStyle=value.groundMaterial;plateAppearance=value.plate;licensePlateEnabled=value.plateEnabled
            turnFrequencyPerMinute=value.turnFrequencyPerMinute;groundLightEnabled=value.groundLights
            lampReflectionEnabled=value.lampReflections;viewOptions=value.renderOptions
        }
    }

    fun visuals(callback:(Result<VehicleVisualState>)->Unit)=submit(callback){
        VehicleVisualState(renderer.interiorAmbientColor.toList(),renderer.airflowMode,renderer.airflowFan,
            renderer.airflowDirections.toSet(),renderer.weatherMode,renderer.weatherIntensity,renderer.weatherTemperatureC,
            renderer.groundMaterialStyle,renderer.plateAppearance,renderer.licensePlateEnabled,renderer.turnFrequencyPerMinute,
            renderer.groundLightEnabled,renderer.lampReflectionEnabled,renderer.viewOptions).normalized()
    }

    /** 返回模型实际 GPU 初始化结果；不能仅凭命令入队判定加载成功。 */
    fun load(loaded:ModelRepository.Loaded,camera:DesktopCamera,options:DesktopViewOptions,period:DayPeriod,
        scene:SceneAsset?,callback:(Result<Unit>)->Unit) = submit<Unit>({result->if(result.isFailure)callback(result)}) {
        pendingLoad?.let{previous->callbackExecutor.execute{previous(Result.failure(java.util.concurrent.CancellationException("已切换到新模型")))}}
        pendingLoad=callback
        renderer.onModelInitialized={model,error->
            if(model===loaded.model) {
                renderer.onModelInitialized=null
                pendingLoad=null
                callbackExecutor.execute{callback(if(error==null)Result.success(Unit) else Result.failure(error))}
            }
        }
        renderer.setDesktopCamera(camera);renderer.viewOptions=options
        renderer.setPalette(loaded.palette,minOf(2,loaded.colors.lastIndex).coerceAtLeast(0))
        renderer.setLamps(loaded.lamps);renderer.setVehicleBeams(loaded.beams);renderer.setHiddenNodes(loaded.hidden)
        renderer.setDayPeriod(period);renderer.setScene(scene);renderer.setModel(loaded.model)
    }

    fun scene(period:DayPeriod,scene:SceneAsset?,callback:((Result<Unit>)->Unit)?=null)=
        edit(callback){transitionDayPeriod(period,scene)}
    fun environment(bytes:ByteArray?,name:String,callback:((Result<Unit>)->Unit)?=null) {
        val copy=bytes?.copyOf();edit(callback){setPanoramaEnvironment(copy,name)}
    }
    fun camera(preset:CameraPreset,callback:((Result<Unit>)->Unit)?=null)=edit(callback){selectCameraPreset(preset)}
    fun gestureBegin()=edit{beginCameraGesture()}
    fun gestureDrag(dx:Float,dy:Float,height:Float)=edit{dragCameraPixels(dx,dy,height)}
    fun gestureRelease(vx:Float,vy:Float,height:Float)=edit{flingCameraPixels(vx,vy,height)}
    fun zoom(delta:Float)=edit{zoomCamera(delta)}
    fun autoRotate(enabled:Boolean)=edit{autoRotate=enabled}
    fun viewportInset(widthPixels:Float)=edit{settingsPanelWidthPixels=widthPixels.coerceAtLeast(0f)}
    fun door(corner:VehicleCorner,open:Boolean)=edit{setDoorOpen(corner,open)}
    fun window(corner:VehicleCorner,open:Boolean)=edit{setWindowOpen(corner,open)}
    fun progress(node:String,progress:Float?)=edit{setNodeProgress(node,progress)}
    fun body(state:VehicleBodyState,callback:((Result<Unit>)->Unit)?=null) {
        val value=state.validated()
        edit(callback){
            value.doors.forEach{(corner,open)->setDoorOpen(corner,open)}
            value.windows.forEach{(corner,open)->setWindowOpen(corner,open)}
            value.progress.forEach{(node,progress)->setNodeProgress(node,progress)}
        }
    }
    fun body(callback:(Result<VehicleBodyState>)->Unit)=submit(callback){
        VehicleBodyState(VehicleCorner.entries.associateWith(renderer::doorOpen),
            VehicleCorner.entries.associateWith(renderer::windowOpen),renderer.nodeProgressSnapshot())
    }
    fun bodyColor(index:Int)=edit{selectColor(index)}
    fun trimColor(index:Int)=edit{selectTrimColor(index)}
    fun paint(region:PaintRegion,style:PaintStyle?)=edit{customizePaint(region,style)}
    fun charging(port:ChargingPort,powerKw:Float,command:ChargingCommand,callback:((Result<Boolean>)->Unit)?=null)=submit(callback){
        renderer.selectedChargingPort=port;renderer.chargingPowerKw=powerKw
        when(command){
            ChargingCommand.CONNECT->renderer.connectCharging()
            ChargingCommand.START->renderer.startCharging()
            ChargingCommand.STOP->renderer.stopCharging()
            ChargingCommand.COMPLETE->renderer.completeCharging()
            ChargingCommand.DISCONNECT->renderer.disconnectCharging()
        }
    }
    fun charging(callback:(Result<ChargingSnapshot>)->Unit)=submit(callback){renderer.chargingSnapshot()}
    /** 低频后台调用；只返回原始遥测，不引入悬浮调试 UI 或定时采样。 */
    fun diagnostics():RenderDebugSnapshot=renderer.sampleDebugInfo()
    fun diagnosticsEnabled(enabled:Boolean)=edit{debugInfoEnabled=enabled}
    fun clearWeather()=edit{clearSurfaceWeather()}
    fun weatherSeed(seed:Long)=edit{setWeatherSeed(seed)}
}
