package io.github.safedecimal.decayingaftermath.medical;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class I18nResourceTest {
    private JsonObject language(String locale) throws IOException {
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/decaying_aftermath/lang/"+locale+".json")) {
            assertNotNull(in);
            return JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void russianAndEnglishHaveIdenticalCompleteKeySets() throws IOException {
        var en=language("en_us");var ru=language("ru_ru");
        assertEquals(en.keySet(),ru.keySet());assertTrue(en.size()>=41);
        for(String key:new String[]{"decaying_aftermath.procedure.guide.steady","decaying_aftermath.thought.supply_space"})
            assertTrue(en.has(key),key);
        for(String key:en.keySet()) {assertFalse(en.get(key).getAsString().isBlank());assertFalse(ru.get(key).getAsString().isBlank());}
    }
    @Test void formattingPlaceholdersMatchAcrossLanguages() throws IOException {
        var en=language("en_us");var ru=language("ru_ru");var pattern=Pattern.compile("%(?:\\d+\\$)?s|%%");
        for(String key:en.keySet()) assertEquals(pattern.matcher(en.get(key).getAsString()).results().map(m->m.group()).sorted().toList(),
                pattern.matcher(ru.get(key).getAsString()).results().map(m->m.group()).sorted().toList(),key);
    }
    @Test void bodyNamesAndUserThoughtsAreActuallyRussian() throws IOException {
        var ru=language("ru_ru");
        for(BodyPart part:BodyPart.values()) assertTrue(ru.get(part.translationKey()).getAsString().codePoints().anyMatch(c->c>127));
        assertEquals("Кажется, это не помогло...",ru.get(PotionPolicy.WASTED_HEALING_THOUGHT_KEY).getAsString());
        assertEquals("It's seems didn't help..",language("en_us").get(PotionPolicy.WASTED_HEALING_THOUGHT_KEY).getAsString());
    }
    @Test void allMedicalMinigamesHaveLocalizedInstructions() throws IOException {
        var ru=language("ru_ru");
        for(String suffix:new String[]{"wrap","syringe","potion","splint_align","splint_straps","tourniquet_fit","tourniquet_tighten"})
            assertTrue(ru.get("decaying_aftermath.procedure.guide."+suffix).getAsString().matches(".*[А-Яа-яЁё].*"));
    }
}
