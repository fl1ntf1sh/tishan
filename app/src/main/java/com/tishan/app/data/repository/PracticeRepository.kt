package com.tishan.app.data.repository

import androidx.room.withTransaction
import com.tishan.app.data.local.FavoriteEntity
import com.tishan.app.data.local.PracticeAttemptEntity
import com.tishan.app.data.local.PracticeSessionEntity
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.data.local.WrongQuestionEntity
import com.tishan.app.domain.practice.PracticeOrderMode
import com.tishan.app.domain.practice.PracticeOrdering
import com.tishan.app.domain.practice.PracticeStats
import com.tishan.app.domain.question.AnswerJudgement
import com.tishan.app.domain.question.Question
import com.tishan.app.domain.question.QuestionType
import com.tishan.app.domain.question.SubmittedAnswer
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class PracticeTypeSummary(
    val type: QuestionType,
    val questionCount: Int,
)

data class PracticeProgress(val current: Int, val total: Int)

data class PracticeQuestion(
    val question: Question,
    val isFavorite: Boolean,
)

data class PracticeSessionData(
    val sessionId: String,
    val libraryId: String,
    val questionType: QuestionType,
    val orderMode: PracticeOrderMode,
    val randomSeed: Long,
    val questions: List<PracticeQuestion>,
    val currentIndex: Int,
    val selectedChoiceKeys: Set<String>,
    val isSubmitted: Boolean,
    val judgement: AnswerJudgement?,
    val sessionScope: String = "PRACTICE",
) {
    val currentQuestion: PracticeQuestion get() = questions[currentIndex]
}

