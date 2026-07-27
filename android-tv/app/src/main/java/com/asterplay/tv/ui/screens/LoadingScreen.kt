package com.asterplay.tv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.asterplay.tv.net.TopHomePreload
import com.asterplay.tv.net.XtreamApi
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.NeonCyan
import com.asterplay.tv.ui.theme.NeonPurple
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(onReady: () -> Unit, onFail: () -> Unit) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("carregando informações da lista...") }
    var sub by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val c = XtreamStore.get(ctx)
        if (c == null) { onFail(); return@LaunchedEffect }
        val ok = XtreamApi.authenticate(c)
        if (ok) {
            TopHomePreload.run(ctx)
            delay(200); onReady()
        } else {
            status = "Não foi possível conectar"
            sub = "Verifique seus dados e tente novamente."
            delay(2000)
            XtreamStore.clear(ctx); onFail()
        }
    }

    Box(Modifier.fillMaxSize().background(BgBase), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            NeonLoader(modifier = Modifier.size(96.dp))
            Spacer(Modifier.height(16.dp))
            Text(status, color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(sub, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NeonLoader(modifier: Modifier = Modifier) {
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

        // Anel de fundo sutil
        drawArc(
            color = Color.White.copy(alpha = 0.06f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = outerOffset,
            size = outerSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        // Arco externo neon (ciano→roxo)
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

        // Arco interno menor girando ao contrário
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
