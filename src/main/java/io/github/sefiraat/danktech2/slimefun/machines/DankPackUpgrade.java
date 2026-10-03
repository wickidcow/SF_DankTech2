package io.github.sefiraat.danktech2.slimefun.machines;

import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.utils.Keys;
import io.github.sefiraat.danktech2.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.danktech2.utils.datatypes.PersistentDankInstanceType;
import java.util.function.LongPredicate;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** An upgrade must not re-register a pack invalidated by an admin replacement. */
final class DankPackUpgrade {
    private DankPackUpgrade() {}

    static boolean copyInstance(ItemMeta source, ItemMeta destination, int tier, LongPredicate isDeleted) {
        if (!source.getPersistentDataContainer().has(Keys.DANK_INSTANCE)) {
            DankPackInstance instance = new DankPackInstance(System.currentTimeMillis(), tier);
            DataTypeMethods.setCustom(destination, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE, instance);
            return true;
        }

        DankPackInstance instance = DataTypeMethods.getCustom(
            source, Keys.DANK_INSTANCE, PersistentDankInstanceType.TYPE
        );
        if (instance == null || instance.getItems() == null || instance.getAmounts() == null) {
            throw new IllegalArgumentException("Existing Dank Pack instance is unreadable");
        }
        if (isDeleted.test(instance.getId())) {
            return false;
        }

        PersistentDataContainer existing = source.getPersistentDataContainer().get(
            Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER
        );
        PersistentDataContainer upgraded = destination.getPersistentDataContainer()
            .getAdapterContext().newPersistentDataContainer();
        existing.copyTo(upgraded, true);
        upgraded.set(PersistentDankInstanceType.DANK_TIER, PersistentDataType.INTEGER, tier);
        destination.getPersistentDataContainer().set(Keys.DANK_INSTANCE, PersistentDataType.TAG_CONTAINER, upgraded);
        return true;
    }
}
