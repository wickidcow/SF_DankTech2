package io.github.sefiraat.danktech2.core;

import io.github.sefiraat.danktech2.managers.ConfigManager;
import io.github.sefiraat.danktech2.theme.ThemeType;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.MessageFormat;
import java.util.List;

public class AdminGUI extends ChestMenu {

    protected static final int[] BACKGROUND_SLOTS = new int[]{
        0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 47, 48, 49, 50, 51, 53
    };
    protected static final int PAGE_BACK_SLOT = 46;
    protected static final int PAGE_FORWARD_SLOT = 52;

    protected static final ItemStack PAGE_BACK_STACK = new CustomItemStack(
        Material.PAPER,
        MessageFormat.format("{0}Page Back", ThemeType.PASSIVE.getColor())
    );
    protected static final ItemStack PAGE_FORWARD_STACK = new CustomItemStack(
        Material.PAPER,
        MessageFormat.format("{0}Page Forward", ThemeType.PASSIVE.getColor())
    );
    protected static final ItemStack EMPTY_STACK = new CustomItemStack(
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        MessageFormat.format("{0}Empty", ThemeType.PASSIVE.getColor())
    );


    private final List<ItemStack> itemStacks;
    private int page = 1;
    private final int maxPages;

    public AdminGUI() {
        super("Dank Admin");

        ChestMenuUtils.drawBackground(this, BACKGROUND_SLOTS);

        this.itemStacks = ConfigManager.getInstance().getAllPacks();

        maxPages = (int) Math.ceil(this.itemStacks.size() / 36D);

        addItem(
            PAGE_BACK_SLOT,
            PAGE_BACK_STACK,
            (p, slot, item, action) -> {
                if (this.page > 1) {
                    this.page--;
                } else {
                    this.page = 1;
                }
                setupAllItems();
                return false;
            }
        );
        addItem(
            PAGE_FORWARD_SLOT,
            PAGE_FORWARD_STACK,
            (p, slot, item, action) -> {
                if (this.page < maxPages) {
                    this.page++;
                } else {
                    this.page = maxPages;
                }
                setupAllItems();
                return false;
            }
        );

        for (int i = 0; i < 36; i++) {
            addItem(9 + i, EMPTY_STACK, (p, slot, item, action) -> false);
        }

        // Don't let players shift click into the GUI to bypass the handler
        addPlayerInventoryClickHandler((p, slot, item, action) -> !action.isShiftClicked());
        setupAllItems();
    }

    private void setupAllItems() {
        int start = (page - 1) * 36;
        int testEnd = start + 36;
        int size = itemStacks.size();
        int end = Math.min(testEnd, size);
        List<ItemStack> itemStacksSubset = itemStacks.subList(start, end);
        for (int i = 0; i < 36; i++) {
            if (i >= itemStacksSubset.size()) {
                replaceExistingItem(9 + i, EMPTY_STACK);
                addMenuClickHandler(9 + i, (p, slot, item, action) -> false);
            } else {
                ItemStack dank = itemStacksSubset.get(i);
                final DankPackInstance dankPackInstance = DataTypeMethods.getCustom(
                    dank.getItemMeta(),
                    Keys.DANK_INSTANCE,
                    PersistentDankInstanceType.TYPE
                );
                replaceExistingItem(9 + i, getDisplayDank(dank, dankPackInstance));
                addMenuClickHandler(9 + i, (p, slot, item, action) -> {
                    if (action.isRightClicked()) {
                        p.getInventory().addItem(cloneDank(dank));
                        p.closeInventory();
                    } else {
                        DankGUI dankGUI = new DankGUI(dankPackInstance, dank);
                        dankGUI.open(p);
                    }
                    return false;
                });
            }
        }
    }

    private ItemStack cloneDank(ItemStack dank) {
        ItemStack cloneDank = dank.clone();
        ItemMeta cloneMeta = cloneDank.getItemMeta();
        final DankPackInstance dankPackInstance = DataTypeMethods.getCustom(
            cloneMeta,
            Keys.DANK_INSTANCE,
            PersistentDankInstanceType.TYPE
        );
        ConfigManager.getInstance().deletePack(dankPackInstance.getId());
        dankPackInstance.setId(System.currentTimeMillis());
        DataTypeMethods.setCustom(cloneMeta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, dankPackInstance);
        cloneDank.setItemMeta(cloneMeta);
        ConfigManager.getInstance().saveDankPack(cloneDank);
        return cloneDank;
    }

    private ItemStack getDisplayDank(ItemStack dank, DankPackInstance dankPackInstance) {
        ItemStack displayDank = dank.clone();
        ItemMeta displayMeta = displayDank.getItemMeta();
        List<String> lore = displayMeta.getLore();
        lore.add("");
        lore.add(MessageFormat.format("{0}Left Click: {1}Open Dank", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor()));
        lore.add(MessageFormat.format("{0}Right Click: {1}Clone Dank", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor()));
        lore.add("");
        lore.add(MessageFormat.format("{0}Warning - Cloning a DankPack", ThemeType.ERROR.getColor()));
        lore.add(MessageFormat.format("{0}will delete the original and", ThemeType.ERROR.getColor()));
        lore.add(MessageFormat.format("{0}remove them from players inventories", ThemeType.ERROR.getColor()));
        lore.add(MessageFormat.format("{0}and unloaders", ThemeType.ERROR.getColor()));
        lore.add("");
        lore.add(MessageFormat.format("{0}Last User: {1}{2}", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor(), dankPackInstance.getLastUser()));
        displayMeta.setLore(lore);
        displayDank.setItemMeta(displayMeta);
        return displayDank;
    }
}
