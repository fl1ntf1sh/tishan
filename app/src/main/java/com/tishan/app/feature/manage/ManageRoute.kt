package com.tishan.app.feature.manage

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tishan.app.TishanApplication
import com.tishan.app.core.designsystem.TishanPageHeader
import com.tishan.app.core.designsystem.TishanActionDialog
import com.tishan.app.core.designsystem.TishanEmptyState
import com.tishan.app.core.designsystem.TishanInfoCard
import com.tishan.app.core.designsystem.TishanOutlinedCard
import com.tishan.app.core.designsystem.TishanMoreButton
import com.tishan.app.core.designsystem.TishanResourceTile
import com.tishan.app.core.designsystem.TishanSectionTitle
import com.tishan.app.core.designsystem.theme.TishanSemanticColors
import com.tishan.app.data.local.LibrarySummary
import com.tishan.app.data.repository.ImportMode
import com.tishan.app.data.repository.ImportPreview
import com.tishan.app.data.repository.ImportRejectedException
import com.tishan.app.data.repository.ImportResult
import com.tishan.app.data.repository.LibraryRepository
import com.tishan.app.data.saf.QuestionResourceReader
import com.tishan.app.data.saf.ShareFileCache
import com.tishan.app.domain.library.LibraryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface OpenAction {
    data class New(val libraryType: LibraryType) : OpenAction
    data class Existing(val libraryId: String, val mode: ImportMode) : OpenAction
}

private data class PendingImport(
    val preview: ImportPreview,
    val initialName: String,
    val fileName: String,
)

private data class PendingSave(
    val fileName: String,
    val content: String,
)

private data class CompletedImport(val name: String, val result: ImportResult)

@Composable
fun ManageRoute() {
    val context = LocalContext.current
    val application = context.applicationContext as TishanApplication
    ManageScreen(
        repository = application.libraryRepository,
        reader = remember(context) { QuestionResourceReader(context) },
        shareCache = remember(context) { ShareFileCache(context) },
        context = context,
    )
}

