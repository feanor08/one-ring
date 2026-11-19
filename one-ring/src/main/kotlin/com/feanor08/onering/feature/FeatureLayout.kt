package com.feanor08.onering.feature

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin

/**
 * Where each badge goes (degrees clockwise from 12 o'clock, 0 until 360), and which of the badges changing rows must ride over a badge resting on the way
 * ([passing]). Badges only ever ride clockwise.
 */
internal class FeaturePlan(val targets: Map<String, Float>, val passing: Set<String> = emptySet())

/**
 * Where each feature badge goes: two rows outside the ring, one at the bottom (6 o'clock) for features that are idle or turned off, one at the top (12
 * o'clock) for features that are on. Badges in a row sit side by side, centred on the row, never touching.
 *
 * A badge changes row by riding the ring's edge, always clockwise: up through 9 o'clock, down through 3 o'clock, whatever else is on the ring (with a growing
 * list of features, a badge that sometimes went the other way would make the motion unpredictable). It arrives behind the badges already in the new row, at
 * the counter-clockwise end, so it never has to pass one of them to join. It can still have to pass a badge resting in the row it leaves, when that one is
 * ahead of it; [FeaturePlan.passing] says which, so the drawing can treat them. Pure, so it is unit-tested.
 */
internal object FeatureLayout {
    const val TOP_DEG = 0f
    const val BOTTOM_DEG = 180f

    /** The angle between neighbouring badges' centres in a row, so that badges [badgePx] across with [spacingPx] between them just clear each other on a circle of [orbitRadiusPx]. */
    fun pitchDeg(orbitRadiusPx: Float, badgePx: Float, spacingPx: Float): Float {
        val chord = (badgePx + spacingPx).coerceAtLeast(0f)
        val half = (chord / (2f * orbitRadiusPx)).coerceIn(0f, 1f)
        return (2f * asin(half) * 180f / PI.toFloat())
    }

    fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** The angle of the [index]th of [count] badges in a row centred on [centreDeg]; a bigger index is further clockwise. */
    fun slot(centreDeg: Float, index: Int, count: Int, pitchDeg: Float): Float = normalize(centreDeg + (index - (count - 1) / 2f) * pitchDeg)

    /**
     * How to get from [currentDeg] (any turn) to [targetDeg] (0 until 360): a small shift within a row goes the short way; a trip between rows always goes on
     * clockwise. Returns the angle to animate to (not normalised).
     */
    fun destination(currentDeg: Float, targetDeg: Float, smallShiftDeg: Float = SMALL_SHIFT_DEG): Float {
        val cur = normalize(currentDeg)
        var shortest = targetDeg - cur
        while (shortest > 180f) shortest -= 360f
        while (shortest <= -180f) shortest += 360f
        return if (abs(shortest) <= smallShiftDeg) currentDeg + shortest else currentDeg + normalize(targetDeg - cur)
    }

    /** A badge riding from [startDeg] to [endDeg] (clockwise), now at [currentDeg]. */
    data class Ride(val currentDeg: Float, val startDeg: Float, val endDeg: Float)

    /**
     * How far a badge resting at [angleDeg] should step aside (0 none, 1 fully) for the badges riding in [rides]. Only a rider that actually crosses it steps
     * it aside: the resting badge must lie on the rider's way from its start to its end. A badge next to the one that leaves, or beside the place the rider
     * arrives, is not on the way and does not move. For a rider that crosses it, the move is complete by the time the rider is a pitch away (so that they
     * cannot touch) and eases to nothing at about two pitches and a quarter, so it moves out as the rider approaches and comes back as it goes on.
     */
    fun dodge(angleDeg: Float, rides: List<Ride>, pitchDeg: Float): Float {
        if (pitchDeg <= 0f) return 0f
        var best = 0f
        for (r in rides) {
            if (!blocks(r.startDeg, r.endDeg, angleDeg, pitchDeg * 0.1f)) continue
            val d = abs(((angleDeg - r.currentDeg) % 360f + 540f) % 360f - 180f)
            val f = (1f - (d - 0.9f * pitchDeg) / (1.3f * pitchDeg)).coerceIn(0f, 1f)
            if (f > best) best = f
        }
        return best
    }

