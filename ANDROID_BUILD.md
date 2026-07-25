# Build do APK Asterplay (Android TV) via GitHub Actions

Este projeto é uma reimplementação web (React + TanStack Start) do app Roku
Asterplay, empacotada como APK Android TV via [Capacitor](https://capacitorjs.com/).
O build é feito pelo GitHub Actions (`.github/workflows/android-apk.yml`) — você
não precisa de Android Studio.

## Como gerar o APK

### 1. Enviar o código para o repo `Yalespatrique/appasterplay`

Dentro do Lovable: **Plus (+) → GitHub → Connect project** e escolha
`Yalespatrique/appasterplay`. Se ele já existir e não estiver vazio, precisa
apagá-lo/renomeá-lo antes — o Lovable só cria um repo novo.

### 2. (Opcional, mas recomendado) Configurar assinatura de release

Sem keystore o workflow ainda gera um **APK debug** — instala em TV, mas cada
build usa uma assinatura nova (a TV pede reinstalar).

Gere um keystore uma única vez, na sua máquina:

```bash
keytool -genkey -v \
  -keystore asterplay.keystore \
  -alias asterplay \
  -keyalg RSA -keysize 2048 -validity 10000
```

Codifique em base64:

```bash
base64 -w 0 asterplay.keystore > asterplay.keystore.b64
```

No GitHub, em **Settings → Secrets and variables → Actions**, crie:

| Secret | Valor |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | conteúdo de `asterplay.keystore.b64` |
| `KEYSTORE_PASSWORD` | senha da keystore |
| `KEY_ALIAS` | `asterplay` |
| `KEY_PASSWORD` | senha da chave |

### 3. Rodar o workflow

Duas formas:

- **Manual**: aba **Actions → Android APK → Run workflow**.
- **Tag de release**: `git tag v1.0.0 && git push --tags` — o APK é anexado
  automaticamente à Release criada.

Ao final, baixe o APK em **Actions → run mais recente → Artifacts →
`asterplay-apk`**.

## Instalar na Android TV / Fire TV

1. Na TV, **Ajustes → Dispositivo → Sobre → Build** — clique 7× para ativar
   modo desenvolvedor.
2. **Ajustes → Dispositivo → Opções de desenvolvedor → Fontes desconhecidas** → ligar.
3. Instale um gerenciador (ex: **Downloader** by AFTVnews) na loja da TV.
4. No Downloader, abra o link direto do APK (ou envie via **adb install**):

```bash
adb connect <IP-DA-TV>:5555
adb install asterplay-release.apk
```

O app aparece com **banner** e categoria `LEANBACK_LAUNCHER` — abre direto
no launcher da TV com D-pad ativo (setas + OK + Back).

## Estado atual (Fase 1)

Implementado:

- Splash + intro (mesmo `intro.mp4` do Roku)
- Tela de **Login/Ativação** replicando `LoginScene.brs`:
  MAC do dispositivo, código de 6 dígitos, chamada ao painel
  (`https://appasterplay.top/api/public/playlist`, com fallback
  `painel.appasterplay.top`), fallback manual host/user/senha, QR code.
- Navegação D-pad (`@noriginmedia/norigin-spatial-navigation`).
- Persistência de credenciais e status do device (localStorage,
  análogo ao `RegWrite` do Roku).
- Home placeholder com 6 tiles.
- Pipeline completo de build de APK.

Próximas fases (peça uma por vez):

2. Home + Continue Assistindo · 3. Playlist (M3U) + Categorias + Grid · 4. Detalhes + Elenco (TMDB) · 5. Live TV + EPG · 6. Player HLS · 7. Settings + Pedidos + Tradução.
