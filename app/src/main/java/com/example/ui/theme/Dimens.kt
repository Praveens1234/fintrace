package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Design tokens: an 8-point spacing grid and a single "Modern" corner-radius personality,
 * applied consistently so layout rhythm and shape language stay coherent across every screen.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp   // default screen / component padding
    val lg = 24.dp   // card padding, section breaks
    val xl = 32.dp   // generous section spacing
    val xxl = 48.dp  // major separators
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 999.dp
}

/** Minimum accessible touch target (Material/WCAG). */
val MinTouchTarget = 48.dp

val FinTraceShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.sm),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl)
)
