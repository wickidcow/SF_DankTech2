package io.github.sefiraat.danktech2.listeners;

import io.github.sefiraat.danktech2.core.DankPackInstance;
import io.github.sefiraat.danktech2.testing.DankTestEnvironment;
import io.github.sefiraat.danktech2.utils.Keys;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackListenerBuildTest extends DankTestEnvironment {
    @Test
    void newlyCreatedPackCanUseBuildModeWithAnEmptySelectedSlot() throws Exception {
        DankPackInstance instance = new DankPackInstance(42L, 1);
        assertRefusedWithoutMutation(instance, 0);
    }

    @Test
    void clearedNonDefaultSlotDoesNotDereferenceItsRemovedTemplate() throws Exception {
        DankPackInstance instance = new DankPackInstance(9007199254740993L, 9);
        instance.setItem(8, new ItemStack(Material.STONE));
        instance.setAmount(8, 10);
        instance.clearItem(8);
        assertRefusedWithoutMutation(instance, 8);
    }

    @Test
    void airTemplateCannotBePlacedOrConsumeStoredCount() throws Exception {
        DankPackInstance instance = new DankPackInstance(43L, 1);
        instance.setItem(0, new ItemStack(Material.AIR));
        instance.setAmount(0, 12);
        assertRefusedWithoutMutation(instance, 0);
    }

    @Test
    void finalRegisteredBlockIsStillRetained() throws Exception {
        DankPackInstance instance = new DankPackInstance(44L, 1);
        instance.setItem(0, new ItemStack(Material.STONE));
        instance.setAmount(0, 1);
        assertRefusedWithoutMutation(instance, 0);
    }

    @Test
    void nonBlockItemIsStillRefused() throws Exception {
        DankPackInstance instance = new DankPackInstance(45L, 1);
        instance.setItem(0, new ItemStack(Material.DIAMOND));
        instance.setAmount(0, 20);
        assertRefusedWithoutMutation(instance, 0);
    }

    @Test
    void customBlockMetadataIsStillRefused() throws Exception {
        DankPackInstance instance = new DankPackInstance(46L, 1);
        ItemStack customBlock = new ItemStack(Material.STONE);
        ItemMeta metadata = customBlock.getItemMeta();
        metadata.displayName(Component.text("Existing custom block"));
        customBlock.setItemMeta(metadata);
        instance.setItem(0, customBlock);
        instance.setAmount(0, 20);
        assertRefusedWithoutMutation(instance, 0);
    }

    private void assertRefusedWithoutMutation(DankPackInstance instance, int selectedSlot) throws Exception {
        ItemStack held = new ItemStack(Material.PAPER);
        ItemMeta metadata = held.getItemMeta();
        metadata.getPersistentDataContainer().set(Keys.DANK_SELECTED_SLOT, PersistentDataType.INTEGER, selectedSlot);
        held.setItemMeta(metadata);
        ItemStack before = held.clone();
        ItemStack[] itemsBefore = instance.getItems().clone();
        int[] amountsBefore = instance.getAmounts().clone();
        Block target = server.addSimpleWorld("build-world").getBlockAt(0, 80, 0);
        target.setType(Material.AIR);
        Method build = PackListener.class.getDeclaredMethod("tryBuild", ItemStack.class, ItemMeta.class,
            DankPackInstance.class, Block.class, Player.class);
        build.setAccessible(true);

        try {
            build.invoke(new PackListener(), held, held.getItemMeta(), instance, target, player);
        } catch (InvocationTargetException failure) {
            fail("Build mode must refuse this slot without throwing", failure.getCause());
        }

        assertEquals(Material.AIR, target.getType());
        assertEquals(before, held);
        assertArrayEquals(itemsBefore, instance.getItems());
        assertArrayEquals(amountsBefore, instance.getAmounts());
    }
}
