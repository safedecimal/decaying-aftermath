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
                assertEquals(MedicalPanelLayout.GAP,box.x());
                assertEquals(MedicalPanelLayout.GAP,box.y());
                assertEquals(vanillaLeft,left);
                assertTrue(box.width() > 0);
                assertTrue(box.height() > 0);
                assertTrue(box.width() <= MedicalPanelLayout.SOURCE_WIDTH);
                assertTrue(box.height() <= MedicalPanelLayout.SOURCE_HEIGHT);
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
        assertEquals(MedicalPanelLayout.forScreen(427,240),
                MedicalPanelLayout.locate(427,240,203,37,176,166,true));
        assertEquals(MedicalPanelLayout.locate(427,240,72,99,176,166,false),
                MedicalPanelLayout.locate(427,240,203,37,176,166,true));
    }

    @Test void medicalMonitorUsesGuiPixelsAtScaleTwoEvenOnHighResolutionDisplays() {
        var monitor = MedicalPanelLayout.forScreen(1920 / 2, 1080 / 2);
        assertEquals(1f, monitor.scale());
        assertEquals(133, monitor.width());
        assertEquals(196, monitor.height());
        assertEquals(monitor, MedicalPanelLayout.forScreen(2560 / 2, 1440 / 2));
        assertEquals(monitor, MedicalPanelLayout.forScreen(3840 / 2, 2160 / 2));
    }

    @Test void compactWindowsFitInventoryAndMonitorAtTheSameScale() {
        int gap = MedicalPanelLayout.GAP;
        for (int[] size : new int[][] {{387,480},{320,240},{160,260},{427,240},{240,180},{640,480}}) {
            var box = MedicalPanelLayout.forScreen(size[0],size[1]);
            // The monitor's integer bounds round up by less than one pixel.
            assertTrue(box.x()+box.width()+gap+MedicalInventoryLayout.WIDTH*box.scale() <= size[0]-(gap-1));
            assertTrue(2*gap+MedicalInventoryLayout.HEIGHT*box.scale() <= size[1]+.001f);
            assertTrue(box.y()+box.height() <= size[1]-gap);
            assertEquals(50,box.localX(box.x()+50*box.scale()),.001f);
            assertEquals(80,box.localY(box.y()+80*box.scale()),.001f);
        }
    }
}
