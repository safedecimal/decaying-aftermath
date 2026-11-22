package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuDefibFontTest {
    @Test void nativeFaceSupportsAllSourceNumericFieldsWithoutInventedCyrillicGlyphs() {
        assertTrue(CuDefibFont.supports("0123456789%-/n/a"));assertFalse(CuDefibFont.supports("А"));
        assertTrue(CuDefibFont.width("200",33.333f)>0);
        assertEquals(2*CuDefibFont.width("200",33.333f),CuDefibFont.width("200",66.666f),.001f);
    }
}
