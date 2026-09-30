package com.tishan.app.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tishan.app.core.designsystem.TishanQuestionLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tishan.app.TishanApplication
import com.tishan.app.core.designsystem.TishanPageHeader
import com.tishan.app.core.designsystem.TishanEmptyState
import com.tishan.app.core.designsystem.TishanInfoCard
import com.tishan.app.core.designsystem.TishanOutlinedCard
import com.tishan.app.core.designsystem.TishanProgressBar
import com.tishan.app.core.designsystem.TishanResourceTile
import com.tishan.app.core.designsystem.TishanSectionTitle
import com.tishan.app.core.designsystem.TishanActionDialog
import com.tishan.app.core.designsystem.TishanQuestionChoice
import com.tishan.app.core.designsystem.TishanSearchButton
import com.tishan.app.core.designsystem.TishanSearchField
import com.tishan.app.core.designsystem.TishanChoiceChips
import com.tishan.app.core.designsystem.TishanSegment
import com.tishan.app.core.designsystem.theme.TishanSemanticColors
import com.tishan.app.data.local.LibrarySummary
import com.tishan.app.data.repository.PracticeQuestion
import com.tishan.app.data.repository.PracticeProgress
import com.tishan.app.data.repository.PracticeRepository
import com.tishan.app.data.repository.PracticeSessionData
import com.tishan.app.data.repository.PracticeTypeSummary
import com.tishan.app.data.repository.LibraryRepository
import com.tishan.app.domain.practice.PracticeOrderMode
import com.tishan.app.domain.practice.PracticeStats
import com.tishan.app.domain.question.AnswerJudgement
import com.tishan.app.domain.question.ExpectedAnswer
import com.tishan.app.domain.question.QuestionType
import com.tishan.app.domain.question.SubmittedAnswer
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
fun PracticeRoute(onImport: () -> Unit = {}) {
    val context = LocalContext.current
    val application = context.applicationContext as TishanApplication
    PracticeScreen(
        onImport = onImport,
        libraryRepository = application.libraryRepository,
        practiceRepository = application.practiceRepository,
    )
}

