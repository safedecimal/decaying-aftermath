package io.github.safedecimal.decayingaftermath.client;

import java.util.List;
import java.util.stream.Stream;

/** Native geometry from CU level1/StatMenu RectTransforms, plus the mod's blood group readout. */
public final class CuMedicalGeometry {
    private CuMedicalGeometry() {}
    public record Rect(int x,int y,int width,int height) {
        public boolean contains(double px,double py) { return px>=x&&px<x+width&&py>=y&&py<y+height; }
    }
    public record Readout(String field,Rect box,int alignment) {}
    public record Sprite(String field,String name,Rect box,boolean vertical) {}
    static final Readout BLOOD_GROUP = new Readout("BloodGroupText",new Rect(55,63,25,10),4);
    public static final List<Readout> NATIVE_READOUTS=List.of(
        new Readout("BrainText",new Rect(25,38,27,8),1),
        new Readout("ConscText",new Rect(94,40,25,8),4),
        new Readout("ImmuneText",new Rect(55,51,25,6),4),
        new Readout("PainText",new Rect(94,48,25,10),4),
        new Readout("BloodText",new Rect(22,51,29,6),1),
        new Readout("BPMText",new Rect(16,154,17,10),1),
        new Readout("BleedText",new Rect(22,62,25,6),1),
        new Readout("TempText",new Rect(94,70,25,6),4),
        new Readout("HappinessText",new Rect(90,94,25,10),4),
        new Readout("SicknessText",new Rect(28,96,18,6),1),
        new Readout("WeightText",new Rect(28,106,25,8),1),
        new Readout("RRText",new Rect(9,140,24,10),1),
        new Readout("OpiateText",new Rect(40,140,24,10),1),
        new Readout("OpiateText (1)",new Rect(75,140,24,10),4),
        new Readout("RadText",new Rect(101,138,25,10),4),
        new Readout("EnergyText",new Rect(90,105,25,10),4),
        new Readout("WaterText",new Rect(72,76,19,6),1),
        new Readout("FoodText",new Rect(84,87,18,6),1),
        new Readout("OxyText",new Rect(94,154,25,10),4));
    public static final List<Readout> READOUTS=Stream.concat(NATIVE_READOUTS.stream(),Stream.of(BLOOD_GROUP)).toList();
    public static final List<Sprite> SPRITES=List.of(
        new Sprite("BrainBar","barbrain",new Rect(28,35,96,2),false),
        new Sprite("TempFill","barthermometer",new Rect(118,66,7,24),true),
        new Sprite("FoodFill","barfood",new Rect(19,90,61,1),false),
        new Sprite("WaterFill","barwater",new Rect(19,79,49,1),false),
        new Sprite("BloodFill","barblood",new Rect(9,49,2,19),true),
        new Sprite("LungFill1","lungfill",new Rect(9,124,7,15),true),
        // Mirror in the texture so the right lung keeps the GUI quad's front-facing winding.
        new Sprite("LungFill2","lungfill_right",new Rect(22,124,7,15),true),
        new Sprite("HemothoraxFill","hemothoraxfill",new Rect(8,123,22,17),true),
        new Sprite("OpiateBack","syringeoutline",new Rect(38,121,29,29),false),
        new Sprite("OpiateFill","syringefill",new Rect(45,126,13,13),true),
        new Sprite("ToxinBack","toxinoutline",new Rect(71,121,29,29),false),
        new Sprite("ToxinFill","toxinoutlinedither",new Rect(75,121,23,23),true),
        new Sprite("Mood","neutral",new Rect(116,93,11,11),true));
    public static final Rect ECG=new Rect(7,165,120,25);
}
