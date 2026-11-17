package io.github.safedecimal.decayingaftermath.client;

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

class MedicalVitalsAssetBindingTest {
    private byte[] bytes(String path)throws Exception {
        try(InputStream input=getClass().getClassLoader().getResourceAsStream("assets/decaying_aftermath/"+path)) {
            assertNotNull(input,path);return input.readAllBytes();
        }
    }
    private JsonObject json(String path)throws Exception {
        return JsonParser.parseString(new String(bytes(path),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    @Test void deathTracksAreTheActualSerializedMusicManagerFields()throws Exception {
        var bindings=json("cu_vitals_presentation_provenance.json").getAsJsonObject("bindings");
        var dying=bindings.getAsJsonObject("dyingClip");var dead=bindings.getAsJsonObject("deadClip");
        assertEquals("DrawYourLastBreath1",dying.get("name").getAsString());
        assertEquals(1632,dying.getAsJsonObject("clip").get("path_id").getAsInt());
        assertEquals("DrawYourLastBreath2",dead.get("name").getAsString());
        assertEquals(1979,dead.getAsJsonObject("clip").get("path_id").getAsInt());
    }
    @Test void newExportsAreHashIdenticalToVerifiedFullSpritesAndActualAudio()throws Exception {
        var manifest=json("cu_vitals_presentation_provenance.json");assertFalse(manifest.get("games_started").getAsBoolean());
        var records=manifest.getAsJsonArray("records");assertEquals(5,records.size());
        for(var element:records) {
            var record=element.getAsJsonObject();String path=record.get("path").getAsString();byte[] data=bytes(path);
            assertEquals(record.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)),path);
            if(path.endsWith(".ogg"))assertEquals("OggS",new String(data,0,4,StandardCharsets.US_ASCII));
            else {
                var image=ImageIO.read(new ByteArrayInputStream(data));
                assertEquals(record.getAsJsonArray("dimensions").get(0).getAsInt(),image.getWidth());
                assertEquals(record.getAsJsonArray("dimensions").get(1).getAsInt(),image.getHeight());
            }
        }
    }
    @Test void bothSoundtracksAreStreamedWithoutChangingExistingProcedureSounds()throws Exception {
        var sounds=json("sounds.json");
        for(String name:new String[]{"brain_dying_music","death_music"}) {
            var record=sounds.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject();
            assertEquals("decaying_aftermath:"+name,record.get("name").getAsString());assertTrue(record.get("stream").getAsBoolean());
        }
        assertFalse(sounds.has("procedure_tourniquet"));
        assertTrue(sounds.has("procedure_syringe_loop"));assertTrue(sounds.has("last_standing_drone"));
    }
}
