# Design: OpenCode Terminal (APK autocontido para Android)

**Data:** 2026-10-27 · **Status:** pronto para aprovação do usuário · **Rota:** B (app novo enxuto)

## 1. Intenção e porquê

O usuário quer **um único APK** que, ao abrir, mostre a **TUI do OpenCode** rodando
nativamente no celular (alvo: Samsung Galaxy S26+, Exynos 2600, Android 16 / API 36,
One UI 8.5). Hoje a alternativa exige instalar Termux + pacotes de terceiros com
comandos manuais — exatamente a dependência que ele quer eliminar.

**Por que Rota B (app novo enxuto) e não fork do Termux:** o fork arrastaria um
codebase gigante sob GPL (obrigaria a publicar fonte) e herda infra de bootstrap de
terceiros. O caminho enxuto vendoria componentes permissivos e mantém tudo em um APK.

**Por que não proot/chroot:** overhead de ptrace, frágil no Android 16, depende de
bootstrap de distro — contradiz "um app só".

## 2. Metas / Não-metas / Anti-metas

### Metas
- M1. Um APK instala → abre → TUI do OpenCode pronta (sem passos manuais pós-install).
- M2. Zero dependência em **runtime**: nada baixa de terceiros após a instalação.
- M3. Terminal funcional: PTY real, VT100/xterm, teclado virtual utilizável com
  teclas que a TUI exige (Esc, Ctrl, setas, Tab).
- M4. Ciclo de teste 100% no PC antes de tocar no celular (requisito explícito).
- M5. Instalação final no S26+ via depuração sem fio (`adb pair`), só no fim de tudo.
- M6. Licenças permissivas, com notices incluídos no app.

### Não-metas (fora de escopo desta iteração)
- Publicação na Play Store / distribuição pública.
- Atualizador in-app do runtime; builds de outros ABIs (x86, armeabi).
- Multi-sessão, abas, rodar shell genérica como app-principal (o shell existe só
  como fallback de teste/debug).
- Modo servidor/headless (`opencode serve`/`pair`) — a Rota 1 (WebView) foi
  descartada; este app é TUI.
- Build próprio da runtime a partir de fonte (é a fase B2, futura).

### Anti-metas (falha mesmo que "funcione tecnicamente")
- Tela branca silenciosa quando algo falha (extrair/executar) — sempre diagnóstico
  visível.
- APK que precisa de internet após instalar para funcionar.
- App que exige root, Shizuku ou permissões especiais.
- Copiar código sem preservar licença/attribution.

## 3. Restrições

| Restrição | Valor |
|---|---|
| Alvo | arm64-v8a (S26+ = Exynos 2600, aarch64) |
| Android | minSdk **28**, compileSdk/targetSdk **36** (validado no toolchain) |
| Toolchain | AGP 8.11.1, Gradle 8.14.3, JDK 21, Java 17 source (tudo já validado) |
| Idioma do código | Java (consistente com o scaffold existente; sem Kotlin p/ manter build enxuto) |
| NDK | `ndk;28.1.13356709` (disponível no sdkmanager — instalar, ~2 GB, **pré-autorizado pelo usuário**) |
| Licenças | MIT (runtime guysoft), Apache-2.0 (jackpal), MIT/Unlicense (ripgrep) |
| Tamanho do APK | ~70–75 MB (a runtime pré-compilada é ~69 MB comprimida) |
| Espaço em disco no aparelho | ~150 MB após extração (zip ~69 MB → ~140 MB extraído + app) |
| Projeto | `/home/HyprKlavier/opencode-android/` (scaffold do verify-app já validado vive lá) |

## 4. Abordagens consideradas

### A. Fork do Termux completo — **rejetada**
Fork de `termux/termux-app` (GPL-3.0) com bootstrap próprio.
*Trade-off:* terminal provado em produção, mas codebase enorme, GPL exige publicar
fonte, e herda infra de bootstrap de terceiros. Contra "um app só, sem depender de
terceiros" e contra a preferência de manter o projeto leve.

### Proot/chroot Ubuntu — **rejetada**
Binário oficial (glibc) sob proot. Overhead de ptrace, frágil no Android 16,
depende de bootstrap de distro de terceiro.

