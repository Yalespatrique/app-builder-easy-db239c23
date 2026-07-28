package com.asterplay.tv.ui.screens

import android.graphics.Color as AndroidColor
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import com.asterplay.tv.BuildConfig
import com.asterplay.tv.core.DeviceId
import com.asterplay.tv.net.PanelApi
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.AccentGlow
import com.asterplay.tv.ui.theme.BgSelected
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
            alpha = 0.35f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color(0xE005050C)))

        Row(
            Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            // ---------- ESQUERDA: informações ----------
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.asterplay.tv.R.drawable.logo_asterplay),
                        contentDescription = "Asterplay",
                        modifier = Modifier.size(84.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "ATIVE SUA LISTA",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text("appasterplay.top", color = Accent, style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.height(28.dp))
                Text(
                    "Este app é apenas um reprodutor de mídia. Não somos responsáveis por nenhum conteúdo carregado. Não fornecemos conteúdos, canais nem listas de reprodução — a lista é de responsabilidade exclusiva do usuário.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Column {
                        Text("MAC", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            DeviceId.formatted(mac),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Column {
                        Text("CHAVE", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            key,
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { tryActivateMac() },
                    enabled = !loading,
                    shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0x14FFFFFF),
                        contentColor = TextSecondary,
                        focusedContainerColor = BgSelected,
                        focusedContentColor = TextPrimary,
                    ),
                    modifier = Modifier.height(46.dp),
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verificar ativação", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(16.dp))
                Text("v${BuildConfig.VERSION_NAME}", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            }

            // ---------- DIREITA: card de login ----------
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xF2101120), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 36.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "ENTRAR",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Código, usuário e senha",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    TvTextField(value = code, onChange = { code = it }, label = "CÓDIGO")
                    TvTextField(value = user, onChange = { user = it }, label = "USUÁRIO")
                    TvTextField(value = pass, onChange = { pass = it }, label = "SENHA", isPassword = true)

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { tryLoginCode() },
                        enabled = !loading,
                        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ButtonDefaults.colors(
                            containerColor = Accent,
                            contentColor = Color.Black,
                            focusedContainerColor = AccentGlow,
                            focusedContentColor = Color.Black,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            if (loading) "ENTRANDO..." else "ENTRAR",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    if (message != null) {
                        Text(message!!, color = Accent, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * Campo de texto baseado em EditText nativo: na TV o IME abre de forma confiável
 * ao receber foco/clique (BasicTextField costuma não abrir o teclado).
 */
@Composable
private fun TvTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, color = if (focused) Accent else TextMuted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color(0xFF1A1B2A), RoundedCornerShape(8.dp))
                .then(
                    if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                    else Modifier.border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp)),
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { c ->
                    EditText(c).apply {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        setTextColor(AndroidColor.WHITE)
                        setHintTextColor(AndroidColor.GRAY)
                        textSize = 18f
                        isSingleLine = true
                        setPadding(0, 0, 0, 0)
                        imeOptions = EditorInfo.IME_ACTION_NEXT
                        inputType = if (isPassword) {
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        } else {
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        }
                        isFocusable = true
                        isFocusableInTouchMode = true
                        showSoftInputOnFocus = false

                        fun openKeyboard() {
                            requestFocus()
                            val imm = c.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                        }

                        setOnClickListener { openKeyboard() }
                        setOnKeyListener { _, keyCode, event ->
                            if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                                (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                            ) {
                                openKeyboard()
                                true
                            } else false
                        }
                        setOnFocusChangeListener { _, hasFocus ->
                            focused = hasFocus
                        }
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                val t = s?.toString().orEmpty()
                                if (t != value) onChange(t)
                            }
                        })
                    }
                },
                update = { et ->
                    if (et.text.toString() != value) et.setText(value)
                },
            )
        }
    }
}
