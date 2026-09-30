package com.tishan.app.domain.practice

import com.tishan.app.domain.question.QuestionType
import kotlinx.serialization.Serializable

data class ExamDurationSettings(
    val singleChoiceSeconds: Int = 30 * 60,
    val multipleChoiceSeconds: Int = 30 * 60,
    val trueFalseSeconds: Int = 15 * 60,
) {
    init {
        require(singleChoiceSeconds >= 0)
        require(multipleChoiceSeconds >= 0)
        require(trueFalseSeconds >= 0)
    }

    fun durationSeconds(type: QuestionType): Int? = when (type) {
        QuestionType.SINGLE_CHOICE -> singleChoiceSeconds
        QuestionType.MULTIPLE_CHOICE -> multipleChoiceSeconds
        QuestionType.TRUE_FALSE -> trueFalseSeconds
        QuestionType.ESSAY -> null
    }
}

data class ExamTimerState(
    val elapsedByTypeMillis: Map<QuestionType, Long> = emptyMap(),
    val activeType: QuestionType? = null,
    val activeSinceEpochMillis: Long? = null,
    val isPaused: Boolean = false,
)

data class ExamTimerSnapshot(
    val elapsedMillis: Long,
    val remainingMillis: Long?,
    val timedOut: Boolean,
)

/** Pure timer transitions. The caller supplies time so pause/resume and timeout are testable. */
object ExamTimer {
    fun start(state: ExamTimerState, type: QuestionType, nowMillis: Long): ExamTimerState {
        val committed = commit(state, nowMillis)
        return if (type == QuestionType.ESSAY) {
            committed.copy(activeType = null, activeSinceEpochMillis = null, isPaused = false)
        } else {
            committed.copy(activeType = type, activeSinceEpochMillis = nowMillis, isPaused = false)
        }
    }

    fun pause(state: ExamTimerState, nowMillis: Long): ExamTimerState =
        commit(state, nowMillis).copy(activeType = null, activeSinceEpochMillis = null, isPaused = true)

    fun elapsedMillis(state: ExamTimerState, type: QuestionType, nowMillis: Long): Long =
        commit(state, nowMillis).elapsedByTypeMillis[type].orZero()

    fun snapshot(
        state: ExamTimerState,
        settings: ExamDurationSettings,
        type: QuestionType,
        nowMillis: Long,
    ): ExamTimerSnapshot {
        val elapsed = elapsedMillis(state, type, nowMillis)
        val duration = settings.durationSeconds(type)?.times(1000L)
        val remaining = duration?.let { (it - elapsed).coerceAtLeast(0L) }
        return ExamTimerSnapshot(
            elapsedMillis = elapsed,
            remainingMillis = remaining,
            timedOut = remaining == 0L,
        )
    }

    private fun commit(state: ExamTimerState, nowMillis: Long): ExamTimerState {
        val activeType = state.activeType
        val activeSince = state.activeSinceEpochMillis
        if (activeType == null || activeSince == null || state.isPaused) return state
        val delta = (nowMillis - activeSince).coerceAtLeast(0L)
        if (delta == 0L) return state
        return state.copy(
            elapsedByTypeMillis = state.elapsedByTypeMillis + (
                activeType to (state.elapsedByTypeMillis[activeType].orZero() + delta)
            ),
            activeSinceEpochMillis = nowMillis,
        )
    }

    private fun Long?.orZero(): Long = this ?: 0L
}

@Serializable
data class ExamAnswerRecord(
    val questionId: String,
    val submittedAnswer: String,
    val judgement: String,
    val isCorrect: Boolean,
    val answeredAtEpochMillis: Long,
)

data class ExamTypeScore(
    val type: QuestionType,
    val correct: Int,
    val total: Int,
) {
    val percentage: Int get() = if (total == 0) 0 else (correct * 100 / total)
}

data class ExamResult(
    val sessionId: String,
    val libraryId: String,
    val totalScore: Int,
    val objectiveTotal: Int,
    val elapsedMillis: Long,
    val typeScores: List<ExamTypeScore>,
    val wrongQuestionIds: List<String>,
)
