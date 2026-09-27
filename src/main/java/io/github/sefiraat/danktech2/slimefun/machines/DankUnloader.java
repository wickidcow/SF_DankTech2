package io.github.sefiraat.danktech2.slimefun.machines;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.managers.ConfigManager;
import io.github.sefiraat.danktech2.slimefun.Machines;
import io.github.sefiraat.danktech2.slimefun.packs.DankPack;
import io.github.sefiraat.danktech2.theme.ThemeType;
import io.github.sefiraat.danktech2.utils.ArmourStandUtils;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.ParticleUtils;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DankUnloader extends SlimefunItem {

    private static final int[] BACKGROUND_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] TOGGLE_SLOTS = new int[]{
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int INPUT_SLOT = 10;
    private static final int OUTPUT_SLOT = 16;

    private final Map<Location, Boolean[]> toggleStates = new HashMap<>();
    private final Map<Location, UUID> armorStands = new HashMap<>();

    @ParametersAreNonnullByDefault
    public DankUnloader(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(
            getBlockBreakHandler(),
            new BlockTicker() {
                @Override
                public boolean isSynchronized() {
                    return true;
                }

                @Override
                public void tick(Block block, SlimefunItem item, Config data) {
                    final BlockMenu blockMenu = BlockStorage.getInventory(block);
                    final ItemStack inputItem = blockMenu.getItemInSlot(INPUT_SLOT);
                    final SlimefunItem slimefunItem = SlimefunItem.getByItem(inputItem);

                    if (!(slimefunItem instanceof DankPack)) {
                        // clearDisplay(blockMenu);
                        return;
                    }

                    if (blockMenu.getItemInSlot(OUTPUT_SLOT) != null) {
                        return;
                    }

                    // setDisplay(blockMenu, inputItem.clone());

                    final ItemMeta inputMeta = inputItem.getItemMeta();
                    final DankPack dankPack = (DankPack) slimefunItem;
                    final DankPackInstance dankPackInstance = DataTypeMethods.getCustom(inputMeta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE);
                    final Boolean[] booleans = toggleStates.get(blockMenu.getLocation());

                    if (ConfigManager.getInstance().checkDankDeletion(dankPackInstance.getId())) {
                        inputItem.setAmount(0);
                        return;
                    }

                    for (int i = 0; i < dankPack.getSlots(); i++) {
                        if (Boolean.FALSE.equals(booleans[i])) {
                            continue;
                        }

                        final ItemStack dankItem = dankPackInstance.getItem(i);
                        final int currentAmount = dankPackInstance.getAmount(i);

                        if (dankItem == null || currentAmount == 1) {
                            continue;
                        }

                        final int amountToRemove = Math.min(currentAmount - 1, dankItem.getMaxStackSize());
                        final ItemStack outputItem = dankItem.clone();

                        outputItem.setAmount(amountToRemove);
                        dankPackInstance.setAmount(i, currentAmount - amountToRemove);
                        DataTypeMethods.setCustom(inputMeta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, dankPackInstance);
                        inputItem.setItemMeta(inputMeta);
                        blockMenu.pushItem(outputItem, OUTPUT_SLOT);
                        ParticleUtils.displayParticleEffect(blockMenu.getLocation().clone().add(0.5,0.5,0.5), 0.3, 4, new Particle.DustOptions(Color.RED, 1));
                        return;
                    }
                }
            }
        );
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {
            @Override
            public void init() {
                drawBackground(BACKGROUND_SLOTS);
                for (int slot = 0; slot < 9; slot++) {
                    addItem(
                        TOGGLE_SLOTS[slot],
                        getSlotOffStack(slot),
                        (p, slot1, item, action) -> false
                    );
                }
            }

            @Override
            public boolean canOpen(@NotNull Block block, @NotNull Player player) {
                return Machines.getDankCrafter().canUse(player, false)
                    && Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.WITHDRAW) {
                    return new int[]{DankUnloader.OUTPUT_SLOT};
                }
                return new int[]{DankUnloader.INPUT_SLOT};
            }

            @Override
            public void newInstance(@NotNull BlockMenu menu, @NotNull Block b) {
                String slotInfoString = BlockStorage.getLocationInfo(menu.getLocation(), "slots");
                String standInfoString = BlockStorage.getLocationInfo(menu.getLocation(), "stand");

                // Toggle States
                Boolean[] booleans = new Boolean[9];
                if (slotInfoString == null) {
                    booleans = new Boolean[]{false, false, false, false, false, false, false, false, false};
                    BlockStorage.addBlockInfo(menu.getLocation(), "slots", Arrays.toString(booleans));
                } else {
                    int i = 0;
                    Iterable<String> strings = Splitter.on(",")
                        .trimResults(CharMatcher.whitespace().or(CharMatcher.anyOf("[]")))
                        .split(slotInfoString);
                    for (String s : strings) {
                        boolean parseBoolean = Boolean.parseBoolean(s);
                        booleans[i] = parseBoolean;
                        i++;
                    }
                }
                toggleStates.put(menu.getLocation(), booleans);
                for (int slot = 0; slot < 9; slot++) {
                    final boolean toggle = booleans[slot];
                    final int finalSlot = slot;
                    menu.addMenuClickHandler(TOGGLE_SLOTS[slot], (p, slot1, item, action) -> toggleButton(menu, finalSlot));
                    if (toggle) {
                        menu.replaceExistingItem(
                            TOGGLE_SLOTS[slot],
                            getSlotOnStack(slot)
                        );
                    } else {
                        menu.replaceExistingItem(
                            TOGGLE_SLOTS[slot],
                            getSlotOffStack(slot)
                        );
                    }
                }

//                // ArmorStand
//                ArmorStand armorStand;
//                if (standInfoString == null) {
//                    Location spawnLocation = menu.getLocation().clone().add(0.5, -1.3, 0.5);
//                    armorStand = (ArmorStand) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);
//                    ArmourStandUtils.setDisplay(armorStand);
//                    BlockStorage.addBlockInfo(menu.getLocation(), "stand", armorStand.getUniqueId().toString());
//                } else {
//                    armorStand = (ArmorStand) Bukkit.getEntity(UUID.fromString(standInfoString));
//                }
//                armorStands.put(menu.getLocation(), armorStand.getUniqueId());
            }
        };
    }

