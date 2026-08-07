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

    /** A leaf item that fires [OneRing]'s onAction callback when committed. */
    @Immutable
    public data class Action(
        override val id: String,
        override val label: String,
        override val icon: OneRingIcon = OneRingIcon.None,
        override val enabled: Boolean = true,
        override val contentDescription: String? = null,
        public val payload: Any? = null,
    ) : OneRingItem

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
    ) : OneRingItem

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
    ) : OneRingItem
}
