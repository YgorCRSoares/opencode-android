package dev.opencode.terminal;

/**
 * Decide, com base nas ABIs suportadas e na presença do wrapper de runtime,
 * se o app spawna o binário nativo do OpenCode (RUNTIME) ou cai no shell
 * de fallback (FALLBACK).
 */
public final class TermBridge {

    /** Modo de operação escolhido pelo {@link #planFor}. */
    public enum Mode {
        RUNTIME,
        FALLBACK
    }

    /** Resultado de {@link #planFor}. */
    public static final class Plan {
        public final Mode mode;
        public final String reason;

        public Plan(Mode mode, String reason) {
            this.mode = mode;
            this.reason = reason;
        }
    }

    private TermBridge() {
    }

    /**
     * Decisão pura: {@code arm64-v8a} precisa estar entre as ABIs suportadas
     * <strong>e</strong> o wrapper de runtime precisa estar presente.
     *
     * @param supportedAbis   ABIs reportadas pelo sistema (ex.: {@code Build.SUPPORTED_ABIS})
     * @param wrapperPresent  {@code true} se o binário {@code opencode/opencode2} (ou wrapper)
     *                        existe em disco após a extração
     * @return plano com modo e motivo
     */
    public static Plan planFor(String[] supportedAbis, boolean wrapperPresent) {
        boolean arm64 = false;
        if (supportedAbis != null) {
            for (String abi : supportedAbis) {
                if ("arm64-v8a".equals(abi)) {
                    arm64 = true;
                    break;
                }
            }
        }
        if (arm64 && wrapperPresent) {
            return new Plan(Mode.RUNTIME, "ok");
        }
        if (!arm64) {
            return new Plan(Mode.FALLBACK, "arm64_only");
        }
        return new Plan(Mode.FALLBACK, "missing_runtime");
    }
}
