package com.tishan.app.domain.question

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerRulesTest {
    @Test
    fun choiceAnswerNormalizesCaseWhitespaceAndOrder() {
        val normalized = AnswerRules.normalizeChoice(" a C ")

        assertEquals(setOf("A", "C"), normalized.keys)
        assertEquals(" a C ", normalized.rawValue)
    }

    @Test
    fun multipleChoiceAcceptsARealDatasetSingletonWithoutChangingRawAnswer() {
        val normalized = AnswerRules.normalizeChoice("A")

        assertEquals(setOf("A"), normalized.keys)
        assertEquals("A", normalized.rawValue)
    }

    @Test
    fun displayValueUsesStableChoiceOrderAndPreservesOtherAnswers() {
        assertEquals("AC", AnswerRules.normalizeChoice("CA").displayValue())
        assertEquals("√", AnswerRules.normalizeTrueFalse("√").displayValue())
        assertEquals("参考答案", AnswerRules.normalizeEssay("参考答案").displayValue())
    }

    @Test
    fun trueFalseUsesOnlyTheSourceSymbols() {
        assertEquals(true, AnswerRules.normalizeTrueFalse("√").value)
        assertEquals(false, AnswerRules.normalizeTrueFalse(" × ").value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun trueFalseRejectsNaturalLanguageAliases() {
        AnswerRules.normalizeTrueFalse("正确")
    }

    @Test
    fun essayAnswerIsRetainedForReviewAndNotScored() {
        val answer = AnswerRules.normalizeEssay("参考答案\n第二段")

        assertEquals("参考答案\n第二段", answer.rawValue)
    }
}
