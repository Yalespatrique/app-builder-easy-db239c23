# Asterplay — Android TV (Kotlin + ExoPlayer)

Reprodutor de mídia nativo para Android TV / Fire TV. Espelha o app Roku
Asterplay: o usuário vê o MAC na TV, vincula a lista M3U dele no painel web
(`appasterplay.top`), e o app carrega automaticamente.

## Stack

- Kotlin 2.0 · Android SDK 34 · minSdk 21 (Fire TV Stick 2ª gen+)
- androidx.leanback (UI 10-foot com D-pad)
- androidx.media3 / ExoPlayer 1.4 (HLS/DASH/MP4)
- OkHttp + ZXing (QR code) + DataStore

## Build local (opcional)

Precisa de Android Studio Ladybug+ (JDK 17 embutido).

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug   # gera app/build/outputs/apk/debug/app-debug.apk
```

## Build automático (recomendado)

`.github/workflows/android.yml` gera **APK + AAB** a cada push na `main` e
publica no GitHub Releases a cada tag `v*`. Configure os 4 secrets descritos
em [`PLAYSTORE.md`](./PLAYSTORE.md) para assinar automaticamente.

## Estrutura

```
app/src/main/
├── AndroidManifest.xml          # LEANBACK_LAUNCHER + banner TV
├── java/com/asterplay/tv/
│   ├── data/
│   │   ├── DeviceId.kt          # MAC + DeviceKey (mesmo algoritmo do Roku)
│   │   ├── AsterStore.kt        # persistência local (~ RegRead/RegWrite)
│   │   ├── PanelApi.kt          # cliente do painel (fallback duplo)
│   │   └── M3UParser.kt         # parser #EXTINF
│   └── ui/
│       ├── SplashActivity.kt    # intro.mp4 + roteamento
│       ├── PairingActivity.kt   # MAC + QR + polling
│       ├── BrowseActivity.kt    # lista de canais (Leanback)
│       └── PlayerActivity.kt    # ExoPlayer
└── res/                         # layouts, tema Asterplay, assets do Roku
```

## Contrato do painel (idêntico ao Roku)

```
GET https://appasterplay.top/api/public/playlist?mac={MAC}&key={KEY}&_={epoch}
   ↓ se falhar
GET https://painel.appasterplay.top/api/public/playlist?mac={MAC}&key={KEY}&_={epoch}

Resposta: { "ok": true, "m3u_url": "...", "status": "active", "days_left": "..." }
```

## Publicar

Google Play Store — passo a passo em [`PLAYSTORE.md`](./PLAYSTORE.md).
Amazon Appstore (Fire TV) — mesmo `.apk`, publicação em paralelo.

## Distribuição sem loja

Cada push cria um `.apk` em Actions → *asterplay-android* → download →
`adb install -r app-release.apk`.
