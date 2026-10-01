package io.github.sefiraat.danktech2.managers;

import io.github.sefiraat.danktech2.DankTech2;
import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.slimefun.packs.DankPack;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import lombok.Getter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    @Getter
    private static ConfigManager instance;

    private final FileConfiguration dankPacks;

    public ConfigManager() {
        instance = null;
        DankTech2.getInstance().saveDefaultConfig();
        this.dankPacks = getConfig("dank_packs.yml", false);
        instance = this;
    }

    /**
     * @noinspection ResultOfMethodCallIgnored
     */
    private FileConfiguration getConfig(String fileName, boolean updateWithDefaults) {
        final DankTech2 plugin = DankTech2.getInstance();
        final File file = new File(plugin.getDataFolder(), fileName);
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            file.getParentFile().mkdirs();
            plugin.saveResource(fileName, false);
        }
        try {
            final FileConfiguration config = PackRegistryFile.load(file.toPath());
            if (updateWithDefaults) {
                updateConfig(config, file, fileName);
            }
            return config;
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Unable to load " + fileName
                + "; DankTech2 is stopping to preserve the existing pack registry."
                + " Restore or repair the original file before restarting.", e);
        }
    }

    private void updateConfig(FileConfiguration config, File file, String fileName) throws IOException {
        final InputStream inputStream = DankTech2.getInstance().getResource(fileName);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
        config.addDefaults(defaults);
        config.options().copyDefaults(true);
        config.save(file);
    }

    public void saveAll() {
        DankTech2.getInstance().getLogger().info("DankTech2 saving data.");
        saveConfig(dankPacks, "dank_packs.yml");
    }

    private void saveConfig(FileConfiguration configuration, String filename) {
        File file = new File(DankTech2.getInstance().getDataFolder(), filename);
        try {
            PackRegistryFile.save(configuration, file.toPath());
        } catch (IOException | RuntimeException exception) {
            DankTech2.getInstance().getLogger().log(Level.SEVERE,
                "Unable to save " + filename + "; the previous registry was not intentionally truncated.", exception);
        }
    }

    public void saveDankPack(ItemStack itemStack) {
        SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);
        if (slimefunItem instanceof DankPack) {
            DankPackInstance instance = DataTypeMethods.getCustom(itemStack.getItemMeta(), Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE);
            dankPacks.set(instance.getId() + ".last_user", instance.getLastUser());
            dankPacks.set(instance.getId() + ".item", itemStack.clone());
        }
    }

    public void deletePack(long id) {
        dankPacks.set(String.valueOf(id), null);
    }

    public boolean checkDankDeletion(long id) {
        return dankPacks.getConfigurationSection(String.valueOf(id)) == null;
    }

    public List<ItemStack> getAllPacks() {
        List<ItemStack> itemStackList = new ArrayList<>();
        for (String s : dankPacks.getKeys(false)) {
            ItemStack itemStack = dankPacks.getItemStack(s + ".item");
            if (itemStack != null) {
                itemStackList.add(itemStack);
            }
        }
        return itemStackList;
    }

    public static List<String> getBlacklistedWorldsPickup() {
        return DankTech2.getInstance().getConfig().getStringList("world_blacklist_pickup");
    }

    public static List<String> getBlacklistedWorldsOpen() {
        return DankTech2.getInstance().getConfig().getStringList("world_blacklist_open");
    }

    public static List<String> getBlacklistedWorldsBuild() {
        return DankTech2.getInstance().getConfig().getStringList("world_blacklist_build");
    }
}
