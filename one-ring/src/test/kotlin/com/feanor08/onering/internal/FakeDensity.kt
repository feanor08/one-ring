package com.feanor08.onering.internal

import androidx.compose.ui.unit.Density

/** A fixed-scale [Density] so tests don't depend on any Android runtime. */
internal class FakeDensity(override val density: Float = 2f, override val fontScale: Float = 1f) : Density
