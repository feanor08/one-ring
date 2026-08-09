package com.feanor08.onering.internal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The core invariant behind the "no selection stutter" requirement (see
 * docs/PLAN.md sections 10 and 18): interrupting an [ItemAnimation]'s
 * `expansion` mid-flight and re-targeting it must continue from the current
 * value, never snap to a hardcoded start. This is provable at the pure
 * animation-state level, with no Compose UI runtime involved -- the strongest,
 * cheapest form of regression coverage for this bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemAnimationContinuityTest {

    @Test fun `a fresh ItemAnimation always starts at rest`() {
        assertTrue(ItemAnimation().expansion.value == 0f)
    }

    @Test fun `interrupting an in-flight animation never produces a backward jump or a snap`() = runTest {
        withContext(VirtualFrameClock()) {
            val animation = ItemAnimation()
            val spec = tween<Float>(durationMillis = 300, easing = LinearEasing)

            val job = launch { animation.expansion.animateTo(targetValue = 1f, animationSpec = spec) }
            advanceTimeBy(120) // 40% through a 300ms linear tween
            runCurrent()
            val midFlightValue = animation.expansion.value
            assertTrue("never reached mid-flight: $midFlightValue", midFlightValue > 0.05f)
            assertTrue("already finished: $midFlightValue", midFlightValue < 0.95f)
            job.cancel()

            // Interrupt: retarget toward "selected" (2f), exactly what
            // RingInteractionController.beginOpen() does on commit (Phase 4).
            // animateTo() on an already-animating Animatable cancels the
            // previous animation and continues from here.
            launch { animation.expansion.animateTo(targetValue = 2f, animationSpec = spec) }
            advanceTimeBy(16) // one more frame
            runCurrent()
            val nextValue = animation.expansion.value

            assertTrue(
                "expansion jumped backward toward a hardcoded restart: mid=$midFlightValue next=$nextValue",
                nextValue >= midFlightValue - 0.05f,
            )
            assertTrue(
                "expansion snapped away from its current value in one frame: mid=$midFlightValue next=$nextValue",
                abs(nextValue - midFlightValue) < 0.2f,
            )
        }
    }
}
