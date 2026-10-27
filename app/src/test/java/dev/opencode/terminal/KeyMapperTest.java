package dev.opencode.terminal;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class KeyMapperTest {
    @Test
    public void escIsSingleByte1b() {
        assertArrayEquals(new byte[] { 0x1b }, KeyMapper.bytesFor("ESC", false));
    }

    @Test
    public void leftArrowIsCsI() {
        assertArrayEquals(new byte[] { 0x1b, '[', 'D' }, KeyMapper.bytesFor("LEFT", false));
    }

    @Test
    public void ctrlCIs0x03() {
        assertArrayEquals(new byte[] { 0x03 }, KeyMapper.bytesFor("c", true));
    }

    @Test
    public void ctrlOffLowercaseCIsAsciiC() {
        assertArrayEquals(new byte[] { 'c' }, KeyMapper.bytesFor("c", false));
    }

    @Test
    public void tabIs0x09() {
        assertArrayEquals(new byte[] { 0x09 }, KeyMapper.bytesFor("TAB", false));
    }
}
