package io.github.sefiraat.danktech2.managers;

import org.bukkit.entity.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildStackerBridgeTest {

    @BeforeEach
    void reset() {
        FakeWildStackerAPI.stacked = new FakeStack(37);
    }

    @Test
    void readsTheEntireVirtualStackAndPreservesNameUpdateFlag() {
        WildStackerBridge bridge = new WildStackerBridge(FakeWildStackerAPI.class);
        assertEquals(37, bridge.getItemAmount(null));
        bridge.setStackAmount(null, 12);
        assertEquals(12, FakeWildStackerAPI.stacked.amount);
        assertTrue(FakeWildStackerAPI.stacked.updateName);
    }

    @Test
    void missingApiMethodFailsClosed() {
        assertThrows(IllegalStateException.class, () -> new WildStackerBridge(IncompatibleApi.class));
    }

    @Test
    void missingStackedItemIsNeverTreatedAsAnOrdinaryDrop() {
        WildStackerBridge bridge = new WildStackerBridge(FakeWildStackerAPI.class);
        FakeWildStackerAPI.stacked = null;
        assertThrows(IllegalStateException.class, () -> bridge.setStackAmount(null, 12));
    }

    @Test
    void thrownApiErrorDoesNotFallbackToThePhysicalItemAmount() {
        WildStackerBridge bridge = new WildStackerBridge(ThrowingApi.class);
        assertThrows(IllegalStateException.class, () -> bridge.getItemAmount(null));
    }

    public static final class FakeWildStackerAPI {
        public static FakeStack stacked;

        public static int getItemAmount(Item item) {
            return stacked == null ? 0 : stacked.amount;
        }

        public static FakeStack getStackedItem(Item item) {
            return stacked;
        }
    }

    public static final class FakeStack {
        public int amount;
        public boolean updateName;

        public FakeStack(int amount) {
            this.amount = amount;
        }

        public void setStackAmount(int amount, boolean updateName) {
            this.amount = amount;
            this.updateName = updateName;
        }
    }

    public static final class IncompatibleApi {
        public static int getItemAmount(Item item) {
            return 1;
        }
    }

    public static final class ThrowingApi {
        public static int getItemAmount(Item item) {
            throw new IllegalStateException("API failure");
        }

        public static FakeStack getStackedItem(Item item) {
            return null;
        }
    }
}
