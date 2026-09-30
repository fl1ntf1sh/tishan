package com.tishan.app.domain.question

import java.util.Locale

sealed interface ExpectedAnswer {
    val rawValue: String

    fun displayValue(): String = when (this) {
        is Choice -> keys.sorted().joinToString("")
        is TrueFalse, is Essay -> rawValue
    }

    data class Choice(
        override val rawValue: String,
        val keys: Set<String>,
    ) : ExpectedAnswer {
        init {
            require(rawValue.isNotBlank()) { "choice answer must not be blank" }
            require(keys.isNotEmpty()) { "choice answer must contain at least one key" }
            require(keys.all { it.matches(OPTION_KEY_PATTERN) }) {
                "choice answer keys must be uppercase letters"
            }
        }

        private companion object {
            val OPTION_KEY_PATTERN = Regex("[A-Z]")
        }
    }

    data class TrueFalse(
        override val rawValue: String,
        val value: Boolean,
    ) : ExpectedAnswer {
        init {
            require(rawValue.trim() == "√" || rawValue.trim() == "×") {
                "true/false answer must be √ or ×"
            }
        }
    }

    data class Essay(override val rawValue: String) : ExpectedAnswer {
        init {
            require(rawValue.isNotBlank()) { "essay answer must not be blank" }
        }
    }
}

sealed interface SubmittedAnswer {
    data class Choice(val keys: Set<String>) : SubmittedAnswer {
        init {
            require(keys.isNotEmpty()) { "selected choice keys must not be empty" }
            require(keys.all { it.matches(OPTION_KEY_PATTERN) }) {
                "selected choice keys must be uppercase letters"
            }
        }

        companion object {
            fun fromRaw(rawValue: String): Choice =
                Choice(AnswerRules.normalizeChoiceKeys(rawValue))

            val OPTION_KEY_PATTERN = Regex("[A-Z]")
        }
    }

    data class TrueFalse(val value: Boolean) : SubmittedAnswer

    data class Essay(val text: String) : SubmittedAnswer
}

sealed interface AnswerJudgement {
    data object Correct : AnswerJudgement
    data object Incorrect : AnswerJudgement
    data object NotAutoGraded : AnswerJudgement
    data class Unavailable(val reason: String) : AnswerJudgement
    data class InvalidSubmission(val reason: String) : AnswerJudgement
}

/** Normalization and exact-match rules shared by practice, exams, and import/export. */
object AnswerRules {
    fun normalizeChoice(rawValue: String): ExpectedAnswer.Choice =
        ExpectedAnswer.Choice(rawValue, normalizeChoiceKeys(rawValue))

    fun normalizeTrueFalse(rawValue: String): ExpectedAnswer.TrueFalse =
        when (rawValue.trim()) {
            "√" -> ExpectedAnswer.TrueFalse(rawValue, value = true)
            "×" -> ExpectedAnswer.TrueFalse(rawValue, value = false)
            else -> throw IllegalArgumentException("true/false answer must be √ or ×")
        }

    /** Essay answers are retained verbatim because they are reference text, not scored answers. */
    fun normalizeEssay(rawValue: String): ExpectedAnswer.Essay = ExpectedAnswer.Essay(rawValue)

    internal fun normalizeChoiceKeys(rawValue: String): Set<String> {
        val compact = rawValue.filterNot(Char::isWhitespace).uppercase(Locale.ROOT)
        require(compact.isNotEmpty()) { "choice answer must not be blank" }
        require(compact.all { it in 'A'..'Z' }) {
            "choice answer must contain uppercase option letters only"
        }
        return compact.map(Char::toString).toSet()
    }

    internal fun judge(question: Question, submission: SubmittedAnswer): AnswerJudgement {
        val availability = question.availability
        if (availability is QuestionAvailability.Unanswerable) {
            return AnswerJudgement.Unavailable(availability.reason)
        }

        return when (question.key.questionType) {
            QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> {
                val expected = question.answer as ExpectedAnswer.Choice
                val actual = submission as? SubmittedAnswer.Choice
                    ?: return AnswerJudgement.InvalidSubmission("a choice answer is required")
                if (actual.keys == expected.keys) {
                    AnswerJudgement.Correct
                } else {
                    AnswerJudgement.Incorrect
                }
            }

            QuestionType.TRUE_FALSE -> {
                val expected = question.answer as ExpectedAnswer.TrueFalse
                val actual = submission as? SubmittedAnswer.TrueFalse
                    ?: return AnswerJudgement.InvalidSubmission("a true/false answer is required")
                if (actual.value == expected.value) {
                    AnswerJudgement.Correct
                } else {
                    AnswerJudgement.Incorrect
                }
            }

            QuestionType.ESSAY -> AnswerJudgement.NotAutoGraded
        }
    }
}
