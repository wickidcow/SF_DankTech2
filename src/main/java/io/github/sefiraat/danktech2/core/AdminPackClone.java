package io.github.sefiraat.danktech2.core;

import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/** Performs the admin replacement synchronously from the inventory click handler. */
final class AdminPackClone {

    private AdminPackClone() {
    }

    static boolean replace(ItemStack original, Inventory inventory, LongPredicate isIdAvailable,
                           Consumer<ItemStack> savePack, LongConsumer deletePack) {
        long originalId = readId(original);
        if (isIdAvailable.test(originalId)) {
            // Another admin may have replaced this pack since the menu was opened.
            return false;
        }
        long replacementId = nextAvailableId(originalId, System.currentTimeMillis(), isIdAvailable);
        ItemStack replacement = prepare(original, replacementId);
        ItemStack[] previousContents = inventory.getStorageContents();
        for (int i = 0; i < previousContents.length; i++) {
            if (previousContents[i] != null) {
                previousContents[i] = previousContents[i].clone();
            }
        }

        boolean completed = false;
        try {
            // addItem may mutate its argument and may insert only part of a stack.
            if (!inventory.addItem(replacement.clone()).isEmpty()) {
                return false;
            }
            try {
                savePack.accept(replacement);
                if (isIdAvailable.test(replacementId)) {
                    throw new IllegalStateException("The replacement Dank Pack was not registered");
                }
            } catch (RuntimeException failure) {
                // A failed save may have created an incomplete new registry entry.
                deletePack.accept(replacementId);
                throw failure;
            }
            deletePack.accept(originalId);
            completed = true;
            return true;
        } finally {
            if (!completed) {
                inventory.setStorageContents(previousContents);
            }
        }
    }

    static long nextAvailableId(long originalId, long firstCandidate, LongPredicate isIdAvailable) {
        long candidate = firstCandidate;
        while (candidate == originalId || !isIdAvailable.test(candidate)) {
            candidate++;
        }
        return candidate;
    }

    static ItemStack prepare(ItemStack original, long replacementId) {
        if (readId(original) == replacementId) {
            throw new IllegalArgumentException("A replacement Dank Pack must have a different ID");
        }
        ItemStack replacement = original.clone();
        ItemMeta meta = replacement.getItemMeta();
        PersistentDataContainer contents = meta.getPersistentDataContainer().getAdapterContext()
            .newPersistentDataContainer();
        packData(meta).copyTo(contents, true);
        // Change only the ID so stored items, counts and any unknown PDC fields stay intact.
        contents.set(PersistentDankInstanceType.DANK_ID, PersistentDataType.LONG, replacementId);
        meta.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER, contents);
        replacement.setItemMeta(meta);
        return replacement;
    }

    private static long readId(ItemStack pack) {
        return Objects.requireNonNull(packData(pack.getItemMeta()).get(
            PersistentDankInstanceType.DANK_ID, PersistentDataType.LONG), "Missing Dank Pack ID");
    }

    private static PersistentDataContainer packData(ItemMeta meta) {
        return Objects.requireNonNull(meta.getPersistentDataContainer().get(
            Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER), "Missing Dank Pack storage data");
    }
}
