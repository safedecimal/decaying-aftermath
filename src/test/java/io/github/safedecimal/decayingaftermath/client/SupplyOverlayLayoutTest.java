package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupplyOverlayLayoutTest {
    @Test void numbersReferToRemainingMaterial() {
        assertEquals("100%",SupplyOverlayLayout.percent(1));assertEquals("75%",SupplyOverlayLayout.percent(.75f));assertEquals("59%",SupplyOverlayLayout.percent(.5879636f));
    }
    @Test void positiveMaterialDoesNotRoundToEmpty() {
        assertEquals("1%",SupplyOverlayLayout.percent(.0000001f));assertEquals("0%",SupplyOverlayLayout.percent(0));assertEquals("0%",SupplyOverlayLayout.percent(Float.NaN));
    }
    @Test void leftAndRightLabelsCannotOverlap() {
        for(int countWidth:new int[]{6,12,18,48}) {
            float scale=SupplyOverlayLayout.scale(24,countWidth);assertTrue(24*scale+1<SupplyOverlayLayout.countX(countWidth,scale));assertTrue(SupplyOverlayLayout.countX(countWidth,scale)+countWidth*scale<=16.001);
        }
    }
}