    /** A shift within a row is at most a couple of pitches (a row holds a handful of badges); anything beyond this is a trip between rows. */
    const val SMALL_SHIFT_DEG = 45f

    /** Whether a badge at [blockerDeg] lies on the clockwise way from [startDeg] to [endDeg] (not counting [marginDeg] at either end). */
    fun blocks(startDeg: Float, endDeg: Float, blockerDeg: Float, marginDeg: Float): Boolean {
        val length = normalize(endDeg - startDeg)
        val along = normalize(blockerDeg - startDeg)
        return along > marginDeg && along < length - marginDeg
    }
}

/** Where a badge belongs: the row at the top, the row at the bottom, or the right-hand arc between them (it is releasing: neither row has it). */
internal enum class RowKind { TOP, BOTTOM, ARC }

/** Which badges are in which row and in what order, and the plan for the next layout. */
internal class FeatureRowOrder {
    private val top = mutableListOf<String>()        // each list runs counter-clockwise end to clockwise end
    private val bottom = mutableListOf<String>()
    private var previous: Map<String, Float> = emptyMap()

    /** [entries] in list order with where each belongs. A badge on the [RowKind.ARC] is in neither row, so the rows close up without it, and it has no target here. */
    fun update(entries: List<Pair<String, RowKind>>, pitchDeg: Float): FeaturePlan {
        val wanted = entries.filter { it.second != RowKind.ARC }.associate { it.first to (if (it.second == RowKind.TOP) TOP else BOTTOM) }
        val onArc = entries.filter { it.second == RowKind.ARC }.map { it.first }
        top.retainAll { wanted[it] == TOP }
        bottom.retainAll { wanted[it] == BOTTOM }

        // Badges not in the row they now want: new ones, which just appear, and movers, which ride there.
        val placed = (top + bottom).toSet()
        val arriving = entries.map { it.first }.filter { it in wanted && it !in placed }
        val (movers, fresh) = arriving.partition { it in previous }
        for (id in fresh) rowFor(wanted.getValue(id)).add(id)

        // Always clockwise, joining the new row behind the badges in it. The one furthest ahead goes first, so badges that ride together keep their order.
        val passing = HashSet<String>()
        for (id in movers.sortedByDescending { previous.getValue(it) }) {
            val row = rowFor(wanted.getValue(id))
            val start = previous.getValue(id)
            val survivors = (top + bottom).filter { it !in movers }
            row.add(0, id)
            val end = angles(wanted.getValue(id), row, pitchDeg).getValue(id)
            val others = survivors.mapNotNull { previous[it] }
            if (others.any { FeatureLayout.blocks(start, end, it, pitchDeg * 0.1f) }) passing += id
        }

        val targets = LinkedHashMap<String, Float>()
        targets += angles(TOP, top, pitchDeg)
        targets += angles(BOTTOM, bottom, pitchDeg)
        previous = targets + onArc.associateWith { ARC_ANCHOR_DEG }       // on the arc a badge counts as halfway down the right side
        return FeaturePlan(targets, passing)
    }

    private fun rowFor(row: Int) = if (row == TOP) top else bottom

    private fun angles(row: Int, ids: List<String>, pitchDeg: Float): Map<String, Float> {
        val centre = if (row == TOP) FeatureLayout.TOP_DEG else FeatureLayout.BOTTOM_DEG
        return ids.mapIndexed { k, id -> id to FeatureLayout.slot(centre, k, ids.size, pitchDeg) }.toMap(LinkedHashMap())
    }

    private companion object {
        const val TOP = 0
        const val BOTTOM = 1
        const val ARC_ANCHOR_DEG = 90f
    }
}
