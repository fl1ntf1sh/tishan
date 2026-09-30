package com.tishan.app.domain.question

/** The question types present in the source JSON files. */
enum class QuestionType(val externalValue: String) {
    SINGLE_CHOICE("单选题"),
    MULTIPLE_CHOICE("多选题"),
    TRUE_FALSE("判断题"),
    ESSAY("申论题"),
    ;

    companion object {
        fun fromExternalValue(value: String): QuestionType? =
            entries.firstOrNull { it.externalValue == value }
    }
}

/** The durable business key; the question type is part of the key by design. */
data class QuestionKey(
    val libraryId: String,
    val questionType: QuestionType,
    val questionNumber: Int,
) {
    init {
        require(libraryId.isNotBlank()) { "libraryId must not be blank" }
        require(questionNumber > 0) { "questionNumber must be positive" }
    }
}

data class QuestionOption(
    val key: String,
    val text: String,
    val position: Int,
) {
    init {
        require(key.matches(OPTION_KEY_PATTERN)) { "option key must be an uppercase letter" }
        require(position >= 0) { "option position must not be negative" }
    }

    private companion object {
        val OPTION_KEY_PATTERN = Regex("[A-Z]")
    }
}

sealed interface QuestionAvailability {
    /** The question may be included in normal practice or an objective exam. */
    data object Answerable : QuestionAvailability

    /** The source item is retained for review, but cannot be answered safely. */
    data class Unanswerable(val reason: String) : QuestionAvailability {
        init {
            require(reason.isNotBlank()) { "unanswerable reason must not be blank" }
        }
    }
}

data class Question(
    val key: QuestionKey,
    val prompt: String,
    val options: List<QuestionOption>,
    val answer: ExpectedAnswer,
    val explanation: String?,
    val availability: QuestionAvailability = QuestionAvailability.Answerable,
    val internalId: String? = null,
) {
    init {
        require(prompt.isNotBlank()) { "prompt must not be blank" }
        require(options.map(QuestionOption::key).distinct().size == options.size) {
            "option keys must be unique"
        }
        require(options.map(QuestionOption::position).distinct().size == options.size) {
            "option positions must be unique"
        }

        when (key.questionType) {
            QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE -> {
                require(answer is ExpectedAnswer.Choice) {
                    "choice questions require a choice answer"
                }
                if (availability is QuestionAvailability.Answerable) {
                    require(options.isNotEmpty()) { "answerable choice questions need options" }
                    require(options.map(QuestionOption::key).containsAll(answer.keys)) {
                        "answer keys must exist in the options"
                    }
                }
            }

            QuestionType.TRUE_FALSE -> require(answer is ExpectedAnswer.TrueFalse) {
                "true/false questions require a true/false answer"
            }

            QuestionType.ESSAY -> require(answer is ExpectedAnswer.Essay) {
                "essay questions require an essay answer"
            }
        }
    }

    fun judge(submission: SubmittedAnswer): AnswerJudgement =
        AnswerRules.judge(this, submission)
}
