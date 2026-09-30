package com.tishan.app.domain.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PracticeOrderingTest {
    private val ids = listOf("1", "2", "3", "4", "5")

    @Test
    fun sequentialOrderIsStable() {
        assertEquals(ids, PracticeOrdering.order(ids, PracticeOrderMode.SEQUENTIAL, 42L))
    }

    @Test
    fun randomOrderIsReproducibleBySeed() {
        val first = PracticeOrdering.order(ids, PracticeOrderMode.RANDOM, 42L)
        val second = PracticeOrdering.order(ids, PracticeOrderMode.RANDOM, 42L)
        assertEquals(first, second)
        assertNotEquals(ids, first)
    }
}
