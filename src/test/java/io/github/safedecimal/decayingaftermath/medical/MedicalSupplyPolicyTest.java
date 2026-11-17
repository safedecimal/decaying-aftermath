package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MedicalSupplyPolicyTest {
    @Test void partialMaterialRemainsInTheItem() {
        var use=MedicalSupplyPolicy.spend(1,.25f);assertEquals(.25f,use.used());assertEquals(.75f,use.remaining());assertFalse(use.exhausted());
    }
    @Test void exhaustedItemCannotBorrowFromNextUnit() {
        var use=MedicalSupplyPolicy.spend(.1f,.8f);assertEquals(.1f,use.used());assertEquals(0,use.remaining());assertTrue(use.exhausted());
    }
    @Test void malformedValuesDoNotCreateMaterial() {
        assertEquals(0,MedicalSupplyPolicy.fill(Float.NaN));assertEquals(0,MedicalSupplyPolicy.spend(1,-1).used());assertEquals(0,MedicalSupplyPolicy.spend(1,Float.NaN).used());
    }
    @Test void sourceBandageCostsTwelveTurnsNotOne() {
        assertEquals(1f/12,MedicalSupplyPolicy.wrapCost(1),.000001f);assertEquals(1,MedicalSupplyPolicy.wrapCost(12),.000001f);
    }
    @Test void batchingHasTheSameTreatmentUnits() {
        assertEquals(3,MedicalSupplyPolicy.treatmentAmount(TreatmentKind.BANDAGE,.25f),.000001f);
        assertEquals(.25f,MedicalSupplyPolicy.treatmentAmount(TreatmentKind.FENTANYL,.25f));
    }
    @Test void eachKindHasABoundedTickBudget() {
        for(var kind:TreatmentKind.values())assertTrue(MedicalSupplyPolicy.ticksPerFill(kind)>0);
        var budget=new ProcedureBudget(0,240);float sum=0;for(int i=0;i<1000;i++)sum+=budget.grant(60,1,1);assertEquals(.25f,sum,.000001f);
    }
}
