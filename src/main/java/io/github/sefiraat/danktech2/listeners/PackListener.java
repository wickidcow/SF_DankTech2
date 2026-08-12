package io.github.sefiraat.danktech2.listeners;

import io.github.sefiraat.danktech2.DankTech2;
import io.github.sefiraat.danktech2.managers.ConfigManager;
import io.github.sefiraat.danktech2.core.DankGUI;
import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.core.TrashGUI;
import io.github.sefiraat.danktech2.core.TrashPackInstance;
import io.github.sefiraat.danktech2.managers.SupportedPluginManager;
import io.github.sefiraat.danktech2.slimefun.packs.DankPack;
import io.github.sefiraat.danktech2.slimefun.packs.TrashPack;
import io.github.sefiraat.danktech2.theme.ThemeType;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentTrashInstanceType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.MessageFormat;
import java.util.Collection;

public class PackListener implements Listener {

    @EventHandler
    public void onUsePack(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final ItemStack heldItem = player.getInventory().getItemInMainHand();
        final SlimefunItem slimefunItem = SlimefunItem.getByItem(heldItem);
        final Action action = event.getAction();

        if (slimefunItem instanceof DankPack) {
            event.setCancelled(true);
            if (heldItem.getAmount() > 1) {
                player.sendMessage(MessageFormat.format(
                    "{0}Packs must be unstacked before use",
                    ThemeType.WARNING.getColor())
                );
                return;
            }

            final DankPack dankPack = (DankPack) slimefunItem;
            final ItemMeta heldIemMeta = heldItem.getItemMeta();
            DankPackInstance dankPackInstance = DataTypeMethods.getCustom(
                heldIemMeta,
                Keys.DANK_INSTANCE,
                PersistentDankInstanceType.TYPE
            );

            if (dankPackInstance == null) {
                player.sendMessage(MessageFormat.format(
                    "{0}Dank Pack not crafted - creating new instance",
                    ThemeType.WARNING.getColor())
                );
                dankPackInstance = generateNewDankInstance(heldItem, dankPack.getTier());
            }

            if (ConfigManager.getInstance().checkDankDeletion(dankPackInstance.getId())) {
                player.sendMessage(MessageFormat.format(
                    "{0}Dank Pack has been duped or deleted. Removing",
                    ThemeType.ERROR.getColor())
                );
                heldItem.setAmount(0);
                return;
            }

            if (player.isSneaking()) {
                if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                    incrementSlot(heldItem, dankPackInstance, player);
                } else if (action == Action.RIGHT_CLICK_BLOCK) {
                    Block block = event.getClickedBlock().getRelative(event.getBlockFace());
                    tryBuild(heldItem, heldIemMeta, dankPackInstance, block, player);
                }
            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                DankGUI dankGUI = new DankGUI(dankPackInstance, heldItem);
                dankGUI.open(player);
            }
        } else if (slimefunItem instanceof TrashPack) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                final TrashPack trashPack = (TrashPack) slimefunItem;
                TrashPackInstance trashPackInstance = DataTypeMethods.getCustom(heldItem.getItemMeta(), Keys.TRASH_INSTANCE, PersistentTrashInstanceType.TYPE);

                if (trashPackInstance == null) {
                    player.sendMessage(MessageFormat.format(
                        "{0}Trash Pack not crafted - creating new instance",
                        ThemeType.WARNING.getColor())
                    );
                    trashPackInstance = generateNewTrashInstance(heldItem, trashPack.getTier());
                }

                TrashGUI trashGUI = new TrashGUI(trashPackInstance, heldItem);
                trashGUI.open(player);
            }
        }
    }

    private void incrementSlot(ItemStack heldItem, DankPackInstance dankPackInstance, Player player) {
        int slot = DataTypeMethods.incrementSelectedSlot(heldItem);
        ItemStack itemStack = dankPackInstance.getItem(slot);
        String name = itemStack == null ? "Nothing" : ThemeType.toTitleCase(itemStack.getType().toString());
        player.spigot().sendMessage(
            ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(MessageFormat.format(
                "{0}Selected slot: [{1}] - [{2}]",
                ThemeType.SUCCESS.getColor(),
                slot + 1,
                name)
            )
        );
    }

    private void tryBuild(ItemStack heldItem, ItemMeta itemMeta, DankPackInstance dankPackInstance, Block block, Player player) {
        int selectedSlot = DataTypeMethods.getSelectedSlot(heldItem);
        int amount = dankPackInstance.getAmount(selectedSlot);
        ItemStack stackToPlace = dankPackInstance.getItem(selectedSlot);
        if (amount <= 1) {
            player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(MessageFormat.format(
                    "{0}Not enough {1} left!",
                    ThemeType.ERROR.getColor(),
                    ThemeType.toTitleCase(stackToPlace.getType().toString()))
                )
            );
        } else if (!stackToPlace.getType().isBlock() || stackToPlace.hasItemMeta()) {
            player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(MessageFormat.format(
                    "{0}{1} cannot be placed like this.",
                    ThemeType.ERROR.getColor(),
                    ThemeType.toTitleCase(stackToPlace.getType().toString()))
                )
            );
        } else {
            if (isSafeToBuild(block, player)) {
                dankPackInstance.setAmount(selectedSlot, amount - 1);
                block.setType(stackToPlace.getType());
                DankTech2.getSupportedPluginManager().markBlockPlaced(block);
                DataTypeMethods.setCustom(
                    itemMeta,
                    Keys.DANK_INSTANCE,
                    PersistentDankInstanceType.TYPE,
                    dankPackInstance
                );
                heldItem.setItemMeta(itemMeta);
                ConfigManager.getInstance().saveDankPack(heldItem);
            }
        }
    }

    private boolean isSafeToBuild(Block block, Player player) {
        if (block.getBlockData().getMaterial().equals(Material.AIR)
            && Slimefun.getProtectionManager().hasPermission(player, block, Interaction.PLACE_BLOCK)
        ) {
            Collection<Entity> entities = block.getWorld().getNearbyEntities(
                block.getLocation(), 0.5, 0.5, 0.5,
                entity -> entity.getType() != EntityType.ITEM
            );
            return entities.isEmpty();
        }
        return false;
    }

    private DankPackInstance generateNewDankInstance(ItemStack itemStack, int tier) {
        DankPackInstance dankPackInstance = new DankPackInstance(System.currentTimeMillis(), tier);
        ItemMeta itemMeta = itemStack.getItemMeta();
        DataTypeMethods.setCustom(itemMeta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, dankPackInstance);
        itemStack.setItemMeta(itemMeta);
        ConfigManager.getInstance().saveDankPack(itemStack);
        return dankPackInstance;
    }

    private TrashPackInstance generateNewTrashInstance(ItemStack itemStack, int tier) {
        TrashPackInstance trashPackInstance = new TrashPackInstance(System.currentTimeMillis(), tier);
        ItemMeta itemMeta = itemStack.getItemMeta();
        DataTypeMethods.setCustom(itemMeta, Keys.TRASH_INSTANCE, PersistentTrashInstanceType.TYPE, trashPackInstance);
        itemStack.setItemMeta(itemMeta);
        return trashPackInstance;
    }
}
