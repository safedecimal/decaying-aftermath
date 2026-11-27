package io.github.safedecimal.decayingaftermath.medical;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Maps consumed food items to mood, stress, and psychosis adjustments. */
public final class FoodMoodPolicy {
    public record Impact(float moodDelta, float stressDelta, float psychosisDelta) {
        public static final Impact NEUTRAL = new Impact(0, 0, 0);
    }

    private FoodMoodPolicy() { }

    public static Impact forStack(ItemStack stack, int nutrition) {
        if (stack == null || stack.isEmpty()) return Impact.NEUTRAL;
        Item item = stack.getItem();
        if (item == Items.ROTTEN_FLESH) return new Impact(-0.14f, 0.08f, 0.05f);
        if (item == Items.POISONOUS_POTATO) return new Impact(-0.15f, 0.10f, 0.08f);
        if (item == Items.SPIDER_EYE) return new Impact(-0.18f, 0.12f, 0.10f);
        if (item == Items.PUFFERFISH) return new Impact(-0.14f, 0.10f, 0.06f);
        if (item == Items.POTATO) return new Impact(-0.04f, 0.02f, 0f);
        if (item == Items.CHORUS_FRUIT) return new Impact(-0.05f, 0.04f, 0.04f);
        if (item == Items.BEEF || item == Items.PORKCHOP || item == Items.CHICKEN
                || item == Items.MUTTON || item == Items.RABBIT || item == Items.COD || item == Items.SALMON) {
            return new Impact(-0.05f, 0.02f, 0f);
        }
        if (item == Items.SUSPICIOUS_STEW) return new Impact(0.02f, 0f, 0f);
        if (item == Items.CAKE || item == Items.PUMPKIN_PIE || item == Items.COOKIE
                || item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.HONEY_BOTTLE || item == Items.SWEET_BERRIES || item == Items.GLOW_BERRIES) {
            return new Impact(0.09f, 0f, 0f);
        }
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP || item == Items.COOKED_CHICKEN
                || item == Items.COOKED_MUTTON || item == Items.COOKED_RABBIT || item == Items.COOKED_COD
                || item == Items.COOKED_SALMON || item == Items.BAKED_POTATO || item == Items.BREAD
                || item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW || item == Items.BEETROOT_SOUP
                || item == Items.GOLDEN_CARROT || item == SurvivalItems.PEMMICAN) {
            return new Impact(0.065f, 0f, 0f);
        }
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return forItemId(path, nutrition);
    }

    public static Impact forItemId(String path, int nutrition) {
        if (path == null || path.isEmpty()) return Impact.NEUTRAL;
        if (path.equals("rotten_flesh")) return new Impact(-0.14f, 0.08f, 0.05f);
        if (path.equals("poisonous_potato")) return new Impact(-0.15f, 0.10f, 0.08f);
        if (path.equals("spider_eye")) return new Impact(-0.18f, 0.12f, 0.10f);
        if (path.equals("pufferfish")) return new Impact(-0.14f, 0.10f, 0.06f);
        if (path.equals("potato")) return new Impact(-0.04f, 0.02f, 0f);
        if (path.equals("chorus_fruit")) return new Impact(-0.05f, 0.04f, 0.04f);
        if (path.equals("beef") || path.equals("porkchop") || path.equals("chicken")
                || path.equals("mutton") || path.equals("rabbit") || path.equals("cod") || path.equals("salmon")) {
            return new Impact(-0.05f, 0.02f, 0f);
        }
        if (path.equals("suspicious_stew")) return new Impact(0.02f, 0f, 0f);
        if (path.equals("cake") || path.equals("pumpkin_pie") || path.equals("cookie")
                || path.equals("golden_apple") || path.equals("enchanted_golden_apple")
                || path.equals("honey_bottle") || path.equals("sweet_berries") || path.equals("glow_berries")) {
            return new Impact(0.09f, 0f, 0f);
        }
        if (path.equals("cooked_beef") || path.equals("cooked_porkchop") || path.equals("cooked_chicken")
                || path.equals("cooked_mutton") || path.equals("cooked_rabbit") || path.equals("cooked_cod")
                || path.equals("cooked_salmon") || path.equals("baked_potato") || path.equals("bread")
                || path.equals("mushroom_stew") || path.equals("rabbit_stew") || path.equals("beetroot_soup")
                || path.equals("golden_carrot") || path.equals("pemmican")) {
            return new Impact(0.065f, 0f, 0f);
        }
        if (path.contains("rotten") || path.contains("raw")) return new Impact(-0.04f, 0.02f, 0f);
        if (nutrition >= 4) return new Impact(0.04f, 0f, 0f);
        if (nutrition >= 2) return new Impact(0.02f, 0f, 0f);
        return Impact.NEUTRAL;
    }
}
