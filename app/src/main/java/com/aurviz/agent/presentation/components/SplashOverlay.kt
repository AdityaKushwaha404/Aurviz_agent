package com.aurviz.agent.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Opening animation: the logo springs in with a pulsing halo, the name and tagline fade up,
 * then [onFinished] is called so the caller can fade the overlay out.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val halo = remember { Animatable(0.6f) }
    val haloAlpha = remember { Animatable(0.5f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleShift = remember { Animatable(14f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(350)) }
            launch { logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
            launch {
                halo.animateTo(1.9f, tween(1100, easing = FastOutSlowInEasing))
            }
            launch { haloAlpha.animateTo(0f, tween(1100)) }
            launch {
                kotlinx.coroutines.delay(350)
                launch { titleAlpha.animateTo(1f, tween(450)) }
                launch { titleShift.animateTo(0f, tween(450, easing = FastOutSlowInEasing)) }
            }
            launch {
                kotlinx.coroutines.delay(650)
                taglineAlpha.animateTo(0.85f, tween(450))
            }
        }
        kotlinx.coroutines.delay(350)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF5B21B6), Color(0xFF0369A1)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(halo.value)
                        .alpha(haloAlpha.value)
                        .background(Color.White.copy(alpha = 0.35f), CircleShape)
                )
                AurvizLogo(
                    size = 112.dp,
                    modifier = Modifier
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                "Aurviz",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .scale(1f)
                    .then(Modifier.padding(top = titleShift.value.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Your personal AI agent",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}
