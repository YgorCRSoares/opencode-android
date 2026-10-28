Evidências da validação no PC — 2026-10-28

Runtime arm64 (qemu-user-static, PC):
  pc-tui-mini.txt          TUI "mini" renderizado em tmux
  pc-runtime-version.txt   opencode2 --version
  pc-llm-turn.txt          turno LLM contra mock OpenAI-compatible -> PONG, EXIT 0
  pc-rg.txt                ripgrep com libpcre2-8.so empacotada

UI Android (AVD x86_64, plano FALLBACK arm64_only):
  pc-setup-screen.png      formulário de Setup com insets corrigidos
  pc-setup-saved.png       SALVAR -> MainActivity/terminal
  pc-fallback-layout.png   terminal acima da linha ExtraKeys (sem IME)
  pc-fallback-echo.png     echo hello
  pc-fallback-ctrlc.png    Ctrl+C via Gboard (^C, prompt 130)
  pc-fallback-arrows.png   setas + X -> AXBC
  pc-setup-flow.txt        resultados de SALVAR/PULAR/BACK
  pc-terminal-ui.txt       KB, Ctrl, setas e layout da linha ExtraKeys
  pc-argv.txt              /system/bin/sh\0; sem NULL argv
  pc-plan.txt              install UPTODATE; plano FALLBACK arm64_only

Ainda não validado no PC: o runtime arm64 dentro do APK no aparelho (só via
qemu) e um turno LLM com provedor real (só contra o mock). Esses são os gates
da aceitação no Galaxy S26+; o APK está pronto para instalar quando o
pareamento de depuração sem fio for informado.
