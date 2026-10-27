package dev.opencode.terminal;

import java.nio.charset.StandardCharsets;

public final class KeyMapper {
    private KeyMapper() {}

    public static byte[] bytesFor(String key, boolean ctrlOn) {
        if (key == null || key.isEmpty()) {
            return new byte[0];
        }

        if ("ESC".equals(key)) {
            return new byte[] { 0x1b };
        }
        if ("TAB".equals(key)) {
            return new byte[] { 0x09 };
        }
        if ("LEFT".equals(key)) {
            return new byte[] { 0x1b, '[', 'D' };
        }
        if ("UP".equals(key)) {
            return new byte[] { 0x1b, '[', 'A' };
        }
        if ("RIGHT".equals(key)) {
            return new byte[] { 0x1b, '[', 'C' };
        }
        if ("DOWN".equals(key)) {
            return new byte[] { 0x1b, '[', 'B' };
        }
        if ("/".equals(key)) {
            if (ctrlOn) {
                return new byte[] { (byte) ('/' & 0x1f) };
            }
            return new byte[] { '/' };
        }

        if (key.length() == 1) {
            char c = key.charAt(0);
            if (ctrlOn && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return new byte[] { (byte) (c & 0x1f) };
            }
            return key.getBytes(StandardCharsets.US_ASCII);
        }

        return key.getBytes(StandardCharsets.US_ASCII);
    }
}
