package com.asterplay.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.asterplay.tv.ui.theme.NeonCyan
import com.asterplay.tv.ui.theme.NeonPurple

/** Loader neon padrão do app (anel duplo girando em degradê ciano/roxo). */
@Composable
fun NeonLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "neon-loader")
    val angleOuter by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "outer",
    )
    val angleInner by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "inner",
    )

    Canvas(modifier = modifier) {
        val stroke = 6.dp.toPx()
        val innerStroke = 4.dp.toPx()
        val pad = stroke
        val outerSize = Size(size.width - pad * 2, size.height - pad * 2)
        val outerOffset = Offset(pad, pad)

        drawArc(
            color = Color.White.copy(alpha = 0.06f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = outerOffset,
            size = outerSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        drawArc(
            brush = Brush.sweepGradient(
                listOf(NeonCyan, NeonPurple, NeonCyan),
                center = Offset(size.width / 2f, size.height / 2f),
            ),
            startAngle = angleOuter,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = outerOffset,
            size = outerSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        val innerPad = stroke * 3.5f
        val innerSize = Size(size.width - innerPad * 2, size.height - innerPad * 2)
        val innerOffset = Offset(innerPad, innerPad)
        drawArc(
            brush = Brush.sweepGradient(
                listOf(NeonPurple, NeonCyan, NeonPurple),
                center = Offset(size.width / 2f, size.height / 2f),
            ),
            startAngle = angleInner,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = innerOffset,
            size = innerSize,
            style = Stroke(width = innerStroke, cap = StrokeCap.Round),
        )
    }
}
