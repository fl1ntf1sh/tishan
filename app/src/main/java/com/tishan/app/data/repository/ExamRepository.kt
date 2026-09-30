package com.tishan.app.data.repository

import androidx.room.withTransaction
import com.tishan.app.data.local.ExamResultEntity
import com.tishan.app.data.local.ExamSessionEntity
import com.tishan.app.data.local.ExamSettingsEntity
import com.tishan.app.data.local.LibrarySummary
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.domain.library.LibraryType
import com.tishan.app.domain.practice.ExamAnswerRecord
import com.tishan.app.domain.practice.ExamDurationSettings
import com.tishan.app.domain.practice.ExamResult
import com.tishan.app.domain.practice.ExamTimer
import com.tishan.app.domain.practice.ExamTimerSnapshot
import com.tishan.app.domain.practice.ExamTimerState
import com.tishan.app.domain.practice.ExamTypeScore
import com.tishan.app.domain.question.AnswerJudgement
import com.tishan.app.domain.question.QuestionAvailability
import com.tishan.app.domain.question.QuestionType
import com.tishan.app.domain.question.SubmittedAnswer
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ExamSessionData(
    val sessionId: String,
    val libraryId: String,
    val settings: ExamDurationSettings,
    val questions: List<PracticeQuestion>,
    val currentIndex: Int,
    val selectedChoiceKeys: Set<String>,
    val answers: Map<String, ExamAnswerRecord>,
    val timerState: ExamTimerState,
    val isPaused: Boolean,
    val isSubmitted: Boolean,
    val result: ExamResult? = null,
) {
    val currentQuestion: PracticeQuestion get() = questions[currentIndex]
    val currentAnswer: ExamAnswerRecord?
        get() = currentQuestion.question.internalId?.let(answers::get)
}

data class ExamResultSummary(
    val result: ExamResult,
    val libraryName: String,
    val submittedAtEpochMillis: Long,
)

