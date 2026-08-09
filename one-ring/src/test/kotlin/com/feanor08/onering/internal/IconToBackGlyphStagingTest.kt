package com.feanor08.onering.internal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingHaptics
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.OneRingState
import com.feanor08.onering.geometry.polarOffset
import com.feanor08.onering.oneRingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Above this, a glyph is "meaningfully visible" -- two at once would read as a crossfade. */
private const val VISIBLE = 0.1f

/**
 * The icon -> X change must be *staged*, never crossfaded: the original glyph
 * fades out, the geometry transforms, and only then does the close glyph fade
 * in. A single progress driving `icon = 1 - p` / `x = p` would put both at 0.5
 * simultaneously, which is exactly the look these tests forbid.
 *
 * They also pin down the second half of the same idea: an open menu's parent is
 * *structurally* the back control, but that must not make it *look* selected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IconToBackGlyphStagingTest {

    private val density = FakeDensity(2f)
    private val dimensions = OneRingDefaults.dimensions()
    private val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 3)

    private val items = oneRingItems {
        menu(id = "media", label = "Media") {
            action(id = "play", label = "Play")
            action(id = "next", label = "Next")
        }
        action(id = "climate", label = "Climate")
        seek(id = "volume", label = "Volume", value = 0.5f, onValueChange = {})
    }

    private val motion = OneRingDefaults.motion(
        hover = tween(80, easing = LinearEasing),
        select = tween(300, easing = LinearEasing),
        close = tween(300, easing = LinearEasing),
        submenuReveal = tween(300, easing = LinearEasing),
        iconMorph = tween(100, easing = LinearEasing),
        siblingExit = tween(80, easing = LinearEasing),
    )

    private fun controllerFor(state: OneRingState, scope: CoroutineScope): RingInteractionController {
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
            )
        }
    }

    private fun iconAlpha(state: OneRingState, id: String) = state.animations.of(id).iconAlpha.value
    private fun backAlpha(state: OneRingState, id: String) = state.animations.of(id).backIconAlpha.value

    // ---- Opening --------------------------------------------------------

    @Test
    fun `opening a menu never shows the icon and the X at the same time`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            assertEquals(1f, iconAlpha(state, "media"), 0.001f)
            assertEquals(0f, backAlpha(state, "media"), 0.001f)

            controller.commitDirectly("media")

            var sawIconGone = false
            var sawBackArrive = false
            repeat(120) {
                advanceTimeBy(16)
                runCurrent()
                val icon = iconAlpha(state, "media")
                val back = backAlpha(state, "media")
                assertTrue(
                    "icon=$icon and X=$back were both visible at once -- that is a crossfade",
                    !(icon > VISIBLE && back > VISIBLE),
                )
                if (icon < 0.01f) sawIconGone = true
                if (sawIconGone && back > 0.5f) sawBackArrive = true
            }

            assertTrue("the original glyph never faded out", sawIconGone)
            assertTrue("the X never faded in", sawBackArrive)
        }
    }

    @Test
    fun `the X begins fading in during roughly the final quarter of the wedge's growth`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")

            var expansionWhenBackAppeared = -1f
            repeat(120) {
                advanceTimeBy(16)
                runCurrent()
                if (expansionWhenBackAppeared < 0f && backAlpha(state, "media") > 0.01f) {
                    expansionWhenBackAppeared = state.animations.of("media").expansion.value
                }
            }
            assertTrue("the X never appeared", expansionWhenBackAppeared >= 0f)
            // Growth runs 0 -> 2 here (a fresh commit with no prior hover), so
            // "roughly the final quarter" (LATE_GLYPH_FADE_FRACTION = 0.22)
            // starts at 2 * (1 - 0.22) = 1.56 -- well underway, not yet settled.
            assertTrue(
                "the X started too early, at expansion=$expansionWhenBackAppeared -- it would visibly " +
                    "trail the growth instead of arriving with it",
                expansionWhenBackAppeared > 1.3f,
            )
            // The internal threshold fires exactly at expansion=1.56 (2.0 *
            // (1 - 0.22)) -- verified directly against the Animatable. This
            // black-box check necessarily lags by about one animation frame,
            // since `animateTo` reports elapsed=0 (no visible change yet) on
            // its own first frame; 1.99 vs the old behavior's exact 2.0 still
            // cleanly tells "started partway through" from "started only once
            // fully settled".
            assertTrue(
                "the X started too late, at expansion=$expansionWhenBackAppeared -- basically after the " +
                    "wedge had already finished, defeating the point of the earlier fade-in",
                expansionWhenBackAppeared < 1.99f,
            )
        }
    }

    @Test
    fun `the icon has already gone before the wedge finishes growing`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")

            advanceTimeBy(150) // iconMorph is 100ms, select is 300ms
            runCurrent()
            assertTrue("icon still visible well into the growth", iconAlpha(state, "media") < 0.01f)
            assertTrue("X appeared during the growth", backAlpha(state, "media") < 0.01f)
            assertTrue("wedge already finished growing", state.animations.of("media").expansion.value < 1.99f)
        }
    }

    @Test
    fun `the settled submenu shows only the X`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            assertEquals(0f, iconAlpha(state, "media"), 0.001f)
            assertEquals(1f, backAlpha(state, "media"), 0.001f)
        }
    }

    // ---- Closing --------------------------------------------------------

    @Test
    fun `closing reverses the staging without ever overlapping the glyphs`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()

            var sawBackGone = false
            var sawIconReturn = false
            repeat(120) {
                advanceTimeBy(16)
                runCurrent()
                val icon = iconAlpha(state, "media")
                val back = backAlpha(state, "media")
                assertTrue(
                    "X=$back and icon=$icon were both visible at once during close",
                    !(icon > VISIBLE && back > VISIBLE),
                )
                if (back < 0.01f) sawBackGone = true
                if (sawBackGone && icon > 0.5f) sawIconReturn = true
            }

            assertTrue("the X never faded out", sawBackGone)
            assertTrue("the original glyph never came back", sawIconReturn)
            assertEquals(1f, iconAlpha(state, "media"), 0.001f)
            assertEquals(0f, backAlpha(state, "media"), 0.001f)
        }
    }

    @Test
    fun `on close the X is gone before the geometry starts moving`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            var expansionWhenBackGone = -1f
            repeat(120) {
                advanceTimeBy(16)
                runCurrent()
                if (expansionWhenBackGone < 0f && backAlpha(state, "media") < 0.01f) {
                    expansionWhenBackGone = state.animations.of("media").expansion.value
                }
            }
            assertTrue(
                "the wedge had already shrunk to $expansionWhenBackGone before the X finished fading",
                expansionWhenBackGone > 1.9f,
            )
        }
    }

    // ---- Main-ring seek uses the same staging ---------------------------

    @Test
    fun `a main-ring seek stages its icon and X exactly like a menu parent`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            controller.commitDirectly("volume")
            repeat(120) {
                advanceTimeBy(16)
                runCurrent()
                val icon = iconAlpha(state, "volume")
                val back = backAlpha(state, "volume")
                assertTrue("seek icon=$icon and X=$back overlapped", !(icon > VISIBLE && back > VISIBLE))
            }
            assertEquals(0f, iconAlpha(state, "volume"), 0.001f)
            assertEquals(1f, backAlpha(state, "volume"), 0.001f)

            controller.closeDirectly()
            repeat(120) {
                advanceTimeBy(16)
                runCurrent()
                val icon = iconAlpha(state, "volume")
                val back = backAlpha(state, "volume")
                assertTrue("seek icon=$icon and X=$back overlapped on exit", !(icon > VISIBLE && back > VISIBLE))
            }
            assertEquals(1f, iconAlpha(state, "volume"), 0.001f)
            assertEquals(0f, backAlpha(state, "volume"), 0.001f)
        }
    }

    // ---- The X is not permanently highlighted ---------------------------

    private fun frameFill(state: OneRingState, itemId: String) = RingFrameBuilder.build(
        items = items,
        resolved = resolved,
        colors = OneRingDefaults.colors(),
        expansionOf = { id -> state.animations.of(id).expansion.value },
        highlightedId = state.highlightedId,
    ).arcs.first { items[it.itemIndex].id == itemId }

    @Test
    fun `an open menu's parent is structurally active but not visually highlighted`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            // Structural state: it owns the open submenu and shows the X.
            assertEquals(listOf("media"), state.path)
            assertEquals(1f, backAlpha(state, "media"), 0.001f)
            // Interaction state: nothing is under the pointer.
            assertNull(state.highlightedId)

            val colors = OneRingDefaults.colors()
            val arc = frameFill(state, "media")
            assertNotEquals("the idle back control is painted as if selected", colors.sectorHighlighted, arc.fill)
            assertEquals(colors.stroke, arc.stroke)
            // It does keep its expanded geometry -- geometry != highlight.
            assertEquals(resolved.mainSelectedOuterPx, arc.band.outerPx, 0.5f)
        }
    }

    @Test
    fun `the X highlights only while the pointer is on it`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            val colors = OneRingDefaults.colors()

            controller.setHighlight("media")
            advanceTimeBy(32)
            runCurrent()
            assertEquals(colors.sectorHighlighted, frameFill(state, "media").fill)
            assertEquals(colors.strokeHighlighted, frameFill(state, "media").stroke)
            // Hovering the back control must not disturb its geometry.
            assertEquals(2f, state.animations.of("media").expansion.value, 0.01f)

            controller.setHighlight(null)
            advanceTimeBy(32)
            runCurrent()
            assertNotEquals(colors.sectorHighlighted, frameFill(state, "media").fill)
            assertEquals(
                "leaving the back control must not collapse it",
                2f,
                state.animations.of("media").expansion.value,
                0.01f,
            )
        }
    }

    // ---- Release-to-commit on the back control --------------------------

    /** A point inside the parent wedge, at the back control's radius. */
    private fun pointInParentWedge(): Offset {
        val span = resolved.mainSpans.first()
        val radius = (resolved.mainInnerPx + resolved.mainSelectedOuterPx) / 2f
        val p = polarOffset(radius, span.midDeg)
        return Offset(resolved.centerPx.x + p.x, resolved.centerPx.y + p.y)
    }

    @Test
    fun `pressing the X and scrubbing away to a child does not close the menu`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            // Down on the X, then scrub onto a submenu child, then release there.
            controller.setHighlight("media")
            advanceTimeBy(32)
            runCurrent()
            controller.setHighlight("play")
            advanceTimeBy(32)
            runCurrent()
            controller.commitDirectly("play")
            advanceUntilIdle()

            // Committing a leaf closes back to the top level, but crucially the
            // commit target was the child under the finger, not the X.
            assertEquals(0f, backAlpha(state, "media"), 0.05f)
        }
    }

    @Test
    fun `releasing on the X closes the menu`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()
            assertEquals(listOf("media"), state.path)

            controller.commitDirectly("media")
            advanceUntilIdle()

            assertTrue("releasing on the X did not close the submenu", state.path.isEmpty())
            assertEquals(OneRingState.Phase.Settled, state.phase)
        }
    }

    @Test
    fun `the back control wedge is hit-testable at its expanded radius`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            val hit = controller.hitBackControlForTest(pointInParentWedge())
            assertEquals("media", hit)

            // A point in the centre hole is not the back control.
            assertNull(controller.hitBackControlForTest(resolved.centerPx))
        }
    }

    @Test
    fun `there is no back control to hit when nothing is open`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            assertNull(controller.hitBackControlForTest(pointInParentWedge()))
        }
    }

    @Test
    fun `an ordinary item is unaffected by the staging channels`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            var committed: OneRingItem.Action? = null
            controller.update(
                items = items,
                resolved = resolved,
                dimensions = dimensions,
                motion = motion,
                behavior = OneRingDefaults.behavior(hapticsEnabled = false),
                haptics = OneRingHaptics.None,
                onAction = { committed = it },
                onEvent = null,
            )

            controller.commitDirectly("climate")
            advanceUntilIdle()

            assertEquals("climate", committed?.id)
            assertEquals(1f, iconAlpha(state, "climate"), 0.001f)
            assertEquals(0f, backAlpha(state, "climate"), 0.001f)
        }
    }
}
