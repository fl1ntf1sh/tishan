package com.tishan.app.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tishan.app.core.designsystem.TishanQuestionLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tishan.app.TishanApplication
import com.tishan.app.core.designsystem.TishanPageHeader
import com.tishan.app.core.designsystem.TishanEmptyState
import com.tishan.app.core.designsystem.TishanInfoCard
import com.tishan.app.core.designsystem.TishanOutlinedCard
import com.tishan.app.data.repository.PracticeRepository
import com.tishan.app.data.repository.PracticeSessionData
import com.tishan.app.data.repository.ReviewGroup
import com.tishan.app.data.repository.ReviewQuestion
import com.tishan.app.data.repository.ReviewRepository
import com.tishan.app.domain.practice.PracticeOrderMode
import com.tishan.app.domain.question.QuestionAvailability
import com.tishan.app.feature.practice.PracticeSessionScreen
import com.tishan.app.feature.practice.FavoriteButton
import com.tishan.app.core.designsystem.TishanQuestionChoice
import com.tishan.app.domain.question.ExpectedAnswer
import kotlinx.coroutines.launch

private enum class ReviewTab(val label: String) {
    FAVORITES("收藏"),
    WRONG("错题"),
    BACK("背题"),
}

@Composable
fun ReviewRoute() {
    val context = LocalContext.current
    val application = context.applicationContext as TishanApplication
    ReviewScreen(
        reviewRepository = application.reviewRepository,
        practiceRepository = application.practiceRepository,
    )
}

