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

O OpenCode Terminal pode ser instalado de duas formas:

### Opção 1: Build a partir do código (recomendado para desenvolvedores)

```bash
# 1. Clonar o repositório
git clone https://github.com/YgorCRSoares/opencode-android.git
cd opencode-android

# 2. Build e instalar
./build.sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

### Opção 2: Download direto do APK (mais rápido para usuários)

Se você não quer compilar, basta baixar o APK mais recente na [Release do GitHub](https://github.com/YgorCRSoares/opencode-android/releases):

```bash
# Download e instalação direta:
adb install -r https://github.com/YgorCRSoares/opencode-android/releases/latest/download/opencode-terminal.apk
```

> **Nota**: O APK da release já está assinado com a chave de debug, permitindo `install -r` sobre builds anteriores sem perder dados.

### Pré-requisitos

- Dispositivo Android com Termux ou ambiente similar
- ADB (Android Debug Bridge) para instalação
- Para a Opção 1: git e ambiente de build Android

### Primeiro Uso

- Ao abrir o app, ele inicia direto na TUI (sem telas de setup obrigatórias)
- A primeira execução pode demorar um pouco enquanto o runtime é extraído
- Acesse comandos com `Ctrl+P` para ver a palette de comandos

## 🔄 Atualização do Aplicativo

Para manter o app sempre na última versão possível:

```bash
# 1. Atualizar runtime (verifica automaticamente v1.0.3-rc3)
bash scripts/fetch-runtime.sh

# 2. Rebuild e reinstalar (ou baixar nova release)
./build.sh
adb install -r app/build/outputs/apk/release/app-release.apk
# Ou simplesmente baixar a nova release do GitHub
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

## 🤝 Contribuindo

Quer ajudar a melhorar o OpenCode Terminal? Estamos abertos a contribuições! 🎉

### Como Contribuir

1. **Fork o repositório**  
   ```bash
   git fork https://github.com/YgorCRSoares/opencode-android.git
   ```

2. **Crie uma branch para sua feature**  
   ```bash
   git checkout -b feature/nova-funcionalidade
   ```

3. **Faça suas alterações e testes**  
   - Siga o estilo de código existente
   - Atualize o README se necessário
   - Teste em dispositivo real sempre que possível

4. **Commit suas mudanças**  
   ```bash
   git commit -m "feat: descrição da funcionalidade"
   ```

5. **Envie para o fork**  
   ```bash
   git push origin feature/nova-funcionalidade
   ```

6. **Abra um Pull Request**  
   - Preencha o template do PR
   - Descreva o problema/funcionalidade
   - Inclua screenshots se aplicável

### Áreas para Contribuição

Sinta-se livre para trabalhar em qualquer uma das seguintes melhorias:

- **Interface do Usuário**: Melhorias visuais, novos componentes TUI, ajustes de UX
- **Funcionalidades**: Novos comandos, integrações, melhorias de workflow
- **Performance**: Otimização do runtime, redução de tamanho do APK
- **Documentação**: Atualização do README, exemplos, tutoriais
- **Correções de Bug**: Problemas reportados, melhoria de estabilidade
- **Build/CI**: Melhorias no `build.sh`, `fetch-runtime.sh`, automação
- **Testing**: Testes unitários, testes em dispositivo, cobertura de cobertura

### Guia de Commits

Use mensagens de commit claras e descritivas:

- `feat: nova funcionalidade`
- `fix: correção de bug`
- `docs: atualização de documentação`
- `refactor: reorganização de código`
- `test: adição de testes`

### Setup de Desenvolvimento

Para trabalhar localmente:

1. **Clonar o repositório**
   ```bash
   git clone https://github.com/YgorCRSoares/opencode-android.git
   cd opencode-android
   ```

2. **Ambiente Android**
   - Android Studio instalado
   - `ANDROID_HOME` e `JAVA_HOME` configurados
   - Dispositivo ou emulador com Android 8.0+

3. **Build e testes**
   ```bash
   ./build.sh          # build release
   adb install -r      # instalar no dispositivo
   # Testar funcionalidades manualmente
   ```

4. **Lint e qualidade**
   ```bash
   # Verificar problemas comuns
   ./gradlew lint 2>/dev/null || echo "Lint disponível via Android Studio"
   ```

### Questions?

- Abra uma issue no GitHub para perguntar ou discutir ideias
- Entre em contato pela discussão do repositório
- Confira a pasta `docs/` para mais instruções

Obrigado por contribuir para tornar o OpenCode Terminal melhor! 🚀

## 📄 Licença

Projeto mantido por HyprKlavier. O ícone oficial usa o SVG do [anomalyco/opencode](https://github.com/anomalyco/opencode).

---

**OpenCode Terminal** - Sua interface OpenCode na palma da mão.