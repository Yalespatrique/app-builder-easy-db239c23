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
import com.asterplay.tv.ui.components.NeonLoader
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
        if (!ok) {
            status = "Não foi possível conectar"
            sub = "Verifique seus dados e tente novamente."
            delay(2000)
            XtreamStore.clear(ctx); onFail()
            return@LaunchedEffect
        }
        // Pré-carrega o Top 10 e recentes antes de entrar no menu.
        // As categorias e o conteúdo de cada uma só são baixados quando
        // o usuário abrir a tela correspondente (lazy).
        sub = "montando destaques..."
        // Espera todo o preload terminar antes de abrir o menu — sem timeout,
        // para o Home já entrar com Top 10 e recentes populados.
        TopHomePreload.run(ctx)
        delay(120); onReady()
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
