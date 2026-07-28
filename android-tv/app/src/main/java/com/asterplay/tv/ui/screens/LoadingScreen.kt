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
import com.asterplay.tv.core.DeviceId
import com.asterplay.tv.net.MenuPreload
import com.asterplay.tv.net.PanelApi
import com.asterplay.tv.net.TopHomePreload

import com.asterplay.tv.ui.components.NeonLoader
import com.asterplay.tv.net.XtreamApi
import com.asterplay.tv.store.AccountStore
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.store.LoginMethod
import com.asterplay.tv.store.LoginStore
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
        var c = XtreamStore.get(ctx)
        val method = LoginStore.method(ctx)
        val saved = LoginStore.codeLogin(ctx)

        // Sem nenhuma forma de entrar salva -> login.
        if (c == null && method == null) { onFail(); return@LaunchedEffect }

        sub = "verificando sua conta..."
        var result = if (c != null) XtreamApi.authenticateDetailed(c)
        else XtreamApi.AuthResult.INVALID

        // Falhou? Revalida no painel usando o mesmo método do login:
        // código/usuário/senha, ou MAC/Chave. Stream codes (Xtream) já foram
        // testados acima.
        if (result != XtreamApi.AuthResult.OK && method != LoginMethod.XTREAM) {
            sub = "atualizando dados da lista..."
            val r = if (method == LoginMethod.CODE && saved != null) {
                PanelApi.activateWithCode(saved.code, saved.user, saved.pass)
            } else {
                val mac = DeviceId.getMac(ctx)
                PanelApi.activateWithMac(mac, DeviceId.getKey(mac))
            }
            if (r.ok && r.xtream != null) {
                XtreamStore.save(ctx, r.xtream)
                r.playlistUrl?.let { PlaylistStore.save(ctx, it) }
                c = r.xtream
                result = XtreamApi.authenticateDetailed(c)
            }
        }

        if (c == null || result == XtreamApi.AuthResult.INVALID) {
            status = "Conta não encontrada"
            sub = "Verifique seus dados e tente novamente."
            delay(2000)
            XtreamStore.clear(ctx); LoginStore.clear(ctx); onFail()
            return@LaunchedEffect
        }

        if (result == XtreamApi.AuthResult.NETWORK) {
            // Problema de conexão: mantém as credenciais salvas e segue
            // com o que estiver em cache.
            status = "Conexão instável"
            sub = "usando dados salvos..."
            delay(1200)
        }

        // Validade da lista (exp_date do painel Xtream) pra mostrar no menu inicial.
        sub = "verificando validade da lista..."
        XtreamApi.accountInfo(c)?.let {
            AccountStore.saveExpiry(ctx, it.expiryMillis, it.status)
        }

        // DNS cadastrada no painel admin? Se não estiver, o app roda em
        // teste grátis de 7 dias; depois disso volta pro login.
        when (PanelApi.isDnsRegistered(c.host)) {
            true -> AccountStore.markDnsRegistered(ctx)
            false -> {
                AccountStore.startTrialIfNeeded(ctx)
                if (AccountStore.trialExpired(ctx)) {
                    status = "Teste grátis encerrado"
                    sub = "Ative o app ou use uma lista com DNS cadastrada."
                    delay(2600)
                    XtreamStore.clear(ctx); PlaylistStore.clear(ctx); LoginStore.clear(ctx)
                    onFail()
                    return@LaunchedEffect
                }
            }
            null -> { /* sem internet pra checar: não bloqueia */ }
        }

        // Conta validada: carrega só as categorias (rápido) e abre a Home.
        // O catálogo completo (totais por categoria, canais e destaques)
        // continua baixando em segundo plano, sem travar o app.
        MenuPreload.fast(ctx, c) { sub = it }
        MenuPreload.startBackground(ctx, c)
        onReady()
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
