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
                assertEquals(Math.round(19*height/1080f),box.x());
                assertEquals(Math.round(20*height/1080f),box.y());
                assertEquals(vanillaLeft,left);
                assertTrue(box.width() > 0);
                assertTrue(box.height() > 0);
                assertTrue(box.width() <= MedicalPanelLayout.WIDTH);
                assertTrue(box.height() <= MedicalPanelLayout.HEIGHT);
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

    @Test void referenceCanvasUsesExactCuSizeAndScalesByHeightWithoutMinecraftGuiScaleBias() {
        assertEquals(399, MedicalPanelLayout.topLeft().width());
        assertEquals(588, MedicalPanelLayout.topLeft().height());
        assertEquals(3f, MedicalPanelLayout.topLeft().scale());
        for (int[] size : new int[][] {{640,480},{320,240},{240,180},{854,480},{1280,720}}) {
            var box = MedicalPanelLayout.forScreen(size[0],size[1]);
            assertTrue(box.height() <= size[1] - MedicalPanelLayout.GAP);
            assertTrue(box.x()+box.width() <= size[0] / 2);
            assertEquals(size[1]/360f,box.scale(),.0001f);
            assertEquals(box.scale(),box.width()/(float)MedicalPanelLayout.SOURCE_WIDTH,.01f);
            assertEquals(50,box.localX(box.x()+50*box.scale()),.001f);
            assertEquals(80,box.localY(box.y()+80*box.scale()),.001f);
        }
        for(int guiScale:new int[]{1,2,3,4,6}) {
            var box=MedicalPanelLayout.forScreen(1920/guiScale,1080/guiScale);
            assertEquals(399,133*box.scale()*guiScale,.001);
            assertEquals(588,196*box.scale()*guiScale,.001);
        }
    }
}