### B. App novo enxuto — **escolhida pelo usuário** ✅
Novo módulo Android: view de terminal vendorada + PTY próprio + runtime pré-compilada
embutida nos assets.

**B1 vs B2 (proveniência da runtime) — decidido: começar B1, migrar para B2 depois.**
- **B1 (agora):** vendorizar binário pré-compilado das releases da comunidade
  (downloads só no **build time**, no PC; zero dependência em runtime).
- **B2 (futuro):** build próprio em CI usando os scripts MIT do guysoft — independência
  total de terceiros também no build. Não é pré-requisito para M1.

## 5. Decisões de design (como será feito)

### 5.1 Componentes e proveniência

| Componente | Fonte | Licença | Papel |
|---|---|---|---|
| Terminal view (VT100/xterm, parser, render) | vendorizar módulo `emulatorview` de `jackpal/Android-Terminal-Emulator` (archived 2022, 3.2k★) | Apache-2.0 | Emulação + renderização + IME |
| PTY (forkpty, subprocess) | vendorizar módulo `libtermexec` do mesmo repo (JNI C) | Apache-2.0 | Criar PTY real, spawn do processo |
| Runtime OpenCode | release `guysoft/opencode-termux` **`opencode2-1.0.3rc3-android-aarch64.zip` (69 MB)** — alternativa: `Hope2333` `opencode_2.1.4_aarch64.deb` (72 MB) | MIT (empacotamento) | Binário `opencode2` (TUI v2, OpenTUI) |
| ripgrep (obrigatório p/ OpenCode) | pacote Termux `ripgrep` (bionic aarch64) — fallback: build upstream musl estático | MIT | Busca de arquivos do agente |
| JNI próprio | nosso (`ndk;28.1`) | — | Ponte Java↔libtermexec (se precisar de ajustes) |

**Racional jackpal:** é *o* ancestral do Termux (o próprio autor escreveu o "Term" do
AOSP), VT-100 maduro, Apache-2.0 (sem copyleft), e o código é estável — emulator de
terminal não "envelhece mal". Forks modernos ou são GPL (Termux) ou sem manutenção
pior. Se o parser não cobrir algo que a OpenTUI exige, patcheamos (Apache-2.0 nos
permite) — ver risco R1.

### 5.2 Layout em runtime (dentro do app, sem root)

**Inspecionado de verdade** (zip `opencode2-1.0.3rc3` baixado e analisado, ver §5.8):
o release v2 tem só **3 arquivos** — `opencode2` (wrapper sh, 1.9K),
`opencode2.bin` (166 MB, ELF aarch64 bionic, NDK r28c) e `libopentui.so` (21 MB).
Não há `libc++_shared`/`libtagfix` (eram da v1). O wrapper **suporta layout flat**
(procura os dois arquivos no mesmo diretório do wrapper, via `$DIR`) e o `PREFIX` é
`${PREFIX:-...}` — sobrescrevível.

**Android 10+ / targetSdk 36 (W^X):** o S26+ provou que `untrusted_app` **não pode
executar** `opencode2.bin` a partir de `files/` (o filho saía 126, apesar de 755).
O caminho de execução é o diretório nativo extraído do APK
(`/data/app/.../lib/arm64/`), que tem contexto/permissão de execução. Por isso o
runtime é empacotado em `jniLibs/arm64-v8a/`:

```
app/src/main/jniLibs/arm64-v8a/
├── libopencode2.so     # opencode2.bin (PIE aarch64) — execve direto
├── libopentui.so       # dlopen via OPENTUI_LIB_PATH
├── librg.so            # ripgrep (executado via symlink em files/bin/rg)
└── libpcre2-8.so       # sidecar do rg (RUNPATH=$ORIGIN)

/data/data/dev.opencode.terminal/files/
├── .extracted_marker          # marca extração do zip legado (com versão)
├── home/                      # $HOME do OpenCode (config, auth, cache)
│   ├── .config/opencode/      # opencode.json + auth
│   └── .local/share/opencode/ # auth.json, sessões (persistem entre sessões)
├── prefix/                    # $PREFIX nosso (nunca o default Termux)
├── tmp/                       # TMPDIR
└── bin/
    └── rg -> /data/app/.../lib/arm64/librg.so  # symlink
```