@Composable
private fun PracticeScreen(
    onImport: () -> Unit,
    libraryRepository: LibraryRepository,
    practiceRepository: PracticeRepository,
) {
    val scope = rememberCoroutineScope()
    var libraries by remember { mutableStateOf<List<LibrarySummary>>(emptyList()) }
    var stats by remember { mutableStateOf(PracticeStats(0, 0, 0)) }
    var libraryProgress by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedLibrary by remember { mutableStateOf<LibrarySummary?>(null) }
    var typeSummaries by remember { mutableStateOf<List<PracticeTypeSummary>>(emptyList()) }
    var selectedType by remember { mutableStateOf<QuestionType?>(null) }
    var orderMode by remember { mutableStateOf(PracticeOrderMode.SEQUENTIAL) }
    var searchText by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PracticeQuestion>>(emptyList()) }
    var practiceProgress by remember { mutableStateOf<PracticeProgress?>(null) }
    var session by remember { mutableStateOf<PracticeSessionData?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showLibrarySearch by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    fun closeLibrary() {
        selectedLibrary = null
        selectedType = null
        searchText = ""
        searchResults = emptyList()
        practiceProgress = null
    }
    BackHandler(enabled = selectedLibrary != null && session == null) { closeLibrary() }

    fun refreshHome() {
        scope.launch {
            val loaded = runCatching { withContext(Dispatchers.IO) { libraryRepository.listLibraries() } }
                .onFailure {
                    if (it is CancellationException) throw it
                    statusMessage = it.userMessage()
                }
                .getOrNull()
            if (loaded == null) {
                loading = false
                return@launch
            }
            libraries = loaded
            loading = false
            statusMessage = null
            runCatching {
                withContext(Dispatchers.IO) {
                    val loadedStats = practiceRepository.stats()
                    val progress = loaded.associate { summary ->
                        val types = practiceRepository.typeSummaries(summary.library.id)
                        summary.library.id to types.sumOf { type ->
                            practiceRepository.sessionProgress(summary.library.id, type.type)?.current ?: 0
                        }
                    }
                    loadedStats to progress
                }
            }.onSuccess { (loadedStats, progress) ->
                stats = loadedStats
                libraryProgress = progress
            }.onFailure {
                if (it is CancellationException) throw it
                statusMessage = it.userMessage()
            }
        }
    }

    fun openSession(
        type: QuestionType,
        restart: Boolean = false,
        startQuestionId: String? = null,
    ) {
        val library = selectedLibrary ?: return
        if (opening) return
        opening = true
        scope.launch {
            runCatching {
                practiceRepository.openSession(
                    libraryId = library.library.id,
                    questionType = type,
                    orderMode = orderMode,
                    searchText = searchText,
                    restart = restart,
                    startQuestionId = startQuestionId,
                )
            }.onSuccess { opened ->
                if (opened == null) {
                    statusMessage = "当前筛选没有可作答题目"
                } else {
                    session = opened
                    statusMessage = null
                }
            }.onFailure { statusMessage = it.userMessage() }
            opening = false
        }
    }

    LaunchedEffect(Unit) { refreshHome() }
    LaunchedEffect(selectedLibrary?.library?.id) {
        val libraryId = selectedLibrary?.library?.id ?: return@LaunchedEffect
        runCatching { practiceRepository.typeSummaries(libraryId) }
            .onSuccess { loaded ->
                val ordered = loaded.sortedBy { it.type.displayOrder() }
                typeSummaries = ordered
                if (selectedType !in ordered.map(PracticeTypeSummary::type)) selectedType = ordered.firstOrNull()?.type
            }
            .onFailure { statusMessage = it.userMessage() }
    }
    LaunchedEffect(selectedLibrary?.library?.id, selectedType, searchText) {
        val libraryId = selectedLibrary?.library?.id ?: return@LaunchedEffect
        val type = selectedType ?: return@LaunchedEffect
        runCatching {
            (if (searchText.isBlank()) emptyList() else practiceRepository.searchQuestions(libraryId, type, searchText)) to
                practiceRepository.sessionProgress(libraryId, type)
        }
            .onSuccess { (results, progress) ->
                searchResults = results
                practiceProgress = progress
            }
            .onFailure { statusMessage = it.userMessage() }
    }

    val activeSession = session
    if (activeSession != null) {
        PracticeSessionScreen(
            session = activeSession,
            repository = practiceRepository,
            onSessionChange = { session = it },
            libraryName = selectedLibrary?.library?.name,
            onExit = {
                session = null
                selectedLibrary = libraries.firstOrNull { it.library.id == activeSession.libraryId }
                refreshHome()
                scope.launch { practiceProgress = practiceRepository.sessionProgress(activeSession.libraryId, activeSession.questionType) }
            },
        )
        return
    }

    if (confirmRestart) {
        TishanActionDialog(
            title = "重新开始练习？",
            primaryLabel = "重新开始",
            onPrimary = {
                confirmRestart = false
                selectedType?.let { openSession(it, restart = true) }
            },
            secondaryLabel = "取消",
            onSecondary = { confirmRestart = false },
        ) {
            Text("当前题型的练习进度将重置，收藏与答题记录保留。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val pageContent: @Composable () -> Unit = { LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selectedLibrary == null) {
            item {
                TishanPageHeader(
                    title = "题山",
                    subtitle = "专注学习，从容向上",
                    action = {
                        TishanSearchButton(
                            onClick = {
                                showLibrarySearch = !showLibrarySearch
                                if (!showLibrarySearch) searchText = ""
                            },
                            contentDescription = if (showLibrarySearch) "关闭题库搜索" else "搜索题库",
                            selected = showLibrarySearch,
                        )
                    },
                )
            }
            if (showLibrarySearch) {
                item {
                    TishanSearchField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "搜索题库名称",
                        clearDescription = "清空题库搜索",
                    )
                }
            }
            item {
                PracticeStatsBlock(stats)
            }
            statusMessage?.let { message ->
                item { TishanInfoCard("提示") { Text(message) } }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TishanSectionTitle("我的题库")
                    Text("${libraries.size} 个本地资源", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            val visibleLibraries = (if (showLibrarySearch && searchText.isNotBlank()) {
                libraries.filter { it.library.name.contains(searchText, ignoreCase = true) }
            } else libraries).sortedWith(compareBy({ it.library.libraryType != "题库" }, { it.library.name }))
            if (loading) {
                item { Text("正在加载题库…") }
            } else if (visibleLibraries.isEmpty()) {
                item {
                    TishanEmptyState(
                        title = if (libraries.isEmpty()) "还没有题库" else "没有匹配的题库",
                        description = if (libraries.isEmpty()) "导入本地 JSON 题库或试卷，开始学习。" else "换个关键词再试试。",
                        action = { if (libraries.isEmpty()) Button(onClick = onImport) { Text("去导入题库") } },
                    )
                }
            } else {
                items(visibleLibraries, key = { it.library.id }) { summary ->
                    LibraryPracticeCard(summary, libraryProgress[summary.library.id] ?: 0) {
                        searchText = ""
                        showLibrarySearch = false
                        selectedLibrary = summary
                    }
                }
            }
        } else {
            val library = checkNotNull(selectedLibrary)
            item {
                TishanPageHeader(
                    title = library.library.name,
                    subtitle = "${library.library.libraryType} · 可作答 ${library.answerableCount} 道",
                    onBack = ::closeLibrary,
                    backDescription = "返回刷题",
                    backOnRight = true,
                )
            }
            item {
                val total = practiceProgress?.total ?: typeSummaries.firstOrNull { it.type == selectedType }?.questionCount ?: library.answerableCount
                val current = practiceProgress?.current ?: 0
                val percentage = if (total == 0) 0 else current * 100 / total
                TishanInfoCard("上次练到", containerColor = MaterialTheme.colorScheme.surface) {
                    Text(if (current > 0) "${selectedType?.externalValue ?: "题目"} · 第 $current 题" else "还没有作答记录", style = MaterialTheme.typography.titleMedium)
                    TishanProgressBar(current = current, total = total, showLabel = false)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("已练 $current / $total", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Text("$percentage%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("作答顺序", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                TishanChoiceChips(
                    items = listOf(
                        TishanSegment(PracticeOrderMode.SEQUENTIAL.name, "顺序"),
                        TishanSegment(PracticeOrderMode.RANDOM.name, "随机"),
                    ),
                    selectedKey = orderMode.name,
                    onSelected = { orderMode = PracticeOrderMode.valueOf(it) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (typeSummaries.isNotEmpty()) {
                item {
                    Text("题型", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    TishanChoiceChips(
                        items = typeSummaries.map { summary ->
                            TishanSegment(
                                summary.type.name,
                                "${summary.type.externalValue}  ${summary.questionCount}",
                            )
                        },
                        selectedKey = selectedType?.name.orEmpty(),
                        onSelected = { key -> selectedType = QuestionType.valueOf(key) },
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                    )
                }
                item {
                    Text("搜索", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    TishanSearchField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        placeholder = "输入关键词定位题目",
                        clearDescription = "清空题目搜索",
                    )
                }
                if (searchResults.isNotEmpty()) {
                    item {
                        TishanOutlinedCard {
                            Column {
                                val visibleResults = searchResults.take(8)
                                visibleResults.forEachIndexed { index, result ->
                                    SearchResultRow(result) {
                                        selectedType?.let { type ->
                                            openSession(type, restart = true, startQuestionId = result.question.internalId)
                                        }
                                    }
                                    if (index < visibleResults.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                if (searchResults.size > visibleResults.size) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Text(
                                        "已显示前 ${visibleResults.size} 条，共 ${searchResults.size} 条；继续输入关键词可缩小范围",
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                } else if (searchText.isNotBlank()) {
                    item {
                        Text(
                            "没有找到匹配题目，换个关键词试试。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            statusMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
        }
    } }
    if (selectedLibrary == null) {
        pageContent()
    } else {
        TishanQuestionLayout(actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    enabled = selectedType != null && !opening,
                    onClick = { confirmRestart = true },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                ) { Text("重新开始") }
                Button(
                    enabled = selectedType != null && !opening,
                    onClick = { selectedType?.let(::openSession) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                ) { Text(if (opening) "正在打开…" else if (practiceProgress != null && searchText.isBlank()) "继续刷题" else "开始刷题") }
            }
        }) { pageContent() }
    }
}

@Composable
internal fun PracticeSessionScreen(
    session: PracticeSessionData,
    repository: PracticeRepository,
    onSessionChange: (PracticeSessionData) -> Unit,
    onExit: () -> Unit,
    pageTitle: String = "刷题",
    libraryName: String? = null,
) {
    val scope = rememberCoroutineScope()
    val item = session.currentQuestion
    val question = item.question
    val correctKeys = when (val answer = question.answer) {
        is ExpectedAnswer.Choice -> answer.keys
        is ExpectedAnswer.TrueFalse -> setOf(answer.rawValue)
        is ExpectedAnswer.Essay -> emptySet()
    }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(question.internalId) { listState.scrollToItem(0) }
    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.userMessage() }
            finally { busy = false }
        }
    }
    fun submit(answer: SubmittedAnswer) = runAction { onSessionChange(repository.submitCurrent(session, answer)) }
    fun saveSelection(keys: Set<String>) = runAction {
        val updated = session.copy(selectedChoiceKeys = keys, isSubmitted = false, judgement = null)
        repository.saveDraft(updated)
        onSessionChange(updated)
    }
    fun exit() = runAction { repository.saveDraft(session); onExit() }
    fun next() = runAction {
        if (session.currentIndex == session.questions.lastIndex) onExit()
        else onSessionChange(repository.nextQuestion(session))
    }
    fun previous() = runAction { onSessionChange(repository.previousQuestion(session)) }
    BackHandler { exit() }

    TishanQuestionLayout(actions = {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = ::previous,
                    enabled = !busy && session.currentIndex > 0,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                ) { Text("上一题", maxLines = 1) }
                FavoriteButton(
                    selected = item.isFavorite,
                    onClick = { runAction { onSessionChange(repository.toggleFavorite(session)) } },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (!session.isSubmitted && question.key.questionType == QuestionType.MULTIPLE_CHOICE) {
                            submit(SubmittedAnswer.Choice(session.selectedChoiceKeys))
                        } else next()
                    },
                    enabled = !busy && (session.isSubmitted || (question.key.questionType == QuestionType.MULTIPLE_CHOICE && session.selectedChoiceKeys.isNotEmpty())),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                ) { Text(if (!session.isSubmitted && question.key.questionType == QuestionType.MULTIPLE_CHOICE) "确认答案" else if (session.currentIndex == session.questions.lastIndex) "完成" else "下一题", maxLines = 1) }
            }

    }) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("practice-question-list")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TishanPageHeader(
                title = pageTitle,
                subtitle = "${session.currentIndex + 1} / ${session.questions.size}" + (libraryName?.let { " · $it" } ?: ""),
                onBack = ::exit,
                backDescription = "返回题库",
                backOnRight = true,
            )
            TishanProgressBar(
                current = session.currentIndex + 1,
                total = session.questions.size,
                modifier = Modifier.padding(top = 6.dp),
                showLabel = false,
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(question.key.questionType.externalValue, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
                }
                Text("第 ${question.key.questionNumber} 题", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                question.prompt,
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 32.sp),
            )
        }
        when (question.key.questionType) {
            QuestionType.SINGLE_CHOICE -> {
                question.options.forEach { option ->
                    item {
                        TishanQuestionChoice(
                            label = option.key,
                            text = option.text,
                            selected = option.key in session.selectedChoiceKeys,
                            enabled = !busy && !session.isSubmitted,
                            singleSelection = true,
                            correct = session.isSubmitted && option.key in correctKeys,
                            incorrect = session.isSubmitted && option.key in session.selectedChoiceKeys && option.key !in correctKeys,
                            onClick = { submit(SubmittedAnswer.Choice(setOf(option.key))) },
                        )
                    }
                }
            }

            QuestionType.MULTIPLE_CHOICE -> {
                question.options.forEach { option ->
                    item {
                        TishanQuestionChoice(
                            label = option.key,
                            text = option.text,
                            selected = option.key in session.selectedChoiceKeys,
                            enabled = !busy && !session.isSubmitted,
                            singleSelection = false,
                            correct = session.isSubmitted && option.key in correctKeys,
                            incorrect = session.isSubmitted && option.key in session.selectedChoiceKeys && option.key !in correctKeys,
                            onClick = {
                                val next = session.selectedChoiceKeys.toMutableSet().apply {
                                    if (!add(option.key)) remove(option.key)
                                }
                                saveSelection(next)
                            },
                        )
                    }
                }
            }

            QuestionType.TRUE_FALSE -> {
                item {
                    TishanQuestionChoice(
                        label = "√",
                        text = "正确",
                        selected = "√" in session.selectedChoiceKeys,
                        enabled = !busy && !session.isSubmitted,
                        singleSelection = true,
                        correct = session.isSubmitted && "√" in correctKeys,
                        incorrect = session.isSubmitted && "√" in session.selectedChoiceKeys && "√" !in correctKeys,
                        onClick = { submit(SubmittedAnswer.TrueFalse(true)) },
                    )
                }
                item {
                    TishanQuestionChoice(
                        label = "×",
                        text = "错误",
                        selected = "×" in session.selectedChoiceKeys,
                        enabled = !busy && !session.isSubmitted,
                        singleSelection = true,
                        correct = session.isSubmitted && "×" in correctKeys,
                        incorrect = session.isSubmitted && "×" in session.selectedChoiceKeys && "×" !in correctKeys,
                        onClick = { submit(SubmittedAnswer.TrueFalse(false)) },
                    )
                }
            }

            QuestionType.ESSAY -> item { Text("申论题不进入普通刷题。") }
        }
        if (session.isSubmitted) {
            item {
                val isCorrect = session.judgement == AnswerJudgement.Correct
                val semantic = TishanSemanticColors.current
                val feedbackColor = if (isCorrect) semantic.onSuccessContainer else MaterialTheme.colorScheme.onErrorContainer
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = if (isCorrect) semantic.successContainer else MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(
                                when (session.judgement) {
                                    AnswerJudgement.Correct -> "✓ 回答正确"
                                    AnswerJudgement.Incorrect -> "× 回答错误"
                                    is AnswerJudgement.Unavailable -> "此题不可作答"
                                    is AnswerJudgement.InvalidSubmission -> "答案无效"
                                    AnswerJudgement.NotAutoGraded -> "此题不自动评分"
                                    null -> "已提交"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = feedbackColor,
                            )
                            Text("你的答案 ${session.selectedChoiceKeys.sorted().joinToString("、").ifBlank { "未作答" }} · 正确答案 ${question.answer.displayValue()}", color = feedbackColor, style = MaterialTheme.typography.bodySmall)
                        }
                }
            }
            item {
                Text("答案解析", style = MaterialTheme.typography.titleSmall)
                Text(question.explanation?.takeIf { it.isNotBlank() } ?: "暂无解析", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 26.sp))
            }
        }
        if (!session.isSubmitted) {
            item {
                Text(
                    if (question.key.questionType == QuestionType.MULTIPLE_CHOICE) "选中选项后点击「确认答案」查看反馈" else "点击选项作答 · 作答后查看反馈",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    }
}

@Composable
internal fun FavoriteButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).semantics {
            contentDescription = if (selected) "取消收藏" else "收藏题目"
        },
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Text(if (selected) "已收藏" else "收藏", maxLines = 1)
    }
}

@Composable
private fun LibraryPracticeCard(summary: LibrarySummary, practiced: Int, onSelect: () -> Unit) {
    TishanOutlinedCard(onClick = onSelect) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TishanResourceTile(isExam = summary.library.libraryType != "题库")
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(summary.library.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${summary.library.libraryType} · ${summary.questionCount} 道题", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
            }
            TishanProgressBar(current = practiced, total = summary.answerableCount, showLabel = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已练 $practiced / ${summary.answerableCount}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("${if (summary.answerableCount == 0) 0 else practiced * 100 / summary.answerableCount}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            if (summary.library.warningCount > 0) Text("${summary.library.warningCount} 条数据警告", color = TishanSemanticColors.current.warning, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SearchResultRow(question: PracticeQuestion, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("#${question.question.key.questionNumber}", style = MaterialTheme.typography.labelMedium)
        Text(
            question.question.prompt,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PracticeStatsBlock(stats: PracticeStats) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val mountainOpacity = if (LocalDensity.current.fontScale >= 1.5f) 0.12f else 0.32f
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Canvas(Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = 30.dp).size(width = 150.dp, height = 128.dp)) {
            val first = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width * 0.5f, size.height * 0.195f)
                lineTo(size.width, size.height)
                close()
            }
            val second = Path().apply {
                moveTo(size.width * 0.34f, size.height)
                lineTo(size.width * 0.73f, size.height * 0.336f)
                lineTo(size.width * 1.12f, size.height)
                close()
            }
            drawPath(first, ink.copy(alpha = mountainOpacity * 0.4f))
            drawPath(second, ink.copy(alpha = mountainOpacity))
        }
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text("今日已刷", color = ink, style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(stats.todayQuestionCount.toString(), color = ink, style = MaterialTheme.typography.displaySmall)
                Text("题", color = ink, modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            Text("今天也要翻过一座题山。", modifier = Modifier.padding(top = 10.dp), color = ink, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(modifier = Modifier.padding(top = 22.dp), color = ink.copy(alpha = 0.18f))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("错题本   ${stats.wrongQuestionCount}", color = ink, style = MaterialTheme.typography.bodySmall)
                Text("收藏   ${stats.favoriteCount}", color = ink, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun QuestionType.displayOrder(): Int = when (this) {
    QuestionType.SINGLE_CHOICE -> 0
    QuestionType.MULTIPLE_CHOICE -> 1
    QuestionType.TRUE_FALSE -> 2
    QuestionType.ESSAY -> 3
}

private fun Throwable.userMessage(): String = message ?: "操作失败，请重试"
