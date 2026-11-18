package io.github.safedecimal.decayingaftermath.client;

import io.github.safedecimal.decayingaftermath.medical.BodyPart;
import io.github.safedecimal.decayingaftermath.medical.BodySnapshot;
import java.util.List;
import java.util.Locale;

/** CU's native StatMenu presentation. Missing CU measurements are never fabricated. */
public final class CuMedicalMonitor {
    private CuMedicalMonitor() { }
    private static String percent(float n) { return Math.round(n * 100) + "%"; }
    public static String value(BodySnapshot b, String field, int bpm) {
        return switch (field) {
            case "BrainText" -> Integer.toString(Math.round(b.part(BodyPart.HEAD).integrity() * 100));
            case "ConscText" -> percent(b.consciousness());
            case "ImmuneText" -> "--%";
            case "PainText" -> percent(b.pain());
            case "BloodText" -> percent(b.blood());
            case "BPMText" -> bpm + " | --/--";
            case "BleedText" -> bleeding(b) > 0 ? percent(bleeding(b)) : "";
            case "TempText" -> String.format(Locale.ROOT,"%.1f'c",b.survival().temperature());
            case "HappinessText" -> "--";
            case "SicknessText" -> percent(infection(b));
            case "WeightText" -> "--kg";
            case "RRText" -> "--/m";
            case "OpiateText" -> b.drug() > 0 ? percent(b.drug()) : "";
            case "OpiateText (1)" -> b.survival().toxin() > 0 ? percent(b.survival().toxin()) : "";
            case "RadText" -> "--gy";
            case "EnergyText" -> percent(b.energy());
            case "WaterText" -> Integer.toString(Math.round(b.survival().hydration() * 100));
            case "FoodText" -> Integer.toString(Math.round(b.survival().nutrition() * 100));
            case "OxyText" -> percent(b.oxygen());
            default -> throw new IllegalArgumentException(field);
        };
    }
    public static float amount(BodySnapshot b, String field) {
        var s = b.survival();
        return Math.clamp(switch (field) {
            case "BrainBar" -> b.part(BodyPart.HEAD).integrity();
            case "BloodFill" -> b.blood();
            case "TempFill" -> (s.temperature() - 28) * .08f; // Exact native WoundView formula.
            case "WaterFill" -> s.hydration();
            case "FoodFill" -> s.nutrition();
            case "LungFill1" -> (1 - s.fatigue()) * 2;
            case "LungFill2" -> (1 - s.fatigue()) * 2 - 1;
            case "HemothoraxFill" -> 0; // No separate thoracic blood measurement is transmitted.
            case "OpiateBack" -> b.drug() > 0 ? 1 : 0;
            case "OpiateFill" -> b.drug() / 3;
            case "ToxinBack" -> s.toxin() > 0 ? 1 : 0;
            case "ToxinFill" -> s.toxin() / 3;
            case "Mood" -> 1;
            default -> throw new IllegalArgumentException(field);
        }, 0, 1);
    }
    public static boolean critical(BodySnapshot b, String field) {
        return switch (field) {
            case "BrainText" -> b.part(BodyPart.HEAD).integrity() < .5f;
            case "ConscText" -> b.consciousness() < .4f;
            case "PainText" -> b.pain() > .75f;
            case "BloodText" -> b.blood() < .25f;
            case "OxyText" -> b.oxygen() < .7f;
            case "EnergyText" -> b.energy() < .05f;
            case "WaterText" -> b.survival().hydration() < .1f;
            case "FoodText" -> b.survival().nutrition() < .1f;
            case "TempText" -> b.survival().temperature() < 30 || b.survival().temperature() > 41;
            case "SicknessText" -> infection(b) > .7f;
            case "OpiateText" -> b.drug() >= 2.4f;
            case "OpiateText (1)" -> b.survival().toxin() > .9f;
            default -> false;
        };
    }
    private static float bleeding(BodySnapshot b) {
        float total = 0; for (var part : b.parts()) total += part.bleeding(); return total;
    }
    private static float infection(BodySnapshot b) {
        float max = b.survival().gutInfection();
        for (var part : b.parts()) max = Math.max(max, part.infection()); return max;
    }
    public static String label(String field) {
        return switch (field) {
            case "BrainText", "BrainBar" -> "Состояние головы";
            case "ConscText" -> "Сознание";
            case "ImmuneText" -> "Иммунитет";
            case "PainText" -> "Боль";
            case "BloodText", "BloodFill" -> "Кровь";
            case "BPMText" -> "Пульс и давление";
            case "BleedText" -> "Кровотечение";
            case "TempText", "TempFill" -> "Температура тела";
            case "HappinessText", "Mood" -> "Настроение";
            case "SicknessText" -> "Инфекция";
            case "WeightText" -> "Масса тела";
            case "RRText" -> "Частота дыхания";
            case "LungFill1", "LungFill2" -> "Выносливость";
            case "HemothoraxFill" -> "Кровь в грудной полости";
            case "OpiateText", "OpiateBack", "OpiateFill" -> "Препараты";
            case "OpiateText (1)", "ToxinBack", "ToxinFill" -> "Токсины";
            case "RadText" -> "Радиация";
            case "EnergyText" -> "Энергия";
            case "WaterText", "WaterFill" -> "Вода";
            case "FoodText", "FoodFill" -> "Питание";
            case "OxyText" -> "Кислород";
            default -> throw new IllegalArgumentException(field);
        };
    }
    public static String explanation(String field) {
        return switch (field) {
            case "ImmuneText", "HappinessText", "Mood", "WeightText", "RRText", "RadText", "HemothoraxFill" -> "Этот показатель CU не передаётся медицинской моделью Minecraft. Прочерк означает отсутствие данных, а не нулевое значение.";
            case "BrainText", "BrainBar" -> "Целостность головы в медицинской модели Minecraft. Значок и шкала взяты из панели CU.";
            case "BPMText" -> "Пульс — текущее сердцебиение. Давление в этой модели не передаётся. Ниже — игровая ЭКГ.";
            case "LungFill1", "LungFill2" -> "Игровая выносливость: запас, обратный усталости. Лёгкие заполняются последовательно, как в CU.";
            case "TempText", "TempFill" -> "Температура в градусах Цельсия. Шкала использует исходную формулу CU.";
            case "OpiateText", "OpiateBack", "OpiateFill", "OpiateText (1)", "ToxinBack", "ToxinFill" -> "Условная игровая нагрузка. Значок появляется только при ненулевом количестве; значение может превышать 100%.";
            default -> "Живой показатель медицинской модели Minecraft. Проценты — условные игровые величины, не клиническое измерение.";
        };
    }
    private record Hit(String field, CuMedicalGeometry.Rect box) { }
    // Native baked-icon rectangles, not a list of ten invented full-width rows.
    private static final List<Hit> ICONS = List.of(
            new Hit("BrainText",new CuMedicalGeometry.Rect(7,31,17,14)),
            new Hit("BloodText",new CuMedicalGeometry.Rect(7,48,14,12)),
            new Hit("BleedText",new CuMedicalGeometry.Rect(14,60,8,12)),
            new Hit("ImmuneText",new CuMedicalGeometry.Rect(77,49,12,14)),
            new Hit("PainText",new CuMedicalGeometry.Rect(119,42,12,20)),
            new Hit("TempText",new CuMedicalGeometry.Rect(116,63,15,31)),
            new Hit("WaterText",new CuMedicalGeometry.Rect(7,75,10,12)),
            new Hit("FoodText",new CuMedicalGeometry.Rect(7,85,11,12)),
            new Hit("SicknessText",new CuMedicalGeometry.Rect(14,96,12,11)),
            new Hit("WeightText",new CuMedicalGeometry.Rect(14,107,13,9)),
            new Hit("EnergyText",new CuMedicalGeometry.Rect(119,106,12,9)),
            new Hit("LungFill1",new CuMedicalGeometry.Rect(7,120,24,20)),
            new Hit("RadText",new CuMedicalGeometry.Rect(106,121,24,17)),
            new Hit("BPMText",new CuMedicalGeometry.Rect(7,153,9,11)),
            new Hit("OxyText",new CuMedicalGeometry.Rect(119,153,12,11)));
    public static String hover(BodySnapshot b, double x, double y) {
        if (CuMedicalGeometry.ECG.contains(x,y)) return "BPMText";
        for (var icon : ICONS) if (icon.box().contains(x,y)) return icon.field();
        for (var sprite : CuMedicalGeometry.SPRITES)
            if (amount(b,sprite.field()) > 0 && sprite.box().contains(x,y)) return sprite.field();
        for (var readout : CuMedicalGeometry.READOUTS) {
            if (!value(b,readout.field(),72).isEmpty() && readout.box().contains(x,y)) return readout.field();
        }
        return null;
    }
}
