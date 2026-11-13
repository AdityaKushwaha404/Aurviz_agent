package com.aurviz.agent.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.aurviz.agent.R

/** The Aurviz mark: gradient squircle with the "A + spark" glyph. Matches the launcher icon. */
@Composable
fun AurvizLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF4338CA), Color(0xFF7C3AED), Color(0xFF0EA5E9))
                )
            )
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Aurviz logo",
            modifier = Modifier.size(size)
        )
    }
}
