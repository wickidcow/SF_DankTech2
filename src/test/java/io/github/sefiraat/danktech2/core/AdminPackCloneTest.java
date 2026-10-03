package io.github.sefiraat.danktech2.core;

import io.github.sefiraat.danktech2.testing.DankTestEnvironment;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryMock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AdminPackCloneTest extends DankTestEnvironment {

    private static final long ORIGINAL_ID = 9_007_199_254_740_993L;

    @Test
    void preparationChangesOnlyTheLongIdAndPreservesEveryOtherItemField() {
        ItemStack original = pack();
        ItemStack before = original.clone();
        ItemStack replacement = AdminPackClone.prepare(original, Long.MAX_VALUE);

        assertEquals(before, original);
        assertEquals(ORIGINAL_ID, idOf(original));
        assertEquals(Long.MAX_VALUE, idOf(replacement));
        assertEquals(original, AdminPackClone.prepare(replacement, ORIGINAL_ID));

        DankPackInstance contents = DataTypeMethods.getCustom(replacement.getItemMeta(),
            Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE);
        assertNotNull(contents);
        assertEquals(9, contents.getTier());
        assertEquals("ExactOwner", contents.getLastUser());
        assertEquals(Integer.MAX_VALUE, contents.getAmount(0));
        assertEquals(37, contents.getAmount(8));
        assertEquals(Material.COBBLESTONE, contents.getItem(0).getType());
        assertEquals(Material.DIAMOND_SWORD, contents.getItem(8).getType());
    }

    @Test
    void replacementCannotReuseTheOriginalOrAnotherRegisteredLongId() {
        ItemStack original = pack();
        assertThrows(IllegalArgumentException.class, () -> AdminPackClone.prepare(original, ORIGINAL_ID));
        assertEquals(ORIGINAL_ID + 3, AdminPackClone.nextAvailableId(ORIGINAL_ID, ORIGINAL_ID,
            id -> id > ORIGINAL_ID + 2));
        assertEquals(ORIGINAL_ID, idOf(original));
    }

    @Test
    void fullStorageDoesNotDeleteTheOriginalOrRegisterAnUndeliveredClone() {
        ItemStack original = pack();
        Map<Long, ItemStack> registry = registry(original);
        StorageInventory inventory = new StorageInventory(player);
        fillStorage(inventory);
        inventory.setItem(0, original.clone());
        ItemStack[] before = inventory.getStorageContents();
        AtomicBoolean saved = new AtomicBoolean();
        AtomicBoolean deleted = new AtomicBoolean();

        assertFalse(AdminPackClone.replace(original, inventory, id -> !registry.containsKey(id),
            replacement -> saved.set(true), id -> deleted.set(true)));

        assertFalse(saved.get());
        assertFalse(deleted.get());
        assertArrayEquals(before, inventory.getStorageContents());
        assertEquals(Map.of(ORIGINAL_ID, original), registry);
        assertTrue(inventory.getItemInOffHand().isEmpty());
    }

    @Test
    void successfulReplacementIsDeliveredAndRegisteredBeforeTheOriginalIsDeleted() {
        ItemStack original = pack();
        Map<Long, ItemStack> registry = registry(original);
        StorageInventory inventory = new StorageInventory(player);
        fillStorage(inventory);
        inventory.setItem(19, null);
        List<String> operations = new ArrayList<>();

        assertTrue(AdminPackClone.replace(original, inventory, id -> !registry.containsKey(id), replacement -> {
            assertEquals(replacement, inventory.getItem(19));
            assertEquals(original, registry.get(ORIGINAL_ID));
            assertNotEquals(ORIGINAL_ID, idOf(replacement));
            registry.put(idOf(replacement), replacement.clone());
            operations.add("save");
        }, id -> {
            assertEquals(ORIGINAL_ID, id);
            assertEquals(2, registry.size());
            assertTrue(registry.containsKey(idOf(inventory.getItem(19))));
            registry.remove(id);
            operations.add("delete");
        }));

        assertEquals(List.of("save", "delete"), operations);
        assertEquals(1, registry.size());
        assertFalse(registry.containsKey(ORIGINAL_ID));
        assertEquals(inventory.getItem(19), registry.get(idOf(inventory.getItem(19))));
        assertEquals(original, AdminPackClone.prepare(inventory.getItem(19), ORIGINAL_ID));
        assertEquals(ORIGINAL_ID, idOf(original));
    }

    @Test
    void staleAdminMenuCannotCreateAnotherCloneAfterTheOriginalWasReplaced() {
        ItemStack original = pack();
        Map<Long, ItemStack> registry = registry(original);
        StorageInventory inventory = new StorageInventory(player);
        assertTrue(AdminPackClone.replace(original, inventory, id -> !registry.containsKey(id),
            replacement -> registry.put(idOf(replacement), replacement.clone()), registry::remove));
        Map<Long, ItemStack> registryBefore = new HashMap<>(registry);
        ItemStack[] inventoryBefore = inventory.getStorageContents();

        assertFalse(AdminPackClone.replace(original, inventory, id -> !registry.containsKey(id),
            replacement -> fail("The stale menu must not create another replacement"),
            id -> fail("The stale menu must not delete any registered pack")));

        assertEquals(registryBefore, registry);
        assertArrayEquals(inventoryBefore, inventory.getStorageContents());
        assertEquals(1, registry.size());
    }

    @Test
    void partialDeliveryRestoresEveryStorageSlotAndLeavesTheOriginalRegistered() {
        ItemStack original = pack();
        original.setAmount(65);
        Map<Long, ItemStack> registry = registry(original);
        StorageInventory inventory = new StorageInventory(player);
        fillStorage(inventory);
        inventory.setItem(19, null);
        ItemStack[] before = inventory.getStorageContents();
        inventory.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        inventory.setItemInOffHand(new ItemStack(Material.SHIELD));

        assertFalse(AdminPackClone.replace(original, inventory, id -> !registry.containsKey(id),
            replacement -> fail("A partially delivered clone must not be registered"),
            id -> fail("A partially delivered clone must not invalidate a pack")));

        assertArrayEquals(before, inventory.getStorageContents());
        assertEquals(Map.of(ORIGINAL_ID, original), registry);
        assertEquals(new ItemStack(Material.DIAMOND_HELMET), inventory.getHelmet());
        assertEquals(new ItemStack(Material.SHIELD), inventory.getItemInOffHand());
    }

    @Test
    void rejectedRegistrySaveRollsBackDeliveryWithoutInvalidatingTheOriginal() {
        ItemStack original = pack();
        Map<Long, ItemStack> registry = registry(original);
        StorageInventory inventory = new StorageInventory(player);
        inventory.setItem(0, new ItemStack(Material.EMERALD, 37));
        ItemStack[] before = inventory.getStorageContents();

        assertThrows(IllegalStateException.class, () -> AdminPackClone.replace(original, inventory,
            id -> !registry.containsKey(id), replacement -> {
                // ConfigManager refuses items that are not a registered DankPack.
            }, registry::remove));

        assertEquals(Map.of(ORIGINAL_ID, original), registry);
        assertArrayEquals(before, inventory.getStorageContents());
    }

    @Test
    void failedRegistrySaveRemovesOnlyTheIncompleteReplacementAndRestoresDelivery() {
        ItemStack original = pack();
        Map<Long, ItemStack> registry = registry(original);
        StorageInventory inventory = new StorageInventory(player);
        inventory.setItem(0, new ItemStack(Material.EMERALD, 37));
        ItemStack[] before = inventory.getStorageContents();
        IllegalStateException failure = new IllegalStateException("Synthetic registry save failure");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> AdminPackClone.replace(
            original, inventory, id -> !registry.containsKey(id), replacement -> {
                registry.put(idOf(replacement), replacement.clone());
                throw failure;
            }, registry::remove)));

        assertEquals(Map.of(ORIGINAL_ID, original), registry);
        assertArrayEquals(before, inventory.getStorageContents());
    }

    @Test
    void inventoryArgumentMutationDoesNotChangeTheRegisteredOrOriginalStack() {
        ItemStack original = pack();
        original.setAmount(7);
        Map<Long, ItemStack> registry = registry(original);
        Inventory inventory = new StorageInventory(player) {
            @Override
            public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
                ItemStack[] delivered = Arrays.stream(items).map(ItemStack::clone).toArray(ItemStack[]::new);
                HashMap<Integer, ItemStack> leftovers = super.addItem(delivered);
                items[0].setAmount(0);
                return leftovers;
            }
        };

        assertTrue(AdminPackClone.replace(original, inventory, id -> !registry.containsKey(id),
            replacement -> registry.put(idOf(replacement), replacement.clone()), registry::remove));

        ItemStack delivered = inventory.getItem(0);
        assertNotNull(delivered);
        assertEquals(7, delivered.getAmount());
        assertEquals(delivered, registry.get(idOf(delivered)));
        assertEquals(7, original.getAmount());
        assertEquals(ORIGINAL_ID, idOf(original));
    }

    private ItemStack pack() {
        DankPackInstance contents = new DankPackInstance(ORIGINAL_ID, 9);
        contents.setLastUser("ExactOwner");
        contents.setItem(0, new ItemStack(Material.COBBLESTONE));
        contents.setAmount(0, Integer.MAX_VALUE);
        ItemStack stored = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta storedMeta = stored.getItemMeta();
        storedMeta.displayName(Component.text("Stored item name"));
        storedMeta.getPersistentDataContainer().set(NamespacedKey.fromString("otherplugin:stored_item"),
            PersistentDataType.STRING, "keep");
        stored.setItemMeta(storedMeta);
        contents.setItem(8, stored);
        contents.setAmount(8, 37);

        ItemStack pack = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = pack.getItemMeta();
        meta.displayName(Component.text("Restored Dank Pack"));
        meta.lore(List.of(Component.text("Existing lore")));
        DataTypeMethods.setCustom(meta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, contents);
        PersistentDataContainer data = meta.getPersistentDataContainer().get(
            Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER);
        assertNotNull(data);
        data.set(Keys.newKey("unknown_nested_data"), PersistentDataType.BYTE_ARRAY, new byte[]{1, 2, 3});
        meta.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER, data);
        meta.getPersistentDataContainer().set(Keys.DANK_SELECTED_SLOT, PersistentDataType.INTEGER, 8);
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("slimefun:slimefun_item"),
            PersistentDataType.STRING, "DK2_PACK_9");
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("otherplugin:unknown_data"),
            PersistentDataType.LONG, Long.MAX_VALUE);
        pack.setItemMeta(meta);
        return pack;
    }

    private static Map<Long, ItemStack> registry(ItemStack original) {
        return new HashMap<>(Map.of(ORIGINAL_ID, original.clone()));
    }

    private static void fillStorage(Inventory inventory) {
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, 64));
        }
    }

    private static long idOf(ItemStack pack) {
        PersistentDataContainer contents = pack.getItemMeta().getPersistentDataContainer().get(
            Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER);
        assertNotNull(contents);
        return contents.get(PersistentDankInstanceType.DANK_ID, PersistentDataType.LONG);
    }

    /** MockBukkit 4.110's inherited addItem includes armor/offhand; Bukkit uses only storage. */
    private static class StorageInventory extends PlayerInventoryMock {
        StorageInventory(HumanEntity holder) {
            super(holder);
        }

        @Override
        public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
            InventoryMock storage = new InventoryMock(getHolder(), getStorageContents().length, InventoryType.CHEST);
            storage.setStorageContents(getStorageContents());
            HashMap<Integer, ItemStack> leftovers = storage.addItem(items);
            setStorageContents(storage.getStorageContents());
            return leftovers;
        }
    }
}
