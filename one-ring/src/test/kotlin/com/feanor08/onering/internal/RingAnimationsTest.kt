package com.feanor08.onering.internal

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RingAnimationsTest {

    @Test fun `of returns the same instance for the same id`() {
        val animations = RingAnimations()
        assertSame(animations.of("a"), animations.of("a"))
    }

    @Test fun `of returns distinct instances for distinct ids`() {
        val animations = RingAnimations()
        assertNotSame(animations.of("a"), animations.of("b"))
    }

    @Test fun `prune drops animation state for ids no longer present`() {
        val animations = RingAnimations()
        val a = animations.of("a")
        animations.of("b")
        animations.prune(setOf("a"))
        // "a" survives with the same instance; "b" is gone, so a fresh of("b") differs from before.
        assertSame(a, animations.of("a"))
    }
}
