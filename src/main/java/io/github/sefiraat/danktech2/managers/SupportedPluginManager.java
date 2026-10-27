package io.github.sefiraat.danktech2.managers;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.skills.CombatUtils;
import dev.rosewood.rosestacker.api.RoseStackerAPI;
import dev.rosewood.rosestacker.stack.StackedItem;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@Getter
public class SupportedPluginManager {

    private final boolean isMcMMO;
    private final boolean wildStacker;
    private final boolean roseStacker;

    private RoseStackerAPI roseStackerAPI;
    private final WildStackerBridge wildStackerBridge;

    public SupportedPluginManager() {
        isMcMMO = Bukkit.getPluginManager().isPluginEnabled("mcMMO");
        wildStacker = Bukkit.getPluginManager().isPluginEnabled("WildStacker");
        roseStacker = Bukkit.getPluginManager().isPluginEnabled("RoseStacker");
        // Load the optional API only on servers that actually enable WildStacker.
        wildStackerBridge = wildStacker ? new WildStackerBridge() : null;


        if (roseStacker) {
            this.roseStackerAPI = RoseStackerAPI.getInstance();
        }
    }

    /**
     * Damaging an entity and attributing to a player will make mcMMO give exp based
     * on the held item. If mcMMO is installed, we need to flag the entity to be ignored
     * briefly.
     *
     * @param livingEntity The {@link LivingEntity} to be damaged
     * @param player       The {@link Player} to attribute the damage/drops to
     * @param damage       The damage to apply
     */
    public void playerDamageWithoutMcMMO(LivingEntity livingEntity, Player player, double damage) {
        markEntityMcMMOIgnoreDamage(livingEntity);
        livingEntity.damage(damage, player);
        clearEntityMcMMOIgnoreDamage(livingEntity);
    }

    public void markBlockPlaced(Block block) {
        if (isMcMMO) {
            mcMMO.getPlaceStore().setTrue(block);
        }
    }

    public void markEntityMcMMOIgnoreDamage(LivingEntity livingEntity) {
        if (isMcMMO) CombatUtils.applyIgnoreDamageMetadata(livingEntity);
    }

    public void clearEntityMcMMOIgnoreDamage(LivingEntity livingEntity) {
        if (isMcMMO) CombatUtils.removeIgnoreDamageMetadata(livingEntity);
    }

    public int getStackAmount(Item item) {
        if (wildStacker) {
            return wildStackerBridge.getItemAmount(item);
        } else if (roseStacker) {
            StackedItem stackedItem = roseStackerAPI.getStackedItem(item);
            return stackedItem == null ? item.getItemStack().getAmount() : stackedItem.getStackSize();
        } else {
            return item.getItemStack().getAmount();
        }
    }

    public void setStackAmount(Item item, int amount) {
        if (wildStacker) {
            wildStackerBridge.setStackAmount(item, amount);
        } else if (roseStacker) {
            StackedItem stackedItem = roseStackerAPI.getStackedItem(item);
            stackedItem.setStackSize(amount);
        } else {
            item.getItemStack().setAmount(amount);
        }
    }
}
