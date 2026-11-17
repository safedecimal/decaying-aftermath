package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.util.List;

/** Six screen-local rows inside CU's unchanged 133x196 monitor frame. */
public final class MedicalVitalsReadout {
    public record Row(String key, int percent, int x, int y) { }
    private MedicalVitalsReadout() { }
    public static List<Row> rows(BodySnapshot body) {
        return List.of(row("blood",body.blood(),34),row("oxygen",body.oxygen(),46),
                row("pain",body.pain(),58),row("drug",body.drug(),70),
                row("consciousness",body.consciousness(),82),row("energy",body.energy(),94));
    }
    private static Row row(String key,float value,int y) {
        return new Row("decaying_aftermath.stat."+key,Math.round(value*100),7,y);
    }
}
