package com.tishan.app.feature.practice.exam

import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tishan.app.core.designsystem.TishanQuestionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tishan.app.data.local.LibrarySummary
import com.tishan.app.TishanApplication
import com.tishan.app.data.repository.ExamRepository
import com.tishan.app.data.repository.ExamResultSummary
import com.tishan.app.data.repository.ExamSessionData
import com.tishan.app.domain.practice.ExamDurationSettings
import com.tishan.app.domain.practice.ExamResult
import com.tishan.app.domain.question.QuestionAvailability
import com.tishan.app.domain.question.ExpectedAnswer
import com.tishan.app.domain.question.QuestionType
import com.tishan.app.domain.question.SubmittedAnswer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import com.tishan.app.core.designsystem.TishanPageHeader
import com.tishan.app.core.designsystem.TishanEmptyState
import com.tishan.app.core.designsystem.TishanInfoCard
import com.tishan.app.core.designsystem.TishanOutlinedCard
import com.tishan.app.core.designsystem.TishanProgressBar
import com.tishan.app.core.designsystem.TishanQuestionChoice
import com.tishan.app.core.designsystem.TishanSectionTitle
import com.tishan.app.core.designsystem.TishanActionDialog
import com.tishan.app.core.designsystem.TishanResourceTile
import com.tishan.app.core.designsystem.theme.TishanSemanticColors

@Composable
fun ExamRoute() {
    val context = LocalContext.current
    val application = context.applicationContext as TishanApplication
    ExamScreen(
        examRepository = application.examRepository,
    )
}

