package io.github.safedecimal.decayingaftermath.medical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuDefibAssetsTest {
    private byte[] bytes(String path)throws Exception {
        try(var input=getClass().getClassLoader().getResourceAsStream("assets/decaying_aftermath/"+path)) {
            assertNotNull(input,path);return input.readAllBytes();
        }
    }
    private JsonObject json(String path)throws Exception {
        return JsonParser.parseString(new String(bytes(path),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Test void originalCuAssemblyAndBothPrefabsAreBoundByIdentity()throws Exception {
        var manifest=json("cu_defib_provenance.json");
        assertEquals("48c7f2296fc4a659faae3b1822884ca5b7ff73d6767ef159e6a6c2bc0b2d38f1",manifest.get("source_assembly_sha256").getAsString());
        assertEquals("0x0600049D",manifest.getAsJsonObject("prefab_methods").get("AEDMinigame.Update").getAsString());
        assertEquals("0x06000496",manifest.getAsJsonObject("prefab_methods").get("ManualDefibMinigame.Update").getAsString());
        assertEquals(23,manifest.getAsJsonArray("records").size());assertEquals(5,manifest.getAsJsonArray("sounds").size());
    }
    @Test void everyNativeSpriteAndInventoryIconReopensWithItsRecordedHashAndDimensions()throws Exception {
        for(var element:json("cu_defib_provenance.json").getAsJsonArray("records")) {
            var record=element.getAsJsonObject();String path=record.get("destination").getAsString();byte[] data=bytes(path);
            assertEquals(record.get("output_sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)),path);
            var image=ImageIO.read(new ByteArrayInputStream(data));assertNotNull(image,path);
            assertEquals(record.getAsJsonArray("dimensions").get(0).getAsInt(),image.getWidth(),path);
            assertEquals(record.getAsJsonArray("dimensions").get(1).getAsInt(),image.getHeight(),path);
        }
    }
    @Test void fiveSourceVoicesAndDeviceSoundsAreRegisteredAsRealOggResources()throws Exception {
        var sounds=json("sounds.json");
        for(var element:json("cu_defib_provenance.json").getAsJsonArray("sounds")) {
            var record=element.getAsJsonObject();String alias="cu_"+record.get("alias").getAsString();
            assertTrue(sounds.has(alias),alias);byte[] data=bytes(record.get("destination").getAsString());
            assertEquals("OggS",new String(data,0,4,StandardCharsets.US_ASCII));
            assertEquals(record.get("output_sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
        }
    }
    @Test void layoutUsesSourcePadsDialButtonsAndSeparate122By41Ecg()throws Exception {
        var layout=json("cu_defib_provenance.json").getAsJsonObject("layout");
        var dial=layout.getAsJsonObject("/ManualDefibMinigame/Dial").getAsJsonArray("position");
        assertEquals(-225,dial.get(0).getAsFloat());assertEquals(-174,dial.get(1).getAsFloat());
        var ecg=layout.getAsJsonObject("/ManualDefibMinigame/Screen/ECG").getAsJsonObject("size");
        assertEquals(122*3,ecg.get("x").getAsFloat());assertEquals(41*3,ecg.get("y").getAsFloat());
        assertEquals(141,layout.getAsJsonObject("/AEDMinigame/Shock").getAsJsonObject("size").get("x").getAsFloat(),.001);
    }
    @Test void sourceReadoutsAndHintsAreLocalizedWithoutTheOldSymbolAndRhythmGame()throws Exception {
        for(String lang:new String[]{"en_us","ru_ru"}) {
            var data=json("lang/"+lang+".json");
            for(String key:new String[]{"cu_aed_guide","cu_manual_guide","cu_exit","empty_battery"})assertTrue(data.has("decaying_aftermath.defib."+key));
            for(int state=0;state<6;state++)assertTrue(data.has("decaying_aftermath.defib.cu_aed_state"+state));
            for(String key:new String[]{"pad","keys","lock","calibrate","automatic_fire"})assertFalse(data.has("decaying_aftermath.defib."+key));
        }
    }
    @Test void numericTypefaceIsTheActualCuFaceWithVerifiedAtlasAndMetrics()throws Exception {
        var font=json("cu_defib_font.json");assertEquals("Retro Gaming",font.get("family").getAsString());
        assertEquals(121,font.get("point_size").getAsInt());assertEquals(95,font.getAsJsonObject("glyphs").size());
        byte[] data=bytes("textures/gui/defib/retro_gaming.png");
        assertEquals(font.get("atlas_sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
        var atlas=ImageIO.read(new ByteArrayInputStream(data));assertEquals(1024,atlas.getWidth());assertEquals(1024,atlas.getHeight());
        assertEquals(font.get("atlas_sha256").getAsString(),json("cu_defib_provenance.json").getAsJsonObject("font").get("atlas_sha256").getAsString());
    }
}
