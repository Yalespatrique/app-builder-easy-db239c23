package com.asterplay.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.asterplay.tv.net.TopHomePreload
import com.asterplay.tv.net.XtreamApi
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Valida as credenciais Xtream contra o servidor.
 * Sem download de M3U — nada de OOM. Deve terminar em 1–3s.
 */
@Composable
fun LoadingScreen(onReady: () -> Unit, onFail: () -> Unit) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Conectando ao servidor...") }
    var sub by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val c = XtreamStore.get(ctx)
        if (c == null) { onFail(); return@LaunchedEffect }
        status = "Validando credenciais..."
        sub = c.host
        val ok = XtreamApi.authenticate(c)
        if (ok) {
            status = "Carregando destaques..."
            sub = "Buscando Top 10 da semana"
            TopHomePreload.run(ctx)
            status = "Pronto!"
            sub = "Bem-vindo"
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
            Text(status, color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(sub, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(BgElevated)) {
                Box(Modifier.fillMaxWidth(0.4f).height(6.dp).background(Accent))
            }
        }
    }
}