@Composable
private fun ExamScreen(
    examRepository: ExamRepository,
) {
    val scope = rememberCoroutineScope()
    var papers by remember { mutableStateOf<List<LibrarySummary>>(emptyList()) }
    var recentResults by remember { mutableStateOf<List<ExamResultSummary>>(emptyList()) }
    var settingsPaper by remember { mutableStateOf<LibrarySummary?>(null) }
    var settings by remember { mutableStateOf<ExamDurationSettings?>(null) }
    var session by remember { mutableStateOf<ExamSessionData?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshPapers() {
        scope.launch {
            runCatching { examRepository.listExamPapers() to examRepository.listRecentResults() }
                .onSuccess { (loadedPapers, loadedResults) ->
                    papers = loadedPapers
                    recentResults = loadedResults
                }
                .onFailure { statusMessage = it.userMessage() }
        }
    }

    fun openSession(paper: LibrarySummary, restart: Boolean = false) {
        scope.launch {
            runCatching { examRepository.openSession(paper.library.id, restart) }
                .onSuccess { opened ->
                    if (opened == null) statusMessage = "该试卷没有可用题目"
                    else {
                        session = opened
                        statusMessage = null
                    }
                }
                .onFailure { statusMessage = it.userMessage() }
        }
    }

    LaunchedEffect(Unit) {
        refreshPapers()
    }
    LaunchedEffect(settingsPaper?.library?.id) {
        settingsPaper?.let { paper ->
            runCatching { examRepository.loadSettings(paper.library.id) }
                .onSuccess { settings = it }
                .onFailure { statusMessage = it.userMessage() }
        }
    }

    val activeSession = session
    if (activeSession != null) {
        activeSession.result?.let { result ->
            ExamResultScreen(
                session = activeSession,
                result = result,
                paperName = papers.firstOrNull { it.library.id == activeSession.libraryId }?.library?.name,
                onExit = {
                    session = null
                    refreshPapers()
                },
            )
        } ?: ExamSessionScreen(
            session = activeSession,
            repository = examRepository,
            onSessionChange = { session = it },
            onExit = { session = null },
        )
        return
    }

    val paper = settingsPaper
    val activeSettings = settings
    if (paper != null && activeSettings != null) {
        ExamSettingsScreen(
            paper = paper,
            settings = activeSettings,
            repository = examRepository,
            statusMessage = statusMessage,
            onSaved = { settings = it },
            onBack = { settingsPaper = null },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TishanPageHeader(title = "模拟", subtitle = "模拟实战，检验所学")
        }
        statusMessage?.let { message -> item { TishanInfoCard("提示") { Text(message) } } }
        if (recentResults.isNotEmpty()) {
            items(recentResults, key = { it.result.sessionId }) { summary ->
                ExamHistoryCard(summary)
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TishanSectionTitle("可开始")
                Text("${papers.size} 份试卷", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (papers.isEmpty()) {
            item {
                TishanEmptyState(
                    title = "暂无可模拟试卷",
                    description = "请先在管理页通过“导入试卷”添加资源。",
                )
            }
        } else {
            items(papers, key = { it.library.id }) { summary ->
                TishanOutlinedCard(onClick = { openSession(summary) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TishanResourceTile(isExam = true)
                            Column(Modifier.weight(1f)) {
                                Text(summary.library.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${summary.questionCount} 道客观题 · 点击开始", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("可作答 ${summary.answerableCount} 题", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { settingsPaper = summary }) { Text("考前设置") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamHistoryCard(summary: ExamResultSummary) {
    val result = summary.result
    val total = result.objectiveTotal.coerceAtLeast(1)
    val percentage = result.totalScore * 100 / total
    val semantic = TishanSemanticColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = semantic.successContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("最近成绩 · ${formatDate(summary.submittedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall, color = semantic.onSuccessContainer)
                Text("◴", style = MaterialTheme.typography.titleMedium, color = semantic.onSuccessContainer)
            }
            Text(summary.libraryName, style = MaterialTheme.typography.titleMedium, color = semantic.onSuccessContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${result.totalScore}", style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp), color = semantic.onSuccessContainer)
                    Text("/ ${result.objectiveTotal} 分", style = MaterialTheme.typography.bodySmall, color = semantic.onSuccessContainer, modifier = Modifier.padding(bottom = 8.dp))
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 7.dp)) {
                    Text("正确率 $percentage%", style = MaterialTheme.typography.bodySmall, color = semantic.onSuccessContainer)
                    Text("用时 ${result.elapsedMillis / 60_000L} 分钟", style = MaterialTheme.typography.bodySmall, color = semantic.onSuccessContainer)
                }
            }
        }
    }
}

@Composable
private fun ExamSettingsScreen(
    paper: LibrarySummary,
    settings: ExamDurationSettings,
    repository: ExamRepository,
    statusMessage: String?,
    onSaved: (ExamDurationSettings) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var single by remember(settings) { mutableStateOf((settings.singleChoiceSeconds / 60).toString()) }
    var multiple by remember(settings) { mutableStateOf((settings.multipleChoiceSeconds / 60).toString()) }
    var trueFalse by remember(settings) { mutableStateOf((settings.trueFalseSeconds / 60).toString()) }
    var localMessage by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    fun parseSettings(): ExamDurationSettings? {
        val parsed = runCatching {
            ExamDurationSettings(
                singleChoiceSeconds = single.toInt().coerceIn(0, 24 * 60) * 60,
                multipleChoiceSeconds = multiple.toInt().coerceIn(0, 24 * 60) * 60,
                trueFalseSeconds = trueFalse.toInt().coerceIn(0, 24 * 60) * 60,
            )
        }.getOrElse {
            localMessage = "请输入非负整数分钟"
            return null
        }
        return parsed
    }

    fun saveOnly() {
        val parsed = parseSettings() ?: return
        scope.launch {
            runCatching { repository.saveSettings(paper.library.id, parsed) }
                .onSuccess {
                    onSaved(parsed)
                    localMessage = "默认时长已保存"
                }
                .onFailure { localMessage = it.userMessage() }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TishanPageHeader(
                title = "考前设置",
                subtitle = paper.library.name,
                onBack = onBack,
                backDescription = "返回模拟",
                backOnRight = true,
            )
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("为每种题型设置作答时长。超时后提醒，仍可继续作答。", modifier = Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TishanSectionTitle("题型时长")
                Text("单位：分钟", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            TishanOutlinedCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 8.dp),
                ) {
                    DurationField("单选题", single) { single = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DurationField("多选题", multiple) { multiple = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DurationField("判断题", trueFalse) { trueFalse = it }
                }
            }
        }
        item {
            Text(
                "申论题与不可作答的异常题不参与计时和自动评分。",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        (statusMessage ?: localMessage)?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item {
            Button(onClick = ::saveOnly, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("保存设置") }
        }
    }
}

@Composable
private fun DurationField(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .width(76.dp)
                .semantics { contentDescription = "$label 时长（分钟）" },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        innerTextField()
                    }
                }
            },
        )
        Text("分", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun ExamSessionScreen(
    session: ExamSessionData,
    repository: ExamRepository,
    onSessionChange: (ExamSessionData) -> Unit,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showSubmit by remember { mutableStateOf(false) }
    var showAnswerSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val question = session.currentQuestion.question
    LaunchedEffect(question.internalId) { listState.scrollToItem(0) }
    LaunchedEffect(session.sessionId, session.isPaused) {
        while (isActive) { now = System.currentTimeMillis(); delay(1_000L) }
    }
    val timer = repository.timerSnapshot(session, now)
    val canAnswer = !busy && !session.isPaused && question.availability is QuestionAvailability.Answerable
    val objectiveIndices = session.questions.indices.filter { index ->
        val candidate = session.questions[index].question
        candidate.key.questionType != QuestionType.ESSAY && candidate.availability is QuestionAvailability.Answerable
    }
    val unanswered = objectiveIndices.filter { session.questions[it].question.internalId !in session.answers }
    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        statusMessage = null
        scope.launch {
            try { action() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { statusMessage = failure.userMessage() }
            finally { busy = false }
        }
    }
    fun submit(answer: SubmittedAnswer) = runAction { onSessionChange(repository.submitCurrent(session, answer)) }
    fun move(index: Int) = runAction { onSessionChange(repository.moveToQuestion(session, index)) }
    fun exit() = runAction {
        if (!session.isPaused) repository.pause(session) else repository.saveDraft(session)
        onExit()
    }
    BackHandler { exit() }

    TishanQuestionLayout(actions = {
        statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { move(session.currentIndex - 1) },
                enabled = !busy && !session.isPaused && session.currentIndex > 0,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("上一题") }
            OutlinedButton(onClick = { showAnswerSheet = true }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("答题卡", style = MaterialTheme.typography.labelMedium)
                    Text("检查 / 交卷", style = MaterialTheme.typography.labelSmall)
                }
            }
            Button(
                onClick = { if (session.currentIndex == session.questions.lastIndex) showSubmit = true else move(session.currentIndex + 1) },
                enabled = !busy && !session.isPaused,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text(if (session.currentIndex == session.questions.lastIndex) "交卷" else "下一题") }
        }
    }) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TishanPageHeader(
                    title = "模拟作答", onBack = ::exit, backDescription = "退出并保存",
                    subtitle = "第 ${session.currentIndex + 1} 题 / 共 ${session.questions.size} 题",
                    backOnRight = true,
                )
                TishanProgressBar(session.currentIndex + 1, session.questions.size, modifier = Modifier.padding(top = 18.dp), showLabel = false)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(if (session.isPaused) "已暂停" else "本题型剩余", style = MaterialTheme.typography.labelSmall)
                            Text(timer.remainingMillis?.let(::formatMillis) ?: "不计时", style = MaterialTheme.typography.titleMedium)
                        }
                        TextButton(
                            onClick = { runAction { onSessionChange(if (session.isPaused) repository.resume(session) else repository.pause(session)) } },
                            enabled = !busy,
                        ) {
                            ExamActionIcon(if (session.isPaused) ExamAction.Resume else ExamAction.Pause)
                            Spacer(Modifier.width(6.dp))
                            Text(if (session.isPaused) "继续" else "暂停")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(question.key.questionType.externalValue, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("第 ${question.key.questionNumber} 题", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text(question.prompt, Modifier.padding(top = 28.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 32.sp))
                if (timer.timedOut) Text("本题型已超时，可继续作答", color = TishanSemanticColors.current.warning)
                Spacer(Modifier.height(12.dp))
            }
            if (question.availability is QuestionAvailability.Unanswerable) {
                item { Text("不可作答：${question.availability.reason}") }
            } else when (question.key.questionType) {
                QuestionType.SINGLE_CHOICE -> question.options.forEach { option ->
                    item {
                        TishanQuestionChoice(option.key, option.text, option.key in session.selectedChoiceKeys, canAnswer, true) {
                            submit(SubmittedAnswer.Choice(setOf(option.key)))
                        }
                    }
                }
                QuestionType.MULTIPLE_CHOICE -> question.options.forEach { option ->
                    item {
                        TishanQuestionChoice(option.key, option.text, option.key in session.selectedChoiceKeys, canAnswer, false) {
                            val keys = session.selectedChoiceKeys.toMutableSet().apply { if (!add(option.key)) remove(option.key) }
                            if (keys.isEmpty()) runAction { onSessionChange(repository.clearCurrentAnswer(session)) }
                            else submit(SubmittedAnswer.Choice(keys))
                        }
                    }
                }
                QuestionType.TRUE_FALSE -> {
                    item { TishanQuestionChoice("√", "正确", "√" in session.selectedChoiceKeys, canAnswer, true) { submit(SubmittedAnswer.TrueFalse(true)) } }
                    item { TishanQuestionChoice("×", "错误", "×" in session.selectedChoiceKeys, canAnswer, true) { submit(SubmittedAnswer.TrueFalse(false)) } }
                }
                QuestionType.ESSAY -> item { Text("申论题不自动评分，交卷后可查看参考答案。") }
            }
            item {
                Text(
                    when {
                        busy -> "正在保存…"
                        session.isPaused -> "考试已暂停，点击继续后作答。"
                        session.currentAnswer != null -> "已保存，可修改答案 · 交卷后查看解析"
                        else -> "选择后自动保存，可跳过稍后作答。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (showSubmit) {
        TishanActionDialog(
            title = "确认交卷？",
            primaryLabel = "确认交卷",
            onPrimary = {
                showSubmit = false
                runAction { onSessionChange(repository.submitExam(session).first) }
            },
            primaryEnabled = !busy,
            secondaryLabel = "继续作答",
            onSecondary = {
                showSubmit = false
                if (unanswered.isNotEmpty()) move(unanswered.first())
            },
            onDismiss = { showSubmit = false },
        ) {
            Text("客观题已答 ${objectiveIndices.size - unanswered.size} / ${objectiveIndices.size}，未答 ${unanswered.size} 题。", style = MaterialTheme.typography.bodySmall)
            Text("交卷后不能修改答案；申论题不自动评分。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showAnswerSheet) {
        Dialog(onDismissRequest = { showAnswerSheet = false }) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("答题卡", style = MaterialTheme.typography.headlineMedium)
                Text("已答 ${objectiveIndices.size - unanswered.size} / ${objectiveIndices.size} 题 · 点击题号检查遗漏。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("蓝色与勾号表示已记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(session.questions.size) { index ->
                        val candidate = session.questions[index].question
                        val answered = candidate.internalId in session.answers
                        OutlinedButton(
                            onClick = { showAnswerSheet = false; move(index) },
                            enabled = !busy && !session.isPaused,
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (answered || index == session.currentIndex) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        ) {
                            Text(if (answered) "${index + 1} ✓" else "${index + 1}", color = if (answered || index == session.currentIndex) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { runAction { onSessionChange(repository.toggleFavorite(session)) } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (session.currentQuestion.isFavorite) "已收藏本题" else "收藏本题") }
                Button(onClick = { showAnswerSheet = false; showSubmit = true }, enabled = !busy && !session.isPaused, modifier = Modifier.fillMaxWidth()) { Text("检查并交卷") }
                TextButton(onClick = { showAnswerSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("继续作答") }
            }
            }
        }
    }
}

@Composable
private fun ExamResultScreen(session: ExamSessionData, result: ExamResult, paperName: String?, onExit: () -> Unit) {
    val objectiveTotal = result.objectiveTotal.coerceAtLeast(1)
    val percentage = (result.totalScore * 100 / objectiveTotal)
    BackHandler(onBack = onExit)
    var showOnlyWrong by remember { mutableStateOf(true) }
    val visibleQuestions = session.questions.filter { item ->
        item.question.internalId?.let(session.answers::get)?.isCorrect == !showOnlyWrong
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TishanPageHeader(
                title = "成绩单",
                subtitle = listOfNotNull(paperName, "${result.objectiveTotal} 道客观题").joinToString(" · "),
                onBack = onExit,
                backDescription = "返回模拟首页",
                backOnRight = true,
            )
        }
        item {
            val semantic = TishanSemanticColors.current
            Column(
                modifier = Modifier.fillMaxWidth().background(semantic.successContainer, RoundedCornerShape(24.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("客观题得分", color = semantic.onSuccessContainer, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(result.totalScore.toString(), color = semantic.onSuccessContainer, style = MaterialTheme.typography.displaySmall)
                    Text(" / ${result.objectiveTotal}", color = semantic.onSuccessContainer, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("正确率 $percentage%   ·   用时 ${formatMillis(result.elapsedMillis)}", color = semantic.onSuccessContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Text("分型正确率", style = MaterialTheme.typography.titleMedium) }
        item { TishanOutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        result.typeScores.forEach { score ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(score.type.externalValue, style = MaterialTheme.typography.bodyMedium)
                    Text("${score.correct}/${score.total} · ${score.percentage}%", style = MaterialTheme.typography.labelMedium)
                }
                TishanProgressBar(current = score.correct, total = score.total, showLabel = false)
            }
        }
        } } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TishanSectionTitle("逐题解析")
                Text("共 ${result.objectiveTotal} 题", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    listOf(true to "错题 ${result.wrongQuestionIds.size}", false to "答对 ${result.totalScore}").forEach { (wrong, label) ->
                        val selected = showOnlyWrong == wrong
                        Column(Modifier.weight(1f).clickable { showOnlyWrong = wrong }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, modifier = Modifier.padding(vertical = 12.dp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(thickness = if (selected) 2.dp else 1.dp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        if (visibleQuestions.isEmpty()) {
            item { TishanEmptyState(if (showOnlyWrong) "没有错题" else "暂无答对题", if (showOnlyWrong) "本次模拟的客观题全部答对。" else "本次没有答对的客观题。") }
        }
        items(visibleQuestions, key = { it.question.internalId ?: it.question.key.questionNumber }) { item ->
            val question = item.question
            val record = question.internalId?.let(session.answers::get)
            TishanOutlinedCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val unanswered = record?.submittedAnswer == null || record.submittedAnswer == "未作答"
                    Surface(shape = CircleShape, color = if (record?.isCorrect == true) TishanSemanticColors.current.successContainer else MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "${if (record?.isCorrect == true) "✓ 答对" else if (unanswered) "未作答" else "× 答错"} · ${question.key.questionType.externalValue} #${question.key.questionNumber}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = if (record?.isCorrect == true) TishanSemanticColors.current.onSuccessContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(question.prompt, style = MaterialTheme.typography.bodyLarge)
                    val answerKeys = when (val answer = question.answer) {
                        is ExpectedAnswer.Choice -> answer.keys
                        is ExpectedAnswer.TrueFalse -> setOf(answer.rawValue)
                        is ExpectedAnswer.Essay -> emptySet()
                    }
                    val optionPairs = if (question.key.questionType == QuestionType.TRUE_FALSE) listOf("√" to "正确", "×" to "错误") else question.options.map { it.key to it.text }
                    optionPairs.forEach { (key, text) ->
                        val isAnswer = key in answerKeys
                        val isChosen = record?.submittedAnswer?.contains(key) == true
                        val optionColor = when {
                            isAnswer -> TishanSemanticColors.current.successContainer
                            isChosen -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val ink = when {
                            isAnswer -> TishanSemanticColors.current.onSuccessContainer
                            isChosen -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Row(Modifier.fillMaxWidth().background(optionColor, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(key, color = ink, style = MaterialTheme.typography.labelMedium)
                            Column {
                                Text(text, color = ink, style = MaterialTheme.typography.bodyMedium)
                                val marks = listOfNotNull(if (isAnswer) "正确答案" else null, if (isChosen) "你的选择" else null)
                                if (marks.isNotEmpty()) Text(marks.joinToString(" · "), color = ink, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("你的答案：${record?.submittedAnswer ?: "未作答"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text("正确答案：${question.answer.displayValue()} · ${optionPairs.filter { it.first in answerKeys }.joinToString("、") { it.second }}", color = TishanSemanticColors.current.success, style = MaterialTheme.typography.bodySmall)
                    Text("解析：${question.explanation?.takeIf(String::isNotBlank) ?: "暂无解析"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatMillis(value: Long?): String {
    if (value == null) return "--"
    val totalSeconds = value / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatDate(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun Throwable.userMessage(): String = message ?: "操作失败，请重试"
