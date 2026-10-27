# OpenCode Terminal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Um APK autocontido que abre direto na TUI do OpenCode em Android arm64, com todo o ciclo validado em emulador no PC antes de tocar no celular real.

**Architecture:** App Android novo (Java) que vendoriza a view de terminal + PTY JNI da biblioteca Apache-2.0 do jackpal, extrai na primeira execução uma runtime OpenCode arm64 (bionic) fixada por versão+SHA256 a partir dos assets do APK, e a executa num PTY real com ambiente montado por uma classe pura `EnvBuilder`. Teste em AVD x86_64 primeiro (caminho de fallback shell), depois AVD arm64 via TCG (TUI real), por último Galaxy S26+.

**Tech Stack:** AGP 8.11.1, Gradle 8.14.3, JDK 21 (Java 17 source), NDK `28.1.13356709`, JUnit 4, jackpal/Android-Terminal-Emulator (Apache-2.0, vendorizado), release guysoft `opencode2-1.0.3rc3` (MIT, asset pinado), ripgrep Termux 15.2.0 (asset pinado).

**Spec:** `docs/superpowers/specs/2026-10-27-opencode-terminal-design.md` — o plano argumenta a partir do spec; quem executa lê os dois.

## Global Constraints

- minSdk **28**; compileSdk e targetSdk **36**; `sourceCompatibility` Java 17; **Java-only, sem Kotlin**.
- applicationId/namespace **`dev.opencode.terminal`**, label **"OpenCode Terminal"**.
- ABIs no APK: **`arm64-v8a`** (alvo real) **e `x86_64`** (instalação no AVD de teste) — sem elas o APK não instala no AVD x86_64.
- NDK exato: **`ndk;28.1.13356709`** (`sdkmanager "ndk;28.1.13356709"`).
- Runtime pinada: `https://github.com/guysoft/opencode-termux/releases/download/v1.0.3-rc3/opencode2-1.0.3rc3-android-aarch64.zip` SHA256 **`0725f3278cde20b39e30be1833966c67276a13829ea634f60d91f85ba3d13893`**.
- ripgrep pinado: `https://packages.termux.dev/apt/termux-main/pool/main/r/ripgrep/ripgrep_15.2.0_aarch64.deb` SHA256 **`38e28bc297000517b24702568a483eca7dc3323eb6bdccc9033f031776bdcc6c`**.
- **git não é vendorizado** (MVP sem git, decisão do usuário 2026-10-27).
- Segredos: chave de API **nunca** em repo, log, `logcat` ou em texto literal de comando; T2 usa arquivo local temporário fora do repo; T3 digita no celular. Nenhum `ANTHROPIC_*` hardcoded.
- Código vendorizado carrega `LICENSE`/`NOTICE` originais (Apache-2.0) no repo.
- `shellcheck` antes de executar qualquer `.sh` (`shellcheck x.sh && bash x.sh`); operações longas em `tmux new-session -d`; shells não interativos exportam `JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21` e `ANDROID_HOME=/home/HyprKlavier/android-sdk` explicitamente (bashrc não carrega).
- APKs: `./build.sh` (fixa env + `gradle assembleDebug`); testes unitários: mesmo env + `gradle :app:testDebugUnitTest`.
- Celular real (T3) **só** depois que Task 11 (T2) estiver verde. Aceite final = **turno LLM real** completo.

## Review Focus

Falhas que o spec implica mas que nenhum teste unitário óbvio pega — cada linha tem o teste no dono:

1. **Extração interrompida no meio** (app morte) deixa runtime parcial mas marcado como ok → Task 3: teste com `InputStream` que falha após N bytes → `IOException` sem marker → nova execução extrai com sucesso.
2. **Execução falha / ABI errada mostra tela morta** → Task 10: no AVD x86_64, `pm clear` + launch → banner de fallback com texto de motivo visível (`uiautomator dump`) e shell funcional abaixo dele.
3. **Vazamento de key** (logcat, histórico, repo) → Task 8: key fake salva → `adb logcat -d` não contém o valor; Task 9: `gitleaks detect` limpo e `git check-ignore` do asset.
4. **Sessão congela quando o processo filho termina** → Task 6: `exit` na shell → overlay "sessão encerrada" aparece e a UI responde (não é tela morta).
5. **Sequências VT da OpenTUI não renderizam** (risco R1) → Task 6: `tests/ansi-stress.sh` no AVD com marcadores visíveis (clear/alt-screen/256 cores); Task 11: screencap da TUI real com painel desenhado + logcat sem erro de linker/dlopen (R2).

---

## File Structure

