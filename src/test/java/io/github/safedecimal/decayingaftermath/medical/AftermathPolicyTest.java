package io.github.safedecimal.decayingaftermath.medical;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class AftermathPolicyTest {
    @Test void strictMinus75BoundaryAndEveryCauseUsesTheSameMoodTest() {
        assertEquals(AftermathPolicy.Cause.ORDINARY,AftermathPolicy.fromMood(-74));
        assertEquals(AftermathPolicy.Cause.ORDINARY,AftermathPolicy.fromMood(-75));
        assertEquals(AftermathPolicy.Cause.SEVERE_DEPRESSION,AftermathPolicy.fromMood(-75.001f));
        assertEquals(AftermathPolicy.Cause.SEVERE_DEPRESSION,AftermathPolicy.fromMood(-76));
        assertEquals(AftermathPolicy.Cause.ORDINARY,AftermathPolicy.fromMood(Float.NaN));
    }
    @Test void branchesHaveMutuallyExclusivePresentation() {
        assertTrue(AftermathPolicy.hasTimer(AftermathPolicy.Cause.ORDINARY));
        assertFalse(AftermathPolicy.monochrome(AftermathPolicy.Cause.ORDINARY));
        assertFalse(AftermathPolicy.hasTimer(AftermathPolicy.Cause.SEVERE_DEPRESSION));
        assertTrue(AftermathPolicy.monochrome(AftermathPolicy.Cause.SEVERE_DEPRESSION));
        assertTrue(AftermathPolicy.redactFigures(AftermathPolicy.Cause.SEVERE_DEPRESSION));
        assertFalse(AftermathPolicy.showVanillaDeathScreen());assertTrue(AftermathPolicy.lockMovement());
    }
    @Test void countdownUsesCeilingAndNeverShowsNegativeValues() {
        assertEquals("06:00",AftermathPolicy.timerText(360));
        assertEquals("05:59",AftermathPolicy.timerText(358.9));
        assertEquals("00:01",AftermathPolicy.timerText(.01));
        assertEquals("00:00",AftermathPolicy.timerText(-20));
        assertEquals("00:00",AftermathPolicy.timerText(Double.NaN));
        assertEquals(0,AftermathPolicy.timerOpacity(0));assertTrue(AftermathPolicy.timerOpacity(4)>.6);
    }
    @Test void durationConfigIsBoundedAndDefaultsToSixMinutes() {
        assertEquals(360,AftermathPolicy.DEFAULT_SECONDS);
        assertEquals(360,AftermathPolicy.checkedSeconds(360));
        assertThrows(IllegalArgumentException.class,()->AftermathPolicy.checkedSeconds(0));
        assertThrows(IllegalArgumentException.class,()->AftermathPolicy.checkedSeconds(3601));
    }
    @Test void playlistContainsExactlyTheFiveRequestedTracksAndLoopsInOrder() {
        assertEquals(5,AftermathPolicy.PLAYLIST.size());
        assertEquals("afterlife_lobby_time",AftermathPolicy.PLAYLIST.get(0));
        assertEquals("afterlife_george_street_shuffle",AftermathPolicy.PLAYLIST.get(4));
        int index=0;for(int i=0;i<5;i++)index=AftermathPolicy.nextTrack(index);assertEquals(0,index);
    }
    @Test void cameraEmergesThenMovesWithoutAnyInputAndLooksAtTheBody() {
        var anchor=new AftermathPolicy.Point(10,64,20);
        var start=AftermathPolicy.camera(anchor,30,0);var raised=AftermathPolicy.camera(anchor,30,4);
        assertTrue(start.position().y()<64.3);assertTrue(raised.position().y()>66.5);
        assertTrue(Math.hypot(raised.position().x()-10,raised.position().z()-20)>4.5);
        assertEquals(64.2,raised.target().y(),.000001);
        assertNotEquals(raised.position(),AftermathPolicy.camera(anchor,30,15).position());
        assertTrue(Float.isFinite(raised.yaw()));assertTrue(Float.isFinite(raised.pitch()));
    }
    @Test void thoughtLanesDriftAndStayTranslucent() {
        for(int lane=0;lane<5;lane++) {
            var a=AftermathPolicy.thought(lane,7);var b=AftermathPolicy.thought(lane,20);
            assertTrue(a.index()>=0&&a.index()<AftermathPolicy.THOUGHT_COUNT);
            assertNotEquals(a.x(),b.x());assertTrue(a.opacity()>=0&&a.opacity()<=.43);
        }
    }
    @Test void nativeSignedMoodCanReachHeavyDepressionAndHealthyMoodIsUnchanged() {
        var healthy=new BodyModel().snapshot();assertEquals(1,MedicalPhysiology.mood(healthy));
        var survival=new SurvivalSnapshot(.01f,.01f,37,1,1,1,1,0,0,0,0,0,1,0,1);
        var low=new BodySnapshot(1,1,1,1,0,72,BodySnapshot.Mode.CONSCIOUS,1,0,1,healthy.parts(),survival);
        assertEquals(-1,MedicalPhysiology.mood(low));
        assertEquals(AftermathPolicy.Cause.SEVERE_DEPRESSION,AftermathPolicy.fromMood(MedicalPhysiology.mood(low)*100));
    }
    @Test void allMusicIsRealStreamedVorbisAndNumbIsBundledSlowedAndDistorted() throws Exception {
        Path assets=Path.of("src/main/resources/assets/decaying_aftermath");
        var sounds=JsonParser.parseString(Files.readString(assets.resolve("sounds.json"))).getAsJsonObject();
        for(String key:AftermathPolicy.PLAYLIST)assertTrue(sounds.getAsJsonObject(key).getAsJsonArray("sounds").get(0).getAsJsonObject().get("stream").getAsBoolean());
        var manifest=JsonParser.parseString(Files.readString(assets.resolve("aftermath_audio_manifest.json"))).getAsJsonObject();
        assertEquals(5,manifest.getAsJsonArray("normal_playlist").size());assertTrue(manifest.get("numb_bundled").getAsBoolean());
        var numb=manifest.getAsJsonObject("depression");assertTrue(numb.get("duration").getAsDouble()>numb.get("source_duration").getAsDouble()*1.35);
        assertTrue(numb.get("filter").getAsString().contains("acrusher"));
        for(var entry:manifest.getAsJsonArray("normal_playlist"))assertTrue(Files.size(assets.resolve(entry.getAsJsonObject().get("path").getAsString()))>10000);
        byte[] ogg=Files.readAllBytes(assets.resolve(numb.get("path").getAsString()));assertEquals("OggS",new String(ogg,0,4));
    }
    @Test void thinFontUsesValidLowercaseNativeResourceLocation() throws Exception {
        Path assets=Path.of("src/main/resources/assets/decaying_aftermath");
        var json=JsonParser.parseString(Files.readString(assets.resolve("font/aftermath_thin.json"))).getAsJsonObject();
        String file=json.getAsJsonArray("providers").get(0).getAsJsonObject().get("file").getAsString();
        assertEquals("decaying_aftermath:vcr_osd_mono.ttf",file);
        assertEquals(75864,Files.size(assets.resolve("font/vcr_osd_mono.ttf")));
    }
    @Test void cameraAndEveryDeathBranchAreBoundIntoNativeMixinsAndServerAuthority() throws Exception {
        String client=Files.readString(Path.of("src/main/resources/decaying-aftermath.client.mixins.json"));
        for(String name:new String[]{"AftermathCameraMixin","AftermathKeyboardMixin","AftermathMouseMixin","AftermathGuiMixin","AftermathVisionMixin","AftermathRedactionMixin"})assertTrue(client.contains(name));
        String server=Files.readString(Path.of("src/main/java/io/github/safedecimal/decayingaftermath/server/ServerAftermathSystem.java"));
        assertTrue(server.contains("AftermathPolicy.fromMood(mood)"));assertFalse(server.contains("SEVERE_DEPRESSION ="));
        assertTrue(server.contains("player.setGameMode(GameType.SPECTATOR)"));assertTrue(server.contains("LIVE_MOOD"));
        assertTrue(server.contains("session.hardcore"));assertTrue(server.contains("remaining_ticks"));
    }
}
