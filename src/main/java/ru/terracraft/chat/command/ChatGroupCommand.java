package ru.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.minimessage.translation.Argument;
import org.bukkit.entity.Player;
import ru.terracraft.chat.TerraCraftChatPlugin;
import ru.terracraft.chat.storage.StorageManager;
import ru.terracraft.chat.util.MessageUtil;
import ru.terracraft.chat.util.Tasks;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Групповые чаты.
 *
 * <p>Все обращения к БД выполняются на виртуальных потоках
 * ({@link Tasks#supply}), а ответ игроку возвращается в main-поток.
 * Раньше каждая подкоманда блокировала главный поток на запрос к MariaDB.
 */
public class ChatGroupCommand {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_]{3,16}");

    private final TerraCraftChatPlugin plugin;

    public ChatGroupCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chat")
                .withSubcommand(createGroup())
                .withSubcommand(invite())
                .withSubcommand(leave())
                .withSubcommand(delete())
                .withSubcommand(send())
                .register();
    }

    // ============================================================
    //  ПОДКОМАНДЫ
    // ============================================================

    private CommandAPICommand createGroup() {
        return new CommandAPICommand("create-group")
                .withPermission("terracraftchat.group.create")
                .withArguments(new StringArgument("name"))
                .executesPlayer((sender, args) -> {
                    String name = ((String) args.get("name")).toLowerCase(Locale.ROOT).trim();
                    if (!NAME_PATTERN.matcher(name).matches()) {
                        MessageUtil.sendPrefixed(sender, "chat.group-invalid-name");
                        return;
                    }

                    Supplier<CreateResult> work = () -> {
                        var groups = plugin.getGroupManager();
                        if (groups.findByName(name) != null) {
                            return new CreateResult.Taken();
                        }
                        UUID id = groups.create(name, sender);
                        return id == null ? new CreateResult.Failed() : new CreateResult.Created(id);
                    };

                    runAsync(sender, "group/create", work, result -> {
                        switch (result) {
                            case CreateResult.Taken ignored -> MessageUtil.sendPrefixed(sender,
                                    "chat.group-name-taken", Argument.string("name", name));
                            case CreateResult.Failed ignored -> MessageUtil.sendPrefixed(sender,
                                    "chat.group-create-failed");
                            case CreateResult.Created created -> {
                                plugin.getGroupManager().setActive(sender, created.id());
                                MessageUtil.sendPrefixed(sender, "chat.group-created",
                                        Argument.string("name", name));
                            }
                        }
                    });
                });
    }

    private CommandAPICommand invite() {
        return new CommandAPICommand("invite")
                .withPermission("terracraftchat.group.invite")
                .withArguments(new StringArgument("group"))
                .withArguments(new PlayerProfileArgument("target"))
                .executesPlayer((sender, args) -> {
                    String groupName = ((String) args.get("group")).toLowerCase(Locale.ROOT);
                    List<PlayerProfile> profiles = asProfiles(args.get("target"));
                    if (profiles.isEmpty()) return;
                    UUID targetId = profiles.get(0).getId();

                    runAsync(sender, "group/invite", () -> {
                        StorageManager.GroupRow group = plugin.getGroupManager().findByName(groupName);
                        if (group == null) return GroupResult.notFound();
                        if (!group.ownerUuid().equals(sender.getUniqueId())) return GroupResult.notOwner();

                        plugin.getGroupManager().addMember(group.id(), targetId);
                        return GroupResult.ok(group);
                    }, result -> {
                        switch (result.status()) {
                            case NOT_FOUND -> MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                            case NOT_OWNER -> MessageUtil.sendPrefixed(sender, "chat.group-not-owner");
                            case NOT_MEMBER -> MessageUtil.sendPrefixed(sender, "chat.group-not-member");
                            case OK -> MessageUtil.sendPrefixed(sender, "chat.group-member-added",
                                    Argument.string("group", groupName));
                            default -> { }
                        }
                    });
                });
    }

    private CommandAPICommand leave() {
        return new CommandAPICommand("leave")
                .withPermission("terracraftchat.group.leave")
                .withArguments(new StringArgument("group"))
                .executesPlayer((sender, args) -> {
                    String groupName = ((String) args.get("group")).toLowerCase(Locale.ROOT);

                    runAsync(sender, "group/leave", () -> {
                        StorageManager.GroupRow group = plugin.getGroupManager().findByName(groupName);
                        if (group == null) return GroupResult.notFound();

                        plugin.getGroupManager().removeMember(group.id(), sender.getUniqueId());
                        return GroupResult.ok(group);
                    }, result -> {
                        switch (result.status()) {
                            case NOT_FOUND -> MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                            default -> MessageUtil.sendPrefixed(sender, "chat.group-left",
                                    Argument.string("group", groupName));
                        }
                    });
                });
    }

    private CommandAPICommand delete() {
        return new CommandAPICommand("delete")
                .withPermission("terracraftchat.group.delete")
                .withArguments(new StringArgument("group"))
                .executesPlayer((sender, args) -> {
                    String groupName = ((String) args.get("group")).toLowerCase(Locale.ROOT);
                    boolean admin = sender.hasPermission("terracraftchat.admin");

                    runAsync(sender, "group/delete", () -> {
                        StorageManager.GroupRow group = plugin.getGroupManager().findByName(groupName);
                        if (group == null) return GroupResult.notFound();
                        if (!group.ownerUuid().equals(sender.getUniqueId()) && !admin) {
                            return GroupResult.notOwner();
                        }

                        plugin.getGroupManager().delete(group.id());
                        return GroupResult.ok(group);
                    }, result -> {
                        switch (result.status()) {
                            case NOT_FOUND -> MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                            case NOT_OWNER -> MessageUtil.sendPrefixed(sender, "chat.group-not-owner");
                            default -> MessageUtil.sendPrefixed(sender, "chat.group-deleted",
                                    Argument.string("group", groupName));
                        }
                    });
                });
    }

    private CommandAPICommand send() {
        return new CommandAPICommand("group")
                .withPermission("terracraftchat.group.use")
                .withArguments(new StringArgument("group"))
                .withOptionalArguments(new GreedyStringArgument("message"))
                .executesPlayer((sender, args) -> {
                    String groupName = ((String) args.get("group")).toLowerCase(Locale.ROOT);
                    String message = (String) args.get("message");

                    runAsync(sender, "group/send", () -> {
                        StorageManager.GroupRow group = plugin.getGroupManager().findByName(groupName);
                        if (group == null) return GroupResult.notFound();

                        if (message == null || message.isEmpty()) {
                            plugin.getGroupManager().setActive(sender, group.id());
                            return GroupResult.active(group);
                        }

                        boolean delivered = plugin.getGroupManager().sendToGroup(sender, group, message);
                        return delivered ? GroupResult.ok(group) : GroupResult.notMember();
                    }, result -> {
                        switch (result.status()) {
                            case NOT_FOUND -> MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                            case NOT_MEMBER -> MessageUtil.sendPrefixed(sender, "chat.group-not-member");
                            case ACTIVE -> MessageUtil.sendPrefixed(sender, "chat.group-active",
                                    Argument.string("name", groupName));
                            default -> { }
                        }
                    });
                });
    }

    // ============================================================
    //  ХЕЛПЕРЫ
    // ============================================================

    private <T extends GroupOutcome> void runAsync(Player sender, String what,
                                                   Supplier<T> work,
                                                   Consumer<T> onMain) {
        Tasks.supply(plugin, what, work).thenAccept(outcome -> {
            if (outcome == null) return;
            Tasks.sync(plugin, () -> {
                if (sender.isOnline()) onMain.accept(outcome);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static List<PlayerProfile> asProfiles(Object raw) {
        if (raw instanceof List<?> list) {
            return (List<PlayerProfile>) list;
        }
        return List.of();
    }

    /** Общий результат операций с группой. */
    private sealed interface GroupOutcome permits CreateResult, GroupResult {}

    private sealed interface CreateResult extends GroupOutcome {
        record Created(UUID id) implements CreateResult {}
        record Taken() implements CreateResult {}
        record Failed() implements CreateResult {}
    }

    private record GroupResult(Status status, StorageManager.GroupRow group) implements GroupOutcome {

        enum Status { OK, NOT_FOUND, NOT_OWNER, NOT_MEMBER, ACTIVE }

        static GroupResult ok(StorageManager.GroupRow group) {
            return new GroupResult(Status.OK, group);
        }

        static GroupResult active(StorageManager.GroupRow group) {
            return new GroupResult(Status.ACTIVE, group);
        }

        static GroupResult notFound() {
            return new GroupResult(Status.NOT_FOUND, null);
        }

        static GroupResult notOwner() {
            return new GroupResult(Status.NOT_OWNER, null);
        }

        static GroupResult notMember() {
            return new GroupResult(Status.NOT_MEMBER, null);
        }
    }
}
