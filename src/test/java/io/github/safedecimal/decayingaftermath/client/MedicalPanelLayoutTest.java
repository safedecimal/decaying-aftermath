package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPanelLayoutTest {
    @Test
    void screenCornerDoesNotDependOnInventoryCoordinatesOrWindowWidth() {
        for (int width : new int[] {320, 340, 379, 427, 640, 854, 1280}) {
            for (boolean recipes : new boolean[] {false, true}) {
                int height = 240;
                int vanillaLeft = recipes && width >= 379 ? (width - 176) / 2 + 77 : (width - 176) / 2;
                int left = MedicalPanelLayout.inventoryLeft(width, 176, vanillaLeft, recipes);
                int top = (height - 166) / 2;
                MedicalPanelLayout.Box box = MedicalPanelLayout.locate(width, height, left, top, 176, 166, recipes);
                assertEquals(0,box.x());
                assertEquals(0,box.y());
                assertEquals(vanillaLeft,left);
                assertEquals(133,box.width());
                assertEquals(196,box.height());
                assertFalse(box.compact());
                assertTrue(box.x() + box.width() <= width && box.y() + box.height() <= height, width + ": " + box);
            }
        }
    }

    @Test
    void normalInventoryRetainsItsVanillaPosition() {
        int left = MedicalPanelLayout.inventoryLeft(320,176,72,false);
        assertEquals(72,left);
        assertFalse(MedicalPanelLayout.locate(320,240,left,37,176,166,false).compact());
    }

    @Test
    void openRecipesRetainVanillaInventoryPosition() {
        assertEquals(203, MedicalPanelLayout.inventoryLeft(427,176,203,true));
        assertEquals(MedicalPanelLayout.topLeft(),
                MedicalPanelLayout.locate(427,240,203,37,176,166,true));
        assertEquals(MedicalPanelLayout.locate(427,240,72,99,176,166,false),
                MedicalPanelLayout.locate(427,240,203,37,176,166,true));
    }
}
