package io.github.safedecimal.decayingaftermath.medical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Resource checks do not bootstrap the game or instantiate its registries. */
class MedicalItemResourceTest {
    private static final List<String> ITEMS = List.of(
            "bandage", "pressure_dressing", "splint", "tourniquet", "fentanyl");

    @Test
    void allFiveRegisteredItemNamesHaveModernModelsAndOriginalIconAssets() throws IOException {
        for (String name : ITEMS) {
            JsonObject item = json("assets/decaying_aftermath/items/" + name + ".json");
            assertEquals("minecraft:model", item.getAsJsonObject("model").get("type").getAsString());
            assertEquals("decaying_aftermath:item/" + name,
                    item.getAsJsonObject("model").get("model").getAsString());
            JsonObject model = json("assets/decaying_aftermath/models/item/" + name + ".json");
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("decaying_aftermath:item/" + name,
                    model.getAsJsonObject("textures").get("layer0").getAsString());
            byte[] png = bytes("assets/decaying_aftermath/textures/item/" + name + ".png");
            assertTrue(png.length > 8);
            assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10},
                    java.util.Arrays.copyOf(png, 8));
        }
    }

    @Test
    void fourShapelessCraftsUseStringIngredientsAndFentanylHasNoCraft() throws IOException {
        int[] counts = {4, 1, 1, 1};
        int[] ingredients = {2, 3, 4, 3};
        for (int index = 0; index < 4; index++) {
            String name = ITEMS.get(index);
            JsonObject recipe = json("data/decaying_aftermath/recipe/" + name + ".json");
            assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
            assertEquals(ingredients[index], recipe.getAsJsonArray("ingredients").size());
            recipe.getAsJsonArray("ingredients").forEach(ingredient ->
                    assertTrue(ingredient.isJsonPrimitive() && ingredient.getAsJsonPrimitive().isString()));
            assertEquals("decaying_aftermath:" + name, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(counts[index], recipe.getAsJsonObject("result").get("count").getAsInt());
        }
        assertNull(getClass().getClassLoader().getResource("data/decaying_aftermath/recipe/fentanyl.json"));
        JsonObject bandage = json("data/decaying_aftermath/recipe/bandage.json");
        assertEquals("#minecraft:wool", bandage.getAsJsonArray("ingredients").get(0).getAsString());
        assertEquals("#minecraft:wool", bandage.getAsJsonArray("ingredients").get(1).getAsString());
    }

    @Test
    void medicalTranslationsKeepEnglishLiteralAndLocalizeRussianThought() throws IOException {
        for (String locale : List.of("en_us", "ru_ru")) {
            JsonObject language = json("assets/decaying_aftermath/lang/" + locale + ".json");
            for (String name : ITEMS) assertTrue(language.has("item.decaying_aftermath." + name));
            assertEquals(locale.equals("en_us")?"It's seems didn't help..":"Кажется, это не помогло...",
                    language.get("decaying_aftermath.thought.healing_potion_wasted").getAsString());
        }
    }

    private JsonObject json(String resource) throws IOException {
        return JsonParser.parseString(new String(bytes(resource), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private byte[] bytes(String resource) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return stream.readAllBytes();
        }
    }
}
