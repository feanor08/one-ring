package com.feanor08.onering.feature

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureLayoutTest {
    private val orbit = 152f
    private val badge = 40f
    private val spacing = 8f
    private val pitch = FeatureLayout.pitchDeg(orbit, badge, spacing)

    /** Straight-line distance between two badge centres on the orbit. */
    private fun distance(aDeg: Float, bDeg: Float): Float {
        var d = kotlin.math.abs(aDeg - bDeg) % 360f
        if (d > 180f) d = 360f - d
        return (2.0 * orbit * sin(Math.toRadians(d.toDouble()) / 2.0)).toFloat()
    }

    private fun assertNoOverlap(targets: Map<String, Float>) {
        val l = targets.entries.toList()
        for (i in l.indices) for (j in i + 1 until l.size) {
            assertTrue("${l[i]} vs ${l[j]}", distance(l[i].value, l[j].value) >= badge + spacing - 0.01f)
        }
    }

    @Test fun neighboursInARowJustClearEachOtherWithTheSpacing() {
        assertEquals(badge + spacing, distance(0f, pitch), 0.01f)
    }

    @Test fun aLoneBadgeIsCentredOnItsRowAndTwoSitEitherSide() {
        assertEquals(0f, FeatureLayout.slot(0f, 0, 1, pitch), 0.001f)
        assertEquals(180f, FeatureLayout.slot(180f, 0, 1, pitch), 0.001f)
        assertEquals(FeatureLayout.normalize(-pitch / 2), FeatureLayout.slot(0f, 0, 2, pitch), 0.001f)
        assertEquals(pitch / 2, FeatureLayout.slot(0f, 1, 2, pitch), 0.001f)
    }

    @Test fun badgesInARowNeverOverlapWhateverTheCountAndRow() {
        for (n in 1..5) for (on in listOf(true, false)) {
            val plan = FeatureRowOrder().update((1..n).map { "f$it" to (if (on) RowKind.TOP else RowKind.BOTTOM) }, pitch)
            assertEquals(n, plan.targets.size)
            assertNoOverlap(plan.targets)
        }
    }

    @Test fun aBadgeWithNothingAheadOfItRidesFreely() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.BOTTOM, "b" to RowKind.BOTTOM), pitch)                 // bottom: a at 171, b at 189 (a bigger index is further clockwise)
        val plan = order.update(listOf("a" to RowKind.BOTTOM, "b" to RowKind.TOP), pitch)         // b is the one furthest ahead: nothing ahead of it
        assertEquals(0f, plan.targets.getValue("b"), 0.001f)
        assertTrue(plan.passing.isEmpty())
    }

    @Test fun aBadgeWithAnotherAheadOfItIsReportedAsPassingButStillRidesClockwise() {
        val order = FeatureRowOrder()
        val before = order.update(listOf("a" to RowKind.BOTTOM, "b" to RowKind.BOTTOM), pitch)
        assertTrue(before.targets.getValue("a") < before.targets.getValue("b"))
        val plan = order.update(listOf("a" to RowKind.TOP, "b" to RowKind.BOTTOM), pitch)         // a has to ride clockwise past b
        assertEquals(setOf("a"), plan.passing)
        val end = FeatureLayout.destination(before.targets.getValue("a"), plan.targets.getValue("a"))
        assertTrue("went up through 9 o'clock: $end", end > before.targets.getValue("a"))                       // clockwise: the angle grew
        assertNoOverlap(plan.targets)                                                                           // and it still lands clear of the others
    }

    @Test fun aBadgeLeavingTheTopRowAheadOfTheOtherIsReportedAsPassingToo() {
        val order = FeatureRowOrder()
        val top = order.update(listOf("p" to RowKind.TOP, "q" to RowKind.TOP), pitch)           // p left (351), q right (9)
        assertTrue(top.targets.getValue("p") > 180f && top.targets.getValue("q") < 180f)
        val plan = order.update(listOf("p" to RowKind.BOTTOM, "q" to RowKind.TOP), pitch)
        assertEquals(setOf("p"), plan.passing)
        assertNoOverlap(plan.targets)
    }

    @Test fun aBadgeJoiningARowComesInBehindTheOnesThere() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.BOTTOM, "b" to RowKind.BOTTOM, "c" to RowKind.TOP), pitch)
        val plan = order.update(listOf("a" to RowKind.BOTTOM, "b" to RowKind.TOP, "c" to RowKind.TOP), pitch)       // b joins c at the top
        assertTrue(plan.targets.getValue("b") > 180f)                                         // b is the left-most: it arrived from the left
        assertTrue(plan.targets.getValue("c") < 180f)                                         // c shifted clockwise to make room
        assertNoOverlap(plan.targets)
    }

    @Test fun badgesThatLeaveTogetherKeepTheirOrderAndNeverOverlap() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.BOTTOM, "b" to RowKind.BOTTOM), pitch)
        val plan = order.update(listOf("a" to RowKind.TOP, "b" to RowKind.TOP), pitch)
        assertNoOverlap(plan.targets)
    }

    @Test fun theLastBadgeBackInARowRecentres() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.TOP, "b" to RowKind.TOP), pitch)
        val plan = order.update(listOf("a" to RowKind.TOP, "b" to RowKind.BOTTOM), pitch)
        assertEquals(0f, plan.targets.getValue("a"), 0.001f)
    }

    @Test fun aVanishedBadgeLeavesTheRowAndNoTravelIsRemembered() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.TOP, "b" to RowKind.TOP), pitch)
        val plan = order.update(listOf("b" to RowKind.TOP), pitch)
        assertEquals(setOf("b"), plan.targets.keys)
    }

    @Test fun destinationAlwaysGoesClockwiseBetweenRowsAndShiftsTheShortWayWithinARow() {
        assertEquals(351f, FeatureLayout.destination(171f, 351f), 0.001f)                              // up, through 9 o'clock
        assertEquals(360f + 8.7f, FeatureLayout.destination(171f, 8.7f), 0.001f)                       // and on over the top
        assertEquals(531.3f, FeatureLayout.destination(351.3f, 171.3f), 0.001f)                        // down, through 3 o'clock
        assertEquals(-9f, FeatureLayout.destination(0f, 351f), 0.001f)                                 // a shift within the top row
        for (from in listOf(0f, 40f, 90f, 171f, 189f, 351f)) for (to in listOf(0f, 9f, 171f, 189f, 351f)) {
            val d = FeatureLayout.destination(from, to)
            assertTrue("$from to $to went $d", d >= from || d - from > -FeatureLayout.SMALL_SHIFT_DEG)
        }
    }

    @Test fun blocksIsAboutTheOpenClockwiseStretchBetweenTheEnds() {
        assertTrue(FeatureLayout.blocks(171f, 351f, 189f, 2f))
        assertFalse(FeatureLayout.blocks(171f, 351f, 160f, 2f))
        assertFalse(FeatureLayout.blocks(171f, 351f, 351f, 2f))                                        // the end itself does not block
        assertTrue(FeatureLayout.blocks(351f, 171f, 9f, 2f))                                           // down through 3 o'clock passes a badge at 9
    }

    @Test fun anglesAreNormalisedAndPitchShrinksOnABiggerOrbit() {
        assertEquals(351f, FeatureLayout.normalize(-9f), 0.001f)
        assertEquals(9f, FeatureLayout.normalize(369f), 0.001f)
        assertTrue(FeatureLayout.pitchDeg(200f, badge, spacing) < FeatureLayout.pitchDeg(120f, badge, spacing))
    }

    @Test fun aReleasingBadgeIsInNeitherRowSoBothRowsCloseUpWithoutIt() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.TOP, "b" to RowKind.TOP, "c" to RowKind.BOTTOM), pitch)
        val plan = order.update(listOf("a" to RowKind.ARC, "b" to RowKind.TOP, "c" to RowKind.BOTTOM), pitch)
        assertEquals(setOf("b", "c"), plan.targets.keys)                                         // a has no row target while it rides the arc
        assertEquals(0f, plan.targets.getValue("b"), 0.001f)                                     // b recentres on the top row
        assertEquals(180f, plan.targets.getValue("c"), 0.001f)
    }

    @Test fun aBadgeThatFinishesReleasingJoinsTheBottomRowFromTheRightAndCanGoBackUp() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.TOP, "c" to RowKind.BOTTOM), pitch)
        order.update(listOf("a" to RowKind.ARC, "c" to RowKind.BOTTOM), pitch)
        val landed = order.update(listOf("a" to RowKind.BOTTOM, "c" to RowKind.BOTTOM), pitch)
        assertTrue(landed.targets.getValue("a") < landed.targets.getValue("c"))                  // arrived from the right: the right-most slot (a smaller angle)
        assertNoOverlap(landed.targets)
        // Or, re-engaged mid-release, it goes back to the top row.
        val o2 = FeatureRowOrder()
        o2.update(listOf("a" to RowKind.TOP), pitch)
        o2.update(listOf("a" to RowKind.ARC), pitch)
        val back = o2.update(listOf("a" to RowKind.TOP), pitch)
        assertEquals(0f, back.targets.getValue("a"), 0.001f)
    }

    @Test fun releasingBadgesDoNotCountAsPassingAnyone() {
        val order = FeatureRowOrder()
        order.update(listOf("a" to RowKind.TOP, "b" to RowKind.TOP), pitch)
        val plan = order.update(listOf("a" to RowKind.ARC, "b" to RowKind.TOP), pitch)
        assertTrue(plan.passing.isEmpty())
    }

    private fun ride(current: Float, start: Float = 171f, end: Float = 351f) = FeatureLayout.Ride(current, start, end)

    @Test fun aBadgeOnTheRidersWayStepsAsideAsItApproachesAndReturnsAsItLeaves() {
        // A rider going 171 -> 351 (up through 9 o'clock) crosses a badge resting at 189.
        assertEquals(1f, FeatureLayout.dodge(189f, listOf(ride(189f)), pitch), 0.001f)                   // on top of it
        assertEquals(1f, FeatureLayout.dodge(189f, listOf(ride(189f - pitch * 0.9f)), pitch), 0.001f)    // a pitch away: fully out of the way already
        assertEquals(0f, FeatureLayout.dodge(189f, listOf(ride(189f - pitch * 2.2f)), pitch), 0.001f)    // well before it: not moved yet
        val half = FeatureLayout.dodge(189f, listOf(ride(189f + pitch * 1.55f)), pitch)                   // on the way out
        assertTrue("half way: $half", half in 0.4f..0.6f)
        var last = 1f
        for (step in 0..20) {                                                                             // the rider moves away: the dodge only falls
            val d = FeatureLayout.dodge(189f, listOf(ride(189f + step * pitch / 8f)), pitch)
            assertTrue("step $step: $d", d <= last + 1e-6f); last = d
        }
    }

    @Test fun aBadgeNotOnTheRidersWayNeverMoves() {
        // Same rider, 171 -> 351. A badge behind its start (153) and one beside where it ends (9) are not crossed, however close the rider gets.
        for (current in listOf(153f, 171f, 189f, 270f, 351f)) {
            assertEquals("behind the start, rider at $current", 0f, FeatureLayout.dodge(153f, listOf(ride(current)), pitch), 0f)
            assertEquals("beside the end, rider at $current", 0f, FeatureLayout.dodge(9f, listOf(ride(current)), pitch), 0f)
        }
    }

    @Test fun theBadgeNextToTheOneThatLeavesOnlyStepsAsideIfItIsAheadOfIt() {
        // Leaving the bottom row from 171: a neighbour ahead at 189 is crossed, one behind at 153 is not.
        assertTrue(FeatureLayout.dodge(189f, listOf(ride(175f)), pitch) > 0f)
        assertEquals(0f, FeatureLayout.dodge(153f, listOf(ride(175f)), pitch), 0f)
    }

    @Test fun goingDownCrossesTheBadgeToTheRightOfTheOneThatLeavesTheTopRow() {
        val down = FeatureLayout.Ride(currentDeg = 5f, startDeg = 351f, endDeg = 171f)                    // p leaves the top-left, down through 3 o'clock
        assertTrue(FeatureLayout.dodge(9f, listOf(down), pitch) > 0f)                                     // q at the top-right is crossed
        assertEquals(0f, FeatureLayout.dodge(333f, listOf(down.copy(currentDeg = 351f)), pitch), 0f)    // a badge behind it is not
    }

    @Test fun noRidersAndZeroPitchMeanNoDodge() {
        assertEquals(0f, FeatureLayout.dodge(189f, emptyList(), pitch), 0f)
        assertEquals(0f, FeatureLayout.dodge(189f, listOf(ride(189f)), 0f), 0f)
    }

    @Test fun theNearestCrossingRiderDecides() {
        assertEquals(1f, FeatureLayout.dodge(189f, listOf(ride(250f), ride(189f)), pitch), 0.001f)
    }
}
