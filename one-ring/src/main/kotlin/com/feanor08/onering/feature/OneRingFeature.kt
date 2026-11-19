package com.feanor08.onering.feature

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon

/**
 * What a feature is doing right now. A feature is a small badge outside the ring that tells the rider about something the vehicle is doing (auto hold,
 * cruise control...). Where it sits and how it looks follow from this:
 *
 *  - [Hidden]: not drawn (the bike has no such feature, or it is not worth showing).
 *  - [Idle]: there, but doing nothing: greyed, resting in the row at the bottom of the ring.
 *  - [Disabled]: turned off: greyed and crossed out, resting in the bottom row.
 *  - [Active]: on: it rides the ring's edge up to the row at the top, fills with the colour of its [Active.value], and gives out pulses in that colour.
 *  - [Releasing]: on, but ending over a known time (auto hold's soft disengage): it leaves the top row and rides down the right-hand side of the ring, reaching
 *    the bottom as the time runs out, without pulses. If it becomes [Active] again on the way it rides back up the same way, counter-clockwise.
 */
public sealed interface OneRingFeatureStatus {
    public data object Hidden : OneRingFeatureStatus
    public data object Idle : OneRingFeatureStatus
    public data object Disabled : OneRingFeatureStatus

    /** On. [value] picks the colour: 1 is [OneRingFeature.activeColors]`[0]`, 2 is `[1]`, and so on (a value past the end takes the last colour). */
    public data class Active(public val value: Int = 1) : OneRingFeatureStatus

    /** Ending over [durationMs]: the position on the right-hand side of the ring is how far through it is. It takes the colour of [value]. */
    public data class Releasing(public val durationMs: Int, public val value: Int = 2) : OneRingFeatureStatus
}

/**
 * One feature badge. [id] must be unique and stay the same across recompositions (it is what remembers where the badge is). [icon] is drawn at
 * [OneRingFeatureStyle.iconSize], tinted grey when the feature is not on and white when it is. [activeColors] are the fills for [OneRingFeatureStatus.Active] values 1, 2, ...
 */
@Immutable
public class OneRingFeature(
    public val id: String,
    public val icon: OneRingIcon,
    public val contentDescription: String,
    public val activeColors: List<Color>,
    public val status: OneRingFeatureStatus,
) {
    init {
        require(activeColors.isNotEmpty()) { "a feature needs at least one active colour" }
    }

    /** The fill for [value] (1-based). */
    public fun colorFor(value: Int): Color = activeColors[(value - 1).coerceIn(0, activeColors.lastIndex)]
}
