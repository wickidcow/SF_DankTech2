package io.github.sefiraat.danktech2.slimefun.machines;

import io.github.sefiraat.danktech2.core.TrashPackInstance;
import io.github.sefiraat.danktech2.testing.DankTestEnvironment;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentTrashInstanceType;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrashPackUpgradeTest extends DankTestEnvironment {
    @Test
    void upgradingUsesTheExistingTrashKeyAndPreservesLongIdentityAndEveryFilter() {
        TrashPackInstance original = new TrashPackInstance(9007199254740993L, 1);
        original.setItem(0, filter(Material.DIAMOND, "historic_diamond"));
        original.setItem(7, filter(Material.STONE, "historic_stone"));
        original.setItem(17, filter(Material.EMERALD, "historic_emerald"));
        ItemMeta input = metadata(original);
        ItemMeta before = input.clone();
        ItemMeta output = new ItemStack(Material.PAPER).getItemMeta();
        output.displayName(Component.text("Trash Pack - Tier 2"));
        output.getPersistentDataContainer().set(key("slimefun:slimefun_item"),
            PersistentDataType.STRING, "DK2_TRASH_2");

        // A historical Trash Pack stores its instance here, not under the Dank Pack key.
        assertTrue(input.getPersistentDataContainer().has(key("danktech2:trash_instance"),
            PersistentDataType.TAG_CONTAINER));
        assertFalse(input.getPersistentDataContainer().has(Keys.DANK_INSTANCE));
        TrashPackUpgrade.copyInstance(input, output, 2);

        TrashPackInstance upgraded = read(output);
        assertEquals(9007199254740993L, upgraded.getId());
        assertEquals(2, upgraded.getTier());
        assertEquals(18, upgraded.getItems().length);
        assertArrayEquals(original.getItems(), upgraded.getItems());
        assertEquals(before, input, "Crafting must not rewrite the consumed input before success");
        assertEquals(Component.text("Trash Pack - Tier 2"), output.displayName());
        assertEquals("DK2_TRASH_2", output.getPersistentDataContainer().get(
            key("slimefun:slimefun_item"), PersistentDataType.STRING));
        assertFalse(output.getPersistentDataContainer().has(Keys.DANK_INSTANCE));
    }

    @Test
    void everySuccessiveUpgradeRetainsTheOriginalIdAndFilterMetadata() {
        TrashPackInstance original = new TrashPackInstance(Long.MAX_VALUE, 1);
        original.setItem(0, filter(Material.IRON_INGOT, "old_addon_item"));
        ItemMeta current = metadata(original);

        for (int tier = 2; tier <= 9; tier++) {
            ItemMeta next = new ItemStack(Material.PAPER).getItemMeta();
            TrashPackUpgrade.copyInstance(current, next, tier);
            TrashPackInstance upgraded = read(next);
            assertEquals(Long.MAX_VALUE, upgraded.getId());
            assertEquals(tier, upgraded.getTier());
            assertArrayEquals(original.getItems(), upgraded.getItems());
            current = next;
        }
    }

    @Test
    void firstCraftStillCreatesAnEmptyInstanceAtTheRequestedTier() {
        ItemMeta input = new ItemStack(Material.NETHERITE_INGOT).getItemMeta();
        ItemMeta output = new ItemStack(Material.PAPER).getItemMeta();
        long earliest = System.currentTimeMillis();
        TrashPackUpgrade.copyInstance(input, output, 1);
        TrashPackInstance crafted = read(output);

        assertTrue(crafted.getId() >= earliest);
        assertTrue(crafted.getId() <= System.currentTimeMillis());
        assertEquals(1, crafted.getTier());
        assertArrayEquals(new ItemStack[18], crafted.getItems());
        assertFalse(input.getPersistentDataContainer().has(Keys.TRASH_INSTANCE));
    }

    @Test
    void upgradingRetainsOpaqueTypedFieldsAndRawFilterPayload() {
        TrashPackInstance original = new TrashPackInstance(123456789L, 2);
        original.setItem(0, filter(Material.DIAMOND, "historical_filter"));
        ItemMeta input = metadata(original);
        var existing = input.getPersistentDataContainer().get(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER);
        existing.set(key("oldaddon:charge"), PersistentDataType.FLOAT, 0.125F);
        existing.set(key("oldaddon:opaque"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 2, 127});
        var nested = existing.getAdapterContext().newPersistentDataContainer();
        nested.set(key("oldaddon:counter"), PersistentDataType.LONG, 9007199254740993L);
        existing.set(key("oldaddon:nested"), PersistentDataType.TAG_CONTAINER, nested);
        input.getPersistentDataContainer().set(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER, existing);
        ItemMeta before = input.clone();
        ItemMeta output = new ItemStack(Material.PAPER).getItemMeta();

        TrashPackUpgrade.copyInstance(input, output, 3);

        var actual = output.getPersistentDataContainer().get(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER);
        var expected = existing.getAdapterContext().newPersistentDataContainer();
        existing.copyTo(expected, true);
        expected.set(PersistentTrashInstanceType.TRASH_TIER, PersistentDataType.INTEGER, 3);
        assertEquals(expected, actual, "Only the stored tier may change inside the instance");
        assertTrue(actual.has(key("oldaddon:charge"), PersistentDataType.FLOAT));
        assertEquals(0.125F, actual.get(key("oldaddon:charge"), PersistentDataType.FLOAT));
        assertArrayEquals(new byte[] {0, -1, 2, 127}, actual.get(key("oldaddon:opaque"),
            PersistentDataType.BYTE_ARRAY));
        assertEquals(before, input);
    }

    @Test
    void unreadableExistingInstanceDoesNotBecomeAnEmptyReplacement() {
        ItemMeta input = new ItemStack(Material.PAPER).getItemMeta();
        var broken = input.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        broken.set(PersistentTrashInstanceType.TRASH_ID, PersistentDataType.STRING, "not-a-long");
        input.getPersistentDataContainer().set(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER, broken);
        ItemMeta output = new ItemStack(Material.PAPER).getItemMeta();
        ItemMeta inputBefore = input.clone();
        ItemMeta outputBefore = output.clone();

        assertThrows(RuntimeException.class, () -> TrashPackUpgrade.copyInstance(input, output, 2));
        assertEquals(inputBefore, input);
        assertEquals(outputBefore, output);
    }

    private static ItemMeta metadata(TrashPackInstance instance) {
        ItemMeta metadata = new ItemStack(Material.PAPER).getItemMeta();
        DataTypeMethods.setCustom(metadata, Keys.TRASH_INSTANCE, PersistentTrashInstanceType.TYPE, instance);
        return metadata;
    }

    private static TrashPackInstance read(ItemMeta metadata) {
        TrashPackInstance instance = DataTypeMethods.getCustom(metadata, Keys.TRASH_INSTANCE,
            PersistentTrashInstanceType.TYPE);
        assertNotNull(instance);
        return instance;
    }

    private static ItemStack filter(Material material, String identity) {
        ItemStack stack = new ItemStack(material);
        ItemMeta metadata = stack.getItemMeta();
        metadata.displayName(Component.text("Original " + identity));
        metadata.getPersistentDataContainer().set(key("oldaddon:item"), PersistentDataType.STRING, identity);
        metadata.getPersistentDataContainer().set(key("oldaddon:counter"), PersistentDataType.LONG,
            9007199254740993L);
        stack.setItemMeta(metadata);
        return stack;
    }

    private static NamespacedKey key(String value) {
        return NamespacedKey.fromString(value);
    }
}
