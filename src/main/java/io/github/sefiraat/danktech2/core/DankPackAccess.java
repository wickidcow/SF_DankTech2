package io.github.sefiraat.danktech2.core;

import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.LongPredicate;

/** Reads an existing pack only while the same identity remains registered. */
final class DankPackAccess {
    private DankPackAccess() {
    }

    static DankPackInstance loadExisting(ItemStack pack, long expectedId, LongPredicate isDeleted) {
        if (pack == null || pack.isEmpty()) {
            return null;
        }
        ItemMeta meta = pack.getItemMeta();
        DankPackInstance current = meta == null ? null : DataTypeMethods.getCustom(
            meta, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE);
        if (current == null || current.getId() != expectedId || isDeleted.test(expectedId)) {
            return null;
        }
        return current;
    }
}
