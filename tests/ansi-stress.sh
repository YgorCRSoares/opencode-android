#!/bin/bash
# Teste de stress VT — Task 6 (spec T1).
#
# Ordem fixada pelo brief:
#   MARCADOR_INICIO → clear → alt-screen (?1049h … ?1049l) → fundo 256 cores
#   ([48;5;27m) → MARCADOR_FIM
#
# O "clear" preserva a linha 1 (MARCADOR_INICIO) e apaga as linhas de "lixo"
# abaixo dela: as linhas de lixo somem na imagem e o marcador permanece — é a
# prova visual de que a área foi limpa (ED0 a partir da linha 2).

set -euo pipefail

printf 'MARCADOR_INICIO\n'

# Conteúdo que o clear deve remover (prova visual da limpeza).
printf 'LIXO_1 (deve sumir no clear)\n'
printf 'LIXO_2 (deve sumir no clear)\n'
printf 'LIXO_3 (deve sumir no clear)\n'

# clear: cursor para a linha 2 e apaga daí até o fim da tela.
# A linha 1 (MARCADOR_INICIO) fica intacta.
printf '\033[2;1H\033[J'

# Alt-screen: entra (?1049h), imprime algo que só existe no buffer alternativo
# e sai (?1049l) — ao sair, o buffer normal (marcador + área limpa) é restaurado.
printf '\033[?1049h'
printf 'ALT_SCREEN_ATIVA (some ao sair do alt-screen)\n'
printf '\033[?1049l'

# Cursor garantido na linha 2 antes do resto (o emulatorview não restaura o
# cursor na saída do alt-screen).
printf '\033[2;1H'

# Fundo 256 cores (48;5;27 = azul) + reset.
printf '\033[48;5;27m Fundo 256 cores (48;5;27) \033[0m\n'

printf 'MARCADOR_FIM\n'