```
opencode-android/
├── .gitignore                                  # cria na Task 1 (ignora asset, logs, build/)
├── app/
│   ├── build.gradle.kts                        # modifica: id, minSdk, abis, ndk, junit
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml             # modifica: label, INTERNET
│       │   ├── java/dev/opencode/terminal/
│       │   │   ├── MainActivity.java           # vira orquestrador (Task 2 → 10)
│       │   │   ├── SetupActivity.java          # Task 8
│       │   │   ├── RuntimeInstaller.java       # Task 3 (extração + marker)
│       │   │   ├── EnvBuilder.java             # Task 4 (mapa de env puro)
│       │   │   ├── TermController.java         # Task 6 (PTY + TermSession + view)
│       │   │   ├── KeyMapper.java              # Task 7 (teclas → bytes)
│       │   │   ├── ExtraKeysView.java          # Task 7 (linha de teclas extras)
│       │   │   ├── SettingsStore.java          # Task 8 (interface + PrefsSettingsStore)
│       │   │   ├── TermBridge.java             # Task 10 (decisão RUNTIME/FALLBACK)
│       │   │   └── AppDirs.java                # Task 10 (mkdirs de layout)
│       │   ├── java/.../TermBridgeTest... etc  # testes JUnit espelhados em src/test/
│       │   └── assets/runtime-arm64.zip        # gerado pela Task 9 (gitignored)
│       └── test/java/dev/opencode/terminal/    # JUnit 4 (Tasks 3,4,7,8,10)
├── vendor/
│   ├── emulatorview/                           # Task 6 (módulo gradle :emulatorview)
│   ├── libtermexec/                            # Task 5 (JNI PTY, módulo ou srcDir)
│   ├── LICENSE-jackpal.txt + NOTICE            # Tasks 5/6
├── scripts/fetch-runtime.sh                    # Task 9 (pins + sha256 + reempacota)
├── tests/ansi-stress.sh                        # Task 6 (gerador de sequências VT)
├── avd-setup.sh                                # modifica na Task 11 (arm64 opcional)
├── build.sh                                    # entrypoint de APK (sem mudança)
└── docs/
    ├── superpowers/specs/2026-10-27-...md      # spec (já existe)
    ├── superpowers/plans/2026-10-27-...md      # este plano
    └── evidence/*.png                          # evidências de tela por tarefa
```

---

### Task 1: Baseline git do repositório

**Files:**
- Create: `.gitignore`
- Modify: nenhum (repo novo)

**Interfaces:**
- Produces: repo com 1 commit; todos os passos seguintes dependem de `git add/commit` funcionando.

- [ ] **Step 1: Criar `.gitignore`**

Conteúdo: `build/`, `.gradle/`, `local.properties`, `*.log`, `.idea/`, `*.iml`,
`app/src/main/assets/runtime-arm64.zip`.

- [ ] **Step 2: Init e commit baseline**

```bash
cd /home/HyprKlavier/opencode-android
git init
git add -A
git commit -m "chore: baseline scaffold do toolchain + design doc"
```

- [ ] **Step 3: Verificar**

Run: `git status --porcelain && git log --oneline`
Expected: saída vazia do status; 1 commit listado.

---

### Task 2: Re-scaffold do app para "OpenCode Terminal"

**Files:**
- Modify: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Move: `app/src/main/java/dev/opencode/verify/MainActivity.java` → `app/src/main/java/dev/opencode/terminal/MainActivity.java` (pacote `dev.opencode.terminal`)
- Delete: `app/src/main/java/dev/opencode/verify/` (resto do pacote antigo)

**Interfaces:**
- Consumes: baseline da Task 1.
- Produces: `applicationId dev.opencode.terminal` instalável nos dois ABIs; MainActivity exibe label + `Build.SUPPORTED_ABIS` (placeholder reescrito na Task 6).

- [ ] **Step 1: Editar `app/build.gradle.kts`**

Valores exatos: `namespace = "dev.opencode.terminal"`, `applicationId = "dev.opencode.terminal"`,
`minSdk = 28` (hoje é 26 — subir), manter `compileSdk/targetSdk = 36` e Java 17,
`defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } }`,
`ndkVersion = "28.1.13356709"` (NDK é instalado na Task 5; declarar antes não quebra builds sem JNI — só passa a ser usado quando houver `externalNativeBuild`; se o AGP reclamar por ausência do NDK nesta etapa, adicionar o `ndkVersion` só na Task 5).

- [ ] **Step 2: Mover MainActivity + atualizar Manifest**

Pacote `dev.opencode.terminal`; label `"OpenCode Terminal"`; adicionar
`<uses-permission android:name="android.permission.INTERNET"/>` (normal, auto-concedida).
Conteúdo do MainActivity: `TextView` com "OpenCode Terminal" + `Build.SUPPORTED_ABIS` (placeholder).

- [ ] **Step 3: Buildar e instalar no AVD existente**

