package io.github.sefiraat.danktech2.theme;

import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;

@UtilityClass
public class GuiElements {

    public static final CustomItemStack MENU_BACKGROUND = new CustomItemStack(
        Material.BLACK_STAINED_GLASS_PANE,
        " "
    );

    public static final CustomItemStack MENU_BACKGROUND_INPUT = new CustomItemStack(
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        LegacyComponentSerializer.legacySection().serialize(Component.text("Input", NamedTextColor.BLUE))
    );
}
