# Design: seleção de texto por toque no terminal (Galaxy S26+)

## Contexto e intenção

O terminal hoje aceita toque curto como clique de mouse na TUI, mas não permite
selecionar texto. O `EmulatorView` tem uma base antiga de seleção, porém ela está
inacessível: o long-press chama `showContextMenu()` sem itens, ninguém chama
`toggleSelectingText()`, o `ACTION_UP` copia e sai imediatamente e não há
auto-scroll durante o arrasto.

O objetivo é transformar o terminal inteiro em uma superfície touch-first:

- toque curto continua clicando na TUI;
- toque longo inicia seleção na célula tocada;
- arrastar para cima/baixo estende a seleção;
- ao soltar, aparece uma barra com **Copiar** e **Cancelar**;
- o texto selecionado preserva quebras de linha e indentação;
- nada abre o Gboard automaticamente.

## Objetivos

1. Selecionar texto em qualquer célula visível do terminal, não apenas nas abas.
2. Fazer seleção linear do ponto inicial ao final, mesmo atravessando linhas.
3. Rolar automaticamente quando o dedo chega perto do topo ou do rodapé.
4. Manter o realce visível enquanto a barra de ações está aberta.
5. Copiar somente após confirmação explícita em **Copiar**.
6. Cancelar a seleção sem alterar o clipboard.
7. Não quebrar o clique de mouse nas abas, o scroll da TUI nem o teclado nativo.

## Não-objetivos

- Alças de seleção arrastáveis depois do release.
- Seleção por palavra, linha inteira ou coluna.
- Menu de copiar/colar completo, compartilhamento ou histórico de clipboard.
- Alterar o teclado, o cabeçalho ou o layout da TUI nesta tarefa.

## Restrições

- Android 17/API 36 no S26+, sem bibliotecas externas.
- O código de terminal fica em `vendor/emulatorview/`; a implementação deve
  continuar autossuficiente e não depender de novos recursos do app.
- `TermController` já liga `setMouseTracking(true)` e desliga o soft input
  automático. A seleção precisa coexistir com esse estado.
- O `MainActivity` está sendo alterado pelo agente de UI; esta tarefa não deve
  editar `MainActivity`, `styles.xml`, `AndroidManifest.xml` nem
  `TerminalKeyboardView`.
- Nenhum texto selecionado, credencial ou conteúdo de clipboard deve ser
  registrado em log.
- O clipboard só é alterado quando o usuário toca em **Copiar**.

## Abordagens consideradas

### A — Barra e seleção dentro do `EmulatorView` (escolhida)

Implementar o estado de seleção, o realce, o auto-scroll e uma `PopupWindow`
não focável dentro do próprio `EmulatorView`. O clipboard usa o
`ClipboardManagerCompat` existente.

- Prós: menor escopo; não conflita com o agente de UI; funciona em qualquer
  host do `EmulatorView`; mantém a lógica de coordenadas perto do renderer.
- Contras: a barra fica com aparência própria do terminal, sem Material; o
  `PopupWindow` precisa ser cuidadosamente posicionado acima do teclado.

### B — Barra no `MainActivity` com callback do `EmulatorView`

O `EmulatorView` expõe `SelectionListener`; o `MainActivity` desenha a barra e
controla o clipboard.

- Prós: visual mais integrado ao app; facilita evoluir para compartilhar,
  “selecionar tudo” e ações extras.
- Contras: mexe em `MainActivity` e `TermController` enquanto outro agente os
  edita; aumenta o acoplamento e o risco de conflito.

### C — ActionMode nativo

Usar o `ActionMode` do Android com a ação “Copiar”.

- Prós: menos código de barra; acessibilidade nativa.
- Contras: posicionamento e aparência imprevisíveis sobre o terminal; o ciclo
  de vida do `ActionMode` pode roubar foco do `EmulatorView`; difícil manter a
  seleção viva até a confirmação.

**Decisão:** A. A barra será escura, pequena e não focável, ancorada na parte
inferior do terminal, acima do teclado nativo.

## Comportamento detalhado

### Toque curto

- Mantém o comportamento atual: `onSingleTapUp` envia press/release de mouse
  quando o mouse tracking está ativo.
- Não inicia seleção e não abre o Gboard.

### Toque longo

- `onLongPress` deixa de abrir o menu de contexto vazio.
- converte a coordenada do toque na célula `(col, row)`;
- entra em modo de seleção;
- define âncora e extremidade inicial na mesma célula;
- não envia clique de mouse para a TUI;
- mostra o realce inicial;
- após 500 ms, o modo fica ativo até copiar/cancelar.

