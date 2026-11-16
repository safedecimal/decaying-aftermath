package io.github.safedecimal.decayingaftermath.medical;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcedureBudgetTest {
    @Test void packetFloodCannotExceedElapsedTimeOrOneItem() {
        var b=new ProcedureBudget(100);
        assertEquals(.5f,b.grant(160,1,1));
        for(int i=0;i<100;i++) assertEquals(0,b.grant(160,1,1));
        assertEquals(.5f,b.grant(220,1,.5f));
        assertEquals(0,b.grant(400,1,1));
        assertEquals(1,b.delivered());
    }
    @Test void invalidValuesAndRemainingFillCannotCreateDose() {
        var b=new ProcedureBudget(100);
        assertEquals(0,b.grant(99,1,1));
        assertEquals(0,b.grant(220,Float.NaN,1));
        assertEquals(0,b.grant(220,1,Float.POSITIVE_INFINITY));
        assertEquals(.1f,b.grant(220,1,.1f));
        assertEquals(0,b.grant(2600,1,1));
    }
}
