# Design: experiência mobile da TUI do OpenCode (Galaxy S26+)

**Data:** 2026-10-28 · **Status:** aprovado pelo usuário — pacote A (teclado próprio completo + imersão total) · **Spec anterior:** `2026-10-27-opencode-terminal-design.md`

## 1. Intenção e porquê

O APK já executa a **TUI real do OpenCode** em um PTY, mas a experiência ainda parece
um terminal genérico: o logo/home não aparece, o spinner vira “tofu”, acentos são
perdidos, as abas não respondem ao toque, não há teclado próprio e o cabeçalho do
Android ocupa espaço.

O objetivo é aproximar o aparelho da experiência do PC **sem trocar a TUI** e sem
voltar a um shell de diagnóstico. Cada melhoria será entregue como uma subtarefa
verificável no S26+, com screenshot antes de avançar.

### Decisões já tomadas

- Pacote **A — Completo + Imersivo**:
  - teclado próprio completo, com QWERTY, acentos, números/símbolos e controles;
  - Gboard opcional apenas para colar/emoji;
  - remoção da ActionBar e das barras de status/navegação, com swipe para revelá-las;
  - layout/escala automáticos.
- A TUI real permanece; não haverá shell genérico como experiência principal.
- A API key só pode ser digitada pelo usuário no celular; o app não a solicita,
  registra ou expõe. O armazenamento continua em `SharedPreferences`.
- O Setup continua utilizável. BACK no Setup não pode iniciar o app sem configuração.
- Nenhum PTY/transcript cru será registrado no app.
- O runtime continua pinado; **não** é necessário atualizar para o logo aparecer.

## 2. Pesquisa: “ASCII” x Unicode e a fonte

### 2.1 O que a TUI desenha

A expressão “ASCII art” usada para o logo do OpenCode é coloquial. No código da TUI
(v2.0.0) o logo é composto por **Unicode block elements**:

```text
██████  ██████  ██████ ...
█    █  █    █  █      ...
```

O componente `Logo` usa `█`, `▀` e `▄`; o spinner/status usa `■`, `⬝` e Braille.
Não são caracteres ASCII de 7 bits. Por isso o terminal precisa de uma fonte com
cobertura Unicode; não basta uma fonte que só tenha letras ASCII.

A pesquisa externa confirma que:

