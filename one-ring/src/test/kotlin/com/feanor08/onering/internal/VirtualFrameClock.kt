package com.feanor08.onering.internal

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * A [MonotonicFrameClock] whose frame ticks are ordinary [delay] calls, so
 * `kotlinx-coroutines-test`'s virtual time scheduler (`advanceTimeBy`/
 * `runCurrent`) drives it precisely -- no wall-clock waiting, no dependency on
 * compose-ui-test (which is androidTest-only). This is the standard technique
 * for exercising [androidx.compose.animation.core.Animatable] on a plain JVM.
 *
 * Frame time comes from [scheduler]'s virtual clock rather than a counter
 * incremented inside [withFrameNanos]. That distinction matters as soon as more
 * than one animation is in flight: every running animation calls
 * `withFrameNanos` once per frame, so a self-incrementing counter would advance
 * the clock N times per frame and make concurrent animations finish N times too
 * fast. Pass the scheduler whenever a test drives several animations at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VirtualFrameClock(
    private val frameMillis: Long = 16,
    private val scheduler: TestCoroutineScheduler? = null,
) : MonotonicFrameClock {
    private var fallbackNanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        delay(frameMillis)
        val nanos = scheduler?.let { it.currentTime * 1_000_000 }
            ?: (fallbackNanos + frameMillis * 1_000_000).also { fallbackNanos = it }
        return onFrame(nanos)
    }
}
