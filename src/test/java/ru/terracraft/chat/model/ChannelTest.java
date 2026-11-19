package ru.terracraft.chat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Channel — модель канала")
class ChannelTest {

    @Test
    @DisplayName("id приводится к нижнему регистру, null-поля безопасны")
    void normalisesFields() {
        Channel channel = new Channel("GLOBAL", null, -1, null, "global", 0, true);

        assertEquals("global", channel.id());
        assertEquals("", channel.prefix());
        assertEquals("", channel.permission());
    }

    @Test
    void detectsGlobalAndPrefixedChannels() {
        assertTrue(new Channel("global", "!", -1, "", "global", 0, true).isGlobal());
        assertFalse(new Channel("local", "", 100, "", "local", 0, true).isGlobal());
        assertTrue(new Channel("global", "!", -1, "", "global", 0, true).hasPrefix());
        assertFalse(new Channel("local", "", 100, "", "local", 0, true).hasPrefix());
    }

    @Test
    @DisplayName("канал с правом никогда не публичный (защита staff-канала)")
    void channelWithPermissionIsNeverPublic() {
        Channel staff = new Channel("staff", "#", -1, "terracraftchat.channel.staff", "staff", 0, true);

        assertFalse(staff.isPublic(), "public: true в конфиге не должен утекать staff-канал в Discord");
    }

    @Test
    void publicChannelWithoutPermissionIsPublic() {
        assertTrue(new Channel("local", "", 100, "", "local", 0, true).isPublic());
    }

    @Test
    void stripsChannelPrefix() {
        Channel global = new Channel("global", "!", -1, "", "global", 0, true);

        assertEquals("привет", global.stripPrefix("!привет"));
        assertEquals("привет", global.stripPrefix("!  привет  "));
        assertNull(global.stripPrefix("привет"));
    }

    @Test
    @DisplayName("канал без префикса ничего не отрезает")
    void channelWithoutPrefixStripsNothing() {
        Channel local = new Channel("local", "", 100, "", "local", 0, true);

        assertNull(local.stripPrefix("!привет"));
    }

    @Test
    @DisplayName("record-семантика: equals/hashCode по всем компонентам")
    void recordEquality() {
        Channel first = new Channel("local", "", 100, "", "local", 0, true);
        Channel second = new Channel("LOCAL", "", 100, "", "local", 0, true);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
