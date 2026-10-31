# OpenCode Terminal - Android

A fully functional OpenCode TUI (Text User Interface) running natively on Android. Use OpenCode's terminal features directly from your Android device.

## 📱 Sobre o Aplicativo

OpenCode Terminal traz a interface de linha de comando do OpenCode para o seu celular ou tablet. Execute comandos, gere agents, execute skills e gerencie sessões totalmente pelo dispositivo móvel.

## ✅ Funcionalidades Principais

### Interface do Usuário
- **TUI completo**: Interface texto completa funcionando nativamente em Android
- **Linha de teclado**: Linha extra acima do teclado com `Esc Ctrl Alt Modo ← ↑ ↓ →` (fixa no teclado, desaparece quando ele se fecha)
- **Ciclo de modos**: Botão `Modo` substitui `Shift+Tab` para ciclar modos do OpenCode
- **Sem avisos de compatibilidade**: Aplica-se diretamente na TUI sem o diálogo "16 KB de página / app depurável"

### Recursos
- **Ícone oficial**: Logotipo "O" do OpenCode (anel quadrado), em 5 densidades de mipmap
- **Links no navegador**: Links abertos no navegador padrão do Android
- **Seleção e cópia**: Seleção de texto com barra `COPIAR` integrada
- **Controle por teclas**: Todas as teclas funcionam como no desktop (Ctrl+P, Shift+Tab, etc.)

## 🛠 Como Instalar

### Pré-requisitos
- Dispositivo Android com Termux ou ambiente similar
- ADB (Android Debug Bridge) para instalação inicial
- Chave SSH configurada no GitHub (opcional, para sync de chaves)

### Instalação via APK

1. **Build e instalar**:
   ```bash
   cd opencode-android
   ./build.sh
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

2. **Ou instalar a versão mais recente**:
   ```bash
   bash scripts/fetch-runtime.sh  # detecta última release automaticamente
   ./build.sh
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

### Primeiro Uso
- Ao abrir o app, ele inicia direto na TUI (sem telas de setup obrigatórias)
- A primeira execução pode demorar um pouco enquanto o runtime é extraído
- Acesse comandos com `Ctrl+P` para ver a palette de comandos

## 🔄 Atualização do Aplicativo

Para manter o app sempre na última versão possível:

```bash
# 1. Atualizar runtime (verifica automaticamente v1.0.3-rc3)
bash scripts/fetch-runtime.sh

# 2. Rebuild e reinstalar
./build.sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 📦 Informações do Release

- **Versão**: `v1.0.3-rc3` (fork `guysoft/opencode-termux`, base `2.0.0-android-termux.2`)
- **APK**: ~150 MB, disponível no repositório
- **Assinatura**: Build release assinado com chave de debug ( `install -r` seguro, sem perda de dados)
- **Icone**: OpenCode "O" oficial em 5 densidades (mdpi→xxxhdpi)

## 🛠 Comandos Úteis

No interior da TUI:
- `Ctrl+P` - Lista comandos disponíveis
- `Modo` - Cicla modos (antes era Shift+Tab)
- `Esc Ctrl Alt Modo ← ↑ ↓ →` - Linha de teclas fixa acima do IME
- Links - Abrem no navegador padrão
- `Ctrl+C` - Limpa input
- `Ctrl+Z` - Undo no input

## 📄 Licença

Projeto mantido por HyprKlavier. O ícone oficial usa o SVG do [anomalyco/opencode](https://github.com/anomalyco/opencode).

---

**OpenCode Terminal** - Sua interface OpenCode na palma da mão.