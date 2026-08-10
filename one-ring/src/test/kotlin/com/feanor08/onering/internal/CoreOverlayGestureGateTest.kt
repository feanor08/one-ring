package com.feanor08.onering.internal

import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingHaptics
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.OneRingState
import com.feanor08.onering.oneRingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ring's own gesture loop must cede pointer-down events that land inside
 * an active `coreOverlay` (e.g. a notification feed's scroll/swipe) rather
 * than consuming them -- see [RingInteractionController.isInsideActiveCoreOverlayForTest]
 * and OneRing.kt's `coreOverlay` param doc. This is the sole gate standing
 * between two independent gesture systems sharing the same screen region, so
 * it's tested directly rather than only implied by higher-level behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoreOverlayGestureGateTest {

    private val density = FakeDensity(2f)
    private val dimensions = OneRingDefaults.dimensions()
    private val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 3)
    private val motion = OneRingDefaults.motion()

    private val items = oneRingItems {
        menu(id = "media", label = "Media") {
            action(id = "play", label = "Play")
        }
        action(id = "climate", label = "Climate")
        seek(id = "volume", label = "Volume", value = 0.5f, onValueChange = {})
    }

    private fun controllerFor(
        state: OneRingState,
        scope: CoroutineScope,
        hasCoreOverlay: Boolean,
    ): RingInteractionController {
        state.scope = scope
        return RingInteractionController(state).apply {
            update(
                items = items,
                resolved = resolved,
                dimensions = dimensions,
                motion = motion,
                behavior = OneRingDefaults.behavior(hapticsEnabled = false),
                haptics = OneRingHaptics.None,
                onAction = {},
                onEvent = null,
                hasCoreOverlay = hasCoreOverlay,
            )
        }
    }

    /** Comfortably inside coreOverlayRadiusPx (0.43 * baseRadius=300 = 129px). */
    private val insideCorePoint = resolved.centerPx

    /** Out on the main ring, well outside the core overlay. */
    private val onMainRingPoint = androidx.compose.ui.geometry.Offset(
        resolved.centerPx.x + (resolved.mainInnerPx + resolved.mainOuterPx) / 2f,
        resolved.centerPx.y,
    )

    @Test
    fun `a center-hole touch is not gated to the overlay when no coreOverlay is configured`() = runTest {
        val state = OneRingState(emptyList())
        val controller = controllerFor(state, this, hasCoreOverlay = false)
        assertFalse(controller.isInsideActiveCoreOverlayForTest(insideCorePoint))
    }

    @Test
    fun `a center-hole touch is gated to the overlay when idle and a coreOverlay is configured`() = runTest {
        val state = OneRingState(emptyList())
        val controller = controllerFor(state, this, hasCoreOverlay = true)
        assertTrue(controller.isInsideActiveCoreOverlayForTest(insideCorePoint))
    }

    @Test
    fun `a touch on the main ring itself is never gated to the overlay`() = runTest {
        val state = OneRingState(emptyList())
        val controller = controllerFor(state, this, hasCoreOverlay = true)
        assertFalse(controller.isInsideActiveCoreOverlayForTest(onMainRingPoint))
    }

    @Test
    fun `a center-hole touch is not gated while a submenu is open`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this, hasCoreOverlay = true)
            controller.commitDirectly("media")
            advanceUntilIdle()

            assertTrue(state.path.isNotEmpty())
            assertFalse(controller.isInsideActiveCoreOverlayForTest(insideCorePoint))
        }
    }

    @Test
    fun `a center-hole touch is not gated while a main seek is active`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this, hasCoreOverlay = true)
            controller.commitDirectly("volume")
            advanceUntilIdle()

            assertTrue(state.seekSession != null)
            assertFalse(controller.isInsideActiveCoreOverlayForTest(insideCorePoint))
        }
    }

    @Test
    fun `a center-hole touch is not gated mid-transition`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this, hasCoreOverlay = true)
            state.phase = OneRingState.Phase.Opening("media")

            assertFalse(controller.isInsideActiveCoreOverlayForTest(insideCorePoint))
        }
    }

    @Test
    fun `the overlay claim resumes once the ring returns to idle after closing`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this, hasCoreOverlay = true)
            controller.commitDirectly("media")
            advanceUntilIdle()
            controller.closeDirectly()
            advanceUntilIdle()

            assertTrue(state.path.isEmpty())
            assertTrue(controller.isInsideActiveCoreOverlayForTest(insideCorePoint))
        }
    }
}