//    private ArmorStand getArmorStand(BlockMenu menu) {
//        return (ArmorStand) Bukkit.getEntity(armorStands.get(menu.getLocation()));
//    }

//    private void clearDisplay(BlockMenu menu) {
//        ArmorStand armorStand = getArmorStand(menu);
//        ArmourStandUtils.clearDisplayItem(armorStand);
//    }

//    private void setDisplay(BlockMenu menu, ItemStack itemStack) {
//        ArmorStand armorStand = getArmorStand(menu);
//        ArmourStandUtils.setDisplayItem(armorStand, itemStack);
//    }

    private void syncBlockInfo(BlockMenu menu) {
        BlockStorage.addBlockInfo(
            menu.getLocation(),
            "slots",
            Arrays.toString(toggleStates.get(menu.getLocation()))
        );
    }

    private boolean toggleButton(BlockMenu blockMenu, int slot) {
        Location location = blockMenu.getLocation();
        Boolean[] booleans = toggleStates.get(location);
        boolean toggle = booleans[slot];
        booleans[slot] = !toggle;
        toggleStates.put(location, booleans);
        blockMenu.replaceExistingItem(
            TOGGLE_SLOTS[slot],
            !toggle ? getSlotOnStack(slot) : getSlotOffStack(slot)
        );
        syncBlockInfo(blockMenu);
        return false;
    }

    private BlockBreakHandler getBlockBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent event, ItemStack itemStack, List<ItemStack> drops) {
                BlockMenu menu = BlockStorage.getInventory(event.getBlock());
                // getArmorStand(menu).remove();
                menu.dropItems(menu.getLocation(), INPUT_SLOT);
                menu.dropItems(menu.getLocation(), OUTPUT_SLOT);
                BlockStorage.clearBlockInfo(event.getBlock());
            }
        };
    }

    private static ItemStack getSlotOnStack(int slot) {
        return new CustomItemStack(
            Material.LIME_STAINED_GLASS_PANE,
            ThemeType.CLICK_INFO.getColor() + "Unloading Slot " + slot,
            ThemeType.PASSIVE.getColor() + "Click to toggle unloading."
        );
    }

    private static ItemStack getSlotOffStack(int slot) {
        return new CustomItemStack(
            Material.RED_STAINED_GLASS_PANE,
            ThemeType.CLICK_INFO.getColor() + "Not Unloading Slot " + slot,
            ThemeType.PASSIVE.getColor() + "Click to toggle unloading."
        );
    }
}