@Composable
private fun ManageScreen(
    repository: LibraryRepository,
    reader: QuestionResourceReader,
    shareCache: ShareFileCache,
    context: Context,
) {
    val scope = rememberCoroutineScope()
    var libraries by remember { mutableStateOf<List<LibrarySummary>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var openAction by remember { mutableStateOf<OpenAction?>(null) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var renameTarget by remember { mutableStateOf<LibrarySummary?>(null) }
    var deleteTarget by remember { mutableStateOf<LibrarySummary?>(null) }
    var exportTarget by remember { mutableStateOf<LibrarySummary?>(null) }
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    var completedImport by remember { mutableStateOf<CompletedImport?>(null) }
    var importBusy by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            runCatching { repository.listLibraries() }
                .onSuccess { libraries = it }
                .onFailure { statusMessage = it.userMessage() }
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val action = openAction
        openAction = null
        if (uri == null || action == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val rawJson = reader.read(uri)
                    val displayName = reader.displayName(uri).orEmpty()
                    val preview = when (action) {
                        is OpenAction.New -> repository.previewNew(
                            libraryType = action.libraryType,
                            suggestedName = displayName.withoutExtension().ifBlank { action.libraryType.externalValue },
                            rawJson = rawJson,
                        )

                        is OpenAction.Existing -> repository.previewExisting(
                            libraryId = action.libraryId,
                            rawJson = rawJson,
                            mode = action.mode,
                        )
                    }
                    PendingImport(preview, preview.suggestedName, displayName)
                }
            }.onSuccess {
                pendingImport = it
                importError = null
            }.onFailure {
                statusMessage = it.userMessage()
            }
        }
    }

    fun selectImport(action: OpenAction) {
        openAction = action
        openDocument.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val save = pendingSave
        pendingSave = null
        if (uri == null || save == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                        checkNotNull(writer) { "无法打开保存位置" }
                        writer.write(save.content)
                    }
                }
            }.onSuccess {
                statusMessage = "已保存 ${save.fileName}"
            }.onFailure {
                statusMessage = it.userMessage()
            }
        }
    }

    fun exportResource(target: LibrarySummary, onExported: suspend (String) -> Unit) {
        exportTarget = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.exportJson(target.library.id) } }
                .onSuccess { onExported(it) }
                .onFailure { statusMessage = it.userMessage() }
        }
    }

    LaunchedEffect(repository) { refresh() }

    pendingImport?.let { state ->
        ImportPreviewScreen(
            pending = state,
            busy = importBusy,
            errorMessage = importError,
            onDismiss = { if (!importBusy) pendingImport = null },
            onConfirm = { name ->
                if (!importBusy) {
                    importBusy = true
                    importError = null
                    scope.launch {
                        try {
                            runCatching { repository.commit(state.preview, name) }
                                .onSuccess { result ->
                                    pendingImport = null
                                    completedImport = CompletedImport(name.trim(), result)
                                    refresh()
                                }
                                .onFailure { error ->
                                    statusMessage = error.userMessage()
                                    importError = error.userMessage()
                                }
                        } finally {
                            importBusy = false
                        }
                    }
                }
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TishanPageHeader(title = "管理", subtitle = "整理资源，井然有序")
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    selectImport(OpenAction.New(LibraryType.QUESTION_BANK))
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ImportButtonIcon()
                        Text("导入题库")
                    }
                }
                Button(onClick = {
                    selectImport(OpenAction.New(LibraryType.EXAM_PAPER))
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ImportButtonIcon()
                        Text("导入试卷")
                    }
                }
            }
        }
        statusMessage?.let { message ->
            item { TishanInfoCard("操作提示") { Text(message) } }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TishanSectionTitle("已导入资源")
                Text("${libraries.size} 个", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (libraries.isEmpty()) {
            item {
                TishanEmptyState(
                    title = "暂无本地资源",
                    description = "选择上方入口导入 JSON 题库或试卷。",
                )
            }
        } else {
            items(libraries, key = { it.library.id }) { summary ->
                LibraryCard(
                    summary = summary,
                    onUpdate = {
                        selectImport(OpenAction.Existing(summary.library.id, ImportMode.UPDATE))
                    },
                    onReplace = {
                        selectImport(OpenAction.Existing(summary.library.id, ImportMode.REPLACE))
                    },
                    onRename = { renameTarget = summary },
                    onDelete = { deleteTarget = summary },
                    onExport = { exportTarget = summary },
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.library.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                scope.launch {
                    runCatching { repository.renameLibrary(target.library.id, name) }
                        .onSuccess {
                            renameTarget = null
                            statusMessage = "已重命名为 ${name.trim()}"
                            refresh()
                        }
                        .onFailure { statusMessage = it.userMessage() }
                }
            },
        )
    }

    deleteTarget?.let { target ->
        TishanActionDialog(
            title = "删除这份资源？",
            primaryLabel = "删除",
            onPrimary = {
                scope.launch {
                    runCatching { repository.deleteLibrary(target.library.id) }
                        .onSuccess {
                            deleteTarget = null
                            statusMessage = "已删除 ${target.library.name}"
                            refresh()
                        }
                        .onFailure { statusMessage = it.userMessage() }
                }
            },
            secondaryLabel = "取消",
            onSecondary = { deleteTarget = null },
            danger = true,
        ) {
            Text("将删除“${target.library.name}”及其题目、关联学习记录。此操作不可撤销。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    exportTarget?.let { target ->
        TishanActionDialog(
            title = "导出题目资源",
            primaryLabel = "保存文件",
            onPrimary = {
                exportResource(target) { content ->
                    val fileName = "${target.library.name.sanitizeFileName()}.json"
                    pendingSave = PendingSave(fileName = fileName, content = content)
                    createDocument.launch(fileName)
                }
            },
            secondaryLabel = "系统分享",
            onSecondary = {
                exportResource(target) { content ->
                    val file = shareCache.createJsonFile(
                        target.library.name.sanitizeFileName(),
                        content,
                    )
                    val shareUri = shareCache.uriFor(file)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND)
                                .setType("application/json")
                                .putExtra(Intent.EXTRA_STREAM, shareUri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            "分享题库资源",
                        ),
                    )
                    shareCache.scheduleCleanup(file)
                }
            },
            onDismiss = { exportTarget = null },
            primaryOnLeft = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(target.library.name, style = MaterialTheme.typography.bodyMedium)
                Text("${target.library.libraryType} · ${target.questionCount} 道题 · JSON", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("可作答 ${target.answerableCount} 道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("仅包含题目数据，不含学习进度、收藏、错题、成绩或设置。", Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    completedImport?.let { done ->
        Dialog(onDismissRequest = { completedImport = null }) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("导入完成", style = MaterialTheme.typography.headlineSmall)
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(done.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("新增 ${done.result.addedCount} · 更新 ${done.result.updatedCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (done.result.retainedCount > 0 || done.result.removedCount > 0) {
                                Text("保留 ${done.result.retainedCount} · 删除 ${done.result.removedCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Text("保留异常 ${done.result.report.unanswerableCount} · 警告 ${done.result.report.warningCount} · 失败 ${done.result.report.errorCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text("资源已写入本地，可在管理页继续操作。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { completedImport = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("知道了") }
                }
            }
        }
    }
}

@Composable
private fun ImportButtonIcon() {
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
private fun LibraryCard(
    summary: LibrarySummary,
    onUpdate: () -> Unit,
    onReplace: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TishanOutlinedCard {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TishanResourceTile(isExam = summary.library.libraryType == LibraryType.EXAM_PAPER.externalValue)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    summary.library.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${summary.library.libraryType} · ${summary.questionCount} 道题",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
                TishanMoreButton(
                    onClick = { menuExpanded = true },
                    contentDescription = "${summary.library.name}更多操作",
                    selected = menuExpanded,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("可作答 ${summary.answerableCount} 道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.library.warningCount > 0) {
                    Surface(shape = RoundedCornerShape(7.dp), color = TishanSemanticColors.current.warningContainer) {
                    Text(
                        "${summary.library.warningCount} 条数据警告",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TishanSemanticColors.current.onWarningContainer,
                    )
                    }
                } else {
                    Text("数据正常", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (menuExpanded) {
        Dialog(onDismissRequest = { menuExpanded = false }) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(summary.library.name, style = MaterialTheme.typography.titleLarge, maxLines = 3)
                    Text("${summary.library.libraryType} · ${summary.questionCount} 道题 · 资源操作", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    ResourceMenuItem("update", "普通更新", "更新题目，保留文件未包含的题目") { menuExpanded = false; onUpdate() }
                    ResourceMenuItem("replace", "整库覆盖", "以新文件为准，需再次确认") { menuExpanded = false; onReplace() }
                    ResourceMenuItem("export", "导出 / 分享", "仅分享题目，不包含学习记录") { menuExpanded = false; onExport() }
                    ResourceMenuItem("rename", "重命名", "修改这个资源的显示名称") { menuExpanded = false; onRename() }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ResourceMenuItem("delete", "删除资源", "同时清理关联学习记录", danger = true) { menuExpanded = false; onDelete() }
                    OutlinedButton(onClick = { menuExpanded = false }, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                }
            }
        }
    }
}

@Composable
private fun ResourceMenuItem(
    icon: String,
    title: String,
    description: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    ResourceMenuGlyph(icon, if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResourceMenuGlyph(kind: String, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.7.dp.toPx()
        val unit = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke, StrokeCap.Round)
        when (kind) {
            "update" -> {
                drawArc(color, 205f, 145f, false, Offset(3f * unit, 3f * unit), Size(18f * unit, 18f * unit), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, 25f, 145f, false, Offset(3f * unit, 3f * unit), Size(18f * unit, 18f * unit), style = Stroke(stroke, cap = StrokeCap.Round))
                line(2f, 4f, 2f, 10f); line(2f, 10f, 8f, 9f)
                line(22f, 20f, 22f, 14f); line(22f, 14f, 16f, 15f)
            }
            "replace", "rename" -> {
                drawRoundRect(color, topLeft = Offset(5f * unit, 3f * unit), size = Size(14f * unit, 18f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit), style = Stroke(stroke))
                line(8f, 8f, 16f, 8f); line(8f, 12f, 16f, 12f)
                if (kind == "rename") line(8f, 16f, 14f, 16f)
            }
            "export" -> {
                line(12f, 2f, 12f, 15f); line(7f, 7f, 12f, 2f); line(17f, 7f, 12f, 2f)
                line(4f, 13f, 4f, 21f); line(20f, 13f, 20f, 21f); line(4f, 21f, 20f, 21f)
            }
            "delete" -> {
                drawRoundRect(color, topLeft = Offset(6f * unit, 7f * unit), size = Size(12f * unit, 14f * unit), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * unit), style = Stroke(stroke))
                line(4f, 5f, 20f, 5f); line(9f, 2f, 15f, 2f); line(10f, 10f, 10f, 18f); line(14f, 10f, 14f, 18f)
            }
        }
    }
}

@Composable
private fun ImportPreviewScreen(
    pending: PendingImport,
    busy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(pending) { mutableStateOf(pending.initialName) }
    val report = pending.preview.report
    val blockingErrors = report.issues.filter {
        it.severity.name == "ERROR" && it.code != "OPTIONS_EMPTY"
    }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TishanPageHeader(title = "导入预览", subtitle = "${pending.preview.mode.label} · ${pending.preview.libraryType.externalValue}", onBack = onDismiss, backDescription = "返回管理", backOnRight = true) }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("已选择：${pending.fileName.ifBlank { pending.initialName }}\n以下为流入前校验结果。", Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TishanSectionTitle("${pending.preview.libraryType.externalValue}名称")
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TishanSectionTitle("内容检查")
                Text("已完成", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TishanOutlinedCard {
                Column(Modifier.padding(horizontal = 17.dp, vertical = 8.dp)) {
                    ImportStatRow("题目总数", "${report.questionCount}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (pending.preview.mode == ImportMode.NEW) {
                        ImportStatRow("新增 / 更新", "${report.questionCount} / 0")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    ImportStatRow("警告 / 不可作答", "${report.warningCount} / ${report.unanswerableCount}")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ImportStatRow("失败", "${report.errorCount}")
                }
            }
        }
        if (report.issues.isNotEmpty()) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = TishanSemanticColors.current.warningContainer) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        report.issues.take(5).forEach { issue ->
                            Text("△ ${issue.message}", color = TishanSemanticColors.current.onWarningContainer, style = MaterialTheme.typography.bodySmall)
                        }
                        if (report.issues.size > 5) Text("还有 ${report.issues.size - 5} 条提示", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        errorMessage?.let { message ->
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = TishanSemanticColors.current.warningContainer) {
                    Text(message, Modifier.fillMaxWidth().padding(14.dp), color = TishanSemanticColors.current.onWarningContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text(if (pending.preview.mode == ImportMode.NEW) "新建独立资源，不影响现有题库。" else "将按题目标识处理当前资源。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { onConfirm(name) },
                enabled = !busy && name.isNotBlank() && blockingErrors.isEmpty() && report.questions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp),
            ) { Text(if (busy) "正在导入…" else "确认${pending.preview.mode.label}") }
        }
    }
}

@Composable
private fun ImportStatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    TishanActionDialog(
        title = "重命名",
        primaryLabel = "保存",
        onPrimary = { onConfirm(name) },
        primaryEnabled = name.isNotBlank(),
        secondaryLabel = "取消",
        onSecondary = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("资源名称", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
        }
    }
}

private val ImportMode.label: String
    get() = when (this) {
        ImportMode.NEW -> "新建导入"
        ImportMode.UPDATE -> "普通更新"
        ImportMode.REPLACE -> "整库覆盖"
    }

private fun String.withoutExtension(): String = substringBeforeLast('.', this)

private fun String.sanitizeFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "题库资源" }

private fun Throwable.userMessage(): String = when (this) {
    is ImportRejectedException -> message ?: "导入被拒绝"
    else -> message ?: "操作失败，请重试"
}
