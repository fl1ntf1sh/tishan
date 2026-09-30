package com.tishan.app.feature.manage

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tishan.app.data.backup.BackupCategory
import com.tishan.app.TishanApplication
import com.tishan.app.core.designsystem.TishanPageHeader
import com.tishan.app.core.designsystem.TishanActionDialog
import com.tishan.app.core.designsystem.TishanFootnote
import com.tishan.app.core.designsystem.TishanInfoCard
import com.tishan.app.core.designsystem.TishanOutlinedCard
import com.tishan.app.core.designsystem.TishanSectionTitle
import com.tishan.app.data.backup.BackupPreview
import com.tishan.app.data.backup.BackupRejectedException
import com.tishan.app.data.backup.BackupRepository
import com.tishan.app.data.settings.SettingsRepository
import com.tishan.app.data.settings.AppSettings
import com.tishan.app.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PendingRestore(
    val bytes: ByteArray,
    val preview: BackupPreview,
)

private enum class AppearancePicker { THEME, FONT }

@Composable
fun SettingsRoute() {
    val context = LocalContext.current
    val application = context.applicationContext as TishanApplication
    ManageToolsScreen(
        backupRepository = application.backupRepository,
        settingsRepository = application.settingsRepository,
        context = context,
    )
}

@Composable
private fun ManageToolsScreen(
    backupRepository: BackupRepository,
    settingsRepository: SettingsRepository,
    context: Context,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.state.collectAsStateWithLifecycle()
    val versionName = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    }
    var categories by remember { mutableStateOf(BackupCategory.all) }
    var showBackup by remember { mutableStateOf(false) }
    BackHandler(enabled = showBackup) { showBackup = false }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var confirmRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri == null || bytes == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("无法打开保存位置")
                }
            }.onSuccess {
                statusMessage = "备份已保存"
            }.onFailure {
                statusMessage = it.userMessage()
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取备份文件")
                }
                bytes to backupRepository.preview(bytes)
            }.onSuccess { (bytes, preview) ->
                pendingRestore = PendingRestore(bytes, preview)
                statusMessage = null
            }.onFailure {
                statusMessage = it.userMessage()
            }
        }
    }

    if (!showBackup) {
        SettingsHomeScreen(
            settings = settings,
            versionName = versionName,
            onTheme = { mode -> settingsRepository.update { it.copy(themeMode = mode) } },
            onFontScale = { scale -> settingsRepository.update { it.copy(fontScale = scale) } },
            onBackup = { showBackup = true },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TishanPageHeader(title = "备份与恢复", subtitle = "妥善备份，安心恢复", onBack = { showBackup = false }, backDescription = "返回设置", backOnRight = true)
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(".ts 保存个人数据；JSON 用于分享题目。恢复 .ts 会替换本地数据。", modifier = Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            TishanSectionTitle("备份内容", Modifier.padding(bottom = 8.dp))
            TishanOutlinedCard {
                Column(modifier = Modifier.padding(horizontal = 17.dp, vertical = 8.dp)) {
                    CategoryRow(
                        label = "题库与试题 · 必选",
                        checked = true,
                        enabled = false,
                        onCheckedChange = {},
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CategoryRow(
                        label = "刷题记录、收藏与错题",
                        checked = BackupCategory.LEARNING in categories,
                        onCheckedChange = { checked -> categories = categories.toggle(BackupCategory.LEARNING, checked) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CategoryRow(
                        label = "模拟测试与成绩",
                        checked = BackupCategory.EXAMS in categories,
                        onCheckedChange = { checked -> categories = categories.toggle(BackupCategory.EXAMS, checked) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CategoryRow(
                        label = "主题与字体",
                        checked = BackupCategory.SETTINGS in categories,
                        onCheckedChange = { checked -> categories = categories.toggle(BackupCategory.SETTINGS, checked) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { categories = BackupCategory.all }, modifier = Modifier.weight(1f)) { Text("全量") }
                OutlinedButton(onClick = { categories = setOf(BackupCategory.QUESTIONS) }, modifier = Modifier.weight(1f)) { Text("仅题库") }
            }
        }
        item {
            TishanSectionTitle("保存与恢复", Modifier.padding(top = 20.dp, bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                runCatching { backupRepository.export(categories) }
                                    .onSuccess {
                                        pendingExport = it
                                        createDocument.launch("tishan-backup.ts")
                                    }
                                    .onFailure { statusMessage = it.userMessage() }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                BackupExportIcon()
                                Text("导出 .ts 备份")
                            }
                        }
                        OutlinedButton(onClick = {
                            openDocument.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                        }, modifier = Modifier.fillMaxWidth()) { Text("选择 .ts 文件恢复") }
            }
        }
        statusMessage?.let { message ->
            item { TishanInfoCard("操作提示") { Text(message) } }
        }
    }

    pendingRestore?.let { pending ->
        RestorePreviewDialog(
            preview = pending.preview,
            onDismiss = { pendingRestore = null },
            onConfirm = {
                pendingRestore = null
                confirmRestore = pending
            },
        )
    }
    confirmRestore?.let { pending ->
        TishanActionDialog(
            title = "确认替换本地数据？",
            primaryLabel = "确认替换",
            onPrimary = {
                confirmRestore = null
                scope.launch {
                    runCatching { backupRepository.restore(pending.bytes) }
                        .onSuccess { statusMessage = "恢复完成，已替换本地数据" }
                        .onFailure { statusMessage = it.userMessage() }
                }
            },
            secondaryLabel = "取消",
            onSecondary = { confirmRestore = null },
            danger = true,
        ) {
            Text("确认后将替换现有数据库，并重置文件未包含的类别。请确认已保存可回退副本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsHomeScreen(
    settings: AppSettings,
    versionName: String,
    onTheme: (ThemeMode) -> Unit,
    onFontScale: (Float) -> Unit,
    onBackup: () -> Unit,
) {
    var picker by remember { mutableStateOf<AppearancePicker?>(null) }
    // 弹窗内的待选字号：点「完成」才写入设置，中途退出视为放弃。
    var previewScale by remember { mutableStateOf<Float?>(null) }
    val themeLabel = when (settings.themeMode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
    }
    val fontLabel = when {
        settings.fontScale < 0.95f -> "小"
        settings.fontScale > 1.07f -> "大"
        else -> "标准"
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TishanPageHeader(title = "设置", subtitle = "舒适阅读，安心存储")
        }
        item {
            TishanSectionTitle("外观", Modifier.padding(bottom = 8.dp))
            TishanOutlinedCard {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SettingsPreferenceRow("theme", "主题", "选择舒适的阅读底色", themeLabel) { picker = AppearancePicker.THEME }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsPreferenceRow("Aa", "字体大小", "调整题目与正文大小", fontLabel) { picker = AppearancePicker.FONT }
                }
            }
        }
        item {
            TishanSectionTitle("备份", Modifier.padding(bottom = 8.dp))
            TishanOutlinedCard(onClick = onBackup) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Box(Modifier.size(width = 44.dp, height = 48.dp), contentAlignment = Alignment.Center) {
                            val iconColor = MaterialTheme.colorScheme.onTertiaryContainer
                            Canvas(Modifier.size(22.dp)) {
                                val shield = Path().apply {
                                    moveTo(size.width * 0.5f, size.height * 0.08f)
                                    lineTo(size.width * 0.85f, size.height * 0.22f)
                                    lineTo(size.width * 0.80f, size.height * 0.63f)
                                    quadraticTo(size.width * 0.68f, size.height * 0.82f, size.width * 0.5f, size.height * 0.94f)
                                    quadraticTo(size.width * 0.32f, size.height * 0.82f, size.width * 0.20f, size.height * 0.63f)
                                    lineTo(size.width * 0.15f, size.height * 0.22f)
                                    close()
                                }
                                drawPath(shield, iconColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()))
                                drawLine(iconColor, Offset(size.width * 0.36f, size.height * 0.49f), Offset(size.width * 0.47f, size.height * 0.61f), 1.6.dp.toPx(), StrokeCap.Round)
                                drawLine(iconColor, Offset(size.width * 0.47f, size.height * 0.61f), Offset(size.width * 0.67f, size.height * 0.39f), 1.6.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
                        Text("选择数据类别 · 导出 / 恢复 .ts", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TishanFootnote("分享题目请在「管理」导出 JSON。")
        }
        item {
            TishanSectionTitle("关于", Modifier.padding(bottom = 8.dp))
            TishanOutlinedCard {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(width = 44.dp, height = 48.dp)) {
                            val mountainInk = MaterialTheme.colorScheme.onPrimaryContainer
                            Canvas(Modifier.padding(10.dp)) {
                                val mountain = Path().apply {
                                    moveTo(size.width * 0.07f, size.height * 0.85f)
                                    lineTo(size.width * 0.42f, size.height * 0.18f)
                                    lineTo(size.width * 0.68f, size.height * 0.65f)
                                    lineTo(size.width * 0.82f, size.height * 0.42f)
                                    lineTo(size.width * 0.97f, size.height * 0.85f)
                                    close()
                                }
                                drawPath(mountain, color = mountainInk, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()))
                            }
                        }
                        Column {
                            Text("题山 / Mt.Q", style = MaterialTheme.typography.titleMedium)
                            Text("本地优先的学习工具", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AboutRow("当前版本", versionName)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AboutRow("作者", "fl1ntf1sh")
                }
            }
        }
    }
    picker?.let { active ->
        Dialog(onDismissRequest = { previewScale = null; picker = null }) {
            // 预览按待选倍数直接换算字号：弹窗窗口的密度由系统提供，
            // 若在弹窗内改动 LocalDensity，系统会直接关闭弹窗（实测）。
            val pendingScale = (previewScale ?: settings.fontScale).coerceIn(0.85f, 1.3f)
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (active == AppearancePicker.THEME) "选择主题" else "字体大小", style = MaterialTheme.typography.headlineSmall)
                    Text(if (active == AppearancePicker.THEME) "找到适合自己的阅读氛围。" else "以一段文字的阅读感受，找到合适的大小。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (active == AppearancePicker.FONT) {
                        TishanOutlinedCard {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("阅读预览", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "诸神惩罚西西弗，让他不停地把巨石推上山顶，而石头由于自身的重量又滚下山去。我们应当想象，西西弗是幸福的。",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = (17f * pendingScale).sp,
                                        lineHeight = (32f * pendingScale).sp,
                                    ),
                                )
                            }
                        }
                    }
                    if (active == AppearancePicker.THEME) {
                        listOf(
                            Triple(ThemeMode.SYSTEM, "跟随系统", "随系统自动切换浅色与深色"),
                            Triple(ThemeMode.LIGHT, "浅色", "温暖纸感，清晰柔和"),
                            Triple(ThemeMode.DARK, "深色", "蓝灰底色，适合夜间阅读"),
                        ).forEach { (mode, title, desc) ->
                            ThemeAppearanceChoice(mode, title, desc, settings.themeMode == mode) { onTheme(mode) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(0.9f to "小", 1.0f to "标准", 1.15f to "大").forEach { (scale, title) ->
                                val chosen = kotlin.math.abs(pendingScale - scale) < 0.04f
                                Surface(onClick = { previewScale = scale }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                                    Column(
                                        Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(10.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text("Aa", style = MaterialTheme.typography.titleMedium)
                                        Text(title, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            previewScale?.let(onFontScale)
                            previewScale = null
                            picker = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("完成") }
                }
            }
        }
    }
}

@Composable
private fun SettingsPreferenceRow(icon: String, title: String, description: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 86.dp).padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(width = 44.dp, height = 44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (icon == "theme") {
                    val ink = MaterialTheme.colorScheme.primary
                    val iconSurface = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(Modifier.size(20.dp)) {
                        val stroke = 1.5.dp.toPx()
                        val knobRadius = 2.dp.toPx()
                        for ((y, knobX) in listOf(0.25f to 0.62f, 0.5f to 0.36f, 0.75f to 0.68f)) {
                            drawLine(ink, Offset(size.width * 0.12f, size.height * y), Offset(size.width * 0.88f, size.height * y), stroke, StrokeCap.Round)
                            drawCircle(iconSurface, knobRadius + stroke, Offset(size.width * knobX, size.height * y))
                            drawCircle(ink, knobRadius, Offset(size.width * knobX, size.height * y))
                        }
                    }
                } else {
                    Text(icon, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ThemeAppearanceChoice(mode: ThemeMode, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(width = 38.dp, height = 48.dp), shape = RoundedCornerShape(9.dp), color = if (mode == ThemeMode.DARK) androidx.compose.ui.graphics.Color(0xFF252932) else androidx.compose.ui.graphics.Color(0xFFFFFCF7), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Canvas(Modifier.fillMaxSize()) {
                    val dark = androidx.compose.ui.graphics.Color(0xFF252932)
                    val lightInk = androidx.compose.ui.graphics.Color(0xFFC0D0E1)
                    val blueInk = androidx.compose.ui.graphics.Color(0xFF566B82)
                    if (mode == ThemeMode.SYSTEM) drawRect(dark, topLeft = Offset(size.width * 0.5f, 0f), size = Size(size.width * 0.5f, size.height))
                    val ink = if (mode == ThemeMode.DARK) lightInk else blueInk
                    drawLine(ink, Offset(size.width * 0.26f, size.height * 0.40f), Offset(size.width * 0.70f, size.height * 0.40f), 1.7.dp.toPx(), StrokeCap.Round)
                    drawLine(ink, Offset(size.width * 0.26f, size.height * 0.58f), Offset(size.width * 0.59f, size.height * 0.58f), 1.7.dp.toPx(), StrokeCap.Round)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (selected) "✓" else "○", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CategoryRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun BackupExportIcon() {
    val color = MaterialTheme.colorScheme.onPrimary
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.7.dp.toPx()
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.08f), Offset(size.width * 0.5f, size.height * 0.68f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.27f, size.height * 0.32f), Offset(size.width * 0.5f, size.height * 0.08f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.73f, size.height * 0.32f), Offset(size.width * 0.5f, size.height * 0.08f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.55f), Offset(size.width * 0.15f, size.height * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.85f, size.height * 0.55f), Offset(size.width * 0.85f, size.height * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.9f), Offset(size.width * 0.85f, size.height * 0.9f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RestorePreviewDialog(
    preview: BackupPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val categories = preview.manifest.categories.joinToString("、") { it.categoryLabel() }
    val counts = preview.manifest.counts.entries.joinToString("\n") { (key, value) ->
        "${key.categoryLabel()}：$value"
    }
    TishanActionDialog(
        title = "恢复预览",
        primaryLabel = "继续",
        onPrimary = onConfirm,
        secondaryLabel = "取消",
        onSecondary = onDismiss,
    ) {
        Text("包含：$categories\n$counts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(14.dp), color = com.tishan.app.core.designsystem.theme.TishanSemanticColors.current.warningContainer) {
            Text("将恢复文件所含类别，并重置未包含类别。当前本地数据会被替换，不会合并。", Modifier.fillMaxWidth().padding(14.dp), color = com.tishan.app.core.designsystem.theme.TishanSemanticColors.current.onWarningContainer, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Set<String>.toggle(value: String, checked: Boolean): Set<String> =
    if (checked) this + value else this - value

private fun String.categoryLabel(): String = when (this) {
    BackupCategory.QUESTIONS -> "题库与题目"
    BackupCategory.LEARNING -> "刷题记录/收藏/错题"
    BackupCategory.EXAMS -> "模拟测试/成绩"
    BackupCategory.SETTINGS -> "主题/字体"
    else -> this
}

private fun Throwable.userMessage(): String = when (this) {
    is BackupRejectedException -> message ?: "备份被拒绝"
    else -> message ?: "操作失败，请重试"
}
