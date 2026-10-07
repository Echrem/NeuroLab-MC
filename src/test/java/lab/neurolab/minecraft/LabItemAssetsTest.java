package lab.neurolab.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class LabItemAssetsTest {
    private static final List<String> ITEMS = List.of("neuro_acoustic_probe", "neuro_gustatory_probe",
            "neuro_looming_probe", "neuro_trial_recorder");
    private static final Path ROOT = Path.of("src/main/resources");

    @Test void eachNewInstrumentHasValidCraftingModelAndEnglishAndTurkishNames() throws Exception {
        JsonObject english = read("assets/neurolab/lang/en_us.json");
        JsonObject turkish = read("assets/neurolab/lang/tr_tr.json");
        for (String item : ITEMS) {
            JsonObject model = read("assets/neurolab/models/item/" + item + ".json");
            assertEquals("minecraft:block/block", model.get("parent").getAsString());
            assertFalse(model.getAsJsonArray("elements").isEmpty(), item + " needs a visible instrument model");
            assertTrue(english.has("item.neurolab." + item));
            assertTrue(turkish.has("item.neurolab." + item));

            JsonObject recipe = read("data/neurolab/recipe/" + item + ".json");
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            assertEquals("neurolab:" + item, recipe.getAsJsonObject("result").get("id").getAsString());
            assertEquals(3, recipe.getAsJsonArray("pattern").size());
        }
    }

    private static JsonObject read(String relative) throws Exception {
        try (var reader = Files.newBufferedReader(ROOT.resolve(relative))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
