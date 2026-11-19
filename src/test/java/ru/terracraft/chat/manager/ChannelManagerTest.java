package ru.terracraft.chat.manager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import ru.terracraft.chat.model.Channel;
import ru.terracraft.chat.storage.StorageManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Выбор канала на мок-сервере: префиксы, права, активный канал игрока.
 *
 * <p>Хранилище передаётся «выключенным» ({@link StorageManager} без {@code init()}):
 * все записи в таком состоянии — no-op, и БД тестам не нужна.
 */
@DisplayName("ChannelManager — выбор канала")
class ChannelManagerTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ChannelManager manager() {
        StorageManager disabledStorage = new StorageManager(null);
        assertFalse(disabledStorage.isAvailable());
        return new ChannelManager(null, disabledStorage);
    }

    private static Channel local() {
        return new Channel("local", "", 100, "", "local", 0, true);
    }

    private static Channel global() {
        return new Channel("global", "!", -1, "", "global", 0, true);
    }

    private static Channel staff() {
        return new Channel("staff", "#", -1, "terracraftchat.channel.staff", "staff", 0, false);
    }

    @Test
    @DisplayName("сообщение с префиксом уходит в нужный канал, префикс отрезается")
    void resolvesPrefixedChannel() {
        ChannelManager channels = manager();
        channels.load(List.of(local(), global(), staff()), "local");

        PlayerMock player = server.addPlayer("Steve");
        ChannelManager.Resolved resolved = channels.resolve(player, "!привет всем");

        assertEquals("global", resolved.channel().id());
        assertEquals("привет всем", resolved.message());
    }

    @Test
    @DisplayName("без префикса используется канал по умолчанию")
    void fallsBackToDefaultChannel() {
        ChannelManager channels = manager();
        channels.load(List.of(local(), global()), "local");

        PlayerMock player = server.addPlayer("Steve");
        ChannelManager.Resolved resolved = channels.resolve(player, "привет");

        assertEquals("local", resolved.channel().id());
        assertEquals("привет", resolved.message());
    }

    @Test
    @DisplayName("канал с правом недоступен без права")
    void channelWithPermissionIsGuarded() {
        ChannelManager channels = manager();
        channels.load(List.of(local(), global(), staff()), "local");

        PlayerMock player = server.addPlayer("Steve");

        assertFalse(channels.canUse(player, channels.get("staff")));
        assertTrue(channels.canUse(player, channels.get("local")));
        assertEquals(List.of("local", "global"),
                channels.getAvailable(player).stream().map(Channel::id).toList());
    }

    @Test
    @DisplayName("активный канал переопределяет канал по умолчанию")
    void activeChannelIsUsed() {
        ChannelManager channels = manager();
        channels.load(List.of(local(), global()), "local");

        PlayerMock player = server.addPlayer("Steve");
        channels.setActive(player, channels.get("global"));

        assertEquals("global", channels.resolve(player, "привет").channel().id());
        assertSame(channels.get("global"), channels.getActive(player));

        channels.clearActive(player);
        assertEquals("local", channels.resolve(player, "привет").channel().id());
    }

    @Test
    @DisplayName("неизвестный id канала не ломает менеджер")
    void unknownChannelIsNull() {
        ChannelManager channels = manager();
        channels.load(List.of(local()), "local");

        assertNull(channels.get("nope"));
        assertNull(channels.get(""));
    }

    @Test
    @DisplayName("самый длинный префикс имеет приоритет")
    void longestPrefixWins() {
        ChannelManager channels = manager();
        Channel single = new Channel("one", "!", -1, "", "global", 0, true);
        Channel doublePrefix = new Channel("two", "!!", -1, "", "global", 0, true);

        channels.load(List.of(single, doublePrefix), "one");

        PlayerMock player = server.addPlayer("Steve");
        assertEquals("two", channels.resolve(player, "!!привет").channel().id());
        assertEquals("one", channels.resolve(player, "!привет").channel().id());
    }

    @Test
    @DisplayName("RP-радиус и дефолтный канал читаются из конфига")
    void defaultChannelFallsBackToFirstEntry() {
        ChannelManager channels = manager();
        channels.load(List.of(local(), global()), "не-существует");

        assertEquals("local", channels.getDefault().id());
    }
}
