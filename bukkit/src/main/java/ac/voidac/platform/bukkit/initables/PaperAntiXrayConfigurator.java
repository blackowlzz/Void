package ac.voidac.platform.bukkit.initables;

import ac.voidac.VoidAPI;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.utils.anticheat.LogUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PaperAntiXrayConfigurator implements StartableInitable {

    private static final List<String> DEFAULT_HIDDEN_BLOCKS = List.of(
            "air",
            "copper_ore", "deepslate_copper_ore", "raw_copper_block",
            "iron_ore", "deepslate_iron_ore",
            "gold_ore", "deepslate_gold_ore",
            "diamond_ore", "deepslate_diamond_ore",
            "emerald_ore", "deepslate_emerald_ore",
            "lapis_ore", "deepslate_lapis_ore",
            "redstone_ore", "deepslate_redstone_ore",
            "coal_ore", "deepslate_coal_ore",
            "nether_quartz_ore", "nether_gold_ore",
            "ancient_debris"
    );

    private static final List<String> DEFAULT_REPLACEMENT_BLOCKS = List.of(
            "stone", "deepslate", "granite", "diorite", "andesite",
            "dirt", "gravel", "tuff", "calcite", "smooth_basalt",
            "coal_ore", "deepslate_coal_ore"
    );

    @Override
    public void start() {
        ConfigManager config = VoidAPI.INSTANCE.getConfigManager().getConfig();
        if (!config.getBooleanElse("AntiXray.configure-paper", false)) {
            return;
        }

        int engineMode = config.getIntElse("AntiXray.engine-mode", 2);
        if (engineMode != 1 && engineMode != 2) {
            LogUtil.warn("[AntiXray] Invalid engine-mode " + engineMode + " in config, must be 1 or 2. Defaulting to 2.");
            engineMode = 2;
        }

        List<String> hiddenBlocks = config.getStringListElse("AntiXray.hidden-blocks", DEFAULT_HIDDEN_BLOCKS);
        if (engineMode == 2 && !hiddenBlocks.contains("air")) {
            hiddenBlocks = new ArrayList<>(hiddenBlocks);
            hiddenBlocks.add(0, "air");
            LogUtil.warn("[AntiXray] engine-mode 2 requires 'air' in hidden-blocks, added automatically.");
        }

        Settings settings = new Settings(
                engineMode,
                config.getIntElse("AntiXray.max-block-height", 320),
                config.getIntElse("AntiXray.update-radius", 16),
                hiddenBlocks,
                config.getStringListElse("AntiXray.replacement-blocks", DEFAULT_REPLACEMENT_BLOCKS));

        try {
            File modern = new File("config/paper-world-defaults.yml");
            File legacy = new File("paper.yml");
            if (modern.exists()) {
                apply(modern, "anticheat.anti-xray", settings);
            } else if (legacy.exists()) {
                apply(legacy, "world-settings.default.anti-xray", settings);
            } else {
                LogUtil.warn("[AntiXray] No Paper config file found. Is this a Paper-based server?");
            }
        } catch (IOException | RuntimeException e) {
            LogUtil.warn("[AntiXray] Failed to configure Paper anti-xray: " + e.getMessage());
        }
    }

    private static void apply(File configFile, String basePath, Settings settings) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);

        if (yaml.getBoolean(basePath + ".enabled", false) && yaml.getInt(basePath + ".engine-mode", 0) == settings.engineMode()) {
            LogUtil.info("[AntiXray] Paper anti-xray already enabled (engine-mode " + settings.engineMode()
                    + ") in " + configFile.getPath() + ", no changes made.");
            return;
        }

        boolean changed = false;
        if (!yaml.getBoolean(basePath + ".enabled", false)) {
            yaml.set(basePath + ".enabled", true);
            changed = true;
        }
        changed |= setIntIfDifferent(yaml, basePath + ".engine-mode", settings.engineMode(), 0);
        changed |= setIntIfDifferent(yaml, basePath + ".max-block-height", settings.maxBlockHeight(), -1);
        changed |= setIntIfDifferent(yaml, basePath + ".update-radius", settings.updateRadius(), -1);
        changed |= setIfMissing(yaml, basePath + ".lava-obscures", false);
        changed |= setIfMissing(yaml, basePath + ".use-permission", false);

        List<String> hidden = new ArrayList<>(yaml.getStringList(basePath + ".hidden-blocks"));
        for (String block : settings.hiddenBlocks()) {
            if (!hidden.contains(block)) {
                hidden.add(block);
                changed = true;
            }
        }
        yaml.set(basePath + ".hidden-blocks", hidden);

        if (yaml.getStringList(basePath + ".replacement-blocks").isEmpty()) {
            yaml.set(basePath + ".replacement-blocks", settings.replacementBlocks());
            changed = true;
        }

        if (changed) {
            yaml.save(configFile);
            LogUtil.info("[AntiXray] Paper anti-xray configured in " + configFile.getPath()
                    + " (engine-mode=" + settings.engineMode() + ", max-height=" + settings.maxBlockHeight() + ")."
                    + " Restart the server to apply.");
        }
    }

    private static boolean setIntIfDifferent(YamlConfiguration yaml, String path, int value, int missing) {
        if (yaml.getInt(path, missing) == value) return false;
        yaml.set(path, value);
        return true;
    }

    private static boolean setIfMissing(YamlConfiguration yaml, String path, Object value) {
        if (yaml.contains(path)) return false;
        yaml.set(path, value);
        return true;
    }

    private record Settings(int engineMode, int maxBlockHeight, int updateRadius,
                            List<String> hiddenBlocks, List<String> replacementBlocks) {
    }
}
