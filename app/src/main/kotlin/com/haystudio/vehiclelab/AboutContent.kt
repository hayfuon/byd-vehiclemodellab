package com.haystudio.vehiclelab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AboutContent(versionName:String) {
    var licensesOpen by remember { mutableStateOf(false) }
    var licenseFile by remember { mutableStateOf<String?>(null) }
    var licenseTitle by remember { mutableStateOf("") }
    val context=LocalContext.current
    val uri=LocalUriHandler.current
    fun openLink(link:String) {
        runCatching {uri.openUri(link)}.onFailure {
            android.widget.Toast.makeText(context,"无法打开链接",android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("BYD VehicleModelLab",style=MaterialTheme.typography.titleMedium)
        Text("v$versionName · HayFuon",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical=8.dp))
        AboutRow("开源协议","Apache License 2.0",Icons.Default.Description) {
            licenseTitle="Apache License 2.0";licenseFile="licenses/Apache-2.0.txt"
        }
        AboutRow("开源许可","第三方依赖与资源",Icons.Default.Code) { licensesOpen=true }
    }
    AppDialog(visible=licensesOpen && licenseFile==null,onDismissRequest={licensesOpen=false},title="开源许可") {
            Column {
                fun showLicense(item:OpenSourceItem) {
                    item.licenseAsset?.let {licenseTitle=item.license;licenseFile=it}
                }
                LicenseLabel("运行依赖")
                OpenSourceCatalog.runtime.forEach { item -> LicenseRow(item,{openLink(item.source)},{showLicense(item)}) }
                LicenseLabel("构建与测试")
                OpenSourceCatalog.development.forEach { item -> LicenseRow(item,{openLink(item.source)},{showLicense(item)}) }
                LicenseLabel("环境资源")
                OpenSourceCatalog.environments.forEach { item ->
                    LicenseRow(item,{openLink(item.source)},{openLink("https://creativecommons.org/publicdomain/zero/1.0/")})
                }
                Text("外部车辆模型、贴图、动画及着色器遵循各自许可。",style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=16.dp))
            }
    }
    var retainedFile by remember {mutableStateOf("licenses/Apache-2.0.txt")}
    SideEffect {licenseFile?.let {retainedFile=it}}
    val file=licenseFile ?: retainedFile
        val content by produceState<String?>(null,file) {
            value=withContext(Dispatchers.IO) {
                runCatching {context.assets.open(file).bufferedReader().use {it.readText()}}.getOrDefault("许可文件读取失败")
            }
        }
    AppDialog(visible=licenseFile!=null,onDismissRequest={licenseFile=null},title=licenseTitle) {
        Text(content ?: "正在读取…",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AboutRow(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) {
    ListItem(headlineContent={Text(title)},supportingContent={Text(subtitle)},
        leadingContent={Icon(icon,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},
        modifier=Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick),
        colors=ListItemDefaults.colors(containerColor=androidx.compose.ui.graphics.Color.Transparent))
}

@Composable
private fun LicenseLabel(text:String) {
    Text(text,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,
        modifier=Modifier.padding(top=16.dp,bottom=8.dp))
}

@Composable
private fun LicenseRow(item:OpenSourceItem,onSource:()->Unit,onLicense:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(item.name,style=MaterialTheme.typography.bodyMedium)
            Text(item.license,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,
                modifier=Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick=if(item.licenseAsset!=null || item.license=="CC0-1.0")onLicense else onSource).padding(vertical=4.dp))
        }
        IconButton(onClick=onSource,modifier=Modifier.size(40.dp)) { Icon(Icons.Default.OpenInNew,"${item.name} 来源",Modifier.size(18.dp)) }
    }
}