- o logo do CLI é arte textual e já sofreu problema de quebra de linha em
  [`anomalyco/opencode#11278`](https://github.com/anomalyco/opencode/issues/11278);
- a fonte é propriedade do **emulador de terminal**, não da TUI, e a TUI não tem
  configuração de fonte portátil
  ([`anomalyco/opencode#12503`](https://github.com/anomalyco/opencode/issues/12503));
- “caixas pretas” podem ser tanto corrupção do renderer do terminal
  ([`anomalyco/opencode#32414`](https://github.com/anomalyco/opencode/issues/32414))
  quanto ausência de glifos na fonte; no nosso caso, a inspeção da fonte do S26+
  mostrou ausência dos glifos;
- fontes Nerd Font adicionam ícones da Private Use Area, mas a TUI atual usa
  símbolos Unicode comuns; uma fonte completa com block/braille/geometric é
  suficiente para esta versão.

### 2.2 Cobertura medida

Fonte extraída do S26+ (`DroidSansMono.ttf`) e fontes candidatas, comparadas com os
81 caracteres não-ASCII usados no código da TUI:

| Fonte | Blocos | Braille | `■` | `⬝` | Símbolos da TUI ausentes |
|---|---:|---:|---:|---:|---:|
| DroidSansMono (atual) | 0/7 | 0/256 | não | não | muitos |
| JetBrains Mono Nerd Font Mono | 7/7 | 256/256 | sim | **não** | 26 |
| Adwaita Mono | 7/7 | 256/256 | sim | **sim** | 2 (CJK de teste) |

**Escolha:** embarcar **Adwaita Mono Regular** (`AdwaitaMono-Regular.ttf`, OFL 1.1,
~1,4 MB). Ela cobre todos os símbolos usados pela TUI, exceto os CJK de teste
`字`/`漢`, que podem cair no fallback do sistema. Não depende de Nerd Font PUA.

A fonte ficará em `app/src/main/assets/fonts/` com a licença OFL em
`app/src/main/assets/fonts/LICENSE-Adwaita.txt`.

### 2.3 Por que não atualizar o runtime agora

O logo já existe no runtime v2.0.0 do celular. A falha é de renderização da fonte.
Atualizar o runtime misturaria risco de regressão com uma correção de UI; uma
eventual paridade de versão será uma subtarefa separada.

## 3. Arquitetura

### 3.1 Fonte e renderer

- `TerminalTypeface` (app) carrega `fonts/AdwaitaMono-Regular.ttf` com
  `Typeface.createFromAsset`; em falha, retorna `Typeface.MONOSPACE` e registra
  apenas um warning.
- `EmulatorView` ganha `setTypeface(Typeface)` e um campo com default
  `Typeface.MONOSPACE`.
- `PaintRenderer` ganha um construtor com `Typeface` e usa essa fonte no
  `Paint`; o construtor antigo continua delegando para `Typeface.MONOSPACE`.
- `TermController` aplica a fonte logo após criar o `EmulatorView`.

### 3.2 UTF-8 e acentos

`TermSession.write(int)` hoje:

```java
Character.toChars(codePoint, charBuf.array(), 0);
encoder.encode(charBuf, byteBuf, true);
write(byteBuf.array(), 0, byteBuf.position() - 1);
```

O `charBuf` não tem `position`/`limit` atualizados; o encoder vê um buffer vazio e
o caractere não-ASCII é descartado. A correção definirá o buffer corretamente e
escreverá `byteBuf.position()` bytes. Para tornar o comportamento testável, a
codificação será extraída para um helper puro `Utf8.encode(int)` (JVM testável), e
`write(int)` usará esse helper. O caminho `write(String)` continua sendo o usado
pelo teclado interno e pelo Gboard em texto colado.

### 3.3 Mouse e abas

- `TermController` chamará `EmulatorView.setMouseTracking(true)`.
- O emulatorview envia eventos X10; o parser do OpenTUI aceita X10.
- Se o teste no aparelho mostrar que o TUI só interpreta SGR em algum fluxo,
  a subtarefa adicionará a codificação SGR, sem criar uma barra nativa paralela.
- A verificação é tocar na aba “Resposta OK” e observar a troca de sessão; scroll
  e toque em botões do TUI também entram no aceite.

### 3.4 Imersão e insets

- `AppTheme` continuará com ActionBar para o **Setup** (formulário e navegação).
- `MainActivity` usará `TerminalTheme` (`Theme.Material.Light.NoActionBar`) e
  esconderá status/navegação com `WindowInsetsController` (API 30+) e fallback
  `SYSTEM_UI_FLAG_IMMERSIVE_STICKY` para API 28/29.
- “Swipe” das bordas revela as barras temporariamente; a tela volta ao modo
  imersivo depois.
- `ContentInsets` passa a aplicar padding de cutout/status no Setup e **não**
  adiciona padding de ActionBar no terminal. A área do terminal começa no topo.
- O teclado interno ocupa o rodapé; o `EmulatorView` recebe a altura restante e
  atualiza o PTY via `updateSize()`.

### 3.5 Escala automática

Novo helper puro `TerminalScale` (app) calcula o tamanho de fonte a partir de:

- largura disponível em px;
- tipo de fonte e largura de um caractere de referência;
- alvo de colunas (80 em retrato, 120 em paisagem);
- limites de legibilidade (mínimo 8 sp, máximo 20 sp).

O `TermController` aplica `setTextSize(dp)` no `EmulatorView` após o primeiro
layout e em mudanças de tamanho/orientação. O `PtyWindowSizer` existente continua
sincronizando o `winsize` do PTY. A rotação será tratada com
`android:configChanges="orientation|screenSize|keyboardHidden"` para não
reabrir a TUI; o layout será recalculado sem perder a sessão.

### 3.6 Build ↔ Plan

`agent.cycle` é `shift+tab` na TUI. A tecla envia `ESC [ Z` (`0x1b 0x5b 0x5a`).
O botão “Build/Plan” no teclado e na barra de teclas usa o mesmo caminho de
`KeyMapper`; o status do agente deve mudar na tela.

### 3.7 Teclado interno

Componentes:

- `TerminalKeyboardView`: view do teclado, sem IME do Android, no rodapé do
  terminal.
- `KeyboardController`: estado de camada (`letters`, `symbols`, `accents`),
  shift/caps, latches de Ctrl/Alt e envio.
- `KeyboardKey`: modelo de tecla (label, ação, bytes/texto, largura).
- `KeyboardInput`: interface de saída; `TermController` implementa com
  `TermSession.write(String)` para texto e bytes para teclas de controle.

Camadas e teclas:

- letras QWERTY + shift/caps, backspace, enter, espaço;
- números/símbolos;
- acentos pt-BR: `á à â ã é ê í ó ô õ ú ü ç` (e maiúsculas), por long-press e
  por uma camada/linha de acentos;
- controles: Esc, Ctrl, Alt, Tab, setas, `/`, `|`, `-`, `Build/Plan`;
- `Gboard` explícito para colar/emoji; ao voltar, o teclado interno retoma.

O `EmulatorView` terá `setShowSoftInputOnFocus(false)` para impedir o Gboard
automático; somente o botão “Gboard” chamará `showSoftKeyboard()`. O teclado
interno não registra texto nem transcript; o envio é direto ao `TermSession`.

### 3.8 Setup e segurança

- `SetupActivity` mantém seu fluxo, BACK sem configuração e validação atual.
- A fonte, o mouse e o teclado não leem nem gravam a API key.
- Nenhum segredo é embutido no APK; a key continua em `SharedPreferences`.
- Logs permitidos: dimensões, plano de execução e erros sem conteúdo digitado.

## 4. Fluxo resumido

```text
MainActivity
  ├─ Setup (se não configurado) → RESULT
  ├─ RuntimeInstaller / TermBridge
  └─ TermController
       ├─ EmulatorView + TermSession + PTY
       ├─ TerminalTypeface (Adwaita Mono)
       ├─ TerminalScale (80/120 colunas)
       ├─ mouse tracking
       └─ TerminalKeyboardView → KeyboardController → TermSession
```

## 5. Arquivos previstos

| Arquivo | Mudança |
|---|---|
| `app/src/main/assets/fonts/AdwaitaMono-Regular.ttf` | nova fonte |
| `app/src/main/assets/fonts/LICENSE-Adwaita.txt` | licença OFL |
| `app/src/main/java/dev/opencode/terminal/TerminalTypeface.java` | loader |
| `vendor/.../PaintRenderer.java` | typeface no Paint |
| `vendor/.../EmulatorView.java` | `setTypeface`, mouse, IME |
| `vendor/.../TermSession.java` | UTF-8 correto |
| `vendor/.../Utf8.java` | encoder puro/testável |
| `app/.../TermController.java` | fonte, mouse, escala, input |
| `app/.../MainActivity.java` | tema, imersão, teclado, rotação |
| `app/.../ContentInsets.java` | insets separados Setup/terminal |
| `app/.../TerminalScale.java` | cálculo de colunas/fonte |
| `app/.../TerminalKeyboardView.java` | teclado interno |
| `app/.../KeyboardController.java` | estado e envio |
| `app/.../KeyMapper.java` | `SHIFT_TAB`/`BuildPlan` |
| `app/src/main/res/values/styles.xml` | `TerminalTheme` |
| `app/src/main/AndroidManifest.xml` | tema/rotação |

## 6. Critérios de aceite por subtarefa

### S1 — Fonte e glifos

- Home exibe o logo do OpenCode no S26+.
- Spinner/status anima e mostra `■`, `⬝` e Braille sem quadrados tofu.
- Screenshot novo em `docs/evidence/`.

### S2 — UTF-8/acentos

- Digitar `ação`, `coração`, `ç`, `ã`, `é`, `ô` no aparelho e ver exatamente.
- Testes JVM para ASCII, acento, `ç`, emoji e code point inválido.
- Nenhum caractere não-ASCII descartado.

### S3 — Mouse/toque

- Tocar em uma aba de sessão troca a sessão.
- Scroll/toque em elementos do TUI não abre o Gboard.
- Screenshot da sessão trocada.

### S4 — Imersão

- “OpenCode Terminal” não aparece no topo.
- Terminal começa no topo; barras somem e voltam com swipe.
- Setup continua com formulário visível e BACK não inicia o app.

### S5 — Build/Plan

- Botão envia `shift+tab`; o status do agente muda.
- Screenshot antes/depois.

### S6 — Escala

- ~80 colunas em retrato e ~120 em paisagem, com fonte legível.
- Rotação não reinicia a TUI; PTY acompanha.
- Sem clipping do prompt/status.

### S7 — Teclado interno

- Letras, números, símbolos, acentos, edição e controles funcionam.
- Gboard só abre pelo botão explícito.
- Teclado não cobre a última linha do TUI; layout recalcula.
- Screenshot e testes de `KeyMapper`/`KeyboardController`.

### S8 — Regressão

- Setup, provedores, key, runtime e fallback continuam funcionando.
- `./build.sh`, testes JVM e `shellcheck` verdes.
- Nenhum PTY/transcript cru ou segredo em logs.
- Screenshots finais home, sessão e teclado.

## 7. Riscos e rollback

| Risco | Mitigação |
|---|---|
| Fonte não carregar no aparelho | fallback `Typeface.MONOSPACE`; screenshot + log de warning |
| Fonte quebrar largura de célula | usar variante monoespaçada; `PaintRenderer` mede a largura |
| Mouse X10 não bastar | adicionar SGR somente se o teste real falhar |
| Imersão esconder conteúdo no Setup | tema separado; `ContentInsets` testado nas duas telas |
| Teclado reduzir demais o terminal | altura máxima, fonte mínima, rolagem interna do teclado |
| Rotação reiniciar a TUI | `configChanges` + recálculo de layout; teste no aparelho |
| Regressão de acentos no Gboard | helper `Utf8` + testes JVM + teste manual |

Qualquer subtarefa pode ser revertida isoladamente; a fonte e o UTF-8 são
independentes do teclado e da imersão.

## 8. Fora de escopo

- Atualizar o runtime v2.0.0 para v2.2.0 nesta rodada.
- Suporte a múltiplas ABIs ou emulador x86_64 como experiência principal.
- Sincronizar sessões com o PC ou alterar a autenticação dos provedores.
- Registrar PTY/transcript ou telemetria de digitação.

## 9. Referências

- Código v2.0.0: `packages/tui/src/component/logo.ts` e `packages/tui/src/routes/home.tsx`.
- OpenCode #11278: logo do CLI é arte textual e quebra por largura.
- OpenCode #12503: fonte é propriedade do emulador; não há configuração portátil.
- OpenCode #32414: caixas pretas no terminal podem ser falha de renderer/fonte.
- Nerd Fonts: <https://github.com/ryanoasis/nerd-fonts>.
- Adwaita Fonts / OFL: <https://gitlab.gnome.org/GNOME/adwaita-fonts>.
- SIL Open Font License 1.1: <https://openfontlicense.org>.
