package io.github.sefiraat.danktech2;

import io.github.sefiraat.danktech2.commands.DankTechMain;
import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.managers.ConfigManager;
import io.github.sefiraat.danktech2.managers.ListenerManager;
import io.github.sefiraat.danktech2.managers.RunnableManager;
import io.github.sefiraat.danktech2.managers.SupportedPluginManager;
import io.github.sefiraat.danktech2.slimefun.ItemGroups;
import io.github.sefiraat.danktech2.slimefun.Machines;
import io.github.sefiraat.danktech2.slimefun.Materials;
import io.github.sefiraat.danktech2.slimefun.Packs;
import io.github.sefiraat.danktech2.theme.ThemeType;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.libraries.dough.updater.GitHubBuildsUpdater;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class DankTech2 extends JavaPlugin implements SlimefunAddon {

    private static DankTech2 instance;

    private final String username;
    private final String repo;
    private final String branch;

    private GitHubBuildsUpdater updater;

    private ConfigManager configManager;
    private ListenerManager listenerManager;
    private SupportedPluginManager supportedPluginManager;
    private RunnableManager runnableManager;

    public DankTech2() {
        this.username = "wickidcow";
        this.repo = "DankTech2";
        this.branch = "master";
    }

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("########################################");
        getLogger().info("       DankTech2 - Legacy Maintained    ");
        getLogger().info("########################################");

        tryUpdate();
        setupSlimefun();

        this.configManager = new ConfigManager();
        this.listenerManager = new ListenerManager();
        this.supportedPluginManager = new SupportedPluginManager();
        this.runnableManager = new RunnableManager();

        if (this.getCommand("danktech2") != null) {
            this.getCommand("danktech2").setExecutor(new DankTechMain());
        }

        setupMetrics();
    }

    @Override
    public void onDisable() {
        if (this.configManager != null) {
            this.configManager.saveAll();
        }
    }

    public void tryUpdate() {
        if (getConfig().getBoolean("auto-update") && getDescription().getVersion().startsWith("DEV")) {
            String updateLocation = MessageFormat.format("{0}/{1}/{2}", this.username, this.repo, this.branch);
            updater = new GitHubBuildsUpdater(this, getFile(), updateLocation);
            updater.start();
        }
    }

    private void setupSlimefun() {
        ItemGroups.setup();
        Materials.setup();
        Packs.setup();
        Machines.setup();
    }

    @NotNull
    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Nullable
    @Override
    public String getBugTrackerURL() {
        return MessageFormat.format("https://github.com/{0}/{1}/issues/", this.username, this.repo);
    }

    public void setupMetrics() {
        final Metrics metrics = new Metrics(this, 13399);
        final Map<String, Integer> dankValues = new HashMap<>();
        final Map<String, Integer> heldItemValues = new HashMap<>();

        for (ItemStack dank : ConfigManager.getInstance().getAllPacks()) {
            if (dank == null) {
                getLogger().warning("Bad Dank");
                continue;
            }

            final DankPackInstance dankPackInstance = DataTypeMethods.getCustom(
                dank.getItemMeta(),
                Keys.DANK_INSTANCE,
                PersistentDankInstanceType.TYPE
            );

            Integer dankAmount = dankValues.get("Tier " + dankPackInstance.getTier());
            if (dankAmount == null) {
                dankAmount = 1;
            } else {
                dankAmount++;
            }
            dankValues.put("Tier " + dankPackInstance.getTier(), dankAmount);

            for (int i = 0; i < dankPackInstance.getTier(); i++) {
                final ItemStack heldItem = dankPackInstance.getItem(i);
                if (heldItem == null) {
                    continue;
                }

                final ItemMeta itemMeta = heldItem.getItemMeta();
                final String name = itemMeta.hasDisplayName()
                    ? itemMeta.getDisplayName()
                    : ThemeType.toTitleCase(heldItem.getType().toString());

                Integer itemAmount = heldItemValues.get(name);
                if (itemAmount == null) {
                    itemAmount = dankPackInstance.getAmount(i);
                } else {
                    itemAmount += dankPackInstance.getAmount(i);
                }
                heldItemValues.put(name, itemAmount);
            }
        }

        AdvancedPie danksChart = new AdvancedPie("danks", () -> dankValues);
        AdvancedPie heldItemsChart = new AdvancedPie("held_items", () -> heldItemValues);

        metrics.addCustomChart(danksChart);
        metrics.addCustomChart(heldItemsChart);
    }

    public static DankTech2 getInstance() {
        return DankTech2.instance;
    }

    public static PluginManager getPluginManager() {
        return DankTech2.getInstance().getServer().getPluginManager();
    }

    public static SupportedPluginManager getSupportedPluginManager() {
        return DankTech2.getInstance().supportedPluginManager;
    }

    public static ConfigManager getConfigManager() {
        return DankTech2.getInstance().configManager;
    }

    public static RunnableManager getRunnableManager() {
        return DankTech2.getInstance().runnableManager;
    }
}