**Decisão:** spawn = caminho absoluto de `nativeLibraryDir/libopencode2.so` (sem
wrapper e sem `sh`). `EnvBuilder` define `OPENTUI_LIB_PATH` (absoluto),
`LD_LIBRARY_PATH=nativeLibraryDir` e `OPENCODE_EXPERIMENTAL_DISABLE_FILEWATCHER=true`;
`PREFIX=$FILES/prefix` evita o default Termux. O `rg` é procurado no `PATH`
(`$FILES/bin`), onde um symlink aponta para `nativeLibraryDir/librg.so`; o kernel
resolve o link e aplica a permissão do arquivo nativo. O `libpcre2-8.so` fica ao
lado do `librg.so` e é achado pelo `RUNPATH=$ORIGIN`.

O `assets/runtime-arm64.zip` + `RuntimeInstaller` continuam no projeto como
compatibilidade/diagnóstico e para o layout flat histórico; **não** são o caminho
de execução no S26+. O wrapper `opencode2` (shebang Termux) também não é usado.

**Contingência (risco R2):** implementada — `libopentui.so` e os executáveis vão
para `jniLibs`, com `android:extractNativeLibs="true"` /
`useLegacyPackaging=true`; `OPENTUI_LIB_PATH`/`LD_LIBRARY_PATH` apontam para o
diretório nativo.

### 5.3 Fluxo de inicialização

```
MainActivity.onCreate
 ├─ 1. Primeira execução? (marker ausente)
 │     └─ SÍ → tela de progresso → extrair assets/runtime-arm64.zip legado
 │              para files/ (barra de progresso real: bytes/descomprimidos)
 │              → escrever marker → prosseguir
 ├─ 2. ABI primária != arm64? (Build.SUPPORTED_ABIS[0] != "arm64-v8a")
 │     └─ SÍ → fallback: spawn de /system/bin/sh no PTY + banner explicando
 │              que a runtime exige arm64 primário (AVD x86_64 de teste)
 ├─ 3. Runtime nativo presente? (nativeLibraryDir/libopencode2.so +
 │     libopentui.so); se não → fallback diagnóstico
 ├─ 4. Montar env: HOME, PREFIX=$FILES/prefix, PATH=$FILES/bin:/system/bin,
 │     SHELL=/system/bin/sh (mksh — o bash tool do OpenCode usa $SHELL),
 │     TERM=xterm-256color, LANG, TMPDIR, XDG_*,
 │     OPENCODE_DISABLE_AUTOUPDATE=1 (sem update-check offline, M2),
 │     OPENTUI_LIB_PATH/LD_LIBRARY_PATH=nativeLibraryDir,
 │     OPENCODE_EXPERIMENTAL_DISABLE_FILEWATCHER=true, chaves de provider (§5.5)
 ├─ 5. Criar symlink files/bin/rg → nativeLibraryDir/librg.so (idempotente;
 │     substitui o rg extraído do zip legado)
 ├─ 6. PTY via libtermexec → winsize inicial 80x24 (TIOCSWINSZ) e atualização
 │     no updateSize; spawn direto: nativeLibraryDir/libopencode2.so (§5.2)
 │     └─ falha de exec → fallback sh + painel de erro legível (anti-meta)
 └─ 7. TermSession + EmulatorView na tela; extra-keys row acima do teclado
```

> **ABI primária:** o arm64 só conta como runtime quando é
> `Build.SUPPORTED_ABIS[0]`. No AVD x86_64, `arm64-v8a` é secundária via
> `libndk_translation`: o `execve` feito pelo processo do app
> (`untrusted_app`) retorna `EACCES`; isso é limitação da bridge no AVD, não do
> S26+ arm64 nativo. No celular, o runtime é validado no aparelho.
>
> **`execv` e caminho absoluto:** o `TermExec` usa `execv(3)`, que não pesquisa
> `PATH`. O argv do runtime é o caminho absoluto
> `nativeLibraryDir/libopencode2.so`; o antigo workaround
> `{"/system/bin/sh", wrapper}` foi substituído (o wrapper não é mais o caminho
> de execução).