```bash
./build.sh
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n dev.opencode.terminal/.MainActivity
adb -s emulator-5554 exec-out screencap -p > docs/evidence/task2-launch.png
```

Expected: `BUILD SUCCESSFUL`, `Success` do install, screenshot com "OpenCode Terminal" e `x86_64` na lista de ABIs.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: re-scaffold como OpenCode Terminal (minSdk 28, 2 ABIs)"
```

---

### Task 3: RuntimeInstaller — extração de zip com marker de versão (TDD)

**Files:**
- Create: `app/src/main/java/dev/opencode/terminal/RuntimeInstaller.java`
- Test: `app/src/test/java/dev/opencode/terminal/RuntimeInstallerTest.java`
- Modify: `app/build.gradle.kts` (adicionar `testImplementation("junit:junit:4.13.2")`)

**Interfaces:**
- Produces:
  - `interface ProgressListener { void onProgress(long bytesExtracted, long totalBytes); }`
  - `enum InstallResult { EXTRACTED, UPTODATE }`
  - `RuntimeInstaller.install(File baseDir, InputStream zip, long totalBytes, String version, ProgressListener listener): InstallResult`
  - Marker: `baseDir/.extracted_marker` contendo `version` (texto puro).
- Comportamento pinado: se marker == version → `UPTODATE`, nada é extraído e `listener` não é chamado. Caso contrário extrai todos os entries (cria dirs pai, sobrescreve existentes), grava marker **só no fim**; `IOException` propaga sem marker. Consumido pela Task 10.

- [ ] **Step 1: Escrever os testes falhando**

```java
@Test public void freshInstallExtractsAndWritesMarker() throws Exception { ... }
@Test public void secondInstallSameVersionIsUpToDate() throws Exception { ... }
@Test public void interruptedExtractionLeavesNoMarker() { ... }
@Test public void rerunAfterFailureOverwritesPartialFiles() throws Exception { ... }
```

(1) zip fixture pequeno em `TemporaryFolder` com 2 entries → `EXTRACTED`, arquivos com conteúdo íntegro, marker = version, listener chamado com total correto. (2) segunda chamada → `UPTODATE` e listener nunca chamado. (3) `InputStream` que lança `IOException` após 4 bytes → `IOException` propagada e marker inexistente. (4) após (3), chamada com stream saudável → `EXTRACTED` e conteúdo final íntegro.

- [ ] **Step 2: Rodar e ver falhar**

Run: `export JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21 ANDROID_HOME=/home/HyprKlavier/android-sdk && export PATH="$JAVA_HOME/bin:/home/HyprKlavier/.local/opt/gradle/bin:$PATH" && gradle :app:testDebugUnitTest --tests '*RuntimeInstaller*'`
Expected: FAIL (classe inexistente).

- [ ] **Step 3: Implementar `RuntimeInstaller`**

Extração com `ZipInputStream`; marker escrito via arquivo temporário + rename. Sem wiping de `baseDir` (contém `home/` do usuário) — só sobrescreve entries do zip.

- [ ] **Step 4: Rodar e ver passar**

Run: mesmo comando do Step 2.
Expected: `BUILD SUCCESSFUL`, 4 testes verdes.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: RuntimeInstaller com marker de versão (TDD)"
```

---

### Task 4: EnvBuilder — mapa de ambiente do spawn (TDD)

**Files:**
- Create: `app/src/main/java/dev/opencode/terminal/EnvBuilder.java`
- Test: `app/src/test/java/dev/opencode/terminal/EnvBuilderTest.java`

**Interfaces:**
- Produces: `static Map<String,String> EnvBuilder.build(File filesDir, String provider, String apiKey)`
- Entradas exatas do mapa (pinadas pelo spec §5.3; testes fixam todos os valores):
  `HOME=filesDir/home`, `PREFIX=filesDir/prefix`, `TMPDIR=filesDir/tmp`,
  `PATH=filesDir/bin:/system/bin`, `SHELL=/system/bin/sh`, `TERM=xterm-256color`,
  `LANG=C.UTF-8`, `XDG_CONFIG_HOME=HOME/.config`, `XDG_DATA_HOME=HOME/.local/share`,
  `XDG_CACHE_HOME=HOME/.cache`, `OPENCODE_DISABLE_AUTOUPDATE=true`.
- Key: se `apiKey` vazio/nulo → nenhuma var de key. Mapeamento: `anthropic→ANTHROPIC_API_KEY`,
  `openai→OPENAI_API_KEY`, `google→GEMINI_API_KEY`, `openrouter→OPENROUTER_API_KEY`;
  provider desconhecido → nenhuma var. Consumido pelas Tasks 6 e 10.

- [ ] **Step 1: Testes falhando**

