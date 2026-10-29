package dev.opencode.terminal;

import jackpal.androidterm.emulatorview.SelectionRange;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SelectionRangeTest {

    @Test
    public void keepsForwardSelection() {
        SelectionRange r = SelectionRange.fromDrag(2, 5, 8, 5);
        assertEquals(2, r.startX);
        assertEquals(5, r.startY);
        assertEquals(8, r.endX);
        assertEquals(5, r.endY);
    }

    @Test
    public void reversesBackwardSelection() {
        SelectionRange r = SelectionRange.fromDrag(8, 9, 2, 2);
        assertEquals(2, r.startX);
        assertEquals(2, r.startY);
        assertEquals(8, r.endX);
        assertEquals(9, r.endY);
    }

    @Test
    public void normalizesSameRowFromTheRight() {
        SelectionRange r = SelectionRange.fromDrag(9, 4, 3, 4);
        assertEquals(3, r.startX);
        assertEquals(4, r.startY);
        assertEquals(9, r.endX);
        assertEquals(4, r.endY);
    }

    @Test
    public void keepsSingleCell() {
        SelectionRange r = SelectionRange.fromDrag(7, 3, 7, 3);
        assertEquals(7, r.startX);
        assertEquals(3, r.startY);
        assertEquals(7, r.endX);
        assertEquals(3, r.endY);
    }
}