- **Extração:** um único asset `runtime-arm64.zip` (o release já é zip; reempacotamos
  no formato do nosso layout durante o build no PC). ~10–30 s no armazenamento do
  S26+ (UFS), com progresso. Marcador com versão → atualização de runtime = nova
  versão do marker, re-extração automática.
- **Atualização do runtime:** via novo build de APK (B1). In-app updater é não-meta.

### 5.4 Teclado e UX da TUI

- `EmulatorView` já trata IME/composição.
- **Extra-keys row** (custom view simples, ~50 linhas) acima do teclado soft, fixa:
  `Esc · Ctrl (toggle) · Tab · ← ↑ ↓ · Enter? · / · Ctrl+C`. Motivo: teclados virtuais
  não têm Esc/setas e a TUI do OpenCode depende delas — sem isso, M3 falha na prática.
  (Decisão minha; layout final é ajuste de builder.)
- Retenção de orientação livre (a view redimensiona; a TUI re-renderiza).
- PTY com `winsize` real: 80x24 inicial e `TIOCSWINSZ` em `updateSize`; sem
  isso o TUI lê 0x0 e pode falhar antes do primeiro layout.
- Fonte monoespaçada padrão, tamanho ajustável depois (não-meta: pinch-zoom).

### 5.5 Chave do provider (API key)

