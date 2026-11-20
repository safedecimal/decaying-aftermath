package io.github.safedecimal.decayingaftermath.medical;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DefibInventoryTest {
    @Test void stableAliveBodyCanBeShockedWithoutChangingRhythm() {
        var body=new BodyModel();
        assertTrue(body.canDefibrillate());
        float bpm=body.snapshot().bpm();
        assertTrue(body.defibrillate());
        assertEquals(0,body.snapshot().cardiacTicks());
        assertEquals(bpm,body.snapshot().bpm());
    }
    @Test void inventoryDragOntoTorsoOpensServerMinigame() throws Exception {
        var j=Path.of(System.getProperty("user.dir"),"src/main/java/io/github/safedecimal/decayingaftermath");
        var screen=Files.readString(j.resolve("client/MedicalInventoryScreen.java"));
        assertTrue(screen.contains("MedicalItems.isDefibrillator(slot.getItem())"));
        assertTrue(screen.contains("if(part==BodyPart.TORSO) {selected=part;ClientMedicalState.applyDefibrillator(source);}"));
        var network=Files.readString(j.resolve("network/DefibNetwork.java"));
        assertTrue(network.contains("registerGlobalReceiver(Apply.TYPE"));
        assertTrue(network.contains("start(actor,actor,InteractionHand.MAIN_HAND,slot,stack,item.automatic());"));
        assertTrue(network.contains("source(s)==s.device"));
    }
}