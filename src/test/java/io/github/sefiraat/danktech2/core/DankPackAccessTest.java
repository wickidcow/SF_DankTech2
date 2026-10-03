package io.github.sefiraat.danktech2.core;

import io.github.sefiraat.danktech2.testing.DankTestEnvironment;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DankPackAccessTest extends DankTestEnvironment {

    private static final long PACK_ID = 9_007_199_254_740_993L;

    @Test
    void registeredPackRetainsItsLongIdentityContentsCountsAndOwner() {
        ItemStack pack = pack();
        ItemStack before = pack.clone();
        Map<Long, ItemStack> registry = registry(pack);

        DankPackInstance loaded = DankPackAccess.loadExisting(pack, PACK_ID, id -> !registry.containsKey(id));

        assertNotNull(loaded);
        assertEquals(PACK_ID, loaded.getId());
        assertEquals(9, loaded.getTier());
        assertEquals("OriginalOwner", loaded.getLastUser());
        assertEquals(Integer.MAX_VALUE, loaded.getAmount(0));
        assertEquals(37, loaded.getAmount(8));
        assertEquals(Material.COBBLESTONE, loaded.getItem(0).getType());
        assertEquals(Material.DIAMOND, loaded.getItem(8).getType());
        assertEquals(before, pack);
        assertEquals(Map.of(PACK_ID, before), registry);
    }

    @Test
    void accessReloadsCurrentItemDataInsteadOfUsingTheMenuCreationSnapshot() {
        ItemStack pack = pack();
        Map<Long, ItemStack> registry = registry(pack);
        DankPackInstance menuSnapshot = contents(pack);
        DankPackInstance updated = contents(pack);
        // MockBukkit returns shared primitive arrays; create a distinct metadata update.
        updated.setAmounts(updated.getAmounts().clone());
        updated.setAmount(8, 73);
        writeContents(pack, updated);

        DankPackInstance loaded = DankPackAccess.loadExisting(pack, PACK_ID, id -> !registry.containsKey(id));

        assertNotNull(loaded);
        assertEquals(37, menuSnapshot.getAmount(8));
        assertEquals(73, loaded.getAmount(8));
    }

    @Test
    void adminReplacementRevokesEveryOldViewWhileTheDeliveredReplacementRemainsUsable() {
        ItemStack original = pack();
        ItemStack otherAdminSnapshot = original.clone();
        Map<Long, ItemStack> registry = registry(original);
        assertNotNull(DankPackAccess.loadExisting(original, PACK_ID, id -> !registry.containsKey(id)));
        assertNotNull(DankPackAccess.loadExisting(otherAdminSnapshot, PACK_ID, id -> !registry.containsKey(id)));

        assertTrue(AdminPackClone.replace(original, player.getInventory(), id -> !registry.containsKey(id),
            replacement -> registry.put(contents(replacement).getId(), replacement.clone()), registry::remove));

        ItemStack replacement = player.getInventory().getItem(0);
        assertNotNull(replacement);
        long replacementId = contents(replacement).getId();
        assertNotEquals(PACK_ID, replacementId);
        assertNull(DankPackAccess.loadExisting(original, PACK_ID, id -> !registry.containsKey(id)));
        assertNull(DankPackAccess.loadExisting(otherAdminSnapshot, PACK_ID, id -> !registry.containsKey(id)));
        assertNotNull(DankPackAccess.loadExisting(replacement, replacementId, id -> !registry.containsKey(id)));
        assertEquals(Map.of(replacementId, replacement), registry);
        assertEquals(Integer.MAX_VALUE, contents(replacement).getAmount(0));
        assertEquals(37, contents(replacement).getAmount(8));
        assertEquals(PACK_ID, contents(original).getId());
    }

    @Test
    void rejectedAccessDoesNotResurrectADeletedRecordAndAnIntentionalRestoreCanRecoverIt() {
        ItemStack pack = pack();
        Map<Long, ItemStack> registry = registry(pack);
        registry.remove(PACK_ID);

        assertNull(DankPackAccess.loadExisting(pack, PACK_ID, id -> !registry.containsKey(id)));
        assertNull(DankPackAccess.loadExisting(pack, PACK_ID, id -> !registry.containsKey(id)));
        assertTrue(registry.isEmpty());

        registry.put(PACK_ID, pack.clone());
        assertNotNull(DankPackAccess.loadExisting(pack, PACK_ID, id -> !registry.containsKey(id)));
        assertEquals(Map.of(PACK_ID, pack), registry);
    }

    @Test
    void replacingTheBackingItemCannotRebindAnOldMenuToAnotherRegisteredPack() {
        ItemStack original = pack();
        ItemStack replacement = AdminPackClone.prepare(original, Long.MAX_VALUE);
        Map<Long, ItemStack> registry = registry(original);
        registry.put(Long.MAX_VALUE, replacement.clone());
        original.setItemMeta(replacement.getItemMeta());

        assertNull(DankPackAccess.loadExisting(original, PACK_ID, id -> !registry.containsKey(id)));
        assertNotNull(DankPackAccess.loadExisting(original, Long.MAX_VALUE, id -> !registry.containsKey(id)));
        assertEquals(2, registry.size());
    }

    @Test
    void missingInstanceIsRefusedWithoutTreatingTheItemAsANewPack() {
        ItemStack pack = pack();
        ItemMeta meta = pack.getItemMeta();
        meta.getPersistentDataContainer().remove(Keys.DANK_INSTANCE);
        pack.setItemMeta(meta);
        ItemStack before = pack.clone();

        assertNull(DankPackAccess.loadExisting(pack, PACK_ID,
            id -> fail("Missing pack data must be refused before registry access")));
        assertEquals(before, pack);
    }

    @Test
    void removedBackingItemsAreRefusedEvenIfTheRegistryStillContainsTheirOldId() {
        ItemStack pack = pack();
        Map<Long, ItemStack> registry = registry(pack);
        pack.setAmount(0);

        assertNull(DankPackAccess.loadExisting(pack, PACK_ID, id -> !registry.containsKey(id)));
        assertNull(DankPackAccess.loadExisting(new ItemStack(Material.AIR), PACK_ID,
            id -> !registry.containsKey(id)));
        assertNull(DankPackAccess.loadExisting(null, PACK_ID, id -> !registry.containsKey(id)));
        assertEquals(1, registry.size());
    }

    private static Map<Long, ItemStack> registry(ItemStack pack) {
        return new HashMap<>(Map.of(PACK_ID, pack.clone()));
    }

    private static ItemStack pack() {
        DankPackInstance contents = new DankPackInstance(PACK_ID, 9);
        contents.setLastUser("OriginalOwner");
        contents.setItem(0, new ItemStack(Material.COBBLESTONE));
        contents.setAmount(0, Integer.MAX_VALUE);
        contents.setItem(8, new ItemStack(Material.DIAMOND));
        contents.setAmount(8, 37);
        ItemStack pack = new ItemStack(Material.PLAYER_HEAD);
        writeContents(pack, contents);
        return pack;
    }

    private static void writeContents(ItemStack pack, DankPackInstance contents) {
        ItemMeta meta = pack.getItemMeta();
        DataTypeMethods.setCustom(meta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, contents);
        pack.setItemMeta(meta);
    }

    private static DankPackInstance contents(ItemStack pack) {
        return DataTypeMethods.getCustom(pack.getItemMeta(), Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE);
    }
}
