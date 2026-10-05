package ac.voidac.manager.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalizedResourcesTest {
    @Test
    void bundledLanguagesHaveMatchingKeysAndSettingTypes() throws Exception {
        for (String directory : List.of("config", "messages", "punishments", "discord", "bridge", "database",
                "databases/mysql", "databases/sqlite", "databases/redis", "databases/postgres", "databases/mongo")) {
            Map<String, Object> reference = flatten(read(directory, "en"));
            for (String language : List.of("de", "en", "es", "fr", "it", "ja", "nl", "pl", "pt", "ro", "ru", "tr", "zh")) {
                if (getClass().getResource("/" + directory + "/" + language + ".yml") == null) continue;
                Map<String, Object> localized = flatten(read(directory, language));
                String resource = directory + "/" + language;
                assertEquals(reference.keySet(), localized.keySet(), resource);
                reference.forEach((key, value) -> {
                    Object actual = localized.get(key);
                    assertEquals(value == null ? null : value.getClass(), actual == null ? null : actual.getClass(), resource + ": " + key);
                    if (value instanceof Boolean || value instanceof Number
                            || directory.equals("punishments") || directory.equals("config") && value instanceof List<?>) {
                        assertEquals(value, actual, resource + ": " + key);
                    }
                });
                if (directory.equals("punishments")) {
                    assertFalse(localized.keySet().stream().anyMatch(key -> key.startsWith("Punishments.Freecam.")), resource);
                }
            }
        }
    }

    private Map<?, ?> read(String directory, String language) throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (var stream = getClass().getResourceAsStream("/" + directory + "/" + language + ".yml")) {
            return new Yaml(new SafeConstructor(options)).load(stream);
        }
    }

    private Map<String, Object> flatten(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (value instanceof Map<?, ?> child) {
                flatten(child).forEach((path, leaf) -> result.put(key + "." + path, leaf));
            } else {
                result.put(key.toString(), value);
            }
        });
        return result;
    }
}