/** Data/domain boundary for ordinary practice sessions. */
class PracticeRepository(
    private val database: TishanDatabase,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        const val PRACTICE_SCOPE = "PRACTICE"
        const val FAVORITE_SCOPE = "FAVORITES"
        const val WRONG_SCOPE = "WRONG"
    }

    private val json = Json

    suspend fun typeSummaries(libraryId: String): List<PracticeTypeSummary> =
        database.questionDao().findPracticeTypeCounts(libraryId).mapNotNull { count ->
            QuestionType.fromExternalValue(count.questionType)?.let {
                PracticeTypeSummary(it, count.questionCount)
            }
        }

    suspend fun searchQuestions(
        libraryId: String,
        questionType: QuestionType,
        searchText: String,
    ): List<PracticeQuestion> {
        val ids = database.questionDao().findPracticeQuestionIds(
            libraryId = libraryId,
            questionType = questionType.externalValue,
            searchText = searchText,
        )
        return loadQuestions(ids)
    }

    suspend fun sessionProgress(libraryId: String, questionType: QuestionType): PracticeProgress? {
        val entity = database.practiceDao().findSession(libraryId, questionType.externalValue, PRACTICE_SCOPE) ?: return null
        val session = loadSession(entity) ?: return null
        return PracticeProgress(
            current = (session.currentIndex + if (session.isSubmitted) 1 else 0).coerceAtMost(session.questions.size),
            total = session.questions.size,
        )
    }

    suspend fun questionsByIds(questionIds: List<String>): List<PracticeQuestion> = loadQuestions(questionIds)

    /** Records an attempt produced by another flow, such as a mock exam. */
    suspend fun recordExternalAttempt(
        questionId: String,
        libraryId: String,
        submittedAnswer: String,
        judgement: AnswerJudgement,
        source: String,
        occurredAtEpochMillis: Long = nowMillis(),
    ) {
        database.withTransaction {
            database.practiceDao().insertAttempt(
                PracticeAttemptEntity(
                    id = idGenerator(),
                    questionId = questionId,
                    libraryId = libraryId,
                    source = source,
                    submittedAnswer = submittedAnswer,
                    judgement = judgement.code,
                    isCorrect = judgement is AnswerJudgement.Correct,
                    occurredAtEpochMillis = occurredAtEpochMillis,
                ),
            )
            updateWrongBook(questionId, libraryId, judgement, occurredAtEpochMillis)
        }
    }

    suspend fun openSession(
        libraryId: String,
        questionType: QuestionType,
        orderMode: PracticeOrderMode,
        searchText: String = "",
        restart: Boolean = false,
        startQuestionId: String? = null,
    ): PracticeSessionData? {
        val sourceIds = database.questionDao().findPracticeQuestionIds(
            libraryId = libraryId,
            questionType = questionType.externalValue,
            searchText = searchText,
        )
        return openSessionForQuestionIds(
            libraryId = libraryId,
            questionType = questionType,
            questionIds = sourceIds,
            orderMode = orderMode,
            sessionScope = PRACTICE_SCOPE,
            restart = restart || searchText.isNotBlank() || startQuestionId != null,
            startQuestionId = startQuestionId,
        )
    }

    suspend fun openSessionForQuestionIds(
        libraryId: String,
        questionType: QuestionType,
        questionIds: List<String>,
        orderMode: PracticeOrderMode,
        sessionScope: String,
        restart: Boolean = false,
        startQuestionId: String? = null,
    ): PracticeSessionData? {
        val practiceDao = database.practiceDao()
        val existing = practiceDao.findSession(libraryId, questionType.externalValue, sessionScope)
        if (!restart && startQuestionId == null &&
            existing != null && existing.orderMode == orderMode.name
        ) {
            return loadSession(existing)
        }

        if (questionIds.isEmpty()) return null
        val seed = if (orderMode == PracticeOrderMode.RANDOM) nowMillis() else 0L
        val orderedIds = PracticeOrdering.order(questionIds, orderMode, seed)
        val startIndex = startQuestionId?.let(orderedIds::indexOf)?.takeIf { it >= 0 } ?: 0
        val entity = PracticeSessionEntity(
            id = idGenerator(),
            libraryId = libraryId,
            questionType = questionType.externalValue,
            sessionScope = sessionScope,
            orderMode = orderMode.name,
            randomSeed = seed,
            questionIdsJson = encodeIds(orderedIds),
            currentIndex = startIndex,
            selectedKeys = "",
            isSubmitted = false,
            lastJudgement = null,
            updatedAtEpochMillis = nowMillis(),
        )
        practiceDao.saveSession(entity)
        return loadSession(entity)
    }

    suspend fun saveDraft(session: PracticeSessionData) {
        database.practiceDao().saveSession(session.toEntity())
    }

    suspend fun submitCurrent(
        session: PracticeSessionData,
        submission: SubmittedAnswer,
    ): PracticeSessionData {
        val question = session.currentQuestion.question
        val judgement = question.judge(submission)
        val now = nowMillis()
        val submittedText = submission.asStorageValue()
        val updated = session.copy(
            selectedChoiceKeys = submission.asSelectedKeys(),
            isSubmitted = true,
            judgement = judgement,
        )
        database.withTransaction {
            database.practiceDao().insertAttempt(
                PracticeAttemptEntity(
                    id = idGenerator(),
                    questionId = question.internalId ?: error("practice question has no internal id"),
                    libraryId = session.libraryId,
                    source = session.attemptSource(),
                    submittedAnswer = submittedText,
                    judgement = judgement.code,
                    isCorrect = judgement is AnswerJudgement.Correct,
                    occurredAtEpochMillis = now,
                ),
            )
            updateWrongBook(question.internalId ?: error("practice question has no internal id"), session.libraryId, judgement, now)
            database.practiceDao().saveSession(updated.toEntity())
        }
        return updated
    }

    suspend fun nextQuestion(session: PracticeSessionData): PracticeSessionData {
        val nextIndex = (session.currentIndex + 1).coerceAtMost(session.questions.lastIndex)
        return moveToQuestion(session, nextIndex)
    }

    suspend fun previousQuestion(session: PracticeSessionData): PracticeSessionData {
        val previousIndex = (session.currentIndex - 1).coerceAtLeast(0)
        return moveToQuestion(session, previousIndex)
    }

    private suspend fun moveToQuestion(session: PracticeSessionData, index: Int): PracticeSessionData {
        val question = session.questions[index].question
        val questionId = requireNotNull(question.internalId)
        val attempt = database.practiceDao().findLatestAttempt(questionId, session.attemptSource())
        val updated = session.copy(
            currentIndex = index,
            selectedChoiceKeys = attempt?.submittedAnswer
                ?.toSelectedKeys(question.key.questionType)
                .orEmpty(),
            isSubmitted = attempt != null,
            judgement = attempt?.judgement?.let(::judgementFromCode),
        )
        saveDraft(updated)
        return updated
    }

    suspend fun toggleFavorite(session: PracticeSessionData): PracticeSessionData {
        val questionId = session.currentQuestion.question.internalId ?: error("practice question has no internal id")
        val selected = toggleFavorite(questionId)
        return session.copy(
            questions = session.questions.map { item ->
                if (item.question.internalId == questionId) item.copy(isFavorite = selected) else item
            },
        )
    }

    suspend fun toggleFavorite(questionId: String): Boolean {
        val dao = database.practiceDao()
        val nowFavorite = dao.isFavorite(questionId)
        if (nowFavorite) {
            dao.removeFavorite(questionId)
        } else {
            dao.addFavorite(FavoriteEntity(questionId, nowMillis()))
        }
        return !nowFavorite
    }

    suspend fun stats(): PracticeStats {
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val dao = database.practiceDao()
        val snapshot = dao.loadStats(startOfDay)
        return PracticeStats(snapshot.todayQuestionCount, snapshot.wrongQuestionCount, snapshot.favoriteCount)
    }

    private suspend fun loadSession(entity: PracticeSessionEntity): PracticeSessionData? {
        val ids = decodeIds(entity.questionIdsJson)
        if (ids.isEmpty()) return null
        val questions = loadQuestions(ids)
        if (questions.isEmpty()) return null
        val index = entity.currentIndex.coerceIn(0, questions.lastIndex)
        return PracticeSessionData(
            sessionId = entity.id,
            libraryId = entity.libraryId,
            questionType = QuestionType.fromExternalValue(entity.questionType)
                ?: error("unsupported practice question type"),
            orderMode = runCatching { PracticeOrderMode.valueOf(entity.orderMode) }
                .getOrDefault(PracticeOrderMode.SEQUENTIAL),
            randomSeed = entity.randomSeed,
            sessionScope = entity.sessionScope,
            questions = questions,
            currentIndex = index,
            selectedChoiceKeys = decodeSelectedKeys(entity.selectedKeys),
            isSubmitted = entity.isSubmitted,
            judgement = entity.lastJudgement?.let(::judgementFromCode),
        )
    }

    private suspend fun loadQuestions(ids: List<String>): List<PracticeQuestion> {
        if (ids.isEmpty()) return emptyList()
        val rows = database.questionDao().findAllWithOptionsByIds(ids)
        val favorites = database.practiceDao().findFavoriteIds(ids).toSet()
        val byId = rows.mapNotNull { row ->
            row.toDomainQuestion()?.let { row.question.id to PracticeQuestion(it, isFavorite = false) }
        }.toMap()
        return ids.mapNotNull { id -> byId[id]?.copy(isFavorite = id in favorites) }
    }

    private suspend fun updateWrongBook(
        questionId: String,
        libraryId: String,
        judgement: AnswerJudgement,
        now: Long,
    ) {
        val dao = database.practiceDao()
        val current = dao.findWrong(questionId)
        when (judgement) {
            AnswerJudgement.Correct -> {
                if (current != null) {
                    val consecutive = current.consecutiveCorrect + 1
                    if (consecutive >= 3) {
                        dao.removeWrong(questionId)
                    } else {
                        dao.saveWrong(current.copy(
                            totalAttempts = current.totalAttempts + 1,
                            consecutiveCorrect = consecutive,
                            lastIsCorrect = true,
                            lastAttemptAtEpochMillis = now,
                        ))
                    }
                }
            }

            AnswerJudgement.Incorrect -> dao.saveWrong(
                current?.copy(
                    totalAttempts = current.totalAttempts + 1,
                    consecutiveCorrect = 0,
                    lastIsCorrect = false,
                    lastAttemptAtEpochMillis = now,
                ) ?: WrongQuestionEntity(
                    questionId = questionId,
                    libraryId = libraryId,
                    totalAttempts = 1,
                    consecutiveCorrect = 0,
                    lastIsCorrect = false,
                    lastAttemptAtEpochMillis = now,
                ),
            )

            else -> Unit
        }
    }

    private fun PracticeSessionData.toEntity() = PracticeSessionEntity(
        id = sessionId,
        libraryId = libraryId,
        questionType = questionType.externalValue,
        sessionScope = sessionScope,
        orderMode = orderMode.name,
        randomSeed = randomSeed,
        questionIdsJson = encodeIds(questions.map { requireNotNull(it.question.internalId) }),
        currentIndex = currentIndex,
        selectedKeys = selectedChoiceKeys.sorted().joinToString(","),
        isSubmitted = isSubmitted,
        lastJudgement = judgement?.code,
        updatedAtEpochMillis = nowMillis(),
    )

    private fun encodeIds(ids: List<String>): String = JsonArray(ids.map(::JsonPrimitive)).toString()

    private fun decodeIds(value: String): List<String> = runCatching {
        (json.parseToJsonElement(value) as JsonArray).mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }
    }.getOrDefault(emptyList())

    private fun decodeSelectedKeys(value: String): Set<String> =
        value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private fun PracticeSessionData.attemptSource(): String = "practice-session:$sessionId"

    private fun String.toSelectedKeys(questionType: QuestionType): Set<String> = when (questionType) {
        QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE ->
            runCatching { SubmittedAnswer.Choice.fromRaw(this).keys }.getOrDefault(emptySet())
        QuestionType.TRUE_FALSE -> when (this) {
            "√", "×" -> setOf(this)
            else -> emptySet()
        }
        QuestionType.ESSAY -> emptySet()
    }

    private fun SubmittedAnswer.asSelectedKeys(): Set<String> = when (this) {
        is SubmittedAnswer.Choice -> keys
        is SubmittedAnswer.TrueFalse -> setOf(if (value) "√" else "×")
        is SubmittedAnswer.Essay -> emptySet()
    }

    private fun SubmittedAnswer.asStorageValue(): String = when (this) {
        is SubmittedAnswer.Choice -> keys.sorted().joinToString("")
        is SubmittedAnswer.TrueFalse -> if (value) "√" else "×"
        is SubmittedAnswer.Essay -> text
    }

    private val AnswerJudgement.code: String
        get() = when (this) {
            AnswerJudgement.Correct -> "CORRECT"
            AnswerJudgement.Incorrect -> "INCORRECT"
            AnswerJudgement.NotAutoGraded -> "NOT_AUTO_GRADED"
            is AnswerJudgement.Unavailable -> "UNAVAILABLE"
            is AnswerJudgement.InvalidSubmission -> "INVALID"
        }

    private fun judgementFromCode(code: String): AnswerJudgement = when (code) {
        "CORRECT" -> AnswerJudgement.Correct
        "INCORRECT" -> AnswerJudgement.Incorrect
        "NOT_AUTO_GRADED" -> AnswerJudgement.NotAutoGraded
        "UNAVAILABLE" -> AnswerJudgement.Unavailable("题目不可作答")
        else -> AnswerJudgement.InvalidSubmission("无法恢复的作答状态")
    }
}
