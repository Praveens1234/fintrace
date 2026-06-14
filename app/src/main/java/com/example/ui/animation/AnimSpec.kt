package com.example.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

object AnimSpec {
    val PageSpring = spring<Int>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val TabSpring = spring<Int>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val ExpandIntSize = spring<Int>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val BounceFloat = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBounce,
        stiffness = Spring.StiffnessMedium
    )
    val FadeTween = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    val FastFade = tween<Float>(durationMillis = 140, easing = LinearOutSlowInEasing)

    // Ordered list of tab routes — used to derive slide direction
    val tabOrder = listOf("prices", "trade", "alerts", "logs", "settings")
}

fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "bounceScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
