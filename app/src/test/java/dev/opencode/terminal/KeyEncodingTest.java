package dev.opencode.terminal;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyEncodingTest {

    @Test
    public void testCharBytes() {
        assertArrayEquals(new byte[]{(byte) 0xC3, (byte) 0xA9}, KeyEncoding.charBytes(233));
        assertArrayEquals(new byte[]{(byte) 0xC2, (byte) 0xBA}, KeyEncoding.charBytes(186));
        assertArrayEquals(new byte[]{(byte) 0xC2, (byte) 0xB7}, KeyEncoding.charBytes(183));
        assertArrayEquals(new byte[]{0x41}, KeyEncoding.charBytes(65));
        assertNull(KeyEncoding.charBytes(-1));
    }

    @Test
    public void testCtrlByte() {
        assertArrayEquals(new byte[]{0x03}, KeyEncoding.ctrlByte(99));
        assertArrayEquals(new byte[]{0x1A}, KeyEncoding.ctrlByte(90));
        assertNull(KeyEncoding.ctrlByte(48));
    }

    @Test
    public void testArrowBytes() {
        assertArrayEquals(new byte[]{0x1B, '[', 'D'}, KeyEncoding.arrowBytes(-20));
        assertArrayEquals(new byte[]{0x1B, '[', 'C'}, KeyEncoding.arrowBytes(-23));
        assertNull(KeyEncoding.arrowBytes(65));
    }

    @Test
    public void testActionBytes() {
        assertArrayEquals(new byte[]{0x7F}, KeyEncoding.actionBytes(-5));
        assertArrayEquals(new byte[]{0x0D}, KeyEncoding.actionBytes(10));
        assertArrayEquals(new byte[]{0x1B}, KeyEncoding.actionBytes(27));
        assertNull(KeyEncoding.actionBytes(-9));
    }
}
