package com.tishan.app.data.repository

import com.tishan.app.data.local.ReviewGroupRow
import com.tishan.app.data.local.TishanDatabase
import com.tishan.app.domain.practice.PracticeOrderMode
import com.tishan.app.domain.practice.PracticeOrdering
import com.tishan.app.domain.question.Question
import com.tishan.app.domain.question.QuestionType

data class ReviewGroup(
    val libraryId: String,
    val libraryName: String,
    val libraryType: String,
    val questionType: QuestionType,
    val questionCount: Int,
    val sourceLabel: String? = null,
)

data class ReviewQuestion(
    val question: Question,
    val isFavorite: Boolean,
)

/** Data/domain boundary for favorites, wrong questions, and full back-reading. */
class ReviewRepository(
    private val database: TishanDatabase,
    private val practiceRepository: PracticeRepository,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun favoriteGroups(): List<ReviewGroup> =
        database.reviewDao().favoriteGroups().mapNotNull(::toGroup)

    suspend fun wrongGroups(): List<ReviewGroup> =
        database.reviewDao().wrongGroups().mapNotNull(::toGroup)

    suspend fun backGroups(): List<ReviewGroup> =
        database.reviewDao().backGroups().mapNotNull(::toGroup)

    suspend fun openFavoriteSession(
        group: ReviewGroup,
        orderMode: PracticeOrderMode,
        restart: Boolean = false,
    ) = practiceRepository.openSessionForQuestionIds(
        libraryId = group.libraryId,
        questionType = group.questionType,
        questionIds = database.reviewDao().favoriteQuestionIds(group.libraryId, group.questionType.externalValue),
        orderMode = orderMode,
        sessionScope = PracticeRepository.FAVORITE_SCOPE,
        restart = restart,
    )

    suspend fun openWrongSession(
        group: ReviewGroup,
        orderMode: PracticeOrderMode,
        restart: Boolean = false,
    ) = practiceRepository.openSessionForQuestionIds(
        libraryId = group.libraryId,
        questionType = group.questionType,
        questionIds = database.reviewDao().wrongQuestionIds(group.libraryId, group.questionType.externalValue),
        orderMode = orderMode,
        sessionScope = PracticeRepository.WRONG_SCOPE,
        restart = restart,
    )

    suspend fun browseAll(
        group: ReviewGroup,
        orderMode: PracticeOrderMode,
    ): List<ReviewQuestion> {
        val ids = database.reviewDao().allQuestionIds(group.libraryId, group.questionType.externalValue)
        if (ids.isEmpty()) return emptyList()
        val seed = if (orderMode == PracticeOrderMode.RANDOM) nowMillis() else 0L
        val orderedIds = PracticeOrdering.order(ids, orderMode, seed)
        val rows = database.questionDao().findAllWithOptionsByIds(orderedIds)
        val favorites = database.practiceDao().findFavoriteIds(orderedIds).toSet()
        val byId = rows.mapNotNull { row -> row.toDomainQuestion()?.let { question -> row.question.id to question } }.toMap()
        return orderedIds.mapNotNull { id ->
            byId[id]?.let { question -> ReviewQuestion(question, id in favorites) }
        }
    }

    private fun toGroup(row: ReviewGroupRow): ReviewGroup? =
        QuestionType.fromExternalValue(row.questionType)?.let { type ->
            ReviewGroup(
                libraryId = row.libraryId,
                libraryName = row.libraryName,
                libraryType = row.libraryType,
                questionType = type,
                questionCount = row.questionCount,
                sourceLabel = row.sourceLabel,
            )
        }
}
