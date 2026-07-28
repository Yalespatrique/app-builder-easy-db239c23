package com.asterplay.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.asterplay.tv.BuildConfig
import com.asterplay.tv.core.DeviceId
import com.asterplay.tv.net.PanelApi
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.BgSurface
import com.asterplay.tv.ui.theme.BrandGradient
import com.asterplay.tv.ui.theme.NeonPurple
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun PairingScreen(onActivated: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val mac = remember { DeviceId.getMac(ctx) }
    val key = remember { DeviceId.getKey(mac) }

    var code by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun tryActivateMac() {
        if (loading) return
        loading = true; message = null
        scope.launch {
            val r = PanelApi.activateWithMac(mac, key)
            loading = false
            if (r.ok && r.playlistUrl != null && r.xtream != null) {
                PlaylistStore.save(ctx, r.playlistUrl)
                com.asterplay.tv.store.XtreamStore.save(ctx, r.xtream)
                com.asterplay.tv.store.LoginStore.saveMac(ctx)
                onActivated()
            } else message = r.message ?: "Ainda não ativado"
        }
    }

    fun tryLoginCode() {
        if (loading) return
        if (code.isBlank() || user.isBlank() || pass.isBlank()) {
            message = "Preencha código, usuário e senha"; return
        }
        loading = true; message = "Verificando sua conta..."
        scope.launch {
            val r = PanelApi.activateWithCode(code.trim(), user.trim(), pass.trim())
            if (!r.ok || r.playlistUrl == null || r.xtream == null) {
                loading = false
                message = r.message ?: "Código, usuário ou senha inválidos"
                return@launch
            }
            // Confirma direto no servidor da lista antes de entrar.
            val auth = com.asterplay.tv.net.XtreamApi.authenticateDetailed(r.xtream)
            loading = false
            if (auth == com.asterplay.tv.net.XtreamApi.AuthResult.INVALID) {
                message = "Usuário ou senha inválidos para esse código"
                return@launch
            }
            PlaylistStore.save(ctx, r.playlistUrl)
            com.asterplay.tv.store.XtreamStore.save(ctx, r.xtream)
            com.asterplay.tv.store.LoginStore.saveCode(ctx, code.trim(), user.trim(), pass.trim())
            message = null
            onActivated()
        }
    }


    Box(Modifier.fillMaxSize().background(com.asterplay.tv.ui.theme.BgBase)) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.asterplay.tv.R.drawable.bg_gradient),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.55f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC0B0B14)),
        )
        Column(
            Modifier.fillMaxSize().padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Cabeçalho
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.asterplay.tv.R.drawable.logo_asterplay),
                    contentDescription = "Asterplay",
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "ATIVE SUA LISTA",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text("appasterplay.top", color = Accent, style = MaterialTheme.typography.titleMedium)
                }
            }

            // Duas colunas: aviso do app (esquerda) e login por código (direita)
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Aviso do app (esquerda)
                Column(
                    Modifier
                        .weight(1f)
                        .background(BgSurface, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "AVISO",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Este é apenas um reprodutor de mídia. Não somos responsáveis por nenhum conteúdo carregado. Não fornecemos conteúdos nem listas de reprodução — o usuário é responsável pelas informações que insere.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )


                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { tryActivateMac() },
                        enabled = !loading,
                        colors = ButtonDefaults.colors(
                            containerColor = BgElevated,
                            contentColor = TextPrimary,
                            focusedContainerColor = Accent,
                            focusedContentColor = Color.Black,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (loading) "Verificando..." else "Verificar ativação", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Login por código (direita)
                Column(
                    Modifier
                        .weight(1f)
                        .background(BgSurface, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TvTextField(value = code, onChange = { code = it }, label = "Código")
                    TvTextField(value = user, onChange = { user = it }, label = "Usuário")
                    TvTextField(value = pass, onChange = { pass = it }, label = "Senha", isPassword = true)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { tryLoginCode() },
                        enabled = !loading,
                        colors = ButtonDefaults.colors(
                            containerColor = NeonPurple,
                            contentColor = Color.White,
                            focusedContainerColor = Accent,
                            focusedContentColor = Color.Black,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (loading) "Entrando..." else "Entrar", fontWeight = FontWeight.SemiBold) }
                }
            }

            if (message != null) {
                Text(message!!, color = Accent, style = MaterialTheme.typography.bodyMedium)
            }

            // Rodapé: MAC à esquerda, site no meio, Chave à direita
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MAC ${DeviceId.formatted(mac)}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Text("appasterplay.top", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Text("Chave $key", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BgElevated, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TvTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    Column {
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 18.sp),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (isPassword) androidx.compose.ui.text.input.KeyboardType.Password
                else androidx.compose.ui.text.input.KeyboardType.Text,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
            ),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .background(BgElevated, RoundedCornerShape(8.dp))
                .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp)) else Modifier)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) keyboard?.show() }
                .clickable { focusRequester.requestFocus(); keyboard?.show() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