/** Data/domain boundary for timed mock exams. */
class ExamRepository(
    private val database: TishanDatabase,
    private val practiceRepository: PracticeRepository,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val defaultDuration: () -> ExamDurationSettings = { ExamDurationSettings() },
) {
    private val json = Json

    suspend fun listExamPapers(): List<LibrarySummary> =
        database.libraryDao().findSummariesByType(LibraryType.EXAM_PAPER.externalValue)

    suspend fun listRecentResults(limit: Int = 3): List<ExamResultSummary> {
        require(limit >= 0) { "limit must not be negative" }
        if (limit == 0) return emptyList()
        val results = database.examDao().findRecentResults(limit)
        val names = database.libraryDao()
            .findByIds(results.map(ExamResultEntity::libraryId).distinct())
            .associate { it.id to it.name }
        return results
            .map { entity ->
                ExamResultSummary(
                    result = entity.toDomain(),
                    libraryName = names[entity.libraryId] ?: "已删除试卷",
                    submittedAtEpochMillis = entity.submittedAtEpochMillis,
                )
            }
    }

    suspend fun loadSettings(libraryId: String): ExamDurationSettings =
        database.examDao().findSettings(libraryId)?.toDomain() ?: defaultDuration()

    suspend fun saveSettings(libraryId: String, settings: ExamDurationSettings) {
        database.examDao().saveSettings(settings.toEntity(libraryId, nowMillis()))
    }

    suspend fun openSession(libraryId: String, restart: Boolean = false): ExamSessionData? {
        require(database.libraryDao().findById(libraryId)?.libraryType == LibraryType.EXAM_PAPER.externalValue) {
            "只能对试卷资源开始模拟测试"
        }
        val settings = loadSettings(libraryId)
        val existing = database.examDao().findSession(libraryId)
        if (!restart && existing != null && !existing.isSubmitted) return loadSession(existing, settings)

        if (existing != null && (restart || existing.isSubmitted)) {
            database.examDao().deleteSession(libraryId)
        }

        val ids = database.questionDao().findExamQuestionIds(libraryId)
        val questions = practiceRepository.questionsByIds(ids)
        if (questions.isEmpty()) return null
        val initialTimer = ExamTimer.start(ExamTimerState(), questions.first().question.key.questionType, nowMillis())
        val session = ExamSessionData(
            sessionId = idGenerator(),
            libraryId = libraryId,
            settings = settings,
            questions = questions,
            currentIndex = 0,
            selectedChoiceKeys = emptySet(),
            answers = emptyMap(),
            timerState = initialTimer,
            isPaused = false,
            isSubmitted = false,
        )
        saveEntity(session)
        return session
    }

    fun timerSnapshot(session: ExamSessionData, now: Long = nowMillis()): ExamTimerSnapshot =
        ExamTimer.snapshot(
            state = session.timerState,
            settings = session.settings,
            type = session.currentQuestion.question.key.questionType,
            nowMillis = now,
        )

    suspend fun pause(session: ExamSessionData): ExamSessionData {
        val paused = session.copy(
            timerState = ExamTimer.pause(session.timerState, nowMillis()),
            isPaused = true,
        )
        saveEntity(paused)
        return paused
    }

    suspend fun resume(session: ExamSessionData): ExamSessionData {
        val resumed = session.copy(
            timerState = ExamTimer.start(
                session.timerState.copy(isPaused = false),
                session.currentQuestion.question.key.questionType,
                nowMillis(),
            ),
            isPaused = false,
        )
        saveEntity(resumed)
        return resumed
    }

    suspend fun saveDraft(session: ExamSessionData) {
        saveEntity(session)
    }

    suspend fun submitCurrent(
        session: ExamSessionData,
        submission: SubmittedAnswer,
    ): ExamSessionData {
        check(!session.isSubmitted) { "试卷已交卷，不能修改答案" }
        check(!session.isPaused) { "请先继续考试" }
        val question = session.currentQuestion.question
        if (question.availability is QuestionAvailability.Unanswerable || question.key.questionType == QuestionType.ESSAY) {
            return session
        }
        val questionId = question.internalId ?: error("exam question has no internal id")
        val judgement = question.judge(submission)
        val answer = ExamAnswerRecord(
            questionId = questionId,
            submittedAnswer = submission.storageValue(),
            judgement = judgement.code,
            isCorrect = judgement is AnswerJudgement.Correct,
            answeredAtEpochMillis = nowMillis(),
        )
        val updated = session.copy(
            selectedChoiceKeys = submission.selectedKeys(),
            answers = session.answers + (questionId to answer),
        )
        saveEntity(updated)
        return updated
    }

    suspend fun clearCurrentAnswer(session: ExamSessionData): ExamSessionData {
        check(!session.isSubmitted) { "试卷已交卷，不能修改答案" }
        check(!session.isPaused) { "请先继续考试" }
        val questionId = session.currentQuestion.question.internalId ?: error("exam question has no internal id")
        val updated = session.copy(selectedChoiceKeys = emptySet(), answers = session.answers - questionId)
        saveEntity(updated)
        return updated
    }

    suspend fun toggleFavorite(session: ExamSessionData): ExamSessionData {
        val questionId = session.currentQuestion.question.internalId ?: error("exam question has no internal id")
        val selected = practiceRepository.toggleFavorite(questionId)
        return session.copy(questions = session.questions.map { item ->
            if (item.question.internalId == questionId) item.copy(isFavorite = selected) else item
        })
    }

    suspend fun nextQuestion(session: ExamSessionData): ExamSessionData {
        if (session.currentIndex == session.questions.lastIndex) return session
        return moveToQuestion(session, session.currentIndex + 1)
    }

    suspend fun previousQuestion(session: ExamSessionData): ExamSessionData {
        if (session.currentIndex == 0) return session
        return moveToQuestion(session, session.currentIndex - 1)
    }

    suspend fun moveToQuestion(session: ExamSessionData, index: Int): ExamSessionData {
        require(index in session.questions.indices) { "题目不存在" }
        check(!session.isSubmitted) { "试卷已交卷" }
        val question = session.questions[index].question
        val type = question.key.questionType
        val timer = if (session.isPaused) {
            session.timerState
        } else {
            ExamTimer.start(session.timerState, type, nowMillis())
        }
        val updated = session.copy(
            currentIndex = index,
            selectedChoiceKeys = question.internalId
                ?.let(session.answers::get)
                ?.submittedAnswer
                ?.let { raw ->
                    if (type == QuestionType.TRUE_FALSE) setOf(raw)
                    else runCatching { SubmittedAnswer.Choice.fromRaw(raw).keys }.getOrDefault(emptySet())
                }
                ?: emptySet(),
            timerState = timer,
        )
        saveEntity(updated)
        return updated
    }

    suspend fun submitExam(session: ExamSessionData): Pair<ExamSessionData, ExamResult> {
        val existing = database.examDao().findResult(session.sessionId)
        if (existing != null) {
            val result = existing.toDomain()
            return session.copy(isSubmitted = true, isPaused = true, result = result) to result
        }

        val objectiveQuestions = session.questions.map { it.question }.filter {
            it.availability is QuestionAvailability.Answerable && it.key.questionType != QuestionType.ESSAY
        }
        val completedAnswers = session.answers.toMutableMap()
        for (question in objectiveQuestions) {
            val questionId = question.internalId ?: continue
            if (questionId !in completedAnswers) {
                val unanswered = ExamAnswerRecord(
                    questionId = questionId,
                    submittedAnswer = "未作答",
                    judgement = "INCORRECT",
                    isCorrect = false,
                    answeredAtEpochMillis = nowMillis(),
                )
                completedAnswers[questionId] = unanswered
            }
        }

        val typeScores = QuestionType.entries.filter { it != QuestionType.ESSAY }.map { type ->
            val typed = objectiveQuestions.filter { it.key.questionType == type }
            ExamTypeScore(
                type = type,
                correct = typed.count { question -> completedAnswers[question.internalId]?.isCorrect == true },
                total = typed.size,
            )
        }
        val wrongIds = objectiveQuestions.mapNotNull { question ->
            val id = question.internalId ?: return@mapNotNull null
            id.takeIf { completedAnswers[id]?.isCorrect != true }
        }
        val pausedTimer = ExamTimer.pause(session.timerState, nowMillis())
        val result = ExamResult(
            sessionId = session.sessionId,
            libraryId = session.libraryId,
            totalScore = typeScores.sumOf(ExamTypeScore::correct),
            objectiveTotal = typeScores.sumOf(ExamTypeScore::total),
            elapsedMillis = typeScores.sumOf { type -> pausedTimer.elapsedByTypeMillis[type.type] ?: 0L },
            typeScores = typeScores,
            wrongQuestionIds = wrongIds,
        )
        val submitted = session.copy(
            answers = completedAnswers,
            timerState = pausedTimer,
            isPaused = true,
            isSubmitted = true,
            result = result,
        )
        database.withTransaction {
            for (question in objectiveQuestions) {
                val questionId = question.internalId ?: continue
                val answer = completedAnswers[questionId] ?: continue
                practiceRepository.recordExternalAttempt(
                    questionId = questionId,
                    libraryId = session.libraryId,
                    submittedAnswer = answer.submittedAnswer,
                    judgement = if (answer.isCorrect) AnswerJudgement.Correct else AnswerJudgement.Incorrect,
                    source = "exam:${session.sessionId}",
                    occurredAtEpochMillis = answer.answeredAtEpochMillis,
                )
            }
            saveEntity(submitted)
            database.examDao().saveResult(result.toEntity(idGenerator(), nowMillis()))
        }
        return submitted to result
    }

    private suspend fun saveEntity(session: ExamSessionData) {
        database.examDao().saveSession(session.toEntity())
    }

    private suspend fun loadSession(
        entity: ExamSessionEntity,
        settings: ExamDurationSettings,
    ): ExamSessionData? {
        val ids = decodeIds(entity.questionIdsJson)
        val questions = practiceRepository.questionsByIds(ids)
        if (questions.isEmpty()) return null
        val index = entity.currentIndex.coerceIn(0, questions.lastIndex)
        val timer = ExamTimerState(
            elapsedByTypeMillis = decodeElapsed(entity.elapsedByTypeJson),
            activeType = entity.activeQuestionType?.let(QuestionType::fromExternalValue),
            activeSinceEpochMillis = entity.activeSinceEpochMillis,
            isPaused = entity.isPaused,
        )
        val result = database.examDao().findResult(entity.id)?.toDomain()
        return ExamSessionData(
            sessionId = entity.id,
            libraryId = entity.libraryId,
            settings = settings,
            questions = questions,
            currentIndex = index,
            selectedChoiceKeys = decodeSelectedKeys(entity.selectedKeys),
            answers = decodeAnswers(entity.answersJson),
            timerState = timer,
            isPaused = entity.isPaused,
            isSubmitted = entity.isSubmitted,
            result = result,
        )
    }

    private fun ExamSessionData.toEntity() = ExamSessionEntity(
        id = sessionId,
        libraryId = libraryId,
        questionIdsJson = encodeIds(questions.map { requireNotNull(it.question.internalId) }),
        currentIndex = currentIndex,
        selectedKeys = selectedChoiceKeys.sorted().joinToString(","),
        answersJson = json.encodeToString(answers.values.toList()),
        elapsedByTypeJson = encodeElapsed(timerState.elapsedByTypeMillis),
        activeQuestionType = timerState.activeType?.externalValue,
        activeSinceEpochMillis = timerState.activeSinceEpochMillis,
        isPaused = isPaused,
        isSubmitted = isSubmitted,
        updatedAtEpochMillis = nowMillis(),
    )

    private fun ExamSettingsEntity.toDomain() = ExamDurationSettings(
        singleChoiceSeconds = singleChoiceSeconds,
        multipleChoiceSeconds = multipleChoiceSeconds,
        trueFalseSeconds = trueFalseSeconds,
    )

    private fun ExamDurationSettings.toEntity(libraryId: String, now: Long) = ExamSettingsEntity(
        libraryId = libraryId,
        singleChoiceSeconds = singleChoiceSeconds,
        multipleChoiceSeconds = multipleChoiceSeconds,
        trueFalseSeconds = trueFalseSeconds,
        updatedAtEpochMillis = now,
    )

    private fun ExamResult.toEntity(id: String, now: Long) = ExamResultEntity(
        id = id,
        sessionId = sessionId,
        libraryId = libraryId,
        totalScore = totalScore,
        objectiveTotal = objectiveTotal,
        elapsedMillis = elapsedMillis,
        typeScoresJson = json.encodeToString(typeScores.map { ScoreRecord(it.type.externalValue, it.correct, it.total) }),
        wrongQuestionIdsJson = encodeIds(wrongQuestionIds),
        submittedAtEpochMillis = now,
    )

    private fun ExamResultEntity.toDomain(): ExamResult {
        val scores = runCatching {
            json.decodeFromString<List<ScoreRecord>>(typeScoresJson).mapNotNull { score ->
                QuestionType.fromExternalValue(score.type)?.let { ExamTypeScore(it, score.correct, score.total) }
            }
        }.getOrDefault(emptyList())
        return ExamResult(
            sessionId = sessionId,
            libraryId = libraryId,
            totalScore = totalScore,
            objectiveTotal = objectiveTotal,
            elapsedMillis = elapsedMillis,
            typeScores = scores,
            wrongQuestionIds = decodeIds(wrongQuestionIdsJson),
        )
    }

    @Serializable
    private data class ScoreRecord(val type: String, val correct: Int, val total: Int)

    private fun encodeIds(ids: List<String>): String = JsonArray(ids.map(::JsonPrimitive)).toString()

    private fun decodeIds(value: String): List<String> = runCatching {
        (json.parseToJsonElement(value) as JsonArray).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    }.getOrDefault(emptyList())

    private fun encodeElapsed(values: Map<QuestionType, Long>): String = buildJsonObject {
        values.forEach { (type, millis) -> put(type.externalValue, JsonPrimitive(millis)) }
    }.toString()

    private fun decodeElapsed(value: String): Map<QuestionType, Long> = runCatching {
        json.parseToJsonElement(value).jsonObject.mapNotNull { (key, raw) ->
            QuestionType.fromExternalValue(key)?.let { it to raw.jsonPrimitive.content.toLong() }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun decodeAnswers(value: String): Map<String, ExamAnswerRecord> = runCatching {
        json.decodeFromString<List<ExamAnswerRecord>>(value).associateBy(ExamAnswerRecord::questionId)
    }.getOrDefault(emptyMap())

    private fun decodeSelectedKeys(value: String): Set<String> =
        value.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    private fun SubmittedAnswer.selectedKeys(): Set<String> = when (this) {
        is SubmittedAnswer.Choice -> keys
        is SubmittedAnswer.TrueFalse -> setOf(if (value) "√" else "×")
        is SubmittedAnswer.Essay -> emptySet()
    }

    private fun SubmittedAnswer.storageValue(): String = when (this) {
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
}
