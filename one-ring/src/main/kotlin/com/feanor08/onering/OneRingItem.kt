package com.feanor08.onering

import androidx.compose.runtime.Immutable

/**
 * One entry in a ring: a leaf [Action], a [Menu] with nested children, or a
 * [Seek] radial slider. The library is domain-neutral -- it never interprets
 * [id] or [payload]; those are entirely the consumer's business.
 */
@Immutable
public sealed interface OneRingItem {
    public val id: String
    public val label: String
    public val icon: OneRingIcon
    public val enabled: Boolean
    public val contentDescription: String?

    /**
     * This item's angular size *relative to its siblings*, not an absolute
     * angle or a percentage. Weights are normalized against the sum of all
     * siblings in the same band, then scaled to whatever sweep is actually
     * available -- so `2, 1, 1` and `50, 25, 25` are identical, and both give
     * the first item half the ring. The default of `1f` makes every sibling
     * equal, which is exactly the behaviour of a ring that never mentions
     * weights at all.
     *
     * Nested children are weighted independently of their parent: a parent's
     * own weight decides how much of the *main* ring it occupies, and its
     * children's weights divide the submenu's available sweep (the circle
     * minus the parent's reserved gap).
     *
     * Must be finite and strictly positive; anything else throws at
     * construction time rather than producing degenerate geometry that would
     * only surface as a mis-drawn arc much later.
     */
    public val weight: Float

    /**
     * A leaf item that fires [OneRing]'s onAction callback when committed.
     *
     * [holdMs] > 0 makes it a hold-to-commit wedge: releasing on it does NOT commit. Instead, while the
     * pointer stays on the wedge, a fill grows through it from the inner edge to the outer edge over [holdMs],
     * and the action fires when the fill completes. Letting go (or sliding off) first cancels it and raises
     * [OneRingEvent.HoldCancelled]. Meant for actions too consequential for a tap. Semantics (TalkBack)
     * activation commits at once, since an accessibility service cannot hold.
     */
    @Immutable
    public data class Action(
        override val id: String,
        override val label: String,
        override val icon: OneRingIcon = OneRingIcon.None,
        override val enabled: Boolean = true,
        override val contentDescription: String? = null,
        public val payload: Any? = null,
        override val weight: Float = 1f,
        public val holdMs: Long = 0L,
    ) : OneRingItem {
        init {
            requireValidWeight(id, weight)
            require(holdMs >= 0L) { "OneRingItem '$id' has holdMs=$holdMs; it must be >= 0 (0 = release-to-commit)." }
        }
    }

    /** An item that opens a nested submenu of [children] when committed. */
    @Immutable
    public data class Menu(
        override val id: String,
        override val label: String,
        override val icon: OneRingIcon = OneRingIcon.None,
        override val enabled: Boolean = true,
        override val contentDescription: String? = null,
        public val children: List<OneRingItem>,
        public val closeIcon: OneRingIcon? = null,
        override val weight: Float = 1f,
    ) : OneRingItem {
        init {
            requireValidWeight(id, weight)
        }
    }

    /**
     * A boolean/toggle wedge. [checked] is fully controlled by the caller,
     * like [Seek.value] -- one-ring never owns this state, it only renders
     * the provided value and calls [onCheckedChange] when the user commits a
     * toggle (release while highlighted). The library never interprets what
     * the boolean *means* -- Bluetooth, night mode, whatever -- that's the
     * caller's business, same as [Action.payload].
     *
     * Committing a [Toggle] flips it and calls [onCheckedChange] with the new
     * value; unlike [Action], it does not close an enclosing submenu, since a
     * switch is usually flipped more than once per visit.
     */
    @Immutable
    public data class Toggle(
        override val id: String,
        override val label: String,
        override val icon: OneRingIcon = OneRingIcon.None,
        override val enabled: Boolean = true,
        override val contentDescription: String? = null,
        public val checked: Boolean,
        public val onCheckedChange: (Boolean) -> Unit,
        override val weight: Float = 1f,
    ) : OneRingItem {
        init {
            requireValidWeight(id, weight)
        }
    }

    /**
     * A radial slider item. [value] is fully controlled by the caller, like
     * [androidx.compose.material3.Slider] -- one-ring never owns this state.
     */
    @Immutable
    public data class Seek(
        override val id: String,
        override val label: String,
        override val icon: OneRingIcon = OneRingIcon.None,
        override val enabled: Boolean = true,
        override val contentDescription: String? = null,
        public val value: Float,
        public val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        public val steps: Int = 0,
        public val onValueChange: (Float) -> Unit,
        public val onValueChangeFinished: (() -> Unit)? = null,
        public val valueLabel: ((Float) -> String)? = null,
        public val closeIcon: OneRingIcon? = null,
        override val weight: Float = 1f,
    ) : OneRingItem {
        init {
            requireValidWeight(id, weight)
        }
    }
}

/**
 * Rejects weights that can't describe a real sector. A non-positive weight
 * would claim no angle at all (making the item invisible but still hit-
 * testable), and a NaN/Infinite one would poison the sibling total and take
 * every other sector down with it -- so both fail loudly here, at the point the
 * mistake was made.
 */
private fun requireValidWeight(id: String, weight: Float) {
    require(weight.isFinite() && weight > 0f) {
        "OneRingItem '$id' has weight=$weight; weights must be finite and > 0 " +
            "(weights are relative, so 2/1/1 and 50/25/25 are equivalent)."
    }
}
