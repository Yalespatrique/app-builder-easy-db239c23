# Asterplay Android TV

App Android nativo (Kotlin + Leanback + Media3/ExoPlayer) para Android TV / Google TV.

## Abrir no Android Studio
1. **File → Open** e selecione a pasta `android-tv/` (não a raiz do repo).
2. Aguarde o Gradle Sync (baixa Media3, Coil, ZXing).
3. **Run 'app'** com um emulador Android TV (API 34).

## Login
- **Por MAC/Chave**: gerada automática a partir de `ANDROID_ID` (SHA-1 → 12 hex → key 6 dígitos). Painel: `appasterplay.top`.
- **Por Código**: DNS + Usuário + Senha (Xtream Codes → M3U).

## Build local
```bash
cd android-tv
./gradlew assembleDebug   # ou assembleRelease
```
APK em `app/build/outputs/apk/`.

## Recursos
- Grid de canais estilo XCIPTV
- Favoritos (tecla amarela do controle)
- Continuar assistindo (posição salva)
- QR Code de pareamento
- Media3/ExoPlayer com HLS