### Arrastar

- A extremidade segue a célula sob o dedo.
- A seleção é linear: primeira linha começa na coluna inicial; linhas
  intermediárias são inteiras; última linha termina na coluna final.
- Se o arrasto for para cima, a ordem é normalizada por linha, preservando as
  colunas corretas de início e fim.
- Ao chegar a menos de ~1,5 linha do topo ou do rodapé, o terminal rola uma
  linha por tick (~50 ms) e a extremidade continua acompanhando o dedo.
- O realce é redesenhado com `invalidate()`.

### Soltar o dedo

- O modo de seleção continua ativo e o realce permanece.
- Se o texto selecionado não estiver vazio, mostra a barra:
  - **Copiar**
  - **Cancelar**
- Se estiver vazio ou só tiver espaços, não mostra a barra e limpa a seleção.

### Copiar

- Extrai o texto selecionado **sem `.trim()`**, preservando indentação.
- Remove apenas células vazias à direita de cada linha, comportamento já
  realizado pela extração do transcript.
- Usa `ClipboardManagerCompat` para gravar no clipboard.
- Mostra um `Toast` curto: “Texto copiado”.
- Fecha a barra e limpa o realce.
- Não registra o conteúdo em log.

### Cancelar

- Fecha a barra, limpa o realce e não altera o clipboard.
- Um toque fora da barra ou o botão Voltar também podem cancelar, se isso for
  compatível com o foco atual do `EmulatorView`.

### Durante atualizações da TUI

- O modo de seleção permanece ativo.
- O callback de update continua ajustando as coordenadas quando o terminal
  rolar, como já faz hoje.
- Se a seleção ficar inválida após uma atualização, ela é limpa e a barra é
  fechada, sem copiar nada.

## Extração linear

O `TranscriptScreen.internalGetTranscriptText` já foi escrito para uma seleção
linear quando recebe `selY1 <= selY2`: a primeira linha usa `selX1`, a última
usa `selX2` e as linhas do meio usam a largura inteira.

O problema está em `EmulatorView.onTouchEventWhileSelectingText`, que hoje
normaliza para `minx/maxx` e transforma a seleção em um bloco. A correção é
manter a âncora e a extremidade atuais, normalizando apenas a ordem vertical
(e trocando as colunas correspondentes quando a ordem for invertida).

## Arquivos previstos

- `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/EmulatorView.java`
  - estado de seleção e âncora;
  - long-press;
  - arrasto linear e auto-scroll;
  - barra de ações e clipboard;
  - limpeza/restauração do mouse tracking.
- `vendor/emulatorview/src/main/java/jackpal/androidterm/emulatorview/SelectionRange.java`
  - normalização pura da âncora e da extremidade para seleção linear.
- `app/src/test/java/dev/opencode/terminal/SelectionRangeTest.java`
  - testes unitários de direção e ordem da seleção.
- Nenhuma alteração em `MainActivity`, `TermController`, `styles.xml`,
  `AndroidManifest.xml` ou no teclado.

## Critérios de aceite

1. Tocar em uma aba da TUI continua trocando de sessão.
2. Tocar em qualquer outra região continua enviando clique para a TUI.
3. Toque longo em uma linha de código inicia a seleção na célula correta.
4. Arrastar para baixo seleciona várias linhas; arrastar para cima seleciona na
   direção inversa sem inverter o texto.
5. Arrastar até o topo/rodapé rola o terminal e estende a seleção.
6. Ao soltar, aparece a barra com **Copiar** e **Cancelar**.
7. **Copiar** coloca no clipboard exatamente o texto selecionado, com
   indentação e quebras de linha; um `Toast` confirma.
8. **Cancelar** não altera o clipboard.
9. O Gboard não abre sozinho; o teclado nativo continua visível.
10. Build passa e a seleção foi testada no S26+ com screenshot.

## Riscos

- Long-press compete com o `GestureDetector` e com o mouse tracking; o teste no
  aparelho é obrigatório.
- A TUI é viva; uma atualização durante a seleção pode deslocar o conteúdo.
- O `PopupWindow` pode ficar atrás do teclado nativo se a âncora for calculada
  em coordenadas de tela em vez de coordenadas da view.
- Auto-scroll agressivo pode dificultar selecionar uma linha específica; o
  limiar e o intervalo serão ajustáveis.
- A extração de texto com caracteres largos/Unicode precisa ser validada com
  acentos e blocos, mas não será alterada nesta tarefa.
