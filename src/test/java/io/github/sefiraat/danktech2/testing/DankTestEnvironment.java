package io.github.sefiraat.danktech2.testing;

import io.github.sefiraat.danktech2.DankTech2;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Provides real mock item metadata and inventories without enabling Slimefun or addon machines. */
public abstract class DankTestEnvironment {
    protected ServerMock server;
    protected PlayerMock player;

    @BeforeEach
    void createEnvironment() {
        server = MockBukkit.mock();
        String description = "name: DankTech2\nversion: test\nmain: "
            + TestPlugin.class.getName() + "\napi-version: '1.21'\n";
        MockBukkit.loadWith(TestPlugin.class,
            new ByteArrayInputStream(description.getBytes(StandardCharsets.UTF_8)));
        player = server.addPlayer();
    }

    @AfterEach
    void closeEnvironment() throws ReflectiveOperationException {
        MockBukkit.unmock();
        Field instance = DankTech2.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    public static class TestPlugin extends DankTech2 {
        @Override
        public void onEnable() {
            try {
                Field instance = DankTech2.class.getDeclaredField("instance");
                instance.setAccessible(true);
                instance.set(null, this);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Unable to initialize test plugin identity", failure);
            }
        }

        @Override
        public void onDisable() {
            // No production managers or recurring tasks are started by this fixture.
        }
    }
}
