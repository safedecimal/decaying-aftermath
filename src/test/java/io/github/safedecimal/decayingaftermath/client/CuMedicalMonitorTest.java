package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.google.gson.JsonParser;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuMedicalMonitorTest {
    private BodySnapshot body() {
        var parts=new BodySnapshot.PartSnapshot[6];
        Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        return new BodySnapshot(1,.75f,.8f,.2f,1.25f,72,BodySnapshot.Mode.CONSCIOUS,1,.25f,.4f,parts);
    }
    @Test void everyReadoutRetainsOriginalRectTransformCoordinatesAndTextAlignment()throws Exception {
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/decaying_aftermath/cu_exact_monitor_provenance.json")) {
            assertNotNull(in);
            var manifest=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("48c7f2296fc4a659faae3b1822884ca5b7ff73d6767ef159e6a6c2bc0b2d38f1",manifest.get("source_assembly_sha256").getAsString());
            assertEquals(1,manifest.get("canvas_match_height").getAsInt());
            var records=manifest.getAsJsonArray("records");
            assertEquals(19,CuMedicalGeometry.READOUTS.size());
            for(var readout:CuMedicalGeometry.READOUTS) {
                var record=java.util.stream.StreamSupport.stream(records.spliterator(),false).map(e->e.getAsJsonObject()).filter(r->r.get("name").getAsString().equals(readout.field())).findFirst().orElseThrow();
                var b=record.getAsJsonObject("box");
                assertEquals(b.get("x").getAsInt(),readout.box().x());assertEquals(b.get("y").getAsInt(),readout.box().y());
                assertEquals(b.get("width").getAsInt(),readout.box().width());assertEquals(b.get("height").getAsInt(),readout.box().height());
                assertEquals(record.getAsJsonObject("authored").get("m_HorizontalAlignment").getAsInt(),readout.alignment());
            }
        }
        assertEquals(new CuMedicalGeometry.Rect(7,165,120,25),CuMedicalGeometry.ECG);
    }
    @Test void readingsUseCurrentSnapshotButUnsupportedCuVitalsAreNotInvented() {
        assertEquals("75%",CuMedicalMonitor.value(body(),"BloodText",72));
        assertEquals("40%",CuMedicalMonitor.value(body(),"ConscText",72));
        assertEquals("25%",CuMedicalMonitor.value(body(),"EnergyText",72));
        assertEquals("125%",CuMedicalMonitor.value(body(),"OpiateText",72));
        assertEquals("72 | --/--",CuMedicalMonitor.value(body(),"BPMText",72));
        assertEquals("--kg",CuMedicalMonitor.value(body(),"WeightText",72));
        assertEquals("--gy",CuMedicalMonitor.value(body(),"RadText",72));
        assertEquals("--%",CuMedicalMonitor.value(body(),"ImmuneText",72));
        assertEquals("",CuMedicalMonitor.value(body(),"BleedText",72));
    }
    @Test void fillFormulaeAreNativeAndConditionalSyringeAndToxinIconsFollowRealValues() {
        assertEquals(.72f,CuMedicalMonitor.amount(body(),"TempFill"),.0001f);
        assertEquals(.75f,CuMedicalMonitor.amount(body(),"BloodFill"));
        assertEquals(1,CuMedicalMonitor.amount(body(),"LungFill1"));
        assertEquals(1,CuMedicalMonitor.amount(body(),"LungFill2"));
        assertEquals(1,CuMedicalMonitor.amount(body(),"OpiateBack"));
        assertEquals(1.25f/3,CuMedicalMonitor.amount(body(),"OpiateFill"),.0001f);
        assertEquals(0,CuMedicalMonitor.amount(body(),"ToxinBack"));
        assertEquals(0,CuMedicalMonitor.amount(body(),"ToxinFill"));
    }
    @Test void nativeThresholdsDoNotReuseInventedYellowBarsOrUniformPercentLimits() {
        assertFalse(CuMedicalMonitor.critical(body(),"EnergyText"));
        assertFalse(CuMedicalMonitor.critical(body(),"ConscText"));
        assertFalse(CuMedicalMonitor.critical(body(),"OpiateText"));
    }
    @Test void hoverTargetsAreNativeIconsAndFieldsWithExplicitRussianDescriptions() {
        for(String field:new String[]{"BrainText","ConscText","BloodFill","BPMText","OxyText","Mood","ToxinFill"}) {
            assertTrue(CuMedicalMonitor.label(field).chars().anyMatch(c->c>='А'&&c<='я'));
            assertFalse(CuMedicalMonitor.explanation(field).isBlank());
        }
        assertEquals("BrainText",CuMedicalMonitor.hover(body(),12,36));
        assertEquals("TempText",CuMedicalMonitor.hover(body(),126,80));
        assertEquals("OxyText",CuMedicalMonitor.hover(body(),126,159));
        assertEquals("BPMText",CuMedicalMonitor.hover(body(),60,170));
        assertNull(CuMedicalMonitor.hover(body(),132,40));
        for(int[] window:new int[][]{{640,480},{320,240},{1280,720}}) {
            var b=MedicalPanelLayout.forScreen(window[0],window[1]);
            assertEquals("BrainText",CuMedicalMonitor.hover(body(),b.localX(b.x()+12*b.scale()),b.localY(b.y()+36*b.scale())));
        }
    }
    @Test void fullNativeSpritesRetainTrimOffsetsHashesDimensionsAndAllScreenStaticIcons()throws Exception {
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/decaying_aftermath/cu_exact_monitor_provenance.json")) {
            var manifest=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var element:manifest.getAsJsonArray("sprites")) {
                var r=element.getAsJsonObject();
                try(var resource=getClass().getClassLoader().getResourceAsStream("assets/decaying_aftermath/"+r.get("path").getAsString())) {
                    assertNotNull(resource);var bytes=resource.readAllBytes();
                    assertEquals(r.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                    var image=ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    assertEquals(r.getAsJsonArray("dimensions").get(0).getAsInt(),image.getWidth());
                    assertEquals(r.getAsJsonArray("dimensions").get(1).getAsInt(),image.getHeight());
                }
            }
        }
    }
}
