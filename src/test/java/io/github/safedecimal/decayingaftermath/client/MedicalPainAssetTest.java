package io.github.safedecimal.decayingaftermath.client;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MedicalPainAssetTest {
    @Test void shaderDerivedMasksArePackagedWithTransparentCenterAndIncreasingRedEdges() throws Exception {
        int previous=0;
        for(int i=1;i<=MedicalPainState.MASK_STEPS;i++) {
            try(InputStream stream=getClass().getResourceAsStream("/assets/decaying_aftermath/textures/gui/procedures/pain_mask_"+i+".png")) {
                assertNotNull(stream);var image=ImageIO.read(stream);assertEquals(256,image.getWidth());assertEquals(256,image.getHeight());
                assertEquals(0,image.getRGB(128,128)>>>24);
                int corner=image.getRGB(0,0),alpha=corner>>>24;assertTrue(alpha>=previous);previous=alpha;
                assertTrue(((corner>>>16)&255)>((corner>>>8)&255));
            }
        }
        assertTrue(previous>200);
    }
    @Test void provenanceNamesActualShaderRegistersAndTexturesNotGuessedAssets() throws Exception {
        try(InputStream stream=getClass().getResourceAsStream("/assets/decaying_aftermath/cu_pain_shader_provenance.json")) {
            assertNotNull(stream);String data=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            assertTrue(data.contains("CB1[6].x"));assertTrue(data.contains("painimage"));assertTrue(data.contains("paingrain"));
            assertTrue(data.contains(".263464"));assertTrue(data.contains("Normalized screen UVs"));
        }
    }
}
