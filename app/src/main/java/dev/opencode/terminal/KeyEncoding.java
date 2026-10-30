package dev.opencode.terminal;

import java.nio.charset.StandardCharsets;

/**
 * Helper para codificação de teclas.
 */
public final class KeyEncoding {

    private KeyEncoding() {}

    /**
     * Retorna os bytes UTF-8 de um code point.
     */
    public static byte[] charBytes(int code) {
        if (code < 0) return null;
        if (code < 128) return new byte[]{(byte) code};
        return String.valueOf((char) code).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Retorna o byte de controle para um caractere.
     */
    public static byte[] ctrlByte(int code) {
        if ((code >= 65 && code <= 90) || (code >= 97 && code <= 122)) {
            return new byte[]{(byte) (code & 0x1f)};
        }
        return null;
    }

    /**
     * Retorna a sequência de bytes para teclas de seta.
     */
    public static byte[] arrowBytes(int code) {
        switch (code) {
            case -20: return new byte[]{0x1b, '[', 'D'};
            case -21: return new byte[]{0x1b, '[', 'A'};
            case -22: return new byte[]{0x1b, '[', 'B'};
            case -23: return new byte[]{0x1b, '[', 'C'};
            default: return null;
        }
    }

    /**
     * Retorna a sequência de bytes para teclas de ação.
     */
    public static byte[] actionBytes(int code) {
        switch (code) {
            case -5: return new byte[]{0x7f};
            case -4:
            case 10: return new byte[]{0x0d};
            case 9: return new byte[]{0x09};
            case 27: return new byte[]{0x1b};
            case 32: return new byte[]{0x20};
            default: return null;
        }
    }
}
