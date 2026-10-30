package dev.opencode.terminal;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccentMapTest {
    @Test
    public void testVariantsForLowercase() {
        assertEquals("éêèë", AccentMap.variantsFor('e', false));
        assertEquals("áàâãä", AccentMap.variantsFor('a', false));
        assertEquals("ç", AccentMap.variantsFor('c', false));
        assertEquals("óôõòö", AccentMap.variantsFor('o', false));
    }

    @Test
    public void testVariantsForUppercaseInput() {
        assertEquals("éêèë", AccentMap.variantsFor('E', false));
    }

    @Test
    public void testVariantsForUppercaseOutput() {
        assertEquals("ÁÀÂÃÄ", AccentMap.variantsFor('a', true));
    }

    @Test
    public void testVariantsForNoVariants() {
        assertEquals("", AccentMap.variantsFor('x', false));
        assertEquals("", AccentMap.variantsFor('z', true));
    }
}