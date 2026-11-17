package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsciousnessLayoutTest {
    @Test void nativeHeightMatchedCanvasRestoresSourceGeometry() {
        var source = ConsciousnessLayout.at(1920, 1080);
        assertEquals(447, source.diameter());
        assertEquals(417, source.ecgWidth()); assertEquals(69, source.ecgHeight());
        assertEquals(211, ConsciousnessLayout.at(960, 510).diameter());
        assertEquals(149, ConsciousnessLayout.at(640, 360).diameter());
    }
    @Test void differentGuiScalesKeepSameScreenProportionAndCenteredPulse() {
        for (int[] size : new int[][] {{1920,1080},{960,540},{640,360},{480,270},{320,180}}) {
            var box = ConsciousnessLayout.at(size[0], size[1]);
            assertEquals(447.0/1080, box.diameter()/(double)size[1], .003);
            assertEquals(size[0]/2, box.x()+box.diameter()/2);
            assertEquals(size[1]/2, box.y()+box.diameter()/2);
            assertTrue(box.ecgWidth()<box.diameter()); assertTrue(box.ecgHeight()<box.diameter());
        }
    }
    @Test void narrowOrTinyViewportsCannotProduceNegativeOrOffscreenCircleBounds() {
        for (int[] size : new int[][] {{80,900},{1,1},{300,200}}) {
            var box = ConsciousnessLayout.at(size[0],size[1]);
            assertTrue(box.diameter()>0); assertTrue(box.x()>=0); assertTrue(box.y()>=0);
        }
    }
}