@Composable
private fun ReviewScreen(
    reviewRepository: ReviewRepository,
    practiceRepository: PracticeRepository,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(ReviewTab.FAVORITES) }
    var orderMode by remember { mutableStateOf(PracticeOrderMode.SEQUENTIAL) }
    var showOrderMenu by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<ReviewGroup>>(emptyList()) }
    var tabGroups by remember { mutableStateOf<Map<ReviewTab, List<ReviewGroup>>>(emptyMap()) }
    var activeSession by remember { mutableStateOf<PracticeSessionData?>(null) }
    var backQuestions by remember { mutableStateOf<List<ReviewQuestion>?>(null) }
    var activeGroupName by remember { mutableStateOf<String?>(null) }
    var backIndex by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshGroups() {
        scope.launch {
            runCatching {
                mapOf(
                    ReviewTab.FAVORITES to reviewRepository.favoriteGroups(),
                    ReviewTab.WRONG to reviewRepository.wrongGroups(),
                    ReviewTab.BACK to reviewRepository.backGroups(),
                )
            }.onSuccess { loaded ->
                tabGroups = loaded
                groups = loaded[tab].orEmpty()
                statusMessage = null
            }.onFailure { statusMessage = it.userMessage() }
        }
    }

    fun openGroup(group: ReviewGroup) {
        activeGroupName = group.libraryName
        scope.launch {
            runCatching {
                when (tab) {
                    ReviewTab.FAVORITES -> reviewRepository.openFavoriteSession(group, orderMode, restart = false)
                    ReviewTab.WRONG -> reviewRepository.openWrongSession(group, orderMode, restart = false)
                    ReviewTab.BACK -> null
                }
            }.onSuccess { session ->
                if (tab == ReviewTab.BACK) {
                    backQuestions = reviewRepository.browseAll(group, orderMode)
                    backIndex = 0
                    activeSession = null
                    if (backQuestions.isNullOrEmpty()) statusMessage = "当前分组暂无题目"
                } else if (session == null) {
                    statusMessage = "当前分组暂无可复习题目"
                } else {
                    activeSession = session
                    backQuestions = null
                    statusMessage = null
                }
            }.onFailure { statusMessage = it.userMessage() }
        }
    }

    LaunchedEffect(tab) {
        activeSession = null
        backQuestions = null
        backIndex = 0
        if (tabGroups.isEmpty()) refreshGroups() else groups = tabGroups[tab].orEmpty()
    }

    activeSession?.let { session ->
        PracticeSessionScreen(
            session = session,
            repository = practiceRepository,
            onSessionChange = { activeSession = it },
            onExit = {
                activeSession = null
                refreshGroups()
            },
            pageTitle = "复习",
            libraryName = activeGroupName,
        )
        return
    }

    backQuestions?.let { questions ->
        BackQuestionScreen(
            questions = questions,
            libraryName = activeGroupName,
            index = backIndex,
            onIndexChange = { backIndex = it },
            onToggleFavorite = {
                val current = questions[backIndex]
                current.question.internalId?.let { questionId ->
                    scope.launch {
                        val selected = practiceRepository.toggleFavorite(questionId)
                        backQuestions = questions.mapIndexed { itemIndex, item ->
                            if (itemIndex == backIndex) item.copy(isFavorite = selected) else item
                        }
                    }
                }
            },
            onExit = {
                backQuestions = null
                refreshGroups()
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            TishanPageHeader(title = "复习", subtitle = "温故知新，慢慢扎根")
        }
        item {
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                ReviewTab.entries.forEach { candidate ->
                    Tab(
                        selected = tab == candidate,
                        onClick = { tab = candidate },
                        text = { Text(candidate.label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (tab == candidate) FontWeight.SemiBold else FontWeight.Normal)) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            val warning = tab == ReviewTab.WRONG
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = if (warning) com.tishan.app.core.designsystem.theme.TishanSemanticColors.current.warningContainer else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    when (tab) {
                        ReviewTab.FAVORITES -> "把值得回看的题目，留在这里。"
                        ReviewTab.WRONG -> "连续答对 3 次，题目将自动移出错题本。"
                        ReviewTab.BACK -> "直接查看答案与解析，包含申论和暂不可作答题。"
                    },
                    modifier = Modifier.padding(14.dp),
                    color = if (warning) com.tishan.app.core.designsystem.theme.TishanSemanticColors.current.onWarningContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (tab == ReviewTab.BACK) "全部题目" else "按题库与题型", style = MaterialTheme.typography.titleMedium)
                Box {
                    Surface(
                        onClick = { showOrderMenu = true },
                        shape = RoundedCornerShape(999.dp),
                        color = if (showOrderMenu) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            ReviewOrderIcon(orderMode, MaterialTheme.colorScheme.primary)
                            Text(if (orderMode == PracticeOrderMode.SEQUENTIAL) "顺序" else "随机", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            ReviewChevronIcon(MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = showOrderMenu,
                        onDismissRequest = { showOrderMenu = false },
                        modifier = Modifier.width(152.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        listOf(PracticeOrderMode.SEQUENTIAL to "顺序", PracticeOrderMode.RANDOM to "随机").forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { orderMode = mode; showOrderMenu = false },
                                trailingIcon = if (orderMode == mode) {{ Text("✓", color = MaterialTheme.colorScheme.primary) }} else null,
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
        }
        statusMessage?.let { message -> item { TishanInfoCard("提示") { Text(message) } } }
        if (groups.isEmpty()) {
            item {
                TishanEmptyState(
                    title = when (tab) {
                        ReviewTab.FAVORITES -> "暂无收藏题目"
                        ReviewTab.WRONG -> "暂无错题"
                        ReviewTab.BACK -> "暂无导入题目"
                    },
                    description = when (tab) {
                        ReviewTab.FAVORITES -> "刷题时点击「收藏」，题目会汇总到这里。"
                        ReviewTab.WRONG -> "答错的题目会自动进入错题复习。"
                        ReviewTab.BACK -> "请先在管理页导入题库或试卷。"
                    },
                )
            }
        } else {
            items(groups, key = { "${it.libraryId}-${it.questionType.externalValue}" }) { group ->
                ReviewGroupCard(
                    group = group,
                    tab = tab,
                    onOpen = { openGroup(group) },
                )
            }
        }
    }
}

@Composable
private fun ReviewGroupCard(
    group: ReviewGroup,
    tab: ReviewTab,
    onOpen: () -> Unit,
) {
    TishanOutlinedCard(onClick = onOpen) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tileColor = if (tab == ReviewTab.FAVORITES) com.tishan.app.core.designsystem.theme.TishanSemanticColors.current.successContainer else MaterialTheme.colorScheme.secondaryContainer
                val tileInk = if (tab == ReviewTab.FAVORITES) com.tishan.app.core.designsystem.theme.TishanSemanticColors.current.onSuccessContainer else MaterialTheme.colorScheme.onSecondaryContainer
                Surface(shape = RoundedCornerShape(12.dp), color = tileColor, modifier = Modifier.size(width = 44.dp, height = 48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        ReviewGroupIcon(tileInk)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.libraryName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${group.questionType.externalValue} · 来源：${group.sourceLabel ?: if (group.libraryType == "试卷") "模拟" else "刷题"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "${group.questionCount} 道${if (tab == ReviewTab.BACK) "题目" else "${tab.label}题"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReviewGroupIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.7.dp.toPx()
        drawArc(color, startAngle = 205f, sweepAngle = 145f, useCenter = false, topLeft = Offset(size.width * 0.13f, size.height * 0.13f), size = Size(size.width * 0.74f, size.height * 0.74f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, startAngle = 25f, sweepAngle = 145f, useCenter = false, topLeft = Offset(size.width * 0.13f, size.height * 0.13f), size = Size(size.width * 0.74f, size.height * 0.74f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.09f, size.height * 0.16f), Offset(size.width * 0.09f, size.height * 0.44f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.09f, size.height * 0.44f), Offset(size.width * 0.35f, size.height * 0.42f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.91f, size.height * 0.84f), Offset(size.width * 0.91f, size.height * 0.56f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.91f, size.height * 0.56f), Offset(size.width * 0.65f, size.height * 0.58f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ReviewOrderIcon(mode: PracticeOrderMode, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * unit, y1 * unit), Offset(x2 * unit, y2 * unit), stroke.width, StrokeCap.Round)
        if (mode == PracticeOrderMode.SEQUENTIAL) {
            line(5f, 6f, 14f, 6f)
            line(5f, 12f, 12f, 12f)
            line(5f, 18f, 10f, 18f)
            line(18f, 5f, 18f, 19f)
            line(15f, 16f, 18f, 19f)
            line(18f, 19f, 21f, 16f)
        } else {
            val cross = Path().apply {
                moveTo(3f * unit, 6f * unit)
                lineTo(6f * unit, 6f * unit)
                cubicTo(11f * unit, 6f * unit, 13f * unit, 18f * unit, 18f * unit, 18f * unit)
                lineTo(21f * unit, 18f * unit)
                moveTo(3f * unit, 18f * unit)
                lineTo(6f * unit, 18f * unit)
                quadraticTo(9f * unit, 18f * unit, 12f * unit, 12f * unit)
                moveTo(15f * unit, 7f * unit)
                quadraticTo(16.5f * unit, 6f * unit, 18f * unit, 6f * unit)
                lineTo(21f * unit, 6f * unit)
            }
            drawPath(cross, color, style = stroke)
            line(18f, 3f, 21f, 6f)
            line(21f, 6f, 18f, 9f)
            line(18f, 15f, 21f, 18f)
            line(21f, 18f, 18f, 21f)
        }
    }
}

@Composable
private fun ReviewChevronIcon(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val unit = size.width / 24f
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(7f * unit, 10f * unit)
            lineTo(12f * unit, 15f * unit)
            lineTo(17f * unit, 10f * unit)
        }
        drawPath(path, color, style = stroke)
    }
}

@Composable
private fun BackQuestionScreen(
    questions: List<ReviewQuestion>,
    libraryName: String?,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onExit: () -> Unit,
) {
    val item = questions[index]
    val question = item.question
    BackHandler(onBack = onExit)
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.scrollToItem(0) }
    TishanQuestionLayout(actions = {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = index > 0,
                    onClick = { onIndexChange(index - 1) },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                ) { Text("上一题", maxLines = 1) }
                FavoriteButton(
                    selected = item.isFavorite,
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                    if (index == questions.lastIndex) onExit() else onIndexChange(index + 1)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                ) { Text(if (index == questions.lastIndex) "完成" else "下一题", maxLines = 1) }
            }

    }) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TishanPageHeader(
                title = "背题",
                subtitle = "${index + 1} / ${questions.size}" + (libraryName?.let { " · $it" } ?: ""),
                onBack = onExit,
                backDescription = "返回复习",
                backOnRight = true,
            )
        }
        item {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("${question.key.questionType.externalValue} · 第 ${question.key.questionNumber} 题", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
            }
            Text(question.prompt, modifier = Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 32.sp))
        }
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("背题模式 · 仅供阅读，不自动评分", modifier = Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (question.options.isNotEmpty()) {
            items(question.options, key = { it.key }) { option ->
                val correct = (question.answer as? ExpectedAnswer.Choice)?.keys?.contains(option.key) == true
                TishanQuestionChoice(label = option.key, text = option.text, selected = false, enabled = false, singleSelection = true, correct = correct, onClick = {})
            }
        }
        item {
            Text("参考答案", style = MaterialTheme.typography.titleSmall)
            val answerText = when (val answer = question.answer) {
                is ExpectedAnswer.Choice -> question.options.filter { it.key in answer.keys }.joinToString("\n") { "${it.key}  ${it.text}" }.ifBlank { answer.displayValue() }
                is ExpectedAnswer.TrueFalse -> if (answer.rawValue == "√") "√  正确" else "×  错误"
                else -> answer.displayValue()
            }
            if (question.answer is ExpectedAnswer.TrueFalse) {
                val success = com.tishan.app.core.designsystem.theme.TishanSemanticColors.current
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = success.successContainer,
                ) {
                    Text(answerText, modifier = Modifier.padding(14.dp), color = success.onSuccessContainer, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 26.sp))
                }
            } else {
                Text(answerText, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 26.sp))
            }
        }
        item {
            Text("解析", style = MaterialTheme.typography.titleSmall)
            Text(question.explanation?.takeIf { it.isNotBlank() } ?: "暂无解析", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 26.sp))
            if (question.availability is QuestionAvailability.Unanswerable) {
                Text("不可作答：${question.availability.reason}", modifier = Modifier.padding(top = 14.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    }
}

private fun Throwable.userMessage(): String = message ?: "操作失败，请重试"