```java
@Test public void baseEnvHasExactValues() { ... }        // todos os valores acima
@Test public void anthropicKeyExportsAnthropicVar() { ... }
@Test public void nullKeyExportsNoKeyVars() { ... }
@Test public void unknownProviderExportsNoKeyVars() { ... }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `gradle :app:testDebugUnitTest --tests '*EnvBuilder*'`
Expected: FAIL.

- [ ] **Step 3: Implementar `EnvBuilder.build`**

Corpo determinístico pelos pins acima (só monta o `HashMap`; não cria diretórios — isso é `AppDirs` na Task 10).

- [ ] **Step 4: Rodar e ver passar**

Run: mesmo comando. Expected: 4 testes verdes.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: EnvBuilder com env do spawn fixado (TDD)"
```

---

### Task 5: NDK + vendor do libtermexec (PTY JNI compilando para os 2 ABIs)

**Files:**
- Modify: `app/build.gradle.kts` (`ndkVersion`, `externalNativeBuild`)
- Create: `vendor/libtermexec/**` (cópia de `jackpal/Android-Terminal-Emulator`, commit upstream registrado em `vendor/libtermexec/VENDORED.md`), `vendor/LICENSE-jackpal.txt`, `vendor/NOTICE`

**Interfaces:**
- Consumes: NDK `28.1.13356709`.
- Produces: API Java de spawn em PTY — usar a API do módulo vendorizado como está, se ela expuser master fd + pid; senão criar `dev.opencode.terminal.Pty.spawn(String[] argv, String[] envp, String cwd): PtyProcess` com `PtyProcess { public int masterFd; public int pid; }` envolvendo o C do libtermexec. Registrar em `VENDORED.md` qual caminho foi usado. Consumido pela Task 6.

- [ ] **Step 1: Instalar o NDK (tmux)**

```bash
tmux new-session -d -s ndk 'export JAVA_HOME=/home/HyprKlavier/.local/opt/jdk-21 ANDROID_HOME=/home/HyprKlavier/android-sdk; export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"; yes | sdkmanager "ndk;28.1.13356709" > /tmp/opencode/ndk-install.log 2>&1'
```

Check: `sdkmanager --list_installed | rg 'ndk;28.1.13356709'` → presente (monitorar `tmux attach -t ndk`).

- [ ] **Step 2: Vendorizar**

`git clone --depth 1 https://github.com/jackpal/Android-Terminal-Emulator /tmp/jackpal`
→ copiar o módulo do PTY para `vendor/libtermexec/`, copiar `LICENSE` (Apache-2.0) para
`vendor/LICENSE-jackpal.txt` + criar `vendor/NOTICE` creditando jackpal, e anotar o hash do
commit clonado em `vendor/libtermexec/VENDORED.md`.

- [ ] **Step 3: Integrar ao build**

`settings.gradle.kts`/`app/build.gradle.kts`: módulo gradle (ou srcDir) apontando para
`vendor/libtermexec`, com `externalNativeBuild` (ndkBuild se vier `Android.mk`; CMake/clang
se a estrutura pedir — decisão do builder, registrada em `VENDORED.md`). Compilar para
`arm64-v8a` e `x86_64`.

- [ ] **Step 4: Verificar build + `.so` nos dois ABIs**

