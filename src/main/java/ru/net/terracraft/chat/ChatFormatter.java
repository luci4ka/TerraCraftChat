package ru.net.terracraft.chat;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.translation.Argument;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.net.terracraft.chat.config.ChatConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatFormatter {

    private final TerraCraftChatPlugin plugin;
    private final ChatConfig config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final LegacyComponentSerializer legacySerializer =
            LegacyComponentSerializer.legacyAmpersand().toBuilder()
                    .hexColors()
                    .build();

    // ===== Маркеры =====
    private static final String MENTION_START = "\u0001";
    private static final String MENTION_END = "\u0002";
    private static final String ITEM_MARKER = "\u0003";
    private static final String INV_MARKER = "\u0004";
    private static final String EMOJI_MARKER_START = "\u0005";
    private static final String EMOJI_MARKER_END = "\u0006";

    // ===== Regex =====
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]{3,16})");
    private static final Pattern MENTION_MARKER_PATTERN = Pattern.compile(
            "\u0001([A-Za-z0-9_]{3,16})\u0002"
    );
    private static final Pattern EMOJI_MARKER_PATTERN = Pattern.compile(
            "\u0005([a-z0-9_]+)\u0006"
    );
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)"
    );

    public ChatFormatter(TerraCraftChatPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ============================================================
    //  ПУБЛИЧНЫЙ ЧАТ
    // ============================================================

    public Component formatLocal(Player sender, String plainMessage, Player viewer, UUID messageId) {
        return formatPublicWith(sender, plainMessage, viewer, config.formats().local(), messageId);
    }

    public Component formatGlobal(Player sender, String plainMessage, Player viewer, UUID messageId) {
        return formatPublicWith(sender, plainMessage, viewer, config.formats().global(), messageId);
    }

    private Component formatPublicWith(Player sender, String plainMessage, Player viewer,
                                       String formatString, UUID messageId) {
        // 1. PAPI
        formatString = applyPapi(sender, viewer, formatString);

        // 2. SpriteResolver
        formatString = SpriteResolver.resolve(formatString);

        // 3. Подстановка команды для кликабельного ника
        formatString = formatString.replace("{msg_command}", "/msg " + sender.getName() + " ");

        // 4. Резолверы
        TagResolver.Builder resolver = TagResolver.builder();
        resolver.resolver(Placeholder.component("display_name", sender.displayName()));
        resolver.resolver(Placeholder.unparsed("sender_name", sender.getName()));
        resolver.resolver(Placeholder.unparsed("world", sender.getWorld().getName()));

        // 5. Голова
        String senderHead = config.display().showPlayerHeads()
                ? plugin.getSkinHeadCache().getHeadTag(sender)
                : "";
        resolver.resolver(Placeholder.parsed("sender_head", senderHead));

        // 6. Сообщение
        Component messageComponent = parseMessage(sender, plainMessage, viewer);
        resolver.resolver(Placeholder.component("message", messageComponent));

        Component result = miniMessage.deserialize(formatString, resolver.build());

        // 7. Кнопка удаления для модераторов
        if (config.delete().enabled()
                && messageId != null
                && viewer.hasPermission("terracraftchat.moderator")) {
            result = result.append(Component.space()).append(buildDeleteButton(messageId));
        }

        // 8. Панель треда
        if (config.threads().enabled() && messageId != null) {
            Optional<ChatMessage> msgOpt = plugin.getChatMessageRegistry().get(messageId);
            if (msgOpt.isPresent()) {
                result = result.append(buildThreadPanel(msgOpt.get()));
            }
        }

        return result;
    }

    // ============================================================
    //  ТРЕДЫ
    // ============================================================

    /**
     * Панель треда: кнопка [↩] + последние N ответов.
     */
    public Component buildThreadPanel(ChatMessage msg) {
        Component panel = Component.empty();

        // Кнопка ответа
        Component replyBtn = Component.text("[↩]")
                .color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/thread_reply " + msg.getId()))
                .hoverEvent(Component.translatable("chat.thread-button-hover"));
        panel = panel.append(replyBtn);

        // Инлайн-тред
        if (msg.getThreadSize() > 0) {
            int shown = config.threads().inlineThreadSize();
            List<ChatMessage.ThreadReply> recent = msg.getRecentReplies(shown);

            for (ChatMessage.ThreadReply reply : recent) {
                Component line = Component.text("\n  ↳ ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(reply.getAuthorName(), NamedTextColor.GRAY))
                        .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(reply.getText(), NamedTextColor.WHITE));
                panel = panel.append(line);
            }

            int hidden = msg.getThreadSize() - recent.size();
            if (hidden > 0) {
                panel = panel.append(Component.text(
                        "\n  ... ещё " + hidden + " ответов",
                        NamedTextColor.DARK_GRAY
                ));
            }
        }

        return Component.text(" ").append(panel);
    }

    /**
     * Уведомление о новом ответе в треде.
     */
    public Component formatThreadReply(ChatMessage parent, ChatMessage.ThreadReply reply) {
        return Component.text()
                .append(Component.text("  ↳ ", NamedTextColor.DARK_GRAY))
                .append(Component.text(reply.getAuthorName(), NamedTextColor.GRAY))
                .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                .append(Component.translatable(
                        "chat.thread-replied",
                        Argument.string("target", parent.getSenderName())
                ))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(Component.text(reply.getText(), NamedTextColor.WHITE))
                .build();
    }

    // ============================================================
    //  ЛИЧНЫЕ СООБЩЕНИЯ
    // ============================================================

    public Component formatPrivateToSender(Player sender, Player receiver, Component originalMessage) {
        return formatPrivateWith(sender, receiver, originalMessage, config.formats().privateMsg());
    }

    public Component formatPrivateToReceiver(Player sender, Player receiver, Component originalMessage) {
        return formatPrivateWith(sender, receiver, originalMessage, config.formats().privateMsg());
    }

    public Component formatReplyToSender(Player sender, Player receiver, Component originalMessage) {
        return formatPrivateWith(sender, receiver, originalMessage, config.formats().reply());
    }

    public Component formatReplyToReceiver(Player sender, Player receiver, Component originalMessage) {
        return formatPrivateWith(sender, receiver, originalMessage, config.formats().reply());
    }

    private Component formatPrivateWith(Player sender, Player receiver,
                                        Component originalMessage, String formatString) {
        // 1. PAPI
        formatString = applyPapi(sender, receiver, formatString);

        // 2. SpriteResolver
        formatString = SpriteResolver.resolve(formatString);

        // 3. Сообщение
        String plain = PlainTextComponentSerializer.plainText().serialize(originalMessage);
        Component messageComponent = parseMessage(sender, plain, receiver);

        // 4. Головы
        String senderHead = config.display().showPlayerHeads()
                ? plugin.getSkinHeadCache().getHeadTag(sender)
                : "";
        String receiverHead = config.display().showPlayerHeads()
                ? plugin.getSkinHeadCache().getHeadTag(receiver)
                : "";

        // 5. Резолверы
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("sender", sender.displayName()))
                .resolver(Placeholder.component("receiver", receiver.displayName()))
                .resolver(Placeholder.parsed("sender_head", senderHead))
                .resolver(Placeholder.parsed("receiver_head", receiverHead))
                .resolver(Placeholder.component("message", messageComponent))
                .build();

        return miniMessage.deserialize(formatString, resolver);
    }

    // ============================================================
    //  ПАРСИНГ СООБЩЕНИЯ
    // ============================================================

    private Component parseMessage(Player sender, String plainMessage, Player viewer) {
        String working = plainMessage;

        // 0. Эмодзи → маркеры
        if (config.emoji().enabled() && sender.hasPermission("terracraftchat.emoji")) {
            working = plugin.getEmojiManager().replaceWithMarkers(working);
        }

        // 1. @Ник → маркеры
        String marked = MENTION_PATTERN.matcher(working)
                .replaceAll(MENTION_START + "$1" + MENTION_END);

        // 2. [item] → маркер
        if (config.display().itemPlaceholderEnabled() && sender.hasPermission("terracraftchat.item")) {
            marked = marked.replace("[item]", ITEM_MARKER);
        }

        // 3. [inv] → маркер (только если смотрит не сам отправитель)
        boolean canUseInv = config.display().invPlaceholderEnabled()
                && sender.hasPermission("terracraftchat.inv")
                && !sender.getUniqueId().equals(viewer.getUniqueId());
        if (canUseInv) {
            marked = marked.replace("[inv]", INV_MARKER);
        }

        // 4. Парсинг в Component
        Component parsed = sender.hasPermission("terracraftchat.color")
                ? legacySerializer.deserialize(marked)
                : Component.text(marked);

        // 5. Замены по маркерам
        Component result = parsed;

        if (marked.contains(MENTION_START)) {
            result = replaceMentionMarkers(result);
        }
        if (marked.contains(ITEM_MARKER)) {
            result = replaceItemMarker(sender, result);
        }
        if (marked.contains(INV_MARKER)) {
            result = replaceInvMarker(sender, result);
        }
        if (marked.contains(EMOJI_MARKER_START)) {
            result = replaceEmojiMarkers(result);
        }

        // 6. Ссылки — в самом конце
        result = makeLinksClickable(result);

        return result;
    }

    // ============================================================
    //  ЗАМЕНЫ МАРКЕРОВ
    // ============================================================

    private Component replaceMentionMarkers(Component component) {
        Component step1 = component.replaceText(builder -> builder
                .match(MENTION_MARKER_PATTERN)
                .replacement((match, original) ->
                        Component.text("@" + match.group(1)).color(NamedTextColor.YELLOW))
        );

        Component step2 = step1.replaceText(builder -> builder
                .matchLiteral(MENTION_START).replacement(Component.empty()));
        return step2.replaceText(builder -> builder
                .matchLiteral(MENTION_END).replacement(Component.empty()));
    }

    private Component replaceItemMarker(Player sender, Component component) {
        ItemStack item = sender.getInventory().getItemInMainHand();

        Component replacement = item.isEmpty()
                ? Component.translatable("chat.item-hand-empty")
                : Component.translatable(item).hoverEvent(item);

        return component.replaceText(builder -> builder
                .matchLiteral(ITEM_MARKER)
                .replacement(replacement)
        );
    }

    private Component replaceInvMarker(Player sender, Component component) {
        Component invComponent = Component.translatable(
                        "chat.inv-label",
                        Argument.string("player", sender.getName())
                )
                .clickEvent(ClickEvent.runCommand("/inv_show " + sender.getName()))
                .hoverEvent(Component.translatable(
                        "chat.inv-hover",
                        Argument.string("player", sender.getName())
                ));

        return component.replaceText(builder -> builder
                .matchLiteral(INV_MARKER)
                .replacement(invComponent)
        );
    }

    private Component replaceEmojiMarkers(Component component) {
        return component.replaceText(builder -> builder
                .match(EMOJI_MARKER_PATTERN)
                .replacement((match, original) -> {
                    String code = match.group(1);
                    String raw = plugin.getEmojiManager().getRaw(code);
                    if (raw == null) {
                        return Component.text(":" + code + ":");
                    }
                    return miniMessage.deserialize(raw);
                })
        );
    }

    private Component makeLinksClickable(Component component) {
        return component.replaceText(builder -> builder
                .match(URL_PATTERN)
                .replacement((match, original) -> {
                    String url = match.group();
                    return Component.text(url).clickEvent(ClickEvent.openUrl(url));
                })
        );
    }

    // ============================================================
    //  ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    private String applyPapi(Player sender, Player viewer, String formatString) {
        if (!isPapiEnabled()) {
            return formatString;
        }
        formatString = PlaceholderAPI.setPlaceholders(sender, formatString);
        formatString = PlaceholderAPI.setPlaceholders(viewer, formatString);
        return formatString;
    }

    private Component buildDeleteButton(UUID messageId) {
        return miniMessage.deserialize(config.delete().button())
                .clickEvent(ClickEvent.runCommand("/chat_delete " + messageId))
                .hoverEvent(Component.translatable("chat.delete-button-hover"));
    }

    public void notifyMentions(Player sender, String plainMessage) {
        String soundName = config.mentions().sound();
        if (soundName == null || soundName.isEmpty()) {
            return;
        }

        Sound sound = Registry.SOUNDS.get(NamespacedKey.fromString(soundName));
        if (sound == null) {
            return;
        }

        Matcher matcher = MENTION_PATTERN.matcher(plainMessage);
        while (matcher.find()) {
            String name = matcher.group(1);
            Player mentioned = Bukkit.getPlayerExact(name);
            if (mentioned == null || mentioned.equals(sender)) {
                continue;
            }
            mentioned.playSound(mentioned.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    // ============================================================
    //  СОЦ-ШПИОН
    // ============================================================

    public Component formatSocialSpy(String type, Player sender, Player receiver, String message) {
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.unparsed("type", type))
                .resolver(Placeholder.component("sender", sender.displayName()))
                .resolver(Placeholder.component("receiver", receiver.displayName()))
                .resolver(Placeholder.unparsed("message", message))
                .build();

        return miniMessage.deserialize(config.socialSpy().format(), resolver);
    }

    // ============================================================
    //  RP-КОМАНДЫ
    // ============================================================

    public Component formatMe(Player sender, String action, Player viewer) {
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("player", sender.displayName()))
                .resolver(Placeholder.unparsed("message", action))
                .build();
        return miniMessage.deserialize(config.rp().meFormat(), resolver);
    }

    public Component formatDo(Player sender, String action, Player viewer) {
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("player", sender.displayName()))
                .resolver(Placeholder.unparsed("message", action))
                .build();
        return miniMessage.deserialize(config.rp().doFormat(), resolver);
    }

    public Component formatTry(Player sender, String action, boolean success, Player viewer) {
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("player", sender.displayName()))
                .resolver(Placeholder.unparsed("message", action))
                .resolver(Placeholder.parsed("result", success ? config.rp().trySuccess() : config.rp().tryFail()))
                .build();
        return miniMessage.deserialize(config.rp().tryFormat(), resolver);
    }

    public Component formatRoll(Player sender, int result, int max, Player viewer) {
        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("player", sender.displayName()))
                .resolver(Placeholder.unparsed("result", String.valueOf(result)))
                .resolver(Placeholder.unparsed("max", String.valueOf(max)))
                .build();
        return miniMessage.deserialize(config.rp().rollFormat(), resolver);
    }

    // ============================================================
    //  BROADCAST
    // ============================================================

    public Component formatBroadcast(String message) {
        Component msgComponent = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(message);

        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("message", msgComponent))
                .build();
        return miniMessage.deserialize(config.formats().broadcast(), resolver);
    }

    // ============================================================
    //  ХЕЛПЕРЫ
    // ============================================================

    private boolean isPapiEnabled() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }
}