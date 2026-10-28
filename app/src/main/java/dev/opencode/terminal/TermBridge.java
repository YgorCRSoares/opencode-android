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
     * Decisão pura: o runtime arm64 só é usado quando {@code arm64-v8a} é a
     * <strong>ABI primária</strong> (primeiro item de {@code Build.SUPPORTED_ABIS})
     * <strong>e</strong> o runtime nativo está presente no diretório de bibliotecas.
     *
     * <p>No AVD x86_64, {@code arm64-v8a} aparece como ABI secundária através do
     * {@code libndk_translation}. O {@code execve} feito pelo processo do app
     * (uid {@code untrusted_app}) é negado com {@code EACCES}; por isso não
     * tratamos ABI secundária como runtime.
     *
     * @param supportedAbis   ABIs reportadas pelo sistema (ex.: {@code Build.SUPPORTED_ABIS})
     * @param runtimePresent  {@code true} se {@code nativeLibraryDir/libopencode2.so}
     *                        e {@code libopentui.so} existem
     * @return plano com modo e motivo
     */
    public static Plan planFor(String[] supportedAbis, boolean runtimePresent) {
        boolean arm64 = supportedAbis != null
                && supportedAbis.length > 0
                && "arm64-v8a".equals(supportedAbis[0]);
        if (arm64 && runtimePresent) {
            return new Plan(Mode.RUNTIME, "ok");
        }
        if (!arm64) {
            return new Plan(Mode.FALLBACK, "arm64_only");
        }
        return new Plan(Mode.FALLBACK, "missing_runtime");
    }
}
