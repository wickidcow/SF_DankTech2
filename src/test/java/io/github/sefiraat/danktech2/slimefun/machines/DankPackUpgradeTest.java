package io.github.sefiraat.danktech2.slimefun.machines;

import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.testing.DankTestEnvironment;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DankPackUpgradeTest extends DankTestEnvironment {
    @Test
    void deletedOriginalCannotBeUpgradedOrReRegistered() {
        ItemMeta source = storedPack();
        ItemMeta sourceBefore = source.clone();
        ItemMeta destination = new ItemStack(Material.PAPER).getItemMeta();
        ItemMeta destinationBefore = destination.clone();

        assertFalse(DankPackUpgrade.copyInstance(source, destination, 2, id -> {
            assertEquals(9007199254740993L, id);
            return true;
        }));

        assertEquals(sourceBefore, source);
        assertEquals(destinationBefore, destination, "A refused upgrade must not prepare an output instance");
    }

    @Test
    void registeredPackUpgradeChangesOnlyTierAndRetainsOpaquePayload() {
        ItemMeta source = storedPack();
        var original = source.getPersistentDataContainer().get(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER);
        original.set(NamespacedKey.fromString("oldaddon:opaque"), PersistentDataType.BYTE_ARRAY,
            new byte[] {0, -1, 37});
        source.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER, original);
        ItemMeta sourceBefore = source.clone();
        ItemMeta destination = new ItemStack(Material.PAPER).getItemMeta();

        assertTrue(DankPackUpgrade.copyInstance(source, destination, 2, id -> false));

        var expected = original.getAdapterContext().newPersistentDataContainer();
        original.copyTo(expected, true);
        expected.set(PersistentDankInstanceType.DANK_TIER, PersistentDataType.INTEGER, 2);
        assertEquals(expected, destination.getPersistentDataContainer().get(Keys.DANK_INSTANCE,
            PersistentDataType.TAG_CONTAINER));
        DankPackInstance instance = DataTypeMethods.getCustom(destination, Keys.DANK_INSTANCE,
            PersistentDankInstanceType.TYPE);
        assertEquals(9007199254740993L, instance.getId());
        assertEquals(Integer.MAX_VALUE, instance.getAmount(0));
        assertEquals("Original owner", instance.getLastUser());
        assertEquals(sourceBefore, source);
    }

    @Test
    void firstCraftStillCreatesARegisterableEmptyPack() {
        ItemMeta source = new ItemStack(Material.NETHERITE_BLOCK).getItemMeta();
        ItemMeta destination = new ItemStack(Material.PAPER).getItemMeta();

        assertTrue(DankPackUpgrade.copyInstance(source, destination, 1, id -> {
            fail("A newly allocated pack is not required to exist in the registry yet");
            return true;
        }));

        DankPackInstance instance = DataTypeMethods.getCustom(destination, Keys.DANK_INSTANCE,
            PersistentDankInstanceType.TYPE);
        assertNotNull(instance);
        assertEquals(1, instance.getTier());
        assertArrayEquals(new ItemStack[9], instance.getItems());
        assertArrayEquals(new int[9], instance.getAmounts());
        assertFalse(source.getPersistentDataContainer().has(Keys.DANK_INSTANCE));
    }

    @Test
    void unreadableExistingPackIsNeverReplacedWithAFreshIdentity() {
        ItemMeta source = new ItemStack(Material.PAPER).getItemMeta();
        var broken = source.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        broken.set(PersistentDankInstanceType.DANK_ID, PersistentDataType.STRING, "not-a-long");
        source.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER, broken);
        ItemMeta sourceBefore = source.clone();
        ItemMeta destination = new ItemStack(Material.PAPER).getItemMeta();
        ItemMeta destinationBefore = destination.clone();

        assertThrows(RuntimeException.class,
            () -> DankPackUpgrade.copyInstance(source, destination, 2, id -> false));
        assertEquals(sourceBefore, source);
        assertEquals(destinationBefore, destination);
    }

    private static ItemMeta storedPack() {
        DankPackInstance instance = new DankPackInstance(9007199254740993L, 1);
        ItemStack stored = new ItemStack(Material.DIAMOND);
        ItemMeta storedMeta = stored.getItemMeta();
        storedMeta.displayName(Component.text("Existing stored item"));
        stored.setItemMeta(storedMeta);
        instance.setItem(0, stored);
        instance.setAmount(0, Integer.MAX_VALUE);
        instance.setLastUser("Original owner");
        ItemMeta meta = new ItemStack(Material.PAPER).getItemMeta();
        DataTypeMethods.setCustom(meta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, instance);
        return meta;
    }
}
