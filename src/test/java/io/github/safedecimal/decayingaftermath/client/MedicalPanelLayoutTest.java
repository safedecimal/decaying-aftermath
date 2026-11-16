package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPanelLayoutTest {
    @Test
    void monitorFitsAndNeverCoversInventorySlots() {
        for (int width : new int[] {320, 340, 379, 427, 640, 854, 1280}) {
            for (boolean recipes : new boolean[] {false, true}) {
                int height = 240;
                int vanillaLeft = recipes && width >= 379 ? (width - 176) / 2 + 77 : (width - 176) / 2;
                int left = MedicalPanelLayout.inventoryLeft(width, 176, vanillaLeft, recipes);
                int top = (height - 166) / 2;
                MedicalPanelLayout.Box box = MedicalPanelLayout.locate(width, height, left, top, 176, 166, recipes);
                assertTrue(box.x() >= 0 && box.y() >= 0, width + ": " + box);
                assertTrue(box.x() + box.width() <= width && box.y() + box.height() <= height, width + ": " + box);
                boolean intersects = box.x() < left + 176 && box.x() + box.width() > left
                        && box.y() < top + 166 && box.y() + box.height() > top;
                assertFalse(intersects, width + ": " + box);
            }
        }
    }

    @Test
    void narrowScreensUsePulseStripWithoutMovingInventory() {
        assertEquals(72, MedicalPanelLayout.inventoryLeft(320, 176, 72, false));
        assertTrue(MedicalPanelLayout.locate(320,240,72,37,176,166,false).compact());
    }

    @Test
    void openRecipesRetainVanillaInventoryPosition() {
        assertEquals(203, MedicalPanelLayout.inventoryLeft(427,176,203,true));
        assertTrue(MedicalPanelLayout.locate(427,240,203,37,176,166,true).compact());
    }
}
