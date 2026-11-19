package ru.terracraft.chat.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ru.terracraft.chat.storage.StorageManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("IgnoreManager — игнор-лист в памяти")
class IgnoreManagerTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static IgnoreManager manager() {
        return new IgnoreManager(null, new StorageManager(null));
    }

    @Test
    @DisplayName("до загрузки игнор-лист пуст и не бросает NPE")
    void emptyBeforeLoad() {
        PlayerMock viewer = server.addPlayer("Steve");
        IgnoreManager ignores = manager();

        assertFalse(ignores.isIgnoring(viewer.getUniqueId(), UUID.randomUUID()));
        assertFalse(ignores.isIgnoringAny(viewer.getUniqueId()));
        assertEquals(0, ignores.getIgnoredByUuid(viewer.getUniqueId()).size());
    }

    @Test
    @DisplayName("игнор добавляется и снимается")
    void addsAndRemovesIgnore() {
        PlayerMock viewer = server.addPlayer("Steve");
        PlayerMock target = server.addPlayer("Alex");
        IgnoreManager ignores = manager();

        ignores.load(viewer);
        ignores.addIgnore(viewer, target);

        assertTrue(ignores.isIgnoring(viewer, target));
        assertTrue(ignores.isIgnoringAny(viewer.getUniqueId()));

        ignores.removeIgnore(viewer, target);

        assertFalse(ignores.isIgnoring(viewer, target));
    }

    @Test
    @DisplayName("себя игнорировать нельзя")
    void cannotIgnoreSelf() {
        PlayerMock viewer = server.addPlayer("Steve");
        IgnoreManager ignores = manager();

        ignores.addIgnore(viewer, viewer);

        assertFalse(ignores.isIgnoring(viewer, viewer));
    }

    @Test
    @DisplayName("getIgnoredByUuid отдаёт иммутабельный снимок")
    void snapshotIsImmutable() {
        PlayerMock viewer = server.addPlayer("Steve");
        PlayerMock target = server.addPlayer("Alex");
        IgnoreManager ignores = manager();

        ignores.addIgnore(viewer, target);

        assertThrows(UnsupportedOperationException.class,
                () -> ignores.getIgnoredByUuid(viewer.getUniqueId()).clear());
    }

    @Test
    @DisplayName("unload очищает кэш игрока")
    void unloadClearsCache() {
        PlayerMock viewer = server.addPlayer("Steve");
        PlayerMock target = server.addPlayer("Alex");
        IgnoreManager ignores = manager();

        ignores.addIgnore(viewer, target);
        ignores.unload(viewer);

        assertFalse(ignores.isIgnoring(viewer, target));
    }
}
