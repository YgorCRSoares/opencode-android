package dev.opencode.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PtyWindowSizerTest {

    @Test
    public void appliesRowsAndColumnsToOpenPty() {
        final int[] received = new int[3];

        boolean applied = PtyWindowSizer.apply(
                () -> 42,
                false,
                80,
                24,
                (fd, rows, columns) -> {
                    received[0] = fd;
                    received[1] = rows;
                    received[2] = columns;
                });

        assertTrue(applied);
        org.junit.Assert.assertArrayEquals(new int[] {42, 24, 80}, received);
    }

    @Test
    public void closedFlagSkipsIoctl() {
        final boolean[] called = {false};

        boolean applied = PtyWindowSizer.apply(
                () -> 42,
                true,
                80,
                24,
                (fd, rows, columns) -> called[0] = true);

        assertFalse(applied);
        assertFalse("setter não pode ser chamado com PTY fechado", called[0]);
    }

    @Test
    public void closedDescriptorDoesNotPropagateIllegalState() {
        boolean applied = PtyWindowSizer.apply(
                () -> {
                    throw new IllegalStateException("Already closed");
                },
                false,
                80,
                24,
                (fd, rows, columns) -> {
                });

        assertFalse(applied);
    }
}
