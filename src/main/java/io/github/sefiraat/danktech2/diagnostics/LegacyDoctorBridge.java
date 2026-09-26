package io.github.sefiraat.danktech2.diagnostics;

import io.github.sefiraat.danktech2.DankTech2;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

/** Optional reflective bridge to Slimefun Legacy's fingerprinted Doctor schema migration API. */
public final class LegacyDoctorBridge {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final String SCHEMA_PROBE_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe";
    private static final String SCHEMA_CANDIDATE_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate";
    private static final String SCHEMA_READINESS_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate$Readiness";
    private static final String SCHEMA_MIGRATOR_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator";

    private static final String CANDIDATE_TYPE = "legacy-dank-english-presentation";
    private static final Set<String> SUPPORTED_IDS = Set.of(
        "DK2_PACK_1",
        "DK2_PACK_2",
        "DK2_PACK_3",
        "DK2_PACK_4",
        "DK2_PACK_5",
        "DK2_PACK_6",
        "DK2_PACK_7",
        "DK2_PACK_8",
        "DK2_PACK_9",
        "DK2_TRASH"
    );

    private LegacyDoctorBridge() {}

    public static void register(DankTech2 plugin) {
        Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
        if (slimefun == null) {
            return;
        }

        ClassLoader loader = slimefun.getClass().getClassLoader();
        registerSchemaProbe(plugin, loader);
        registerSchemaMigrator(plugin, loader);
    }

    public static void unregister(DankTech2 plugin) {
        Bukkit.getServicesManager().unregisterAll(plugin);
    }

