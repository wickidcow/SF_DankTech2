package io.github.sefiraat.danktech2.theme;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    WARNING(NamedTextColor.YELLOW, "Warning"),
    ERROR(NamedTextColor.RED, "Error"),
    NOTICE(NamedTextColor.WHITE, "Notice"),
    PASSIVE(NamedTextColor.GRAY, ""),
    SUCCESS(NamedTextColor.GREEN, "Success"),
    MAIN(TextColor.color(0x21588f), "DankTech2"),
    CLICK_INFO(TextColor.color(0xe4ed32), "Click here"),
    CRAFTING(TextColor.color(0xdbcea9), "Crafting Material"),
    MACHINE(TextColor.color(0x3295a8), "Machine"),
    GUIDE(TextColor.color(0x444444), "TrashPack"),
    CHEST(TextColor.color(0xb89b1c), "DankPack"),
    DROP(TextColor.color(0xbf307f), "Rare Drop"),
    BASE(TextColor.color(0x9e9e9e), "Base Resource"),
    INFO(TextColor.color(0x21588f), "Information"),
    T1(TextColor.color(0xdeebff), "Tier 1"),
    T2(TextColor.color(0xb8b8b8), "Tier 2"),
    T3(TextColor.color(0xb5ff9e), "Tier 3"),
    T4(TextColor.color(0x34a112), "Tier 4"),
    T5(TextColor.color(0x467dcf), "Tier 5"),
    T6(TextColor.color(0x083578), "Tier 6"),
    T7(TextColor.color(0xec8cff), "Tier 7"),
    T8(TextColor.color(0xb70bd9), "Tier 8"),
    T9(TextColor.color(0xa60000), "Tier 9");

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

    private final TextColor textColor;
    private final String color;
    private final String loreLine;

    ThemeType(TextColor textColor, String loreLine) {
        this.textColor = textColor;
        this.color = serializeLegacyColor(textColor);
        this.loreLine = loreLine;
    }

    private static String serializeLegacyColor(TextColor textColor) {
        String serialized = LegacyColorSerializerHolder.SERIALIZER.serialize(Component.text("x", textColor));
        return serialized.substring(0, serialized.length() - 1);
    }

    private static final class LegacyColorSerializerHolder {
        private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

        private LegacyColorSerializerHolder() {}
    }

    /**
     * Applies the theme color to a given string.
     *
     * @param themeType The {@link ThemeType} to apply the color from
     * @param string    The string to apply the color to
     * @return the supplied string preceded by this theme's legacy color sequence
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
     * @param themeType The {@link ThemeType} color to apply to the {@link SlimefunItemStack} name
     * @param name      The name to apply to the {@link SlimefunItemStack}
     * @param lore      The lore lines for the {@link SlimefunItemStack}. Lore is book-ended with empty strings.
     * @return the new {@link SlimefunItemStack}
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static SlimefunItemStack themedSlimefunItemStack(String id,
                                                            ItemStack itemStack,
                                                            ThemeType themeType,
                                                            String name,
                                                            String... lore
    ) {
        String passiveColor = ThemeType.PASSIVE.getColor();
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
     * @param themeType The {@link ThemeType} color to apply to the {@link ItemStack} name
     * @param name      The name to apply to the {@link ItemStack}
     * @param lore      The lore lines for the {@link ItemStack}. Lore is book-ended with empty strings.
     * @return the new {@link ItemStack}
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public static ItemStack themedItemStack(Material material, ThemeType themeType, String name, String... lore) {
        String passiveColor = ThemeType.PASSIVE.getColor();
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
            character = capNext ? Character.toUpperCase(character) : Character.toLowerCase(character);
            builder.append(character);
            capNext = delimiters.indexOf(character) >= 0;
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
        int rnd = ThreadLocalRandom.current().nextInt(EGG_NAMES.size());
        return EGG_NAMES.get(rnd);
    }

    @Nonnull
    public static List<String> getEggNames() {
        return EGG_NAMES;
    }

    public Particle.DustOptions getDustOptions(float size) {
        int rgb = textColor.value();
        return new Particle.DustOptions(
            Color.fromRGB(
                (rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF,
                rgb & 0xFF
            ),
            size
        );
    }
}
