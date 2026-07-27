
# Migração para Jetpack Compose for TV

## Objetivo

Trocar toda a camada de UI (atualmente Leanback + XML) por **Jetpack Compose for TV**, mantendo intactos:
- ExoPlayer / Media3 (player de vídeo)
- SQLite (`ChannelDb`, `PlaylistCache`)
- Backend (Lovable Cloud, endpoints de ativação)
- Lógica de MAC/Key, login por código, cache de playlist
- Favoritos, Continue Assistindo

Ganhos: UI moderna (cards com foco animado, gradientes, transições fluidas estilo XCIPTV/Netflix), código mais limpo (sem XML), navegação D-pad nativa via `tv-foundation`.

## Etapas

### 1. Configuração do projeto
- Atualizar `android-tv/app/build.gradle.kts`:
  - `compose = true` em `buildFeatures`
  - Adicionar deps: `androidx.compose.bom`, `androidx.tv:tv-foundation`, `androidx.tv:tv-material`, `androidx.activity:activity-compose`, `androidx.lifecycle:lifecycle-viewmodel-compose`, `coil-compose` (para logos dos canais).
  - Bump versão para `1.1.0-compose` (versionCode 10).
- Habilitar `composeOptions { kotlinCompilerExtensionVersion }`.

### 2. Sistema de design (tokens)
- Criar `ui/theme/`:
  - `Color.kt` — paleta baseada na logo UFO neon (roxo/ciano do splash).
  - `Type.kt` — tipografia TV (títulos grandes, legibilidade a 3m).
  - `Theme.kt` — `AsterplayTheme` usando `MaterialTheme` do `tv-material`.
- Sem hardcode de cores nos componentes — só tokens semânticos.

### 3. Reescrever telas em Compose
Ordem, mantendo cada tela funcional antes de passar pra próxima:

1. **SplashScreen** — vídeo intro + logo (mantém `VideoView`, wrapper Compose via `AndroidView`).
2. **PairingScreen** — tabs "Código" / "MAC+Chave", disclaimer, MAC no rodapé esquerdo, site no centro, versão à direita.
3. **LoadingScreen** — progresso de download (MB) + processamento (itens), estilo minimalista.
4. **HomeScreen** — menu lateral (Canais / Filmes / Séries / Busca / Favoritos / Config) + carrossel "Continue Assistindo" e "Favoritos".
5. **BrowseScreen** — Master-Detail: lista de categorias à esquerda (LazyColumn com foco), grade de posters à direita (`TvLazyVerticalGrid`). Cards com foco animado (scale + glow).
6. **SearchScreen** — input + resultados em grade.
7. **PlayerScreen** — `AndroidView` embrulhando `PlayerView` do Media3 (o player em si continua igual).

### 4. Componentes reutilizáveis
- `PosterCard` — card de conteúdo com foco animado, logo via Coil, título abaixo.
- `CategoryItem` — item da lista lateral com indicador de seleção.
- `FocusableButton` — botão TV com escala no foco.

### 5. Navegação
- `NavHost` do `androidx.navigation:navigation-compose` substituindo as `Activity`s.
- Uma única `MainActivity` hospedando o `NavHost`; Player continua Activity separada (fullscreen imersivo).

### 6. Limpeza
Após tudo funcionando, remover:
- Todos os `.xml` de layout (exceto `activity_main` e `activity_player`).
- `MainBrowseFragment`, `CategoryAdapter`, `ChannelAdapter`, `PosterCardPresenter`.
- Referências a `androidx.leanback:*` no Gradle.

### 7. Validação
- Build no Android Studio → rodar no emulador Android TV (API 34).
- Testar: splash → login → loading → home → categorias → player → voltar.
- Confirmar navegação D-pad em todas as telas.

## Detalhes técnicos

**Dependências chave (versões estáveis atuais):**
```
implementation platform("androidx.compose:compose-bom:2024.10.00")
implementation "androidx.tv:tv-foundation:1.0.0-alpha11"
implementation "androidx.tv:tv-material:1.0.0"
implementation "androidx.activity:activity-compose:1.9.3"
implementation "androidx.navigation:navigation-compose:2.8.4"
implementation "io.coil-kt:coil-compose:2.7.0"
```

**Estrutura de arquivos nova:**
```text
android-tv/app/src/main/java/com/asterplay/tv/
├── MainActivity.kt              (novo — hospeda NavHost)
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   └── Theme.kt
│   ├── components/
│   │   ├── PosterCard.kt
│   │   ├── CategoryItem.kt
│   │   └── FocusableButton.kt
│   └── screens/
│       ├── SplashScreen.kt
│       ├── PairingScreen.kt
│       ├── LoadingScreen.kt
│       ├── HomeScreen.kt
│       ├── BrowseScreen.kt
│       └── SearchScreen.kt
├── player/PlayerActivity.kt     (mantém — só player)
├── net/, store/, core/          (intactos)
```

**Riscos conhecidos:**
- `tv-foundation` ainda tem APIs em alpha — pode haver breaking changes.
- Primeira build do Compose demora (~2-3 min extras baixando deps).
- Emulador Android TV precisa ter GPU acelerada, senão o Compose fica lento.

## Escopo desta primeira entrega

Vou fazer numa **única passada** todas as etapas 1–5, deixando a etapa 6 (limpeza dos XML antigos) pra um segundo momento — assim, se algo quebrar no Compose, ainda dá pra rodar o app antigo via `AndroidManifest` alternativo.

Confirma que posso seguir?
