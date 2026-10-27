package io.github.sefiraat.danktech2.managers;

import org.bukkit.entity.Item;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Keeps the original WildStacker item-count contract without fetching a proprietary
 * provided-only API from an unreliable Maven server at build time.
 *
 * <p>This bridge is only constructed when WildStacker is enabled (softdepend).
 * Missing/incompatible APIs fail closed rather than treating a stacked drop as
 * an ordinary Bukkit stack, which could lose items or duplicate pack contents.
 */
public final class WildStackerBridge {

    private static final String API_CLASS = "com.bgsoftware.wildstacker.api.WildStackerAPI";

    private final Method getItemAmount;
    private final Method getStackedItem;
    private final Method setStackAmount;

    public WildStackerBridge() {
        this(loadWildStackerApi());
    }

    // Package-private for a dependency-free compatibility contract test.
    WildStackerBridge(Class<?> apiType) {
        try {
            getItemAmount = apiType.getMethod("getItemAmount", Item.class);
            getStackedItem = apiType.getMethod("getStackedItem", Item.class);
            setStackAmount = getStackedItem.getReturnType().getMethod("setStackAmount", int.class, boolean.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new IllegalStateException("WildStacker item API is incompatible; refusing unsafe pickup handling", e);
        }
    }

    public int getItemAmount(Item item) {
        try {
            Object result = getItemAmount.invoke(null, item);
            if (!(result instanceof Integer amount)) {
                throw new IllegalStateException("WildStacker returned a non-integer item amount");
            }
            return amount;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Could not read WildStacker item count safely", cause(e));
        }
    }

    public void setStackAmount(Item item, int amount) {
        try {
            Object stackedItem = getStackedItem.invoke(null, item);
            if (stackedItem == null) {
                throw new IllegalStateException("WildStacker returned no stacked item; refusing unsafe count update");
            }
            setStackAmount.invoke(stackedItem, amount, true);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Could not update WildStacker item count safely", cause(e));
        }
    }

    private static Throwable cause(ReflectiveOperationException e) {
        return e instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause()
            : e;
    }

    private static Class<?> loadWildStackerApi() {
        try {
            return Class.forName(API_CLASS, true, WildStackerBridge.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("WildStacker is enabled but its item API is unavailable", e);
        }
    }
}