MVP: **tela mínima de setup na primeira execução** (dropdown de provider + campo de
key) → gravar em SharedPreferences e **exportar como env vars** no spawn
(`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, etc. — o próprio README do guysoft confirma
que o OpenCode lê env vars). Sem adivinhar formato de `auth.json`.
*Alternativa documentada:* deixar o usuário rodar `opencode auth login` na própria
TUI (funciona, mas mais lento). Decisão de UX final: builder, após verificar o
mecanismo de auth do OpenCode v2 na documentação oficial.

### 5.6 Estratégia de teste (M4 — tudo no PC)

| Fase | Onde | O que valida | Custo |
|---|---|---|---|
| T1 | AVD x86_64 **já existente** (`oc_test`, API 36, KVM) | Extração, PTY, render VT, extra-keys, IME, fallback sh + stress ANSI (script nosso: cores 256, cursor, alt-screen) | segundos (loop já comprovado: build→install→screencap) |
| T2 | AVD **arm64-v8a via TCG** (`system-images;android-36;google_apis;arm64-v8a` — **confirmada, rev 7**) | Runtime real: `opencode2 --version`, boot da TUI, render OpenTUI, dlopen do `libopentui.so` | boot 10–20 min (uma vez), depois lento (TCG, sem KVM) |
| T2b (opcional) | `qemu-user-static` no PC (`pacman -S qemu-user-static` — **pré-autorizado**) | Smoke: binário aarch64 executa e imprime versão, sem Android | segundos |
| T3 | **S26+ real**, wireless adb (`adb pair`) — **só depois de T1+T2 verdes** | Realidade: teclado Samsung, One UI, Exynos, bateria | M5 |

**Spike antecipado (obrigatório, antes de polir):** T2 com a TUI real rodando o mais
rápido possível — é onde os riscos R1 (parser) e R2 (linker) se materializam.

**Critério de aceite (§5.9):** T2/T3 verdes = **turno LLM real** completado, não só
renderização — T2 com key via env do emulador (nunca commitada); T3 com a key
digitada **somente no celular**, quando o usuário optar por um provider pago.
A validação de 2026-10-28 no S26+ usou o modelo gratuito do próprio TUI (OpenCode
Zen), sem key, e satisfez o critério de turno real; o caminho de key do Setup
continua disponível e não foi exercitado nessa rodada.

### 5.7 Estrutura do repositório (planejada)

```
opencode-android/
├── docs/superpowers/specs/2026-10-27-opencode-terminal-design.md  # este doc
├── app/
│   ├── src/main/java/dev/opencode/terminal/   # MainActivity, SetupActivity,
│   │                                           # TermBridge, ExtraKeysView,
│   │                                           # EnvBuilder, RuntimeLinks, PtyWindowSizer
│   ├── src/main/cpp/                          # JNI (libtermexec vendorado + ponte)
│   ├── src/main/jniLibs/arm64-v8a/             # runtime executável (não commitar)
│   │   ├── libopencode2.so
│   │   ├── libopentui.so
│   │   ├── librg.so
│   │   └── libpcre2-8.so
│   ├── src/main/assets/runtime-arm64.zip      # zip legado (não commitar)
│   └── vendor/emulatorview/ + vendor/libtermexec/  # código jackpal com NOTICE/LICENSE
├── scripts/fetch-runtime.sh                   # baixa release, gera jniLibs + zip
├── build.sh                                   # entrypoint (já existe, estendido)
└── tests/ansi-stress.sh                       # gerador de sequências p/ T1
```

### 5.8 Verificação da runtime (passo 0 — já executado, 2026-10-27)

Antes de qualquer código, o release `opencode2-1.0.3rc3-android-aarch64.zip` foi
baixado e inspecionado no PC (ELF/strings/wrapper). Resultados:

| Verificação | Resultado |
|---|---|
| Linkage | ✅ bionic puro: interpreter `/system/bin/linker64`, "for Android 28/29", NDK r28c — **sem glibc** |
| Libs NEEDED | ✅ só `libc.so`, `libm.so`, `libdl.so` — sem `libc++_shared.so` extra |
| Paths `/data/data/com.termux` escondidos no binário | ✅ nenhum (strings verificado) |
| Wrapper Termux | ✅ legado: shebang ignorado via `sh <wrapper>`; não é mais o caminho de execução no S26+ |
| Execução no S26+ (Android 10+ W^X) | ✅ `untrusted_app` não executa `files/` (status 126); binário em `nativeLibraryDir/libopencode2.so` executa e a TUI sobe |
| 16 KB page alignment (Android 16) | ✅ LOAD align 0x4000/0x10000 (≥16 KB) em bin e `.so` — seguro no S26+ |
| Update-check offline (M2) | ✅ env `OPENCODE_DISABLE_AUTOUPDATE` existe — será setado no spawn |
| TLS sem rede de certs | ✅ binário Bun compila com CA embutida (a confirmar no T2) |
| ripgrep incluso? | ❌ não vem no zip — vendorizar separadamente (§5.1) |
| git incluso? | ❌ não vem; e o Android não tem git — **ver §5.9** |

O que a inspeção **não** prova (vira gate no T2/T3): execução real + boot da TUI no
dispositivo, dlopen efetivo do `libopentui.so`, e um turno LLM ponta-a-ponta.
**Validado no S26+ em 2026-10-28:** TUI real, `rg 15.2.0`, turno LLM real
(`Responda apenas OK` → `OK`; Flint 40B Free, 1,5 s, 96,9 tok/s, repetido no
APK final), sem key. Evidências: `docs/evidence/phone-*.png` e
`phone-runtime.txt`.

### 5.9 Decisões de escopo derivadas da verificação

- **Shell do agente:** o `bash` tool do OpenCode usa `$SHELL` (fallback `/bin/bash`).
  Resolvido sem vendor: `SHELL=/system/bin/sh` (mksh) no env do spawn. Se o mksh
  não bastar para comandos complexos, fallback é vendorizar um bash estático (baixa
  probabilidade; verificar no T2).
- **git ausente:** o OpenCode usa git para contexto de repo/diff. Sem git no Android,
  features git-bound degradam (o agente vê erros de "git: not found" e segue).
  **Decisão do usuário (2026-10-27): MVP sem git** — degradação documentada; o T2
  verifica se a ausência de git quebra algo além das features git-bound. Vendorizar
  git estático fica para depois, se o T2 mostrar necessidade.
- **Barra de aceite do teste (decisão do usuário, 2026-10-27): turno LLM real.**
  T2/T3 só contam como verdes quando uma conversa real com o agente funcionar de
  ponta a ponta (isso exercita TLS + auth + rede de verdade). Provisionamento da key
  no teste: T2 via env do emulador; T3 **somente no celular**, se você optar por um
  provider pago — **nada de key hardcoded no repo (regra da casa)**. Em 2026-10-28 o
  S26+ fechou o gate com o modelo gratuito do TUI (OpenCode Zen), sem key; o Setup
  de key continua disponível e não foi exercitado nessa rodada.
- **Armazenamento da API key (decisão do usuário, 2026-10-27): SharedPreferences
  simples** (armazenamento privado do app; suficiente para app pessoal não publicado).
  Keystore/EncryptedSharedPreferences: opcional depois.
- **Ciclo de vida da sessão (decisão minha, §7 aceite):** MVP **aceita** morte do
  processo ao backgroundar sob pressão de memória — sem foreground service. As
  *sessões* do OpenCode persistem em disco (`~/.local/share/opencode`) e a TUI
  permite retomar a última conversa na reabertura. Se a retomada falhar no T2/T3,
  aí sim vira serviço de primeiro plano (escopo novo, com notificação).

## 6. Riscos e contingências

| # | Risco | Prob. | Contingência |
|---|---|---|---|
| **R1** | Parser VT100 do jackpal não cobre algo que a OpenTUI usa (truecolor? bracketed paste? mouse?) | média | **Spike T2 cedo**; patchear o parser (Apache-2.0 permite, é nosso fork); alternativa: outro lib permissivo |
| **R2** | Linker/W^X bloquear dlopen ou exec em `files/` | **confirmado no S26+** | implementado: `jniLibs` → `nativeLibraryDir`; exec direto + `OPENTUI_LIB_PATH`/`LD_LIBRARY_PATH`; `rg` via symlink (§5.2) |
| **R3** | Wrapper do guysoft acoplar a layout Termux | **irrelevante** | wrapper não é usado no caminho de execução; env replicado no `EnvBuilder` |
| **R4** | Release B1 quebrar silenciosamente em update upstream | média | pinagem por versão + SHA256 no `fetch-runtime.sh`; B2 mitiga |
| **R5** | AVD arm64 indisponível/lento demais no TCG | **baixa** | imagem `android-36;google_apis;arm64-v8a` **confirmada (rev 7)**; T2b (qemu-user) como smoke rápido |
| **R6** | `libtermexec` antigo não compilar com AGP 8/NDK 28 | média | é C pequeno; corrigir warnings ou reescrever a ponte JNI (~100 linhas) |
| **R7** | mksh (`/system/bin/sh`) insuficiente p/ bash tool do agente | baixa | vendorizar bash estático (§5.9); verificar no T2 |
| **R8** | TLS falhar em runtime (CA embutida Bun não cobre algo) | **baixa (validado)** | turno real no S26+ em 2026-10-28; se regredir, exportar `SSL_CERT_FILE`/vendor de certs |

## 7. O que fica para o builder (decisões suas)

- Formato exato da integração JNI (manter Android.mk do jackpal via `ndkBuild` vs.
  reescrever em CMake/clang) e layout do `vendor/`.
- Empacotamento: runtime em `jniLibs` (executável no `nativeLibraryDir`) + zip
  legado; `fetch-runtime.sh` gera os dois layouts.
- Fonte do ripgrep (pacote Termux bionic vs. binário musl estático vs. build com NDK,
  §5.1) — pinar versão + SHA256.
- UX do setup de API key e mecanismo de auth final (§5.5, após verificar docs oficiais).
- Nome/pacote finais (`dev.opencode.terminal` / "OpenCode Terminal" é a minha
  sugestão — renomear é trivial).
- Compressão/assinatura do APK e keystore de release (debug serve para T1–T3).
- Especificação da extra-keys row final (§5.4).
- Ordem do plano de implementação (via `superpowers:writing-plans`, após aprovação).

**Decisões de escopo do usuário — RESOLVIDAS (2026-10-27, ver §5.9):** barra de
aceite = turno LLM real; MVP sem git; key em SharedPreferences simples.

## 8. Fora de escopo futuro (B2+)

Build próprio da runtime em CI (scripts guysoft), in-app updater, publicação,
suporte a outros ABIs, abas/multi-sessão, modo servidor (Rota 1) como feature extra.
