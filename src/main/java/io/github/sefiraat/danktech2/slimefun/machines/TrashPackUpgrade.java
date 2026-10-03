package io.github.sefiraat.danktech2.slimefun.machines;

import io.github.sefiraat.danktech2.core.TrashPackInstance;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentTrashInstanceType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Carries the existing Trash Pack identity and filters into the next crafted tier. */
final class TrashPackUpgrade {
    private TrashPackUpgrade() {}

    static void copyInstance(ItemMeta source, ItemMeta destination, int tier) {
        if (!source.getPersistentDataContainer().has(Keys.TRASH_INSTANCE)) {
            TrashPackInstance instance = new TrashPackInstance(System.currentTimeMillis(), tier);
            DataTypeMethods.setCustom(destination, Keys.TRASH_INSTANCE, PersistentTrashInstanceType.TYPE, instance);
            return;
        }

        // Validate with the historical reader before changing or consuming anything.
        TrashPackInstance instance = DataTypeMethods.getCustom(
            source, Keys.TRASH_INSTANCE, PersistentTrashInstanceType.TYPE
        );
        if (instance == null || instance.getItems() == null) {
            throw new IllegalArgumentException("Existing Trash Pack instance is unreadable");
        }
        PersistentDataContainer existing = source.getPersistentDataContainer().get(
            Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER
        );
        PersistentDataContainer upgraded = destination.getPersistentDataContainer()
            .getAdapterContext().newPersistentDataContainer();
        // Copy the stored payload so unknown fields and exact filter bytes survive too.
        existing.copyTo(upgraded, true);
        upgraded.set(PersistentTrashInstanceType.TRASH_TIER, PersistentDataType.INTEGER, tier);
        destination.getPersistentDataContainer().set(Keys.TRASH_INSTANCE, PersistentDataType.TAG_CONTAINER, upgraded);
    }
}