Run: `./build.sh && unzip -l app/build/outputs/apk/debug/app-debug.apk | rg 'lib/(arm64-v8a|x86_64)/'`
Expected: `BUILD SUCCESSFUL`; a saída lista `.so` do PTY em **ambos** os diretórios.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: vendor libtermexec (Apache-2.0) + NDK 28.1 p/ 2 ABIs"
```

---

### Task 6: Terminal funcional no AVD — vendor emulatorview + shell + stress ANSI (= spec T1)

**Files:**
- Create: `vendor/emulatorview/**` (+ LICENSE/NOTICE já da Task 5), `vendor/emulatorview/build.gradle.kts`
- Modify: `settings.gradle.kts` (`include(":emulatorview")`), `app/build.gradle.kts` (dependência `implementation(project(":emulatorview"))`), `app/src/main/java/dev/opencode/terminal/MainActivity.java`
- Create: `app/src/main/java/dev/opencode/terminal/TermController.java`, `tests/ansi-stress.sh`

**Interfaces:**
- Consumes: Pty API (Task 5), `EnvBuilder.build(filesDir, null, null)` (Task 4), `TermSession(InputStream, OutputStream)` e `EmulatorView` do vendor (ler as fontes vendorizadas para confirmar os construtores antes de codar).
- Produces: `TermController` — `TermController(Activity activity, ViewGroup container)`; `start(String[] argv, Map<String,String> env, File cwd)` (cria PTY, spawna, liga TermSession+EmulatorView); `setOnSessionEndListener(Runnable)` (disparado quando o filho termina). Consumido pelas Tasks 7 e 10.
- Escrita de teclas: `ExtraKeysView` (Task 7) grava **bytes brutos** no OutputStream do PTY, não via `TermSession.write(String)`.

- [ ] **Step 1: Escrever `tests/ansi-stress.sh` (falha primeiro como artefato)**

Script que imprime, em ordem: `MARCADOR_INICIO`, `clear`, sequência alt-screen (`?1049h` … `?1049l`), fundo 256 cores (`[48;5;27m`), `MARCADOR_FIM`. `shellcheck tests/ansi-stress.sh` antes de commitar.

- [ ] **Step 2: Vendorizar emulatorview e construir `TermController`**

Copiar módulo `emulatorview` (namespace `jackpal.androidterm.emulatorview`), incluir no
settings, implementar `TermController.start` com spawn de `"/system/bin/sh"` via PTY e env de
`EnvBuilder`. Overlay de fim: TextView oculto "Sessão encerrada" ligado ao `setOnSessionEndListener`.

- [ ] **Step 3: Ligar no MainActivity e buildar**

Run: `./build.sh` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Teste de device — shell interativo**

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n dev.opencode.terminal/.MainActivity
sleep 3; adb -s emulator-5554 exec-out screencap -p > docs/evidence/task6-shell.png
adb -s emulator-5554 shell input text 'echo\ OC_TERM_OK'; adb -s emulator-5554 shell input keyevent 66
sleep 1; adb -s emulator-5554 exec-out screencap -p > docs/evidence/task6-echo.png
```

Expected: screenshot 1 mostra prompt/shell; screenshot 2 contém `OC_TERM_OK`.

- [ ] **Step 5: Teste stress VT (Review Focus 5)**

Push do script para o AVD, execução dentro do terminal via `input text`, screencap
`docs/evidence/task6-ansi.png`. Expected: imagem mostra `MARCADOR_INICIO`/`MARCADOR_FIM`
e área limpa pós-clear (a inspeção visual da imagem é o assert).

- [ ] **Step 6: Teste de fim de sessão (Review Focus 4)**

`input text 'exit'` + Enter → screencap `docs/evidence/task6-exit.png`. Expected: overlay
"Sessão encerrada" visível; app não congela (reabrir volta ao estado inicial sem crash).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: terminal EmulatorView+PTY no AVD (stress ANSI e fim de sessão)"
```

---

### Task 7: ExtraKeysView + KeyMapper (TDD)

**Files:**
- Create: `app/src/main/java/dev/opencode/terminal/KeyMapper.java`, `app/src/main/java/dev/opencode/terminal/ExtraKeysView.java`
- Modify: `app/src/main/java/dev/opencode/terminal/TermController.java` (expor `OutputStream outputStream()` para as teclas escreverem bytes brutos)
- Test: `app/src/test/java/dev/opencode/terminal/KeyMapperTest.java`

**Interfaces:**
- Consumes: `TermController` (Task 6) — bytes brutos vão no OutputStream do PTY.
- Produces: `static byte[] KeyMapper.bytesFor(String key, boolean ctrlOn)`. Teclas: `"ESC"→{0x1b}`, `"TAB"→{0x09}`, `"LEFT/UP/RIGHT/DOWN"→{0x1b,'[',A|B|C|D}`, `"/"→{'/'}` (com `ctrlOn`: `c & 0x1f` para letras: `'c'→0x03`, `'x'→0x18`).
- `ExtraKeysView`: linha fixa acima do teclado — `Esc · Ctrl · Tab · ← ↑ ↓ → · /`; `Ctrl` é **one-shot**: armado, vale para a próxima tecla alfanumérica e desarma (estado no view, testado indiretamente; a função `bytesFor` é pura e totalmente testada).

- [ ] **Step 1: Testes falhando**

```java
@Test public void escIsSingleByte1b() { ... }
@Test public void leftArrowIsCsI() { ... }        // {0x1b,'[','D'}
@Test public void ctrlCIs0x03() { ... }
@Test public void ctrlOffLowercaseCIsAsciiC() { ... }
@Test public void tabIs0x09() { ... }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `gradle :app:testDebugUnitTest --tests '*KeyMapper*'` → Expected: FAIL.

- [ ] **Step 3: Implementar `KeyMapper` + `ExtraKeysView`**

`ExtraKeysView` infla botões, gerencia `ctrlArmed` (toggle + desarmar pós-disparo, troca de
cor do botão), chama `bytesFor` e escreve bytes no OutputStream do PTY (obtido do
`TermController` — expor `OutputStream outputStream()` nele).

- [ ] **Step 4: Rodar e ver passar**

Run: mesmo comando → Expected: 5 testes verdes.

- [ ] **Step 5: Teste de device — linha visível e operando**

Build+install+launch no AVD; screencap `docs/evidence/task7-extrakeys.png` mostra a linha;
tap no Ctrl (coordenadas via `uiautomator dump`) seguido de `c` num shell → vê-se `^C`
output no terminal. Expected: linha presente e Ctrl produzindo byte de controle.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: extra-keys row (Esc/Ctrl/setas) com KeyMapper testado"
```

---

### Task 8: SetupActivity + SettingsStore (chave do provider)

**Files:**
- Create: `app/src/main/java/dev/opencode/terminal/SettingsStore.java` (interface + `PrefsSettingsStore`), `app/src/main/java/dev/opencode/terminal/SetupActivity.java`
- Test: `app/src/test/java/dev/opencode/terminal/SettingsStoreTest.java`
- Modify: `app/src/main/java/dev/opencode/terminal/MainActivity.java` (gate: se `!setupDone()` → abrir SetupActivity antes do terminal), Manifest (registro da Activity)

**Interfaces:**
- Produces: `interface SettingsStore { String provider(); String apiKey(); boolean setupDone(); void save(String provider, String apiKey); void markSkipped(); }`
  - `PrefsSettingsStore(Context)`: SharedPreferences `"oc_settings"`, `MODE_PRIVATE`, chaves `provider`, `key`, `skipped`.
  - SetupActivity: spinner (`anthropic/openai/google/openrouter`) + campo de key + botões **Salvar** e **Pular** (Pular → `markSkipped()` → terminal).
- Consumido pela Task 10 (`TermBridge`/MainActivity passam `store.provider(), store.apiKey()` ao `EnvBuilder`).

- [ ] **Step 1: Teste falhando (store puro)**

Com um `SettingsStore` fake em memória: `save` → `setupDone()==true`, `provider()/apiKey()`
devolvem o salvo; `markSkipped` → `setupDone()==true` com `apiKey()==null`. (O `PrefsSettingsStore`
real é fino demais para unit test — validado no device no Step 5.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `gradle :app:testDebugUnitTest --tests '*SettingsStore*'` → Expected: FAIL.

- [ ] **Step 3: Implementar interface + fake usado no teste + `PrefsSettingsStore` + `SetupActivity` + gate no MainActivity**

- [ ] **Step 4: Rodar e ver passar**

Run: mesmo comando → Expected: verdes.

- [ ] **Step 5: Teste de device — fluxo e não-vazamento (Review Focus 3)**

`adb shell pm clear dev.opencode.terminal` → launch → screencap mostra Setup
(`docs/evidence/task8-setup.png`); digitar key **fake** (`sk-test-not-real-...`) via
`input text` + tap Salvar → terminal abre; então:

```bash
adb -s emulator-5554 logcat -d | rg -c 'sk-test-not-real' ; echo exit=$?
```

Expected: `0` (sem match, exit=1 do rg). Key fake no shared_prefs é esperada (privado do app).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: setup de provider/key com gate e sem vazamento em log"
```

---

### Task 9: fetch-runtime.sh — pipeline do asset pinado (runtime + ripgrep)

**Files:**
- Create: `scripts/fetch-runtime.sh`
- Modify: `.gitignore` (já cobre o asset)

**Interfaces:**
- Produces: `app/src/main/assets/runtime-arm64.zip` com exatamente 4 entries:
  `opencode/opencode2`, `opencode/opencode2.bin`, `opencode/libopentui.so`, `bin/rg`.
- Pins (constantes no script, com `sha256sum -c` obrigatório): URL+SHA256 da runtime e do
  ripgrep (Global Constraints). Extração do `.deb` via `bsdtar` (entry `data.tar.xz`), pegando
  o `rg` (caminho tipo `./data/data/com.termux/files/usr/bin/rg`).
- Cache: `${XDG_CACHE_HOME:-$HOME/.cache}/opencode-android-build/` (downloads não repetem).

- [ ] **Step 1: Escrever o script com `set -euo pipefail` + pins + asserts**

Asserts internos: (a) ambos os SHA256 conferem; (b) `opencode2.bin` extraído é ELF aarch64
(`readelf -h`); (c) `rg` extraído é ELF aarch64 **e** NEEDED só bionic (`readelf -d | rg 'libc.so.6'`
vazio); (d) reempacota os 4 entries na estrutura pinada.

- [ ] **Step 2: `shellcheck` antes de rodar**

Run: `shellcheck scripts/fetch-runtime.sh` → Expected: sem avisos.

- [ ] **Step 3: Rodar e validar saída**

Run: `bash scripts/fetch-runtime.sh && unzip -l app/src/main/assets/runtime-arm64.zip`
Expected: exatamente os 4 entries; script exit 0 (SHA256 conferem — já conferidos no PC nesta sessão).

- [ ] **Step 4: Segredos e ignorância do asset (Review Focus 3)**

Run: `git check-ignore app/src/main/assets/runtime-arm64.zip && gitleaks detect -v --log-opts=-n 20`
Expected: caminho listado pelo check-ignore; gitleaks sem achados.

- [ ] **Step 5: Commit**

```bash
git add scripts/fetch-runtime.sh && git commit -m "feat: fetch-runtime.sh com runtime+ripgrep pinados por SHA256"
```

---

### Task 10: TermBridge — fluxo completo (extração → decisão → spawn) no AVD x86_64

**Files:**
- Create: `app/src/main/java/dev/opencode/terminal/TermBridge.java`, `app/src/main/java/dev/opencode/terminal/AppDirs.java`
- Test: `app/src/test/java/dev/opencode/terminal/TermBridgeTest.java`
- Modify: `app/src/main/java/dev/opencode/terminal/MainActivity.java` (fluxo §5.3 completo)

**Interfaces:**
- Consumes: `RuntimeInstaller` (T3), `EnvBuilder` (T4), `TermController` (T6), `SettingsStore` (T8), asset da T9.
- Produces:
  - `TermBridge.planFor(String[] supportedAbis, boolean wrapperPresent): TermBridge.Plan` com `enum Mode { RUNTIME, FALLBACK }`, `Plan { Mode mode; String reason; }`.
    - arm64-v8a ∈ abis **e** wrapperPresent → `RUNTIME` (`reason="ok"`)
    - senão → `FALLBACK` (`reason="arm64_only"` se ABI incompatible; `"missing_runtime"` se wrapper ausente)
  - `AppDirs.ensure(File filesDir): void` — cria `home/.config`, `home/.local/share`, `home/.cache`, `prefix`, `tmp`, `bin`.
- Fluxo MainActivity pinado (spec §5.3): `setup gate (T8)` → `AppDirs.ensure` → `RuntimeInstaller.install(files, asset, tamanhoAsset, versionName, listener)` com diálogo de progresso (UPTODATE pula) → `planFor` → RUNTIME: `start(new String[]{"sh", files/opencode/opencode2}, env)`; FALLBACK: `start(new String[]{"/system/bin/sh}, env)` + banner TextView com `Plan.reason`.

- [ ] **Step 1: Testes falhando**

```java
@Test public void arm64WithWrapperIsRuntime() { ... }
@Test public void x86OnlyIsFallbackArm64Only() { ... }
@Test public void arm64MissingWrapperIsFallbackMissingRuntime() { ... }
@Test public void appDirsEnsuresAllDirs() { ... }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `gradle :app:testDebugUnitTest --tests '*TermBridge*'` → Expected: FAIL.

- [ ] **Step 3: Implementar `TermBridge`, `AppDirs` e o fluxo no MainActivity**

- [ ] **Step 4: Rodar e ver passar**

Run: mesmo comando → Expected: 4 testes verdes.

- [ ] **Step 5: Teste de device — fluxo de extração + fallback (Review Focus 1 e 2)**

```bash
./build.sh
adb -s emulator-5554 shell pm clear dev.opencode.terminal
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n dev.opencode.terminal/.MainActivity
```

Se o gate de setup aparecer → tap em **Pular** (bounds via `uiautomator dump`).
Screencap durante a extração (`docs/evidence/task10-extract.png` — barra de progresso) e depois
(`docs/evidence/task10-fallback.png`). Asserts: (a) progresso visível no primeiro launch;
(b) banner com motivo de fallback visível — `adb shell uiautomator dump` contém "arm64";
(c) shell abaixo do banner responde a `echo OC_BRIDGE_OK`; (d) marker existe:
`adb shell run-as dev.opencode.terminal ls files/.extracted_marker`.
Relançar → screencap sem barra de progresso (UPTODATE).

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: fluxo extração→decisão→spawn com fallback visível"
```

---

### Task 11: T2 — AVD arm64 (TCG) com a TUI real + turno LLM (= gate dos riscos R1/R2)

**Files:**
- Modify: `avd-setup.sh` (aceitar `./avd-setup.sh x86_64|arm64`, default `x86_64`; para `arm64`: IMG_PKG `system-images;android-36;google_apis;arm64-v8a`, AVD_NAME `oc_arm64`)
- Create: `docs/evidence/t2-*.png`

**Interfaces:**
- Consumes: APK completo (T1–T10), asset da T9.
- Produces: evidência de aceite parcial: TUI real renderizando + turno LLM completo no emulador.

- [ ] **Step 1: Imagem arm64 + AVD (script parametrizado)**

Edit `avd-setup.sh` (shellcheck **antes**: `shellcheck avd-setup.sh && bash avd-setup.sh arm64`).
Expected: `AVD_SETUP_CONCLUIDO` e `avdmanager list avd` mostra `oc_arm64` (imagem já confirmada: rev 7).

- [ ] **Step 2: Boot em TCG (tmux, lento — até ~20 min)**

```bash
tmux new-session -d -s emu64 'emulator -avd oc_arm64 -gpu swiftshader_indirect -no-snapshot -no-boot-anim'
```

Poll: `adb -s emulator-5556 wait-for-device` e loop de `adb shell getprop sys.boot_completed`
até `1` (timeout ~20 min; verificar porta — se `emulator-5554` ocupado, usar `adb devices`).

- [ ] **Step 3: Instalar e observar extração + boot da TUI (gates R1/R2)**

```bash
./build.sh && adb install -r app/build/outputs/apk/debug/app-debug.apk
# launch → (Pular se pedir setup) → screencaps em sequência:
```

Asserts: (a) extração completa (marker via `run-as`); (b) `adb logcat -d | rg -i 'linker|dlopen|CANNOT_LINK|CANNOT OPEN'` **vazio** (R2); (c) screencap `docs/evidence/t2-tui.png` mostra painel da TUI desenhado, não tela preta (R1).

- [ ] **Step 4: `rg` vivo dentro do app**

Run: `adb shell run-as dev.opencode.terminal sh -c 'files/bin/rg --version | head -1'`
Expected: linha de versão do ripgrep (bionic executa).

- [ ] **Step 5: Turno LLM real — key via arquivo temporário FORA do repo (aceite)**

Instrução ao usuário: criar `~/tmp/oc-avd-key` (conteúdo = só a key, `chmod 600`) — **fora do repo**.
Nada de key literal em comando ou log. Fluxo: launch do app → SetupActivity → tap no campo →
`adb shell input text "$(cat ~/tmp/oc-avd-key)"` (expansão acontece no shell local; o valor não
aparece no texto do comando registrado) → tap Salvar → digitar prompt de teste **que force o
agente a rodar um comando de shell** (ex.: "crie o arquivo t2.txt com 'oi' usando o shell e
depois liste o diretório") — isso exercita o `bash` tool do OpenCode sobre o mksh do Android
(verificação do risco R7) — via `input text`
→ Enter → aguardar (TCG é lento) → screencap `docs/evidence/t2-llm-turn.png` com resposta do modelo
e evidência do comando executado.

- [ ] **Step 6: Aceite + limpeza + evidência**

Expected: resposta visível = **turno LLM real verde** (critério do usuário, §5.9). Então:
`rm ~/tmp/oc-avd-key`; PNGs em `docs/evidence/`; commit das evidências + script:

```bash
git add -A && git commit -m "test: T2 arm64 AVD — TUI real + turno LLM (R1/R2 verdes)"
```

Se R1 ou R2 falharem → **parar aqui** e reportar (contingências no spec §6: patchear parser
ou jniLibs); não prosseguir para a Task 12 com o gate verde falso.

---

### Task 12: T3 — Galaxy S26+ real (aceitação final)

**Files:**
- Modify: nenhum código; `docs/evidence/t3-*.png` + registro final no log de checkpoints.

**Interfaces:**
- Consumes: Tasks 1–11 todas verdes (pré-condição dura).
- Produces: checklist M1–M6 assinado com evidências de tela do celular.

- [ ] **Step 1: Emparelhamento wireless (ações do usuário)**

Pedir ao usuário: Ativar Depuração sem fio no S26+ (Ajustes → Opções de desenvolvedor) →
`adb pair IP:port` com o código pareado → `adb connect`. Comandos usam o IP fornecido pelo usuário.

- [ ] **Step 2: Instalar o APK**

Run: `./build.sh && adb install -r app/build/outputs/apk/debug/app-debug.apk` → Expected: `Success`.

- [ ] **Step 3: Fluxo real no celular (sem adb para a key)**

Usuário abre o app → digita **a própria key** no Setup no aparelho → extração com progresso →
TUI abre → envia um prompt de teste → resposta do modelo. Evidências:
`docs/evidence/t3-setup.png`, `t3-tui.png`, `t3-llm-turn.png`.

- [ ] **Step 4: Checklist de aceite (spec M1–M6)**

Conferir: M1 app único abre na TUI ✓ (screencap); M2 sem rede pós-install para extrair ✓
(extração local; restart offline funciona); M3 teclado com Esc/Ctrl/setas utilizável ✓
(usar extra-keys num comando real); M4/M5 cumpridos por construção ✓; M6 notices no repo ✓
(`ls vendor/NOTICE`). Registrar resultado no checkpoint da sessão.

- [ ] **Step 5: Commit final**

```bash
git add -A && git commit -m "test: T3 Galaxy S26+ — aceitação final (M1-M6)"
```
