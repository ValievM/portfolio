package com.example.phoneagent.ui.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Durations and curves of the brand contract. Motion only tells the phase of the work (waiting,
 * running, done); it is never the only carrier of meaning and every use follows the system
 * "remove animations" setting.
 */
object AppMotion {
    /** Status icon swap: one status replaces another without a jump. */
    const val STATUS_CROSSFADE_MILLIS = 120

    /** An error banner appears (alpha only, no shake). */
    const val BANNER_APPEAR_MILLIS = 160

    /** Expanding a card, rotating its chevron. */
    const val EXPAND_MILLIS = 250

    /** One direction of the "preparing" indicator; a full cycle is twice this. */
    const val PREPARING_HALF_CYCLE_MILLIS = 600

    /** Material's standard decelerate curve: the general curve of the app. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** The curve of the "preparing" indicator. */
    val Preparing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}
