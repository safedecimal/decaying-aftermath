package io.github.safedecimal.decayingaftermath.compat;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import org.junit.jupiter.api.Test;
class MedicalBleedOriginsTest {
    @Test void isolatedArmAlwaysEmitsFromThatArm() {
        var bleed = List.of(0f,0f,.8f,0f,0f,0f);
        for (int i=0;i<=100;i++) assertEquals(BodyPart.LEFT_ARM,MedicalBleedOrigins.select(bleed,i/100.0));
    }
    @Test void weightedPartsNeverSelectAnUninjuredLimb() {
        var bleed=List.of(0f,0f,.2f,0f,0f,.6f);
        assertEquals(BodyPart.LEFT_ARM,MedicalBleedOrigins.select(bleed,.1));
        assertEquals(BodyPart.RIGHT_LEG,MedicalBleedOrigins.select(bleed,.5));
        assertNull(MedicalBleedOrigins.select(List.of(0f,0f,0f,0f,0f,0f),.5));
    }
    @Test void stoppedBandagedLimbNoLongerEmits() {
        assertEquals(BodyPart.RIGHT_ARM,MedicalBleedOrigins.select(List.of(0f,0f,0f,.1f,0f,0f),.5));
        assertNull(MedicalBleedOrigins.select(List.of(0f,0f,0f,0f,0f,0f),.5));
    }
    @Test void limbAnchorsAreSeparateAndMatchDecompiledRestCentres() {
        assertArrayEquals(new double[]{-.375,1.2,0},MedicalBleedOrigins.restOffset(BodyPart.LEFT_ARM));
        assertArrayEquals(new double[]{.375,1.2,0},MedicalBleedOrigins.restOffset(BodyPart.RIGHT_ARM));
        assertEquals(.45,MedicalBleedOrigins.restOffset(BodyPart.LEFT_LEG)[1]);
    }
    @Test void malformedPartsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->MedicalBleedOrigins.checked(List.of(0f)));
        assertThrows(IllegalArgumentException.class,()->MedicalBleedOrigins.select(List.of(Float.NaN,0f,0f,0f,0f,0f),.5));
    }
}
