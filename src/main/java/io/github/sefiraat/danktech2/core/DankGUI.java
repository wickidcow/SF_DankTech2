package io.github.sefiraat.danktech2.core;

import io.github.sefiraat.danktech2.managers.ConfigManager;
import io.github.sefiraat.danktech2.slimefun.packs.DankPack;
import io.github.sefiraat.danktech2.slimefun.packs.TrashPack;
import io.github.sefiraat.danktech2.theme.ThemeType;
import io.github.sefiraat.danktech2.utils.GeneralUtils;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.Skulls;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.MessageFormat;

public class DankGUI extends ChestMenu {

    protected static final int[] BACKGROUND_SLOTS = new int[]{
        0, 1, 2, 3, 5, 6, 7, 8, 36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    protected static final int[] DISPLAY_SLOTS = new int[]{
        9, 10, 11, 12, 13, 14, 15, 16, 17
    };
    protected static final int[] INPUT_SLOTS = new int[]{
        18, 19, 20, 21, 22, 23, 24, 25, 26
    };
    protected static final int[] INTERACTION_SLOTS = new int[]{
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    protected static final int INFO_SLOT = 4;

    protected static final ItemStack EMPTY_STACK = new CustomItemStack(
        Skulls.GUI_EMPTY.getPlayerHead(),
        MessageFormat.format("{0}Slot Empty", ThemeType.PASSIVE.getColor())
    );
    protected static final ItemStack LOCKED_STACK = new CustomItemStack(
        Material.RED_STAINED_GLASS_PANE,
        MessageFormat.format("{0}Slot Locked", ThemeType.PASSIVE.getColor())
    );
    protected static final ItemStack INPUT_STACK = new CustomItemStack(
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        MessageFormat.format("{0}Input", ThemeType.PASSIVE.getColor())
    );


    private DankPackInstance packInstance;
    private final ItemStack itemStack;
    private final DankPack dankPack;

    public DankGUI(DankPackInstance packInstance, ItemStack itemStack) {
        super("Dank Pack - Tier " + packInstance.getTier());
        this.packInstance = packInstance;
        this.itemStack = itemStack;
        this.dankPack = (DankPack) SlimefunItem.getByItem(itemStack);

        ChestMenuUtils.drawBackground(this, BACKGROUND_SLOTS);
        addItem(
            INFO_SLOT,
            getInfoStack(
                itemStack,
                packInstance.getTier(),
                this.dankPack.getCapacityPerSlot().getValue()
            ),
            ChestMenuUtils.getEmptyClickHandler()
        );

        for (int i = 0; i < 9; i++) {
            // Fill with locked slots
            addItem(DISPLAY_SLOTS[i], LOCKED_STACK, ChestMenuUtils.getEmptyClickHandler());
            addItem(INPUT_SLOTS[i], LOCKED_STACK, ChestMenuUtils.getEmptyClickHandler());
            addItem(INTERACTION_SLOTS[i], LOCKED_STACK, ChestMenuUtils.getEmptyClickHandler());
        }

        // Don't let players shift click into the GUI to bypass the handler
        addPlayerInventoryClickHandler((p, slot, item, action) -> !action.isShiftClicked());
        setupAllItems();
    }

    private void setupAllItems() {
        for (int i = 0; i < 9; i++) {

            // No more usable slots, fill with locked slots
            if (i > (packInstance.getTier()) - 1) {
                continue;
            }

            final int finalSlot = i;
            final ItemStack slotStack = packInstance.getItem(i);
            final int amount = packInstance.getAmount(i);

            if (slotStack == null) {
                replaceExistingItem(DISPLAY_SLOTS[i], EMPTY_STACK);

                replaceExistingItem(INPUT_SLOTS[i], INPUT_STACK);
                addMenuClickHandler(INPUT_SLOTS[i], (p, slot, item, action) -> setNewItem(p, finalSlot));

                replaceExistingItem(INTERACTION_SLOTS[i], getInteractionStack(amount));
                addMenuClickHandler(INTERACTION_SLOTS[i], (p, slot, item, action) -> false);
            } else {
                replaceExistingItem(DISPLAY_SLOTS[i], getDisplayStack(slotStack));

                replaceExistingItem(INPUT_SLOTS[i], INPUT_STACK);
                addMenuClickHandler(INPUT_SLOTS[i], (p, slot, item, action) -> addToExistingItem(p, finalSlot));

                replaceExistingItem(INTERACTION_SLOTS[i], getInteractionStack(amount));
                addMenuClickHandler(INTERACTION_SLOTS[i], (player, i1, itemStack1, clickAction) -> interactWithItem(player, clickAction, finalSlot));
            }
        }
    }

    private boolean setNewItem(Player player, int instanceSlot) {
        loadInstance();
        ItemStack heldItem = player.getItemOnCursor();
        if (heldItem.getType() != Material.AIR
            && allowedInDank(heldItem)
        ) {
            ItemStack clone = heldItem.clone();
            clone.setAmount(1);
            heldItem.setAmount(heldItem.getAmount() - 1);
            this.packInstance.setItem(instanceSlot, clone);
            this.packInstance.setAmount(instanceSlot, 1);
            saveInstance();
            setupAllItems();
        }
        return false;
    }

    private boolean addToExistingItem(Player player, int instanceSlot) {
        loadInstance();
        ItemStack heldItem = player.getItemOnCursor();
        int maxAmount = this.dankPack.getCapacityPerSlot().getValue();
        int currentAmount = this.packInstance.getAmount(instanceSlot);
        ItemStack instanceStack = this.packInstance.getItem(instanceSlot);
        if (heldItem.getType() != Material.AIR && heldItem.isSimilar(instanceStack)) {
            ItemStack clone = heldItem.clone();
            long total = (long) currentAmount + clone.getAmount();
            if (total <= maxAmount) {
                heldItem.setAmount(0);
                this.packInstance.setAmount(instanceSlot, (int) total);
            }
            saveInstance();
            setupAllItems();
        }
        return false;
    }

    private boolean interactWithItem(Player player, ClickAction clickAction, int instanceSlot) {
        loadInstance();

        if (clickAction.isShiftClicked()) {
            if (clickAction.isRightClicked()) {
                withdrawMax(player, instanceSlot);
            } else {
                depositAll(player, instanceSlot);
            }
        } else {
            if (clickAction.isRightClicked()) {
                withdrawStack(player, instanceSlot);
            } else {
                withdrawOne(player, instanceSlot);
            }
        }

        return false;
    }

    private void withdrawOne(Player player, int instanceSlot) {
        int firstEmpty = player.getInventory().firstEmpty();
        if (firstEmpty != -1) {
            final int amount = this.packInstance.getAmount(instanceSlot);
            final ItemStack itemToWithdraw = this.packInstance.getItem(instanceSlot).clone();
            itemToWithdraw.setAmount(1);
            if (amount == 1) {
                this.packInstance.clearItem(instanceSlot);
            } else {
                this.packInstance.setAmount(instanceSlot, amount - 1);
            }
            player.getInventory().addItem(itemToWithdraw);
            saveInstance();
            setupAllItems();
        }
    }

    private void withdrawStack(Player player, int instanceSlot) {
        int firstEmpty = player.getInventory().firstEmpty();
        if (firstEmpty != -1) {
            final int amount = this.packInstance.getAmount(instanceSlot);
            final ItemStack itemToWithdraw = this.packInstance.getItem(instanceSlot).clone();
            final int maxStackSize = itemToWithdraw.getMaxStackSize();
            if (amount <= maxStackSize) {
                itemToWithdraw.setAmount(amount - 1);
                this.packInstance.setAmount(instanceSlot, 1);
            } else {
                itemToWithdraw.setAmount(maxStackSize);
                this.packInstance.setAmount(instanceSlot, amount - maxStackSize);
            }
            player.getInventory().addItem(itemToWithdraw);
            saveInstance();
            setupAllItems();
        }
    }

    private void depositAll(Player player, int instanceSlot) {
        final int maxAmount = this.dankPack.getCapacityPerSlot().getValue();
        final ItemStack itemToDeposit = this.packInstance.getItem(instanceSlot).clone();

        for (ItemStack testItem : player.getInventory().getStorageContents()) {
            if (testItem != null && testItem.isSimilar(itemToDeposit)) {
                int amount = this.packInstance.getAmount(instanceSlot);
                int inAmount = testItem.getAmount();
                long testAmount = (long) amount + inAmount;
                if (testAmount > maxAmount) {
                    this.packInstance.setAmount(instanceSlot, maxAmount);
                    testItem.setAmount((int) (testAmount - maxAmount));
                    break;
                } else {
                    this.packInstance.setAmount(instanceSlot, (int) testAmount);
                    testItem.setAmount(0);
                }
            }
        }
        saveInstance();
        setupAllItems();
    }

    private void withdrawMax(Player player, int instanceSlot) {
        final int firstEmpty = player.getInventory().firstEmpty();
        if (firstEmpty != -1) {
            final ItemStack itemToWithdraw = this.packInstance.getItem(instanceSlot).clone();
            final int maxStackSize = itemToWithdraw.getMaxStackSize();
            int freeSlots = GeneralUtils.getEmptySlots(player);
            int amount = this.packInstance.getAmount(instanceSlot);
            int withdrawAmount = (maxStackSize * freeSlots);

            if (amount <= withdrawAmount) {
                withdrawAmount = amount - 1;
            }

            itemToWithdraw.setAmount(withdrawAmount);
            this.packInstance.setAmount(instanceSlot, amount - withdrawAmount);

            do {
                ItemStack giveItem = itemToWithdraw.clone();
                giveItem.setAmount(Math.min(itemToWithdraw.getAmount(), itemToWithdraw.getMaxStackSize()));
                itemToWithdraw.setAmount(itemToWithdraw.getAmount() - giveItem.getAmount());
                player.getInventory().addItem(giveItem);
            } while (
                itemToWithdraw.getAmount() > 0
            );

            saveInstance();
            setupAllItems();
        }
    }

    @Override
    public void open(Player... players) {
        this.packInstance.setLastUser(players[0].getDisplayName());
        saveInstance();
        super.open(players);
    }

    private void loadInstance() {
        this.packInstance = DataTypeMethods.getCustom(
            this.itemStack.getItemMeta(),
            Keys.DANK_INSTANCE,
            PersistentDankInstanceType.TYPE
        );
    }

    private void saveInstance() {
        ItemMeta itemMeta = this.itemStack.getItemMeta();
        DataTypeMethods.setCustom(
            itemMeta,
            Keys.DANK_INSTANCE,
            PersistentDankInstanceType.TYPE,
            packInstance
        );
        this.itemStack.setItemMeta(itemMeta);
        ConfigManager.getInstance().saveDankPack(this.itemStack);
    }

    public boolean allowedInDank(ItemStack itemStack) {
        SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);
        return !(slimefunItem instanceof DankPack) && !(slimefunItem instanceof TrashPack);
    }

    protected static ItemStack getDisplayStack(ItemStack itemStack) {
        return itemStack.clone();
    }

    protected static ItemStack getInteractionStack(int amount) {
        return new CustomItemStack(
            Skulls.GUI_WITHDRAW.getPlayerHead(), ThemeType.CLICK_INFO.getColor() + "Add/Withdraw Items",
            "",
            MessageFormat.format("{0}Left Click: {1}Withdraw One", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor()),
            MessageFormat.format("{0}Right Click: {1}Withdraw Stack", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor()),
            MessageFormat.format("{0}Shift Left Click: {1}Input Whole Inventory", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor()),
            MessageFormat.format("{0}Shift Right Click: {1}Fill Inventory", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor()),
            "",
            MessageFormat.format("{0}Amount: {1}{2}", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor(), amount)
        );
    }

    protected static ItemStack getInfoStack(ItemStack itemStack, int tier, int max) {
        return new CustomItemStack(
            itemStack.clone(),
            MessageFormat.format("{0}Dank Pack Info", ThemeType.CLICK_INFO.getColor()),
            "",
            MessageFormat.format("{0}Tier: {1}{2}", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor(), tier),
            MessageFormat.format("{0}Slots: {1}{2}", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor(), tier),
            MessageFormat.format("{0}Max Per Slot: {1}{2}", ThemeType.CLICK_INFO.getColor(), ThemeType.PASSIVE.getColor(), max)
        );
    }
}