    private static void registerSchemaProbe(DankTech2 plugin, ClassLoader loader) {
        try {
            Class<?> probeInterface = Class.forName(SCHEMA_PROBE_API, false, loader);
            Class<?> candidateClass = Class.forName(SCHEMA_CANDIDATE_API, false, loader);
            Class<?> readinessClass = Class.forName(SCHEMA_READINESS_API, false, loader);

            Constructor<?> candidateConstructor;
            boolean supportsClaim;
            try {
                candidateConstructor = candidateClass.getConstructor(
                    String.class, readinessClass, String.class, String.class);
                supportsClaim = true;
            } catch (NoSuchMethodException ignored) {
                candidateConstructor = candidateClass.getConstructor(String.class, readinessClass, String.class);
                supportsClaim = false;
            }

            Method readinessValueOf = readinessClass.getMethod("valueOf", String.class);
            Constructor<?> finalConstructor = candidateConstructor;
            boolean finalSupportsClaim = supportsClaim;
            InvocationHandler handler = (proxy, method, arguments) -> invokeSchemaProbe(
                proxy, method, arguments, finalConstructor, readinessValueOf, finalSupportsClaim);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {probeInterface}, handler);
            registerRaw(Bukkit.getServicesManager(), probeInterface, provider, plugin);
            plugin.getLogger().info("Registered DankTech2 legacy presentation probe with Slimefun Doctor.");
        } catch (ClassNotFoundException ignored) {
            // Other Slimefun implementations do not necessarily expose the optional Legacy API.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(
                Level.WARNING,
                "Could not register the optional Slimefun Doctor schema probe.",
                exception);
        }
    }

    private static void registerSchemaMigrator(DankTech2 plugin, ClassLoader loader) {
        try {
            Class<?> migratorInterface = Class.forName(SCHEMA_MIGRATOR_API, false, loader);
            Object provider = Proxy.newProxyInstance(
                loader,
                new Class<?>[] {migratorInterface},
                LegacyDoctorBridge::invokeSchemaMigrator);
            registerRaw(Bukkit.getServicesManager(), migratorInterface, provider, plugin);
            plugin.getLogger().info("Registered DankTech2 legacy presentation migrator with Slimefun Doctor.");
        } catch (ClassNotFoundException ignored) {
            // Older Slimefun builds do not expose fingerprinted schema migration.
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                Level.WARNING,
                "Could not register the optional Slimefun Doctor schema migrator.",
                exception);
        }
    }

    private static Object invokeSchemaProbe(
        Object proxy,
        Method method,
        Object[] arguments,
        Constructor<?> candidateConstructor,
        Method readinessValueOf,
        boolean supportsClaim
    ) throws ReflectiveOperationException {
        switch (method.getName()) {
            case "getMigrationName":
                return "DankTech2 legacy English presentation";
            case "getSupportedItemIds":
                return SUPPORTED_IDS;
            case "probeItem":
                if (arguments == null
                    || arguments.length < 2
                    || !(arguments[0] instanceof ItemStack)
                    || !(arguments[1] instanceof String)) {
                    return null;
                }

                ItemStack item = (ItemStack) arguments[0];
                String itemId = (String) arguments[1];
                if (!isLegacyPresentation(item, itemId)) {
                    return null;
                }

                Object readiness = readinessValueOf.invoke(null, supportsClaim ? "READY" : "MANUAL_ONLY");
                String detail = supportsClaim
                    ? "Legacy translated Dank presentation can be rebuilt from the registered English template; PDC state is preserved."
                    : "Legacy translated Dank presentation detected; this Slimefun Legacy build cannot fingerprint the migration.";
                if (supportsClaim) {
                    return candidateConstructor.newInstance(
                        CANDIDATE_TYPE,
                        readiness,
                        detail,
                        fingerprint(item));
                }
                return candidateConstructor.newInstance(CANDIDATE_TYPE, readiness, detail);
            case "toString":
                return "DankTech2LegacyPresentationProbe";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return arguments != null && arguments.length == 1 && arguments[0] == proxy;
            default:
                throw new UnsupportedOperationException(
                    "Unsupported LegacyItemSchemaProbe method: " + method.getName());
        }
    }

    private static Object invokeSchemaMigrator(Object proxy, Method method, Object[] arguments) {
        switch (method.getName()) {
            case "getSupportedCandidateTypes":
                return Set.of(CANDIDATE_TYPE);
            case "migrateItem":
                if (arguments == null
                    || arguments.length < 5
                    || !(arguments[0] instanceof ItemStack)
                    || !(arguments[1] instanceof String)
                    || !CANDIDATE_TYPE.equals(arguments[2])
                    || !(arguments[3] instanceof String)) {
                    return false;
                }

                ItemStack item = (ItemStack) arguments[0];
                String itemId = (String) arguments[1];
                String approvedClaim = (String) arguments[3];
                if (!SUPPORTED_IDS.contains(itemId)
                    || !approvedClaim.equals(fingerprint(item))
                    || !isLegacyPresentation(item, itemId)) {
                    return false;
                }
                return refreshEnglishPresentation(item, itemId);
            case "toString":
                return "DankTech2LegacyPresentationMigrator";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return arguments != null && arguments.length == 1 && arguments[0] == proxy;
            default:
                throw new UnsupportedOperationException(
                    "Unsupported LegacyItemSchemaMigrator method: " + method.getName());
        }
    }

    private static boolean isLegacyPresentation(ItemStack item, String itemId) {
        if (!SUPPORTED_IDS.contains(itemId)) {
            return false;
        }

        SlimefunItem slimefunItem = SlimefunItem.getById(itemId);
        if (slimefunItem == null) {
            return false;
        }

        ItemMeta current = item.getItemMeta();
        ItemMeta canonical = slimefunItem.getItem().getItemMeta();
        Component currentName = current.hasDisplayName() ? current.displayName() : null;
        List<Component> currentLore = current.hasLore() ? current.lore() : null;
        Component canonicalName = canonical.hasDisplayName() ? canonical.displayName() : null;
        List<Component> canonicalLore = canonical.hasLore() ? canonical.lore() : null;
        boolean currentHasCjk = containsCjk(currentName) || containsCjk(currentLore);
        boolean canonicalIsEnglish = !containsCjk(canonicalName) && !containsCjk(canonicalLore);
        return currentHasCjk && canonicalIsEnglish;
    }

    private static boolean refreshEnglishPresentation(ItemStack item, String itemId) {
        SlimefunItem slimefunItem = SlimefunItem.getById(itemId);
        if (slimefunItem == null) {
            return false;
        }

        ItemMeta current = item.getItemMeta();
        ItemMeta canonical = slimefunItem.getItem().getItemMeta();
        boolean changed = false;

        Component currentName = current.hasDisplayName() ? current.displayName() : null;
        Component canonicalName = canonical.hasDisplayName() ? canonical.displayName() : null;
        if (containsCjk(currentName) && canonicalName != null && !containsCjk(canonicalName)) {
            current.displayName(canonicalName);
            changed = true;
        }

        List<Component> currentLore = current.hasLore() ? current.lore() : null;
        List<Component> canonicalLore = canonical.hasLore() ? canonical.lore() : null;
        if (containsCjk(currentLore) && canonicalLore != null && !containsCjk(canonicalLore)) {
            current.lore(mergeStaticLore(currentLore, canonicalLore));
            changed = true;
        }

        if (changed) {
            // Mutate only the existing ItemMeta so every addon-owned PDC value remains intact.
            item.setItemMeta(current);
        }
        return changed;
    }

    private static List<Component> mergeStaticLore(List<Component> currentLore, List<Component> canonicalLore) {
        List<Component> result = new ArrayList<>(currentLore);
        for (int i = 0; i < currentLore.size(); i++) {
            Component line = currentLore.get(i);
            if (!containsCjk(line)) {
                continue;
            }
            result.set(i, i < canonicalLore.size() ? canonicalLore.get(i) : Component.empty());
        }

        for (int i = result.size(); i < canonicalLore.size(); i++) {
            result.add(canonicalLore.get(i));
        }
        while (!result.isEmpty() && PLAIN.serialize(result.get(result.size() - 1)).isEmpty()) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static String fingerprint(ItemStack item) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(item.serializeAsBytes()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean containsCjk(List<Component> lines) {
        if (lines == null) {
            return false;
        }
        for (Component line : lines) {
            if (containsCjk(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCjk(Component component) {
        return component != null && containsCjkText(PLAIN.serialize(component));
    }

    private static boolean containsCjkText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRaw(
        ServicesManager services,
        Class service,
        Object provider,
        DankTech2 plugin
    ) {
        services.register(service, provider, plugin, ServicePriority.Normal);
    }
}
