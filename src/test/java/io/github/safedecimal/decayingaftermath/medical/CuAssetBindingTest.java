package io.github.safedecimal.decayingaftermath.medical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuAssetBindingTest {
    private static final String ROOT="assets/decaying_aftermath/";
    private byte[] bytes(String path)throws Exception {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream(ROOT+path)) {
            assertNotNull(input,path);return input.readAllBytes();
        }
    }
    private JsonObject json(String path)throws Exception {
        return JsonParser.parseString(new String(bytes(path),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Test void currentManifestTiesSleepFillToActualSceneSpriteNotGenericCircle()throws Exception {
        var manifest=json("cu_asset_logic_provenance.json");
        assertEquals("48c7f2296fc4a659faae3b1822884ca5b7ff73d6767ef159e6a6c2bc0b2d38f1",manifest.get("source_assembly_sha256").getAsString());
        var binding=manifest.getAsJsonObject("runtime_bindings").getAsJsonObject("sleepFill");
        assertEquals("sleepcircle",binding.get("sprite").getAsString());assertEquals(443,binding.get("path_id").getAsInt());
        assertEquals(447,binding.get("reference_diameter").getAsInt());assertEquals("Top",binding.get("fill_origin").getAsString());
        var ring=ImageIO.read(new ByteArrayInputStream(bytes("textures/gui/procedures/unconscious_ring.png")));
        assertEquals(149,ring.getWidth());assertEquals(149,ring.getHeight());
    }
    @Test void fullSpriteRectsRetainTrimOffsetsInsteadOfStretchingPackedReticleCrop()throws Exception {
        var outer=ImageIO.read(new ByteArrayInputStream(bytes("textures/gui/procedures/bandage.png")));
        var reticle=ImageIO.read(new ByteArrayInputStream(bytes("textures/gui/procedures/bandage_pointer.png")));
        assertEquals(200,outer.getWidth());assertEquals(200,outer.getHeight());
        assertEquals(300,reticle.getWidth());assertEquals(300,reticle.getHeight());
        assertEquals(0,outer.getRGB(0,0)>>>24);assertEquals(0,reticle.getRGB(0,0)>>>24);
    }
    @Test void allCurrentExportsAreHashVerifiedAndActualOggOrPng()throws Exception {
        for(var element:json("cu_asset_logic_provenance.json").getAsJsonArray("records")) {
            var record=element.getAsJsonObject();String path=record.get("output").getAsString();byte[] data=bytes(path);
            assertEquals(record.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)),path);
            if(path.endsWith(".ogg"))assertEquals("OggS",new String(data,0,4,StandardCharsets.US_ASCII));
            else {var image=ImageIO.read(new ByteArrayInputStream(data));assertEquals(record.getAsJsonArray("dimensions").get(0).getAsInt(),image.getWidth());}
        }
    }
    @Test void originalHeartbeatAndLoopBranchesHaveResourcesAndNoFakeTourniquetSound()throws Exception {
        var sounds=json("sounds.json");
        for(String name:new String[]{"heartbeat","heartbeat_heavy","heartbeat_monitor","critical","unconscious","procedure_syringe_loop","procedure_syringe_fail"}) {
            assertTrue(sounds.has(name));assertTrue(bytes("sounds/"+name+".ogg").length>4);
        }
        assertFalse(sounds.has("procedure_tourniquet"));
        assertTrue(sounds.getAsJsonObject("critical").getAsJsonArray("sounds").get(0).getAsJsonObject().get("stream").getAsBoolean());
        assertEquals(0,json("cu_asset_logic_provenance.json").getAsJsonObject("runtime_bindings").getAsJsonObject("lastStandPanel").get("text_children").getAsInt());
    }
}
