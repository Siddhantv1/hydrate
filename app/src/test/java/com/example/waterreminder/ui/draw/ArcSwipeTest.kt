package com.example.waterreminder.ui.draw

import org.junit.Assert.assertEquals
import org.junit.Test

class ArcSwipeTest {
    private fun p(a: Float, s: ArcSide) = arcProgress(a, s)
    @Test fun left() {
        assertEquals(0f, p(225f, ArcSide.LEFT), 1e-4f); assertEquals(0.5f, p(270f, ArcSide.LEFT), 1e-4f)
        assertEquals(1f, p(315f, ArcSide.LEFT), 1e-4f); assertEquals(0f, p(200f, ArcSide.LEFT), 1e-4f)
        assertEquals(1f, p(10f, ArcSide.LEFT), 1e-4f)   // overshoot past 12 o'clock
    }
    @Test fun right() {
        assertEquals(0f, p(135f, ArcSide.RIGHT), 1e-4f); assertEquals(0.5f, p(90f, ArcSide.RIGHT), 1e-4f)
        assertEquals(1f, p(45f, ArcSide.RIGHT), 1e-4f); assertEquals(0f, p(160f, ArcSide.RIGHT), 1e-4f)
        assertEquals(1f, p(350f, ArcSide.RIGHT), 1e-4f)
    }
}
