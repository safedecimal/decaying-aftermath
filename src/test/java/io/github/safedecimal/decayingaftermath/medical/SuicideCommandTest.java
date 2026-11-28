package io.github.safedecimal.decayingaftermath.medical;

import com.google.gson.JsonParser;
import io.github.safedecimal.decayingaftermath.client.ThoughtTyper;
import io.github.safedecimal.decayingaftermath.network.SuicidePayload;
import io.github.safedecimal.decayingaftermath.server.ServerSuicideSystem;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SuicideCommandTest {
    @Test
    void selfDestructSoundsAreValidOggVorbisAndRegisteredInSoundsJson() throws Exception {
        var soundsJson = JsonParser.parseString(new String(
                getClass().getClassLoader().getResourceAsStream("assets/abjection/sounds.json").readAllBytes(),
                StandardCharsets.UTF_8)).getAsJsonObject();
        for (String name : new String[]{"selfdestruct1", "selfdestruct2"}) {
            assertTrue(soundsJson.has(name), name);
            byte[] ogg = getClass().getClassLoader()
                    .getResourceAsStream("assets/abjection/sounds/" + name + ".ogg").readAllBytes();
            assertTrue(ogg.length > 10000);
            assertEquals("OggS", new String(ogg, 0, 4, StandardCharsets.US_ASCII));
        }
        assertEquals(83, ServerSuicideSystem.COUNTDOWN_TICKS);
    }

    @Test
    void suicideCommandHasNoAdminRequirementAndPayloadRoundTrips() throws Exception {
        String commands = Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/server/MedicalCommands.java"));
        int idx = commands.indexOf("Commands.literal(\"suicide\")");
        assertTrue(idx > 0);
        String snippet = commands.substring(idx, Math.min(commands.length(), idx + 120));
        assertFalse(snippet.contains(".requires("), "/suicide must be available without admin permission");

        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SuicidePayload.CODEC.encode(buf, new SuicidePayload(SuicidePayload.DETONATE));
            assertEquals(SuicidePayload.DETONATE, SuicidePayload.CODEC.decode(buf).stage());
            assertEquals(buf.writerIndex(), buf.readerIndex());
        } finally {
            buf.release();
        }
    }

    @Test
    void panicSpeechSpansEntireBeepCountdown() throws Exception {
        for (String lang : new String[]{"ru_ru", "en_us"}) {
            var json = JsonParser.parseString(new String(
                    getClass().getClassLoader().getResourceAsStream("assets/abjection/lang/" + lang + ".json").readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            String line = json.get("abjection.thought.suicide").getAsString();
            ThoughtTyper.reset();
            ThoughtTyper.enqueue(line);
            for (int t = 0; t < ServerSuicideSystem.COUNTDOWN_TICKS - 5; t++) {
                ThoughtTyper.tick();
            }
            assertTrue(ThoughtTyper.visible().length() < line.length(),
                    lang + " speech should still be speaking through the countdown: " + ThoughtTyper.visible());
            assertTrue(ThoughtTyper.visible().length() > line.length() / 2);
            ThoughtTyper.reset();
        }
    }
}
