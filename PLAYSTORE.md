# Publicando o Asterplay na Google Play

Este projeto é um **Android nativo (Kotlin + Media3/ExoPlayer)** empacotado como
AAB. O app é um **reprodutor de mídia universal** — não embarca listas de
canais. Cada usuário vincula sua própria playlist M3U no painel web usando o
MAC exibido na TV (mesmo contrato do app Roku original).

## 1. Requisitos únicos

1. Conta **Google Play Console** (US$ 25, uma vez). https://play.google.com/console
2. Verificação de identidade concluída (documento + endereço). Leva de 2 a 14 dias.
3. Package name **`com.asterplay.tv`** (imutável depois do 1º upload).
4. Keystore de **upload** (a Google gerencia a chave de assinatura final).

## 2. Gerar o keystore (uma vez)

```bash
keytool -genkey -v \
  -keystore asterplay-upload.keystore \
  -alias asterplay -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 asterplay-upload.keystore > asterplay-upload.keystore.b64
```

No repo GitHub → **Settings → Secrets and variables → Actions**:

| Secret | Valor |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | conteúdo de `asterplay-upload.keystore.b64` |
| `KEYSTORE_PASSWORD` | senha da keystore |
| `KEY_ALIAS` | `asterplay` |
| `KEY_PASSWORD` | senha da chave |

## 3. Build

- Push na `main` → build automático (artifact `asterplay-android` com `.apk` + `.aab`).
- Tag `v1.0.0` → cria Release do GitHub com os arquivos anexados.
- **Manual**: aba Actions → *Android Build* → *Run workflow*.

## 4. 1º upload na Play Console

1. Play Console → **Criar app** → nome "Asterplay", categoria **Player de mídia**.
2. **Configuração → App signing**: aceitar Play App Signing (padrão).
3. **Testes → Teste interno**: subir o `app-release.aab` da run mais recente. Adicionar seu email como tester. Instalar na TV via link do teste interno.
4. **Ficha da loja** — texto obrigatório com wording seguro (evita rejeição por conteúdo):

   > Asterplay é um **reprodutor de mídia** para Android TV. Permite reproduzir listas M3U/HLS que você mesmo configura. O app **não fornece** nem indexa conteúdo — todo material reproduzido vem de fontes fornecidas pelo próprio usuário.

   **Não use** as palavras: "IPTV", "filmes grátis", "canais", nomes de emissoras, logos de canais, imagens de séries/filmes conhecidos. Isso derruba o app em revisão automática.

5. **Classificação de conteúdo**: preencha o questionário → resultado esperado "Livre / Todos".
6. **Público-alvo**: 13+ (evita restrições extras da política familiar).
7. **Política de privacidade**: obrigatória. Modelo mínimo em `PRIVACY.md`
   (hospede em qualquer URL pública — GitHub Pages serve).
8. **Data Safety**: declare que o app **coleta**: identificador de dispositivo (MAC gerado localmente) — usado apenas para vincular a lista M3U do usuário.

## 5. Promoção a produção

Depois de aprovado no teste interno:
- **Teste fechado** (opcional, testadores externos por email)
- **Teste aberto** (opt-in por link, sem revisão adicional)
- **Produção** (revisão de 1–7 dias)

## 6. Amazon Appstore (Fire TV) — em paralelo

Recomendo publicar também na Amazon Appstore. Mesmo `.apk`, revisão mais
rápida, e Fire TV é o maior mercado de Android TV no Brasil.
https://developer.amazon.com/apps-and-games

## 7. Instalação por sideload (dev / debug)

```bash
adb connect <IP-DA-TV>:5555
adb install -r out/app-release.apk
```
