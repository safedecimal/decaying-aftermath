package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalSupplyConsolidationTest {
    @Test void differentPartialRollsRepackWithoutCreatingMaterial() {
        var result=MedicalSupplyPolicy.repack(.25+.5+.75);
        assertEquals(1,result.fullUnits());assertEquals(.5f,result.residual());
    }
    @Test void countedPartialUnitsAreNotRefilled() {
        assertEquals(2.25,MedicalSupplyPolicy.material(3,.75f));
        var result=MedicalSupplyPolicy.repack(MedicalSupplyPolicy.material(3,.75f)-.25);
        assertEquals(2,result.fullUnits());assertEquals(0,result.residual());
    }
    @Test void fullInventoryClutterPreservesAllTwentyTwoAndAHalfRolls() {
        double material=0;for(int i=0;i<36;i++)material+=MedicalSupplyPolicy.material(1,new float[]{.25f,.5f,.75f,1}[i%4]);
        var result=MedicalSupplyPolicy.repack(material);
        assertEquals(22,result.fullUnits());assertEquals(.5f,result.residual());
    }
    @Test void malformedValuesNeverGenerateMaterial() {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}) {
            var result=MedicalSupplyPolicy.repack(value);assertEquals(0,result.fullUnits());assertEquals(0,result.residual());
        }
        assertEquals(0,MedicalSupplyPolicy.material(3,Float.NaN));
        assertEquals(0,MedicalSupplyPolicy.material(-1,1));
    }
    @Test void roundedResidualNeverExceedsOriginalMaterial() {
        for(int i=1;i<10000;i++) {
            double total=i/10001.0+5;
            var result=MedicalSupplyPolicy.repack(total);
            assertTrue(result.fullUnits()+(double)result.residual()<=total);
            assertEquals(total,result.fullUnits()+(double)result.residual(),.0000001);
        }
    }
    @Test void onlyRegisteredMedicalSupplyKindsAreRepacked() {
        assertFalse(MedicalSupplyPolicy.inventorySupply(null));
        assertFalse(MedicalSupplyPolicy.inventorySupply(TreatmentKind.POTION));
        assertFalse(MedicalSupplyPolicy.inventorySupply(TreatmentKind.HEALING_POTION));
        assertFalse(MedicalSupplyPolicy.inventorySupply(TreatmentKind.REGENERATION_POTION));
        assertTrue(MedicalSupplyPolicy.inventorySupply(TreatmentKind.BANDAGE));
        assertTrue(MedicalSupplyPolicy.inventorySupply(TreatmentKind.FENTANYL));
    }
}
