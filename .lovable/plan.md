# Asterplay — Fase XCIPTV

Preview do Lovable já foi corrigido (era o build:dev). Agora o roadmap Kotlin para deixar o app estilo XCIPTV, mantendo o painel/contrato Roku intacto.

## Objetivo
Transformar a tela `BrowseActivity` (hoje uma lista simples) numa experiência tipo XCIPTV/Roku original: grid de pôsteres com logo do canal, categorias na lateral, e todas as funções pedidas.

## Escopo desta fase

1. **Grid de pôsteres estilo Roku**
   - `VerticalGridSupportFragment` (Leanback) com colunas responsivas.
   - Cada card usa `tvg-logo` do M3U (via Coil), fallback pra `banner.png`.
   - Categorias (`group-title`) viram linhas do `BrowseSupportFragment` (Netflix-like) com um "Todos" e uma linha "Favoritos" e "Continuar assistindo" fixas no topo.

2. **Favoritos** — DataStore (`favorites: Set<String>` por `stream_id`). Botão amarelo (Y) no controle alterna favorito. Linha dedicada no topo.

3. **Continue assistindo** — salva `positionMs` + `durationMs` por stream ao pausar/sair do `PlayerActivity`. Linha "Continuar" mostra itens com `< 95%` assistido. Ao abrir, `player.seekTo(pos)`.

4. **Busca** — `SearchSupportFragment` do Leanback. Digitação/voz filtra por `name` ignorando acentos.

5. **EPG (XMLTV)** — painel devolve `epg_url` junto com `m3u_url`. Parser XMLTV (`<programme channel="..." start="..." stop="...">`) → mapa `channelId → List<Programme>`. Card mostra "Agora / Depois". Tecla ▶ Info abre timeline.

6. **Legendas externas** — no `PlayerActivity`, botão "CC" lista `.srt`/`.vtt` do stream (campo `subtitles` do M3U extendido) e permite anexar via `MergingMediaSource` + `SingleSampleMediaSource` (Media3).

7. **Controle parental (PIN)**
   - Primeira vez em Ajustes: cria PIN 4 dígitos (DataStore, hash SHA-256).
   - Categoria marcada como "adulto" (`group-title` contendo "XXX", "Adult", "+18") exige PIN antes de abrir.
   - Tela Ajustes: alterar PIN, listar categorias bloqueadas.

## Arquitetura

```text
data/
  FavoritesStore.kt      DataStore<Set<String>>
  ResumeStore.kt         DataStore<Map<streamId, ResumePoint>>
  ParentalStore.kt       DataStore<pinHash, blockedGroups>
  XmltvParser.kt         XmlPullParser → List<Programme>
  EpgRepository.kt       cache 30 min em disco (files/epg.xml)
  Models.kt              Channel, Programme, ResumePoint
ui/
  BrowseActivity.kt      substitui lista por BrowseSupportFragment
  fragments/
    MainBrowseFragment.kt      linhas: Continuar / Favoritos / <categorias>
    GridCategoryFragment.kt    grid de uma categoria
    SearchFragment.kt          SearchSupportFragment
    SettingsFragment.kt        PIN + info
  presenters/
    PosterCardPresenter.kt     ImageCardView 220x330, título embaixo
  PlayerActivity.kt      + resume, + legendas, + parental gate
```

## Contrato com painel (mínimo, sem breaking change)
Resposta atual `{ m3u_url }` passa a aceitar campos opcionais:
```json
{ "m3u_url": "...", "epg_url": "...", "user_expires_at": "..." }
```
Se `epg_url` faltar, EPG some silenciosamente. Nada quebra pra painéis antigos.

## Entrega em 2 sub-fases

**Sub-fase A (essencial visual + comportamento)**
Grid Leanback, PosterCardPresenter, Favoritos, Continue assistindo, Busca.

**Sub-fase B (avançado)**
EPG XMLTV, Legendas externas, PIN parental, tela de Ajustes.

Começo pela **Sub-fase A** — é o que muda a cara do app pra "estilo XCIPTV/Roku". Depois que compilar e você validar no emulador de TV, sigo pra B.

## Observações
- Nada no painel precisa mudar hoje; `epg_url` é opt-in.
- Play Store OK: continua BYO playlist, sem conteúdo embutido.
- Preview Lovable seguirá mostrando só a página estática (é projeto nativo).

Confirma que posso executar Sub-fase A agora?
