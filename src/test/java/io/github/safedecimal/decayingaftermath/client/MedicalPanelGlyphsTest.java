package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPanelGlyphsTest {
    @Test
    void sourceAdvancesAreNotReplacedByMonospacePadding() {
        assertEquals(4,MedicalPanelGlyphs.advance('1'));
        assertEquals(4,MedicalPanelGlyphs.advance('7'));
        assertEquals(16,MedicalPanelGlyphs.width("139"));
        assertEquals(2,MedicalPanelGlyphs.advance('i'));
    }

    @Test
    void completeMinecraftUsernamesFitTheOriginalPlayerHeader() {
        for (String nickname : new String[] {"Test_Player", "safedecimal", "WWWWWWWWWWWWWWWW", "________________"}) {
            assertTrue(6 + MedicalPanelGlyphs.width(nickname) <= 130);
        }
        assertEquals('?' - 32,MedicalPanelGlyphs.index('\u0410'));
    }
}
