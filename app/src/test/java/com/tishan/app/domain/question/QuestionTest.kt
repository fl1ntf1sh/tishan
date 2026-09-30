package com.tishan.app.domain.question

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionTest {
    @Test
    fun questionKeyIncludesQuestionType() {
        val single = QuestionKey("library", QuestionType.SINGLE_CHOICE, 1)
        val multiple = QuestionKey("library", QuestionType.MULTIPLE_CHOICE, 1)

        assertEquals(false, single == multiple)
    }

    @Test
    fun singleChoiceUsesExactChoiceMatch() {
        val question = choiceQuestion(QuestionType.SINGLE_CHOICE, "B")

        assertEquals(
            AnswerJudgement.Correct,
            question.judge(SubmittedAnswer.Choice(setOf("B"))),
        )
        assertEquals(
            AnswerJudgement.Incorrect,
            question.judge(SubmittedAnswer.Choice(setOf("A"))),
        )
    }

    @Test
    fun multipleChoiceIsOrderIndependentButDoesNotPartiallyScore() {
        val question = choiceQuestion(QuestionType.MULTIPLE_CHOICE, "ACD")

        assertEquals(
            AnswerJudgement.Correct,
            question.judge(SubmittedAnswer.Choice.fromRaw("DCA")),
        )
        assertEquals(
            AnswerJudgement.Incorrect,
            question.judge(SubmittedAnswer.Choice.fromRaw("AC")),
        )
    }

    @Test
    fun trueFalseIsJudgedByBooleanValue() {
        val question = Question(
            key = QuestionKey("library", QuestionType.TRUE_FALSE, 2),
            prompt = "判断题",
            options = emptyList(),
            answer = AnswerRules.normalizeTrueFalse("×"),
            explanation = null,
        )

        assertEquals(
            AnswerJudgement.Correct,
            question.judge(SubmittedAnswer.TrueFalse(false)),
        )
        assertEquals(
            AnswerJudgement.Incorrect,
            question.judge(SubmittedAnswer.TrueFalse(true)),
        )
    }

    @Test
    fun essayIsVisibleButNotAutomaticallyGraded() {
        val question = Question(
            key = QuestionKey("library", QuestionType.ESSAY, 3),
            prompt = "申论题",
            options = emptyList(),
            answer = AnswerRules.normalizeEssay("参考答案"),
            explanation = "评分说明",
        )

        assertEquals(
            AnswerJudgement.NotAutoGraded,
            question.judge(SubmittedAnswer.Essay("用户作答")),
        )
    }

    @Test
    fun unanswerableObjectiveQuestionIsRetainedButCannotBeJudged() {
        val question = Question(
            key = QuestionKey("library", QuestionType.SINGLE_CHOICE, 487),
            prompt = "选项嵌在题干中的异常题",
            options = emptyList(),
            answer = AnswerRules.normalizeChoice("A"),
            explanation = null,
            availability = QuestionAvailability.Unanswerable("选项为空"),
        )

        assertEquals(
            AnswerJudgement.Unavailable("选项为空"),
            question.judge(SubmittedAnswer.Choice(setOf("A"))),
        )
    }

    private fun choiceQuestion(type: QuestionType, answer: String): Question = Question(
        key = QuestionKey("library", type, 1),
        prompt = "选择题",
        options = listOf(
            QuestionOption("A", "选项 A", 0),
            QuestionOption("B", "选项 B", 1),
            QuestionOption("C", "选项 C", 2),
            QuestionOption("D", "选项 D", 3),
        ),
        answer = AnswerRules.normalizeChoice(answer),
        explanation = null,
    )
}
