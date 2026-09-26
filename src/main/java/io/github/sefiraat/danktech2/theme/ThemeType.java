package io.github.sefiraat.danktech2.theme;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum ThemeType {
    WARNING(ChatColor.YELLOW, "Warning"),
    ERROR(ChatColor.RED, "Error"),
    NOTICE(ChatColor.WHITE, "Notice"),
    PASSIVE(ChatColor.GRAY, ""),
    SUCCESS(ChatColor.GREEN, "Success"),
    MAIN(ChatColor.of("#21588f"), "DankTech2"),
    CLICK_INFO(ChatColor.of("#e4ed32"), "Click here"),
    CRAFTING(ChatColor.of("#dbcea9"), "Crafting Material"),
    MACHINE(ChatColor.of("#3295a8"), "Machine"),
    GUIDE(ChatColor.of("#444444"), "TrashPack"),
    CHEST(ChatColor.of("#b89b1c"), "DankPack"),
    DROP(ChatColor.of("#bf307f"), "Rare Drop"),
    BASE(ChatColor.of("#9e9e9e"), "Base Resource"),
    INFO(ChatColor.of("#21588f"), "Information"),
    T1(ChatColor.of("#deebff"), "Tier 1"),
    T2(ChatColor.of("#b8b8b8"), "Tier 2"),
    T3(ChatColor.of("#b5ff9e"), "Tier 3"),
    T4(ChatColor.of("#34a112"), "Tier 4"),
    T5(ChatColor.of("#467dcf"), "Tier 5"),
    T6(ChatColor.of("#083578"), "Tier 6"),
    T7(ChatColor.of("#ec8cff"), "Tier 7"),
    T8(ChatColor.of("#b70bd9"), "Tier 8"),
    T9(ChatColor.of("#a60000"), "Tier 9");

    @Nonnull
    protected static final List<String> EGG_NAMES = Arrays.asList(
        "TheBusyBiscuit",
        "Alessio",
        "Walshy",
        "Jeff",
        "Seggan",
        "BOOMER_1",
        "svr333",
        "variananora",
        "ProfElements",
        "Riley",
        "FluffyBear",
        "GallowsDove",
        "Apeiros",
        "Martin",
        "Bunnky",
        "ReasonFoundDecoy",
        "Oah",
        "Azak",
        "andrewandy",
        "EpicPlayer10",
        "GentlemanCheesy",
        "ybw0014",
        "Ashian",
        "R.I.P",
        "OOOOMAGAAA",
        "TerslenK",
        "FN_FAL",
        "supertechxter"
    );

    @Getter
    protected static final ThemeType[] cachedValues = values();
    private final ChatColor color;
    private final String loreLine;

    ThemeType(ChatColor color, String loreLine) {
        this.color = color;
        this.loreLine = loreLine;
    }

    /**
     * Applies the theme color to a given string
     *
     * @param themeType The {@link ThemeType} to apply the color from
     * @param string    The string to apply the color to
     * @return Returns the string provides preceded by the color
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static String applyThemeToString(ThemeType themeType, String string) {
        return themeType.getColor() + string;
    }

    /**
     * Gets a SlimefunItemStack with a pre-populated lore and name with themed colors.
     *
     * @param id        The ID for the new {@link SlimefunItemStack}
     * @param itemStack The vanilla {@link ItemStack} used to base the {@link SlimefunItemStack} on
     * @param themeType The {@link ThemeType} {@link ChatColor} to apply to the {@link SlimefunItemStack} name
     * @param name      The name to apply to the {@link SlimefunItemStack}
     * @param lore      The lore lines for the {@link SlimefunItemStack}. Lore is book-ended with empty strings.
     * @return Returns the new {@link SlimefunItemStack}
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static SlimefunItemStack themedSlimefunItemStack(String id,
                                                            ItemStack itemStack,
                                                            ThemeType themeType,
                                                            String name,
                                                            String... lore
    ) {
        ChatColor passiveColor = ThemeType.PASSIVE.getColor();
        List<String> finalLore = new ArrayList<>();
        finalLore.add("");
        for (String s : lore) {
            finalLore.add(passiveColor + s);
        }
        finalLore.add("");
        finalLore.add(applyThemeToString(ThemeType.CLICK_INFO, themeType.getLoreLine()));
        return new SlimefunItemStack(
            id,
            itemStack,
            ThemeType.applyThemeToString(themeType, name),
            finalLore.toArray(new String[finalLore.size() - 1])
        );
    }

    /**
     * Gets an ItemStack with a pre-populated lore and name with themed colors.
     *
     * @param material  The {@link Material} used to base the {@link ItemStack} on
     * @param themeType The {@link ThemeType} {@link ChatColor} to apply to the {@link ItemStack} name
     * @param name      The name to apply to the {@link ItemStack}
     * @param lore      The lore lines for the {@link ItemStack}. Lore is book-ended with empty strings.
     * @return Returns the new {@link ItemStack}
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static ItemStack themedItemStack(Material material, ThemeType themeType, String name, String... lore) {
        ChatColor passiveColor = ThemeType.PASSIVE.getColor();
        List<String> finalLore = new ArrayList<>();
        finalLore.add("");
        for (String s : lore) {
            finalLore.add(passiveColor + s);
        }
        finalLore.add("");
        finalLore.add(applyThemeToString(ThemeType.CLICK_INFO, themeType.getLoreLine()));
        return new CustomItemStack(
            material,
            ThemeType.applyThemeToString(themeType, name),
            finalLore.toArray(new String[finalLore.size() - 1])
        );
    }

    @Nonnull
    public static String toTitleCase(@Nonnull String string) {
        return toTitleCase(string, true);
    }

    @Nonnull
    public static String toTitleCase(@Nonnull String string, boolean delimiterToSpace) {
        return toTitleCase(string, delimiterToSpace, " _'-/");
    }

    @Nonnull
    public static String toTitleCase(@Nonnull String string, boolean delimiterToSpace, @Nonnull String delimiters) {
        final StringBuilder builder = new StringBuilder();
        boolean capNext = true;

        for (char character : string.toCharArray()) {
            character = (capNext) ? Character.toUpperCase(character) : Character.toLowerCase(character);
            builder.append(character);
            capNext = (delimiters.indexOf(character) >= 0);
        }

        String built = builder.toString();

        if (delimiterToSpace) {
            final char space = ' ';
            for (char c : delimiters.toCharArray()) {
                built = built.replace(c, space);
            }
        }
        return built;
    }

    @Nonnull
    public static String getRandomEggName() {
        int rnd = ThreadLocalRandom.current().nextInt(0, EGG_NAMES.size());
        return EGG_NAMES.get(rnd);
    }

    @Nonnull
    public static List<String> getEggNames() {
        return EGG_NAMES;
    }

    public Particle.DustOptions getDustOptions(float size) {
        return new Particle.DustOptions(
            Color.fromRGB(
                color.getColor().getRed(),
                color.getColor().getGreen(),
                color.getColor().getBlue()
            ),
            size
        );
    }
}
