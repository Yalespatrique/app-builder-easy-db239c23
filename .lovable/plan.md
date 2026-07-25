
# Plano — Asterplay Android TV (APK via GitHub Actions)

## Realidade técnica primeiro (importante)

O que você enviou é um app **Roku** (BrightScript + SceneGraph, ~5.300 linhas). **Não existe conversor** para Android. O que dá pra fazer é **reimplementar** o app em outra stack que gere APK e rode em Android TV com D-pad, mantendo o mesmo visual, mesmas telas e a mesma lógica (login por MAC, painel de ativação, playlist M3U, EPG, player, favoritos, pedidos, categorias, detalhes, live TV, settings).

Lovable é uma plataforma **web** (TanStack Start / React). Um projeto Android TV *nativo em Kotlin* não é editável dentro do Lovable. As duas opções realistas são:

- **A) React TV app + Capacitor WebView (APK Android TV)** — 100% construível aqui, buildado por GitHub Actions. UI idêntica ao Roku, D-pad via spatial navigation, player com hls.js/Shaka, `<video>` HTML5. É o caminho recomendado.
- **B) Kotlin nativo (Leanback/Compose TV)** — melhor performance de player, mas o código Kotlin **não** vive dentro do Lovable; eu só criaria o esqueleto e o workflow, e você editaria em Android Studio.

**Vou seguir com a opção A**, que atende seu pedido "app nativo para TV, mostrar igual ao Roku" e permite build automático de APK pelo GitHub Actions sem você precisar de Android Studio.

## Escopo desta primeira entrega

Escala grande — vou dividir em fases. Esta fase implementa a **fundação + tela de Login** (equivalente ao `LoginScene.brs`), com todo o pipeline de build de APK pronto. As telas restantes (Home, Live, Categorias, Detalhes, Player, Playlist, Settings, Requests, Actor) virão em fases seguintes, uma por vez, para manter qualidade.

### Fase 1 (este turno)
1. **Assets** — Extrair `images/` e `videos/intro.mp4` do zip, subir pra CDN via `lovable-assets`, gerar ícone Android a partir de `icon_focus_hd.png` e splash a partir de `splash_fhd.jpg`.
2. **UI base TV** — Tema escuro Asterplay (fundo `bg_gradient.jpg`, cor de foco, tipografia), layout fullscreen 1920×1080 com escala responsiva, sistema de foco por D-pad (setas + Enter + Back) usando `@noriginmedia/norigin-spatial-navigation`.
3. **Rotas** — `/` → intro (vídeo `intro.mp4` + splash) → `/login`. Estrutura pronta para `/home`, `/live`, `/category/:type`, `/details/:id`, `/player`, `/settings`, `/requests`, `/playlist`.
4. **Tela de Login** — Replica `LoginScene.xml/.brs`:
   - Detecção de "MAC" do device (no web: hash persistente em localStorage, análogo ao `ResolveStreamCodesMac`)
   - Chamada ao painel de ativação (`PanelTask`) — vou extrair a URL/protocolo do `.brs`
   - Card com MAC, device key, QR de ativação (`qr_activate.png`), texto de status
   - Campos host/usuário/senha manuais quando o painel falha
   - Persistência em localStorage (equivalente ao `RegWrite`)
5. **Capacitor Android** — Adicionar `@capacitor/core`, `@capacitor/android`, `capacitor.config.ts` com `appId=com.asterplay.tv`, `appName=Asterplay`. Pasta `android/` commitada com Leanback (`<uses-feature android:name="android.software.leanback">`, banner TV, category `LEANBACK_LAUNCHER`).
6. **GitHub Actions** — `.github/workflows/android-apk.yml`: build web → `npx cap sync android` → `./gradlew assembleRelease` → assinar com keystore (secrets do repo) → publicar APK como artifact e em Releases a cada tag `v*`.
7. **Docs** — `ANDROID_BUILD.md` explicando: secrets a criar (`ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), como gerar o keystore, como instalar o APK numa Android TV / Fire TV.

### Fases seguintes (turnos futuros, uma por pedido seu)
- Fase 2: `MainScene` + `HomeScene` (grid principal com Filmes/Séries/TV/Playlist/Requests/Settings + faixa "Continue assistindo")
- Fase 3: `PlaylistScene` (parser M3U + `PlaylistActionTask`) e `CategoryScene` + `CategoryGrid` + `PosterItem`
- Fase 4: `DetailsScene` + `ActorScene` + `InfoTask` (TMDB/painel)
- Fase 5: `LiveScene` + `EpgTask`
- Fase 6: `PlayerScene` (hls.js + controles ctrl_play/pause/forward/rewind, legendas, resume)
- Fase 7: `SettingsScene` + `RequestsScene` + `TranslateTask`

## Detalhes técnicos

**Stack:** React 19 + TanStack Router (já no projeto), Zustand para estado global (creds, playlist, favoritos), `@noriginmedia/norigin-spatial-navigation` para foco D-pad, `hls.js` para streams, Capacitor 6 para APK.

**Manifest Android TV:**
```xml
<uses-feature android:name="android.software.leanback" android:required="true"/>
<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
<application android:banner="@drawable/banner">
  <activity ...>
    <intent-filter>
      <category android:name="android.intent.category.LEANBACK_LAUNCHER"/>
    </intent-filter>
  </activity>
</application>
```

**Workflow (esboço):**
```text
on: push tags v*, workflow_dispatch
jobs.build:
  - checkout
  - setup-node + bun install + bun run build
  - setup-java 17
  - npx cap sync android
  - decode keystore secret
  - ./gradlew assembleRelease
  - upload-artifact + softprops/action-gh-release
```

**Repo Yalespatrique/appasterplay:** o Lovable sincroniza para GitHub via a integração nativa (Plus → GitHub → Connect). Eu não consigo apontar pra um repo já existente — o fluxo padrão é criar o repo pelo Lovable. Se você **precisa** que seja exatamente esse repo, temos duas opções: (1) você renomeia/apaga o repo atual e deixa o Lovable criar `appasterplay` na sua conta, ou (2) depois de eu terminar, você exporta o código e faz push manual. **Confirma qual prefere** antes de eu concluir o build final.

## Perguntas que travam a Fase 1

1. **Endpoint do painel de ativação** — no `.brs` deve estar a URL base. Vou extrair automaticamente, mas se você tiver uma URL de painel diferente, me diz.
2. **Repo GitHub** — opção (1) recriar no Lovable ou (2) push manual depois? (não bloqueia o desenvolvimento, só o passo final)

Se tudo estiver ok, aprova que eu começo a Fase 1 já.
