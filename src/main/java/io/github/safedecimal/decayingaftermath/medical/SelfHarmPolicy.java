package io.github.safedecimal.decayingaftermath.medical;

import java.util.Locale;

/** Server-owned rules for severe-depression self-harm urges, weapon selection, and emotional numbing. */
public final class SelfHarmPolicy {
    public static final int TRIGGER_TICKS = 320;
    public static final int URGE_WARN_TICKS = 200;
    public static final int COOLDOWN_TICKS = 2400;

    private SelfHarmPolicy() { }

    public static boolean eligible(BodySnapshot body) {
        if (body == null || body.mode() != BodySnapshot.Mode.CONSCIOUS) return false;
        if (body.survival().anxiolytic() >= 0.45f) return false;
        return body.survival().depression().severe() || MedicalPhysiology.mood(body) <= -0.70f;
    }

    public static int requiredCuts(BodySnapshot body) {
        if (body == null) return 1;
        float mood = MedicalPhysiology.mood(body);
        if (mood <= -0.92f) return 3;
        if (mood <= -0.82f) return 2;
        return 1;
    }

    public static int hesitationsForCut(BodySnapshot body, int cutIndex) {
        return cutIndex <= 0 ? 6 : 4;
    }

    public static float weaponDamage(boolean heavyBlade) {
        return heavyBlade ? 4.8f : 3.4f;
    }

    public static float mentalReliefPerCut(int totalCuts) {
        if (totalCuts <= 1) return 0.24f;
        if (totalCuts == 2) return 0.16f;
        return 0.12f;
    }

    public static boolean isWeaponId(String path) {
        if (path == null || path.isBlank()) return false;
        String id = path.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        return id.endsWith("_sword") || id.endsWith("_axe") || id.endsWith("_hoe")
                || id.endsWith("_pickaxe") || id.endsWith("_shovel")
                || id.equals("trident") || id.equals("mace") || id.equals("shears")
                || id.equals("flint") || id.equals("arrow") || id.equals("spectral_arrow")
                || id.equals("tipped_arrow") || id.equals("prismarine_shard")
                || id.equals("amethyst_shard") || id.equals("tweezers")
                || id.contains("knife") || id.contains("dagger") || id.contains("blade");
    }

    public static boolean isHeavyBladeId(String path) {
        if (path == null || path.isBlank()) return false;
        String id = path.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        return id.endsWith("_sword") || id.endsWith("_axe") || id.equals("trident")
                || id.equals("mace") || id.contains("dagger") || id.contains("blade");
    }
}
