package com.tishan.app.domain.practice

import kotlin.random.Random

enum class PracticeOrderMode(val label: String) {
    SEQUENTIAL("顺序"),
    RANDOM("随机"),
}

object PracticeOrdering {
    fun order(questionIds: List<String>, mode: PracticeOrderMode, seed: Long): List<String> =
        when (mode) {
            PracticeOrderMode.SEQUENTIAL -> questionIds
            PracticeOrderMode.RANDOM -> questionIds.shuffled(Random(seed))
        }
}

data class PracticeStats(
    val todayQuestionCount: Int,
    val wrongQuestionCount: Int,
    val favoriteCount: Int,
)
