package audit;

import io.github.sefiraat.danktech2.DankTech2;
import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.core.DankGUI;
import io.github.sefiraat.danktech2.core.AdminGUI;
import io.github.sefiraat.danktech2.core.TrashPackInstance;
import io.github.sefiraat.danktech2.managers.ConfigManager;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentTrashInstanceType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;

/** Real Paper server/inventory probe; menu rejection checks use tracked Player-interface proxies. */
public final class PackPreservationProbe extends JavaPlugin {
    private static final long ORIGINAL_ID = 9_007_199_254_740_993L;
    private final List<String> evidence = new ArrayList<>();
    private Method upgrade;
    private Method replace;
    private Method prepare;
    private Method nextAvailableId;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String phase = System.getProperty("probe.phase", Files.exists(Path.of("probe-expected.yml")) ? "second" : "first");
            try {
                if (Boolean.getBoolean("probe.expect-registry-refusal")) {
                    registryRefusal();
                    evidence.add("PASS " + phase);
                    return;
                }
                check(Bukkit.isPrimaryThread(), "probe runs on the real Paper server thread");
                check(Bukkit.getPluginManager().isPluginEnabled("Slimefun"), "published Slimefun enabled");
                check(Bukkit.getPluginManager().isPluginEnabled("DankTech2"), "DankTech2 enabled");
                String expectedVersion = System.getProperty("probe.expected-dank-version", "1.1.03");
                check(expectedVersion.equals(DankTech2.getInstance().getPluginMeta().getVersion()), "exact requested DankTech2 " + expectedVersion + " loaded");
                evidence.add("SERVER " + Bukkit.getVersion());
                evidence.add("JAVA " + System.getProperty("java.version"));
                ClassLoader loader = DankTech2.getInstance().getClass().getClassLoader();
                upgrade = method(loader, "io.github.sefiraat.danktech2.slimefun.machines.TrashPackUpgrade",
                    "copyInstance", ItemMeta.class, ItemMeta.class, int.class);
                replace = method(loader, "io.github.sefiraat.danktech2.core.AdminPackClone", "replace",
                    ItemStack.class, Inventory.class, LongPredicate.class, Consumer.class, LongConsumer.class);
                prepare = method(loader, "io.github.sefiraat.danktech2.core.AdminPackClone", "prepare", ItemStack.class, long.class);
                nextAvailableId = method(loader, "io.github.sefiraat.danktech2.core.AdminPackClone", "nextAvailableId",
                    long.class, long.class, LongPredicate.class);
                if ("first".equals(phase)) first(); else second();
                evidence.add("PASS " + phase);
            } catch (Throwable failure) {
                StringWriter stack = new StringWriter();
                failure.printStackTrace(new PrintWriter(stack));
                evidence.add("FAIL " + phase + "\n" + stack);
                getLogger().severe(stack.toString());
            } finally {
                try {
                    Files.writeString(Path.of("probe-result-" + phase + ".txt"), String.join("\n", evidence) + "\n");
                    evidence.forEach(getLogger()::info);
                } catch (Exception failure) {
                    getLogger().severe("Cannot write probe evidence: " + failure);
                }
                Bukkit.shutdown();
            }
        }, 60L);
    }

    private void registryRefusal() throws Exception {
        check(Bukkit.isPrimaryThread(), "startup-refusal probe runs on real Paper server thread");
        check(Bukkit.getPluginManager().isPluginEnabled("Slimefun"), "published Slimefun remains enabled for refusal test");
        Plugin plugin = Bukkit.getPluginManager().getPlugin("DankTech2");
        check(plugin != null && !plugin.isEnabled(), "unreadable existing registry leaves DankTech2 disabled");
        // Disabled plugins no longer export dependency classes to this helper. Inspect the
        // already-loaded class through that plugin's own classloader without re-enabling it.
        Class<?> configClass = Class.forName("io.github.sefiraat.danktech2.managers.ConfigManager", false,
            plugin.getClass().getClassLoader());
        check(configClass.getMethod("getInstance").invoke(null) == null,
            "unreadable registry never initializes ConfigManager singleton");
        check(HandlerList.getRegisteredListeners(plugin).isEmpty(), "disabled addon has no registered event listeners");
        check(Bukkit.getScheduler().getPendingTasks().stream().noneMatch(task -> task.getOwner() == plugin),
            "disabled addon has no pending scheduled tasks");
        check(SlimefunItem.getById("DK2_PACK_9") == null && SlimefunItem.getById("DK2_CRAFTER_1") == null,
            "unreadable registry stops startup before pack or machine registration");
        check(Arrays.equals(Files.readAllBytes(Path.of("probe-unreadable-registry-input.yml")),
            Files.readAllBytes(Path.of("plugins/DankTech2/dank_packs.yml"))),
            "refused registry remains byte-for-byte unchanged before shutdown");
    }

    private void first() throws Throwable {
        ItemStack upgradedTrash = trashUpgrade();
        ItemStack original = pack();
        ItemStack untouchedOriginal = original.clone();
        ItemStack maximumIdPack = (ItemStack) invoke(prepare, original, Long.MAX_VALUE);
        check(id(maximumIdPack) == Long.MAX_VALUE, "admin preparation retains full 64-bit replacement ID");
        check(original.equals(invoke(prepare, maximumIdPack, ORIGINAL_ID)), "admin preparation changes only the ID");
        check(original.equals(untouchedOriginal), "admin preparation never mutates source item");
        long next = (long) invoke(nextAvailableId, ORIGINAL_ID, ORIGINAL_ID,
            (LongPredicate) candidate -> candidate > ORIGINAL_ID + 2);
        check(next == ORIGINAL_ID + 3, "admin clone skips original and occupied long IDs");
        refusedDeliveries(original);
        refusedSaves(original);
        malformedClone();

        ConfigManager registry = ConfigManager.getInstance();
        registry.saveDankPack(original);
        check(!registry.checkDankDeletion(ORIGINAL_ID), "real ConfigManager registers original high-ID Dank Pack");
        DankGUI retainedDankMenu = new DankGUI(DataTypeMethods.getCustom(original.getItemMeta(),
            Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE), original);
        AdminGUI retainedAdminMenu = new AdminGUI();
        Chest chest = freshChest();
        Inventory inventory = chest.getBlockInventory();
        evidence.add("INVENTORY " + inventory.getClass().getName());
        fill(inventory);
        inventory.setItem(19, null);
        List<String> order = new ArrayList<>();
        boolean accepted = (boolean) invoke(replace, original, inventory,
            (LongPredicate) registry::checkDankDeletion,
            (Consumer<ItemStack>) replacement -> {
                check(replacement.equals(inventory.getItem(19)), "replacement delivered before real registry save");
                check(!registry.checkDankDeletion(ORIGINAL_ID), "original remains registered during new save");
                registry.saveDankPack(replacement);
                check(!registry.checkDankDeletion(id(replacement)), "real ConfigManager accepted replacement");
                order.add("save");
            },
            (LongConsumer) oldId -> {
                check(oldId == ORIGINAL_ID, "successful clone deletes only original ID");
                check(!registry.checkDankDeletion(id(inventory.getItem(19))), "replacement remains registered before old deletion");
                registry.deletePack(oldId);
                order.add("delete");
            });
        check(accepted, "real chest and ConfigManager accept clone");
        check(order.equals(List.of("save", "delete")), "successful order is delivery then registry save then original deletion");
        ItemStack replacement = inventory.getItem(19).clone();
        check(original.equals(invoke(prepare, replacement, ORIGINAL_ID)), "real delivered clone preserves complete rich item payload");
        check(registry.checkDankDeletion(ORIGINAL_ID), "old identity invalidated after successful clone");
        ItemStack[] contentsBeforeStale = snapshot(inventory);
        boolean staleAccepted = (boolean) invoke(replace, original, inventory,
            (LongPredicate) registry::checkDankDeletion,
            (Consumer<ItemStack>) ignored -> { throw new AssertionError("Stale original was saved"); },
            (LongConsumer) ignored -> { throw new AssertionError("Stale original was deleted again"); });
        check(!staleAccepted && Arrays.equals(contentsBeforeStale, inventory.getStorageContents()),
            "stale admin menu refused without inventory change");

        if (Boolean.getBoolean("probe.gui-guards")) {
            staleGuiGuards(original, replacement, retainedDankMenu, retainedAdminMenu);
        }

        registry.saveDankPack(maximumIdPack);
        check(!registry.checkDankDeletion(Long.MAX_VALUE), "actual registry retains Long.MAX_VALUE identity");
        inventory.clear();
        inventory.setItem(0, replacement);
        inventory.setItem(1, upgradedTrash);
        inventory.setItem(2, maximumIdPack);
        YamlConfiguration expected = new YamlConfiguration();
        expected.set("replacement", nativeExpectation(replacement));
        expected.set("trash", nativeExpectation(upgradedTrash));
        expected.set("maximum-id-pack", nativeExpectation(maximumIdPack));
        expected.set("original", nativeExpectation(original));
        expected.save(Path.of("probe-expected.yml").toFile());
        Files.write(Path.of("probe-trash-filter-before.bin"), upgradedTrash.getItemMeta().getPersistentDataContainer()
            .get(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER)
            .get(PersistentTrashInstanceType.TRASH_ITEMS, PersistentDataType.BYTE_ARRAY));
        registry.saveAll();
        Files.copy(Path.of("plugins/DankTech2/dank_packs.yml"), Path.of("probe-registry-before.yml"));
        chest.getWorld().save();
        check(Files.size(Path.of("plugins/DankTech2/dank_packs.yml")) > 0,
            "actual pack registry written before clean shutdown");
    }

    private void second() throws Throwable {
        YamlConfiguration expected = new YamlConfiguration();
        expected.load(Path.of("probe-expected.yml").toFile());
        ItemStack replacement = expected.getItemStack("replacement");
        ItemStack trash = expected.getItemStack("trash");
        ItemStack max = expected.getItemStack("maximum-id-pack");
        ItemStack original = expected.getItemStack("original");
        World world = Objects.requireNonNull(Bukkit.getWorld("world"));
        check(world.getBlockAt(1, 100, 1).getState() instanceof Chest, "saved real world chest loaded after restart");
        Inventory inventory = ((Chest) world.getBlockAt(1, 100, 1).getState()).getBlockInventory();
        YamlConfiguration actualItems = new YamlConfiguration();
        actualItems.set("replacement", inventory.getItem(0));
        actualItems.set("trash", inventory.getItem(1));
        actualItems.set("maximum-id-pack", inventory.getItem(2));
        actualItems.save(Path.of("probe-actual.yml").toFile());
        check(Objects.equals(replacement, inventory.getItem(0)), "world chest retains exact clone item after restart");
        check(Objects.equals(trash, inventory.getItem(1)), "world chest retains exact upgraded Trash Pack after restart");
        check(Objects.equals(max, inventory.getItem(2)), "world chest retains exact Long.MAX_VALUE item after restart");
        check(Arrays.equals(Files.readAllBytes(Path.of("probe-trash-filter-before.bin")),
            inventory.getItem(1).getItemMeta().getPersistentDataContainer().get(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER)
                .get(PersistentTrashInstanceType.TRASH_ITEMS, PersistentDataType.BYTE_ARRAY)),
            "historical Trash filter byte array is byte-for-byte unchanged across world save and restart");
        check(SlimefunItem.getByItem(inventory.getItem(0)).getId().equals("DK2_PACK_9")
            && SlimefunItem.getByItem(inventory.getItem(1)).getId().equals("DK2_TRASH_9")
            && SlimefunItem.getByItem(inventory.getItem(2)).getId().equals("DK2_PACK_9"),
            "Slimefun resolves the same registered item IDs after restart");
        ConfigManager registry = ConfigManager.getInstance();
        check(registry.checkDankDeletion(ORIGINAL_ID), "old identity remains deleted in reread registry");
        check(!registry.checkDankDeletion(id(replacement)), "replacement remains registered after restart");
        check(!registry.checkDankDeletion(Long.MAX_VALUE), "Long.MAX_VALUE registry key and LONG payload survive restart");
        List<ItemStack> reread = registry.getAllPacks();
        check(reread.size() == 2 && reread.contains(replacement) && reread.contains(max),
            "ConfigManager rereads both exact saved rich items with no extra records");
        check(original.equals(invoke(prepare, replacement, ORIGINAL_ID)), "restarted clone reverses to exact original when only ID is restored");
        verifyDank(max);
        TrashPackInstance trashData = readTrash(trash.getItemMeta());
        check(trashData.getId() == ORIGINAL_ID && trashData.getTier() == 9,
            "upgraded Trash Pack retains original full long ID and final tier after restart");
        check(trashData.getItems().length == 18 && richItem(Material.DIAMOND_SWORD, "filter_0").equals(trashData.getItems()[0])
            && richItem(Material.EMERALD, "filter_17").equals(trashData.getItems()[17]),
            "historical filter array and rich metadata reread exactly after restart");
    }

    private ItemStack trashUpgrade() throws Throwable {
        TrashPackInstance data = new TrashPackInstance(ORIGINAL_ID, 1);
        data.setItem(0, richItem(Material.DIAMOND_SWORD, "filter_0"));
        data.setItem(17, richItem(Material.EMERALD, "filter_17"));
        ItemMeta current = Objects.requireNonNull(SlimefunItem.getById("DK2_TRASH_1")).getItem().getItemMeta();
        DataTypeMethods.setCustom(current, Keys.TRASH_INSTANCE, PersistentTrashInstanceType.TYPE, data);
        PersistentDataContainer initialData = current.getPersistentDataContainer().get(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER);
        opaque(initialData);
        current.getPersistentDataContainer().set(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER, initialData);
        ItemMeta sourceSnapshot = current.clone();
        for (int tier = 2; tier <= 9; tier++) {
            ItemMeta next = Objects.requireNonNull(SlimefunItem.getById("DK2_TRASH_" + tier)).getItem().getItemMeta();
            Component outputName = next.displayName();
            ItemMeta before = current.clone();
            invoke(upgrade, current, next, tier);
            PersistentDataContainer wanted = initialData.getAdapterContext().newPersistentDataContainer();
            initialData.copyTo(wanted, true);
            wanted.set(PersistentTrashInstanceType.TRASH_TIER, PersistentDataType.INTEGER, tier);
            PersistentDataContainer actual = next.getPersistentDataContainer().get(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER);
            check(wanted.equals(actual), "Trash tier " + tier + " changes only tier inside exact stored PDC");
            check(current.equals(before), "Trash tier " + tier + " leaves source metadata unchanged");
            check(Objects.equals(outputName, next.displayName()), "Trash tier " + tier + " retains output display name");
            TrashPackInstance decoded = readTrash(next);
            check(decoded.getId() == ORIGINAL_ID && decoded.getTier() == tier && Arrays.equals(data.getItems(), decoded.getItems()),
                "Trash tier " + tier + " preserves full long ID and filter array");
            current = next;
        }
        check(sourceSnapshot.getPersistentDataContainer().get(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER).equals(initialData),
            "initial historical Trash payload never mutated by upgrade chain");
        ItemMeta noData = new ItemStack(Material.NETHERITE_INGOT).getItemMeta();
        ItemMeta firstOutput = new ItemStack(Material.PAPER).getItemMeta();
        invoke(upgrade, noData, firstOutput, 1);
        TrashPackInstance first = readTrash(firstOutput);
        check(first.getTier() == 1 && first.getItems().length == 18 && Arrays.stream(first.getItems()).allMatch(Objects::isNull),
            "first Trash craft creates an empty historical 18-slot instance");
        ItemMeta malformed = new ItemStack(Material.PAPER).getItemMeta();
        PersistentDataContainer wrong = malformed.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        wrong.set(PersistentTrashInstanceType.TRASH_ID, PersistentDataType.STRING, "not-a-long");
        malformed.getPersistentDataContainer().set(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER, wrong);
        ItemMeta malformedBefore = malformed.clone();
        ItemMeta output = new ItemStack(Material.PAPER).getItemMeta();
        ItemMeta outputBefore = output.clone();
        expectRuntime(() -> invoke(upgrade, malformed, output, 2));
        check(malformed.equals(malformedBefore) && output.equals(outputBefore),
            "malformed existing Trash instance refused without replacing or mutating it");
        ItemStack result = Objects.requireNonNull(SlimefunItem.getById("DK2_TRASH_9")).getItem().clone();
        result.setItemMeta(current);
        return result;
    }

    private void refusedDeliveries(ItemStack original) throws Throwable {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text("Native capacity probe"));
        evidence.add("CAPACITY-INVENTORY " + inventory.getClass().getName());
        fill(inventory);
        inventory.setItem(0, original.clone());
        Map<Long, ItemStack> registry = registry(original);
        ItemStack[] before = snapshot(inventory);
        boolean accepted = (boolean) invoke(replace, original, inventory,
            (LongPredicate) candidate -> !registry.containsKey(candidate),
            (Consumer<ItemStack>) ignored -> { throw new AssertionError("Full inventory invoked save"); },
            (LongConsumer) ignored -> { throw new AssertionError("Full inventory invoked deletion"); });
        check(!accepted && Arrays.equals(before, inventory.getStorageContents()) && registry.equals(Map.of(ORIGINAL_ID, original)),
            "full native inventory refuses clone without any registry or inventory mutation");
        ItemStack oversized = original.clone();
        oversized.setAmount(65);
        fill(inventory);
        inventory.setItem(19, null);
        before = snapshot(inventory);
        accepted = (boolean) invoke(replace, oversized, inventory,
            (LongPredicate) candidate -> candidate != ORIGINAL_ID,
            (Consumer<ItemStack>) ignored -> { throw new AssertionError("Partial delivery invoked save"); },
            (LongConsumer) ignored -> { throw new AssertionError("Partial delivery invoked deletion"); });
        check(!accepted && Arrays.equals(before, inventory.getStorageContents()),
            "partial native insertion restores all storage slots before refusing clone");
    }

    private void refusedSaves(ItemStack original) throws Throwable {
        for (boolean throwAfterInsert : List.of(false, true)) {
            Map<Long, ItemStack> registry = registry(original);
            Inventory inventory = Bukkit.createInventory(null, 27);
            inventory.setItem(0, new ItemStack(Material.EMERALD, 37));
            ItemStack[] before = snapshot(inventory);
            expectRuntime(() -> invoke(replace, original, inventory,
                (LongPredicate) candidate -> !registry.containsKey(candidate),
                (Consumer<ItemStack>) replacement -> {
                    if (throwAfterInsert) {
                        registry.put(id(replacement), replacement.clone());
                        throw new IllegalStateException("Injected save failure after new record insertion");
                    }
                },
                (LongConsumer) registry::remove));
            check(Arrays.equals(before, inventory.getStorageContents()) && registry.equals(Map.of(ORIGINAL_ID, original)),
                (throwAfterInsert ? "failed" : "refused") + " registry save restores native inventory and keeps only original identity");
        }
    }

    private void malformedClone() throws Throwable {
        ItemStack malformed = new ItemStack(Material.PAPER);
        ItemMeta meta = malformed.getItemMeta();
        PersistentDataContainer bad = meta.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        bad.set(PersistentDankInstanceType.DANK_ID, PersistentDataType.STRING, "bad-id");
        meta.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER, bad);
        malformed.setItemMeta(meta);
        ItemStack before = malformed.clone();
        Inventory inventory = Bukkit.createInventory(null, 27);
        expectRuntime(() -> invoke(replace, malformed, inventory,
            (LongPredicate) ignored -> { throw new AssertionError("Malformed clone reached registry"); },
            (Consumer<ItemStack>) ignored -> { throw new AssertionError("Malformed clone reached save"); },
            (LongConsumer) ignored -> { throw new AssertionError("Malformed clone reached delete"); }));
        check(malformed.equals(before) && Arrays.stream(inventory.getStorageContents()).allMatch(Objects::isNull),
            "malformed Dank ID refused before inventory or registry mutation");
    }

    private void staleGuiGuards(ItemStack original, ItemStack replacement, DankGUI retainedDankMenu,
                                AdminGUI retainedAdminMenu) throws Throwable {
        evidence.add("GUI-SCOPE Real Paper menu instances and registry; tracked Player-interface proxy, no connected player.");
        ConfigManager registry = ConfigManager.getInstance();
        ItemStack before = original.clone();
        List<String> playerApiCalls = new ArrayList<>();
        Player rejectedPlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, api, args) -> {
                String name = api.getName();
                if (name.equals("sendMessage") || name.equals("closeInventory")) {
                    playerApiCalls.add(name);
                    return null;
                }
                throw new AssertionError("Stale GUI must refuse before invoking Player." + name);
            });
        List<String> actions = List.of("stale Admin left click", "stale Dank open", "stale empty-slot deposit",
            "stale existing-slot deposit", "stale withdraw one", "stale withdraw stack", "stale inventory deposit",
            "stale fill inventory", "stale final-item withdrawal and slot clear");
        for (int i = 0; i < actions.size(); i++) {
            playerApiCalls.clear();
            switch (i) {
                case 0 -> click(retainedAdminMenu, rejectedPlayer, 9, false, false);
                case 1 -> retainedDankMenu.open(rejectedPlayer);
                case 2 -> click(retainedDankMenu, rejectedPlayer, 20, false, false);
                case 3 -> click(retainedDankMenu, rejectedPlayer, 26, false, false);
                case 4 -> click(retainedDankMenu, rejectedPlayer, 35, false, false);
                case 5 -> click(retainedDankMenu, rejectedPlayer, 35, true, false);
                case 6 -> click(retainedDankMenu, rejectedPlayer, 35, false, true);
                case 7 -> click(retainedDankMenu, rejectedPlayer, 35, true, true);
                case 8 -> click(retainedDankMenu, rejectedPlayer, 28, false, false);
                default -> throw new AssertionError("Unexpected GUI action");
            }
            check(playerApiCalls.contains("sendMessage") && playerApiCalls.contains("closeInventory"),
                actions.get(i) + " reports refusal and closes the tracked API view");
            check(before.equals(original) && registry.checkDankDeletion(ORIGINAL_ID)
                && registry.getAllPacks().size() == 1 && registry.getAllPacks().contains(replacement),
                actions.get(i) + " leaves original payload and replacement registry unchanged before any cursor/inventory access");
        }
    }

    private static void click(ChestMenu menu, Player player, int slot, boolean right, boolean shift) {
        menu.getMenuClickHandler(slot).onClick(player, slot, menu.getItemInSlot(slot), new ClickAction(right, shift));
    }

    private ItemStack pack() {
        DankPackInstance data = new DankPackInstance(ORIGINAL_ID, 9);
        data.setLastUser("ExactOwner");
        data.setItem(0, richItem(Material.COBBLESTONE, "stored_0"));
        data.setAmount(0, Integer.MAX_VALUE);
        data.setItem(1, new ItemStack(Material.IRON_INGOT));
        data.setAmount(1, 1);
        data.setItem(8, richItem(Material.DIAMOND_SWORD, "stored_8"));
        data.setAmount(8, 37);
        ItemStack pack = Objects.requireNonNull(SlimefunItem.getById("DK2_PACK_9")).getItem().clone();
        ItemMeta meta = pack.getItemMeta();
        meta.displayName(Component.text("Historical Dank Pack", NamedTextColor.DARK_PURPLE));
        meta.lore(List.of(Component.text("Retain exact original lore", NamedTextColor.GOLD)));
        DataTypeMethods.setCustom(meta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, data);
        PersistentDataContainer nested = meta.getPersistentDataContainer().get(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER);
        opaque(nested);
        meta.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER, nested);
        meta.getPersistentDataContainer().set(Keys.DANK_SELECTED_SLOT, PersistentDataType.INTEGER, 8);
        meta.getPersistentDataContainer().set(key("outside:opaque_long"), PersistentDataType.LONG, Long.MAX_VALUE);
        pack.setItemMeta(meta);
        verifyDank(pack);
        return pack;
    }

    private void verifyDank(ItemStack pack) {
        DankPackInstance data = DataTypeMethods.getCustom(pack.getItemMeta(), Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE);
        check(data != null && data.getTier() == 9 && data.getLastUser().equals("ExactOwner") && data.getAmount(0) == Integer.MAX_VALUE
            && data.getAmount(8) == 37 && data.getItems().length == 9 && data.getAmounts().length == 9,
            "Dank tier, exact owner, nine item/count slots and maximum count preserved");
        check(richItem(Material.COBBLESTONE, "stored_0").equals(data.getItem(0))
            && richItem(Material.DIAMOND_SWORD, "stored_8").equals(data.getItem(8)), "stored items retain rich nested metadata");
        PersistentDataContainer nested = pack.getItemMeta().getPersistentDataContainer().get(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER);
        check(Float.valueOf(0.125F).equals(nested.get(key("outside:float"), PersistentDataType.FLOAT))
            && Arrays.equals(new byte[] {0, -1, 2, 127}, nested.get(key("outside:bytes"), PersistentDataType.BYTE_ARRAY))
            && Long.valueOf(ORIGINAL_ID).equals(nested.get(key("outside:nested"), PersistentDataType.TAG_CONTAINER)
                .get(key("outside:counter"), PersistentDataType.LONG)), "unknown FLOAT, BYTE_ARRAY and nested LONG fields retain exact types and values");
    }

    private static ItemStack richItem(Material material, String identity) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Original " + identity, NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Historical lore", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(key("outside:item"), PersistentDataType.STRING, identity);
        meta.getPersistentDataContainer().set(key("outside:counter"), PersistentDataType.LONG, ORIGINAL_ID);
        PersistentDataContainer extra = meta.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        extra.set(key("outside:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {127, 0, -128, 42});
        meta.getPersistentDataContainer().set(key("outside:nested"), PersistentDataType.TAG_CONTAINER, extra);
        if (meta instanceof Damageable damage) {
            damage.setDamage(13);
            meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static void opaque(PersistentDataContainer data) {
        data.set(key("outside:float"), PersistentDataType.FLOAT, 0.125F);
        data.set(key("outside:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 2, 127});
        PersistentDataContainer nested = data.getAdapterContext().newPersistentDataContainer();
        nested.set(key("outside:counter"), PersistentDataType.LONG, ORIGINAL_ID);
        data.set(key("outside:nested"), PersistentDataType.TAG_CONTAINER, nested);
    }

    private static long id(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER)
            .get(PersistentDankInstanceType.DANK_ID, PersistentDataType.LONG);
    }

    private static TrashPackInstance readTrash(ItemMeta meta) {
        return Objects.requireNonNull(DataTypeMethods.getCustom(meta, Keys.TRASH_INSTANCE, PersistentTrashInstanceType.TYPE));
    }

    private static Map<Long, ItemStack> registry(ItemStack original) {
        return new HashMap<>(Map.of(ORIGINAL_ID, original.clone()));
    }

    private static Chest freshChest() {
        World world = Objects.requireNonNull(Bukkit.getWorld("world"));
        world.getBlockAt(1, 100, 1).setType(Material.CHEST, false);
        return (Chest) world.getBlockAt(1, 100, 1).getState();
    }

    private static void fill(Inventory inventory) {
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) inventory.setItem(slot, new ItemStack(Material.STONE, 64));
    }

    private static ItemStack[] snapshot(Inventory inventory) {
        return Arrays.stream(inventory.getStorageContents()).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private ItemStack nativeExpectation(ItemStack item) {
        // YAML aliases identify Java wrapper classes, not stored Minecraft item data. The exact
        // native byte round trip produces an ordinary item, as storing in a Craft inventory does.
        ItemStack expected = ItemStack.deserializeBytes(item.serializeAsBytes());
        check(item.serialize().equals(expected.serialize())
            && item.getItemMeta().getPersistentDataContainer().equals(expected.getItemMeta().getPersistentDataContainer()),
            "native expected-item round trip preserves every serialized component and all typed PDC");
        return expected;
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString(value));
    }

    private static Method method(ClassLoader loader, String className, String name, Class<?>... parameters) throws Exception {
        Method method = Class.forName(className, true, loader).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static Object invoke(Method method, Object... arguments) throws Throwable {
        try { return method.invoke(null, arguments); } catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        evidence.add("OK " + description);
    }

    @FunctionalInterface
    private interface Checked { void run() throws Throwable; }

    private static void expectRuntime(Checked action) throws Throwable {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected refusal with a runtime exception");
    }
}
