package dev.opencode.terminal;

public final class AccentMap {
    private AccentMap() {
        // Private constructor to prevent instantiation
    }

    public static String variantsFor(char base, boolean upper) {
        // Normalize base to lowercase
        char lowerBase = Character.toLowerCase(base);

        // Map of base characters to their accented variants
        switch (lowerBase) {
            case 'a':
                return upper ? "ÁÀÂÃÄ" : "áàâãä";
            case 'e':
                return upper ? "ÉÊÈË" : "éêèë";
            case 'i':
                return upper ? "ÍÌÎÏ" : "íìîï";
            case 'o':
                return upper ? "ÓÔÕÒÖ" : "óôõòö";
            case 'u':
                return upper ? "ÚÛÙÜ" : "úûùü";
            case 'c':
                return upper ? "Ç" : "ç";
            case 'n':
                return upper ? "Ñ" : "ñ";
            default:
                return "";
        }
    }
}