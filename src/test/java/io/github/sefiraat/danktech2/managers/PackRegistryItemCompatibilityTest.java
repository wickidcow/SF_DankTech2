package io.github.sefiraat.danktech2.managers;

import io.github.sefiraat.danktech2.testing.DankTestEnvironment;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PackRegistryItemCompatibilityTest extends DankTestEnvironment {
    private static final String LEGACY_ALIAS =
        "io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack";
    private static final String PACK_ID = "9007199254740993";
    private static final NamespacedKey LONG_ID = new NamespacedKey("danktech2", "unknown_long");
    private static final NamespacedKey BYTES = new NamespacedKey("danktech2", "unknown_bytes");

    @TempDir
    Path directory;

    @Test
    void knownHistoricalAliasRecoversTheEntireItemWithoutChangingTheSourceFileOrGlobalAliases() throws Exception {
        ItemStack expected = itemWithOpaqueData();
        YamlConfiguration original = new YamlConfiguration();
        original.set(PACK_ID + ".last_user", "ExactOwner");
        original.set(PACK_ID + ".item", expected);
        original.set(PACK_ID + ".unknown", List.of("keep", LEGACY_ALIAS));
        original.set(PACK_ID + ".unknown_counts", List.of(Integer.MAX_VALUE, 0, 37));
        original.set(Long.MAX_VALUE + ".last_user", "MaximumOwner");
        original.set(Long.MAX_VALUE + ".item", new ItemStack(Material.EMERALD, 7));
        String historical = original.saveToString().replace(
            "==: org.bukkit.inventory.ItemStack", "==: " + LEGACY_ALIAS);
        assertTrue(historical.contains("==: " + LEGACY_ALIAS));
        byte[] before = historical.getBytes(StandardCharsets.UTF_8);
        Files.write(registry(), before);
        var aliasBefore = ConfigurationSerialization.getClassByAlias(LEGACY_ALIAS);

        YamlConfiguration recovered = PackRegistryFile.load(registry());

        assertOpaqueData(recovered.getItemStack(PACK_ID + ".item"));
        assertEquals("ExactOwner", recovered.getString(PACK_ID + ".last_user"));
        assertEquals(List.of("keep", LEGACY_ALIAS), recovered.getStringList(PACK_ID + ".unknown"));
        assertEquals(List.of(Integer.MAX_VALUE, 0, 37), recovered.getIntegerList(PACK_ID + ".unknown_counts"));
        assertEquals(new ItemStack(Material.EMERALD, 7), recovered.getItemStack(Long.MAX_VALUE + ".item"));
        assertArrayEquals(before, Files.readAllBytes(registry()));
        assertSame(aliasBefore, ConfigurationSerialization.getClassByAlias(LEGACY_ALIAS));

        for (int cycle = 0; cycle < 3; cycle++) {
            PackRegistryFile.save(recovered, registry());
            recovered = PackRegistryFile.load(registry());
            assertOpaqueData(recovered.getItemStack(PACK_ID + ".item"));
            assertEquals("ExactOwner", recovered.getString(PACK_ID + ".last_user"));
            assertEquals(List.of("keep", LEGACY_ALIAS), recovered.getStringList(PACK_ID + ".unknown"));
            assertEquals(List.of(Integer.MAX_VALUE, 0, 37), recovered.getIntegerList(PACK_ID + ".unknown_counts"));
        }
    }

    @Test
    void aliasLookingTextAndUnrelatedItemMetadataRemainOrdinaryData() throws Exception {
        String text = "==: " + LEGACY_ALIAS + "\nitem:\n  ==: " + LEGACY_ALIAS + "\n";
        YamlConfiguration original = new YamlConfiguration();
        original.set(PACK_ID + ".item", new ItemStack(Material.DIAMOND));
        original.set(PACK_ID + ".notes", text);
        original.set("metadata.item", "An unrelated item field");
        original.set("metadata.unknown.items", List.of(LEGACY_ALIAS, text));
        byte[] before = original.saveToString().replace(
            "==: org.bukkit.inventory.ItemStack", "==: " + LEGACY_ALIAS).getBytes(StandardCharsets.UTF_8);
        Files.write(registry(), before);

        var recovered = PackRegistryFile.load(registry());

        assertEquals(text, recovered.getString(PACK_ID + ".notes"));
        assertEquals("An unrelated item field", recovered.getString("metadata.item"));
        assertEquals(List.of(LEGACY_ALIAS, text), recovered.getStringList("metadata.unknown.items"));
        assertArrayEquals(before, Files.readAllBytes(registry()));
    }

    @Test
    void unknownItemAliasRejectsTheWholeRegistryIncludingAnOtherwiseReadablePrefix() throws Exception {
        rejectUnchanged("'42':\n  last_user: KeepThisOwner\n'" + PACK_ID + "':\n"
            + "  item:\n    ==: unregistered.plugin.OtherItemStack\n    type: STONE\n");
    }

    @Test
    void knownAliasOutsideARealPackItemIsNotReinterpreted() throws Exception {
        rejectUnchanged("metadata:\n  item:\n    ==: " + LEGACY_ALIAS + "\n    type: STONE\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "'not an item'", "[]", "{type: STONE, amount: 1}"})
    void explicitUnreadableItemsCannotBecomeAUsableRegistry(String value) throws Exception {
        rejectUnchanged("'" + PACK_ID + "':\n  last_user: KeepThisOwner\n  item: " + value + "\n");
    }

    @Test
    void failedRegisteredItemDeserializerDoesNotSilentlyEraseTheItem() throws Exception {
        rejectUnchanged("'" + PACK_ID + "':\n  item:\n    ==: org.bukkit.inventory.ItemStack\n"
            + "    type: THIS_MATERIAL_DOES_NOT_EXIST\n    amount: 3\n");
    }

    @Test
    void failedNestedDeserializerCannotProduceAPartiallyDecodedItem() throws Exception {
        rejectUnchanged("'" + PACK_ID + "':\n  item:\n    ==: org.bukkit.inventory.ItemStack\n"
            + "    type: STONE\n    meta:\n      ==: unregistered.plugin.UnknownMeta\n"
            + "      opaque: preserve-this-payload\n");
    }

    @Test
    void failedRegisteredUnknownFieldDeserializerCannotBeSilentlyDiscarded() throws Exception {
        rejectUnchanged("'" + PACK_ID + "':\n  last_user: KeepThisOwner\n  unknown:\n"
            + "    ==: Color\n    RED: invalid\n    GREEN: 0\n    BLUE: 0\n");
    }

    private void rejectUnchanged(String contents) throws Exception {
        byte[] before = contents.getBytes(StandardCharsets.UTF_8);
        Files.write(registry(), before);
        assertThrows(InvalidConfigurationException.class, () -> PackRegistryFile.load(registry()));
        assertArrayEquals(before, Files.readAllBytes(registry()));
        try (var paths = Files.list(directory)) {
            assertEquals(List.of(registry()), paths.toList());
        }
    }

    private Path registry() {
        return directory.resolve("dank_packs.yml");
    }

    private static ItemStack itemWithOpaqueData() {
        ItemStack item = new ItemStack(Material.DIAMOND, 3);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Exact item name"));
        meta.lore(List.of(Component.text(LEGACY_ALIAS)));
        PersistentDataContainer payload = meta.getPersistentDataContainer();
        payload.set(LONG_ID, PersistentDataType.LONG, Long.MAX_VALUE);
        payload.set(BYTES, PersistentDataType.BYTE_ARRAY, new byte[] {0, -128, 37, 127});
        // MockBukkit cannot YAML-round-trip nested PDC or INTEGER_ARRAY values;
        // those typed payloads are covered by the real Paper registry restart probe.
        item.setItemMeta(meta);
        return item;
    }

    private static void assertOpaqueData(ItemStack item) {
        assertNotNull(item);
        assertEquals(Material.DIAMOND, item.getType());
        assertEquals(3, item.getAmount());
        assertEquals(Component.text("Exact item name"), item.getItemMeta().displayName());
        assertEquals(List.of(Component.text(LEGACY_ALIAS)), item.getItemMeta().lore());
        PersistentDataContainer payload = item.getItemMeta().getPersistentDataContainer();
        assertNotNull(payload);
        assertEquals(Set.of(LONG_ID, BYTES), payload.getKeys());
        assertEquals(Long.MAX_VALUE, payload.get(LONG_ID, PersistentDataType.LONG));
        assertArrayEquals(new byte[] {0, -128, 37, 127}, payload.get(BYTES, PersistentDataType.BYTE_ARRAY));
    }
}
