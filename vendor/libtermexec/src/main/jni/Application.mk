# 16 KB page-size compatibility (Android 15+ pode usar páginas de 16 KB).
# Alinha os segmentos LOAD do libjackpal-termexec2.so a 16 KB, evitando o
# aviso de compatibilidade que o Android exibe a apps depuráveis.
# As demais libs nativas são pré-compiladas (runtime opencode/termux) e não
# são re-linkadas aqui; o build release não exibe o aviso mesmo assim.
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
