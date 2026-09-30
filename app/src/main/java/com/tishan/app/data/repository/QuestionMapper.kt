package com.tishan.app.data.repository

import com.tishan.app.data.local.QuestionWithOptions
import com.tishan.app.domain.question.AnswerRules
import com.tishan.app.domain.question.Question
import com.tishan.app.domain.question.QuestionAvailability
import com.tishan.app.domain.question.QuestionKey
import com.tishan.app.domain.question.QuestionOption
import com.tishan.app.domain.question.QuestionType

fun QuestionWithOptions.toDomainQuestion(): Question? {
    val type = QuestionType.fromExternalValue(question.questionType) ?: return null
    val answer = runCatching {
        when (type) {
            QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> AnswerRules.normalizeChoice(question.answerRaw)
            QuestionType.TRUE_FALSE -> AnswerRules.normalizeTrueFalse(question.answerRaw)
            QuestionType.ESSAY -> AnswerRules.normalizeEssay(question.answerRaw)
        }
    }.getOrNull() ?: return null
    return runCatching {
        Question(
            key = QuestionKey(question.libraryId, type, question.questionNumber),
            prompt = question.prompt,
            options = options.sortedBy { it.position }
                .map { QuestionOption(it.optionKey, it.text, it.position) },
            answer = answer,
            explanation = question.explanation,
            availability = if (question.isAnswerable) {
                QuestionAvailability.Answerable
            } else {
                QuestionAvailability.Unanswerable(question.unanswerableReason ?: "题目不可作答")
            },
            internalId = question.id,
        )
    }.getOrNull()
}
