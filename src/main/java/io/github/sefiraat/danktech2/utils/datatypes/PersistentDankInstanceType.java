package io.github.sefiraat.danktech2.utils.datatypes;

import com.jeff_media.morepersistentdatatypes.DataType;
import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.utils.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;

/**
 * A {@link PersistentDataType} for {@link DankPackInstance}.
 *
 * <p>The serialized keys and primitive data types are intentionally unchanged so existing DankTech2
 * pack data remains readable while the backing MorePersistentDataTypes library moves from its old
 * {@code de.jeff_media} package to the maintained {@code com.jeff_media} package.</p>
 *
 * @author Sfiguz7
 * @author Walshy
 */
public class PersistentDankInstanceType implements PersistentDataType<PersistentDataContainer, DankPackInstance> {

    public static final PersistentDataType<PersistentDataContainer, DankPackInstance> TYPE = new PersistentDankInstanceType();

    public static final NamespacedKey DANK_ITEMS = Keys.newKey("dank_items");
    public static final NamespacedKey DANK_AMOUNTS = Keys.newKey("dank_amounts");
    public static final NamespacedKey DANK_TIER = Keys.newKey("dank_tier");
    public static final NamespacedKey DANK_ID = Keys.newKey("dank_id");
    public static final NamespacedKey DANK_USER = Keys.newKey("dank_user");

    @Override
    @Nonnull
    public Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @Override
    @Nonnull
    public Class<DankPackInstance> getComplexType() {
        return DankPackInstance.class;
    }

    @Override
    @Nonnull
    public PersistentDataContainer toPrimitive(@Nonnull DankPackInstance complex, @Nonnull PersistentDataAdapterContext context) {
        PersistentDataContainer container = context.newPersistentDataContainer();
        container.set(DANK_ITEMS, DataType.ITEM_STACK_ARRAY, complex.getItems());
        container.set(DANK_AMOUNTS, DataType.INTEGER_ARRAY, complex.getAmounts());
        container.set(DANK_TIER, DataType.INTEGER, complex.getTier());
        container.set(DANK_ID, DataType.LONG, complex.getId());
        container.set(DANK_USER, DataType.STRING, complex.getLastUser());
        return container;
    }

    @Override
    @Nonnull
    public DankPackInstance fromPrimitive(@Nonnull PersistentDataContainer primitive, @Nonnull PersistentDataAdapterContext context) {
        long id = primitive.get(DANK_ID, DataType.LONG);
        String name = primitive.get(DANK_USER, DataType.STRING);
        int tier = primitive.get(DANK_TIER, DataType.INTEGER);
        ItemStack[] items = primitive.get(DANK_ITEMS, DataType.ITEM_STACK_ARRAY);
        int[] amounts = primitive.get(DANK_AMOUNTS, DataType.INTEGER_ARRAY);
        DankPackInstance instance = new DankPackInstance(id, tier);
        instance.setLastUser(name);
        instance.setItems(items);
        instance.setAmounts(amounts);
        return instance;
    }
}
