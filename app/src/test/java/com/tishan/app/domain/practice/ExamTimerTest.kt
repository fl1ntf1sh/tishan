package com.tishan.app.domain.practice

import com.tishan.app.domain.question.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamTimerTest {
    @Test
    fun elapsedAndRemainingUseInjectedTime() {
        val settings = ExamDurationSettings(singleChoiceSeconds = 10, multipleChoiceSeconds = 20, trueFalseSeconds = 5)
        val started = ExamTimer.start(ExamTimerState(), QuestionType.SINGLE_CHOICE, 1_000L)

        val snapshot = ExamTimer.snapshot(started, settings, QuestionType.SINGLE_CHOICE, 4_000L)

        assertEquals(3_000L, snapshot.elapsedMillis)
        assertEquals(7_000L, snapshot.remainingMillis)
        assertFalse(snapshot.timedOut)
    }

    @Test
    fun pauseAndResumeDoNotCountPausedTime() {
        val settings = ExamDurationSettings(singleChoiceSeconds = 10)
        val started = ExamTimer.start(ExamTimerState(), QuestionType.SINGLE_CHOICE, 1_000L)
        val paused = ExamTimer.pause(started, 6_000L)
        val resumed = ExamTimer.start(paused, QuestionType.SINGLE_CHOICE, 10_000L)

        val snapshot = ExamTimer.snapshot(resumed, settings, QuestionType.SINGLE_CHOICE, 11_000L)

        assertEquals(6_000L, snapshot.elapsedMillis)
        assertEquals(4_000L, snapshot.remainingMillis)
    }

    @Test
    fun timeoutIsAWarningAndDoesNotStopTheClockTransition() {
        val settings = ExamDurationSettings(singleChoiceSeconds = 2)
        val started = ExamTimer.start(ExamTimerState(), QuestionType.SINGLE_CHOICE, 1_000L)

        val snapshot = ExamTimer.snapshot(started, settings, QuestionType.SINGLE_CHOICE, 5_000L)

        assertTrue(snapshot.timedOut)
        assertEquals(0L, snapshot.remainingMillis)
        assertEquals(4_000L, snapshot.elapsedMillis)
    }

    @Test
    fun essayHasNoAutomaticTimer() {
        val settings = ExamDurationSettings(singleChoiceSeconds = 1)
        val started = ExamTimer.start(ExamTimerState(), QuestionType.ESSAY, 1_000L)

        val snapshot = ExamTimer.snapshot(started, settings, QuestionType.ESSAY, 99_000L)

        assertEquals(0L, snapshot.elapsedMillis)
        assertEquals(null, snapshot.remainingMillis)
        assertFalse(snapshot.timedOut)
    }
}
