package io.github.safedecimal.decayingaftermath.medical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A wrong category name aborts registry loading and crashes the client before the title screen,
 * so every recipe is checked against the enum its type actually deserializes.
 */
class RecipeCategoryResourceTest {
    private static final Set<String> CRAFTING = Set.of("building", "redstone", "equipment", "misc");
    private static final Set<String> COOKING = Set.of("food", "blocks", "misc");
    private static final Path RECIPES = Path.of("src/main/resources/data");

    @Test
    void everyRecipeCategoryBelongsToItsRecipeType() throws IOException {
        List<Path> recipes;
        try (Stream<Path> paths = Files.walk(RECIPES)) {
            recipes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getParent().getFileName().toString().equals("recipe"))
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList();
        }
        assertFalse(recipes.isEmpty(), "recipe directory scan found nothing");
        for (Path recipe : recipes) {
            JsonObject root = JsonParser.parseString(Files.readString(recipe, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (!root.has("category")) continue;
            String type = root.get("type").getAsString();
            String category = root.get("category").getAsString();
            Set<String> allowed = isCooking(type) ? COOKING : CRAFTING;
            assertTrue(allowed.contains(category),
                    recipe.getFileName() + " uses type " + type + " with category " + category);
        }
    }

    private static boolean isCooking(String type) {
        return type.endsWith(":smelting") || type.endsWith(":blasting")
                || type.endsWith(":smoking") || type.endsWith(":campfire_cooking");
    }
}
