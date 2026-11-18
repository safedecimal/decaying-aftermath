package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MedicalVitalsReadoutTest {
    private BodySnapshot body() {
        var parts=new BodySnapshot.PartSnapshot[6];Arrays.fill(parts,new BodySnapshot.PartSnapshot(1,0,0,false,false,false,false));
        return new BodySnapshot(1,.75f,.8f,.2f,1.25f,72,BodySnapshot.Mode.CONSCIOUS,1,.25f,.4f,parts);
    }
    @Test void actualReceivedConsciousnessAndEnergyAreSeparateRows() {
        var rows=MedicalVitalsReadout.rows(body());assertEquals(10,rows.size());
        assertEquals("decaying_aftermath.stat.consciousness",rows.get(4).key());assertEquals(40,rows.get(4).percent());assertEquals(82,rows.get(4).y());
        assertEquals("decaying_aftermath.stat.energy",rows.get(5).key());assertEquals(25,rows.get(5).percent());assertEquals(94,rows.get(5).y());
    }
    @Test void survivalRowsUseTheRemainingMonitorSpace() {
        var rows=MedicalVitalsReadout.rows(body());
        assertEquals("decaying_aftermath.stat.hydration",rows.get(6).key());
        assertEquals("decaying_aftermath.stat.nutrition",rows.get(7).key());
        assertEquals("decaying_aftermath.stat.temperature",rows.get(8).key());
        assertEquals(37,rows.get(8).percent());assertEquals(142,rows.get(9).y());
    }
    @Test void rowsKeepNativeTenPixelGlyphHeightAndDoNotOverlapEcg() {
        var rows=MedicalVitalsReadout.rows(body());
        for(int i=0;i<rows.size();i++) {
            assertEquals(7,rows.get(i).x());assertTrue(rows.get(i).y()+10<154);
            if(i>0)assertTrue(rows.get(i-1).y()+10<rows.get(i).y());
        }
    }
    @Test void oldFourReadoutsAndCumulativeDrugPercentArePreserved() {
        var rows=MedicalVitalsReadout.rows(body());assertEquals(75,rows.get(0).percent());assertEquals(80,rows.get(1).percent());
        assertEquals(20,rows.get(2).percent());assertEquals(125,rows.get(3).percent());
    }
}
