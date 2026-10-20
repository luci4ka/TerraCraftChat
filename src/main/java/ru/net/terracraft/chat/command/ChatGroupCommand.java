package ru.net.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.minimessage.translation.Argument;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.util.MessageUtil;

import java.util.List;

public class ChatGroupCommand {

    private final TerraCraftChatPlugin plugin;

    public ChatGroupCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("chat")
                .withSubcommand(new CommandAPICommand("create-group")
                        .withPermission("terracraftchat.group.create")
                        .withArguments(new StringArgument("name"))
                        .executesPlayer((sender, args) -> {
                            String name = ((String) args.get("name")).toLowerCase().trim();
                            if (!name.matches("[a-z0-9_]{3,16}")) {
                                MessageUtil.sendPrefixed(sender, "chat.group-invalid-name");
                                return;
                            }

                            var existing = plugin.getGroupManager().getByName(name);
                            if (existing != null) {
                                MessageUtil.sendPrefixed(sender, "chat.group-name-taken",
                                        Argument.string("name", name));
                                return;
                            }

                            var groupId = plugin.getStorageManager()
                                    .createGroup(name, sender.getUniqueId());
                            if (groupId == null) {
                                MessageUtil.sendPrefixed(sender, "chat.group-create-failed");
                                return;
                            }

                            plugin.getStorageManager().addGroupMember(groupId, sender.getUniqueId());
                            plugin.getGroupManager().setActive(sender, groupId);

                            MessageUtil.sendPrefixed(sender, "chat.group-created",
                                    Argument.string("name", name));
                        })
                )
                .withSubcommand(new CommandAPICommand("invite")
                        .withPermission("terracraftchat.group.invite")
                        .withArguments(new StringArgument("group"))
                        .withArguments(new PlayerProfileArgument("target"))
                        .executesPlayer((sender, args) -> {
                            String groupName = ((String) args.get("group")).toLowerCase();
                            var group = plugin.getGroupManager().getByName(groupName);
                            if (group == null) {
                                MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                                return;
                            }
                            if (!group.ownerUuid().equals(sender.getUniqueId())) {
                                MessageUtil.sendPrefixed(sender, "chat.group-not-owner");
                                return;
                            }

                            List<PlayerProfile> profiles =
                                    (List<PlayerProfile>) args.get("target");
                            if (profiles == null || profiles.isEmpty()) return;
                            var targetId = profiles.get(0).getUniqueId();

                            plugin.getStorageManager().addGroupMember(group.id(), targetId);
                            MessageUtil.sendPrefixed(sender, "chat.group-member-added",
                                    Argument.string("group", groupName));
                        })
                )
                .withSubcommand(new CommandAPICommand("leave")
                        .withPermission("terracraftchat.group.leave")
                        .withArguments(new StringArgument("group"))
                        .executesPlayer((sender, args) -> {
                            String groupName = ((String) args.get("group")).toLowerCase();
                            var group = plugin.getGroupManager().getByName(groupName);
                            if (group == null) {
                                MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                                return;
                            }
                            plugin.getStorageManager().removeGroupMember(
                                    group.id(), sender.getUniqueId());
                            MessageUtil.sendPrefixed(sender, "chat.group-left",
                                    Argument.string("group", groupName));
                        })
                )
                .withSubcommand(new CommandAPICommand("delete")
                        .withPermission("terracraftchat.group.delete")
                        .withArguments(new StringArgument("group"))
                        .executesPlayer((sender, args) -> {
                            String groupName = ((String) args.get("group")).toLowerCase();
                            var group = plugin.getGroupManager().getByName(groupName);
                            if (group == null) {
                                MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                                return;
                            }
                            if (!group.ownerUuid().equals(sender.getUniqueId())
                                    && !sender.hasPermission("terracraftchat.admin")) {
                                MessageUtil.sendPrefixed(sender, "chat.group-not-owner");
                                return;
                            }
                            plugin.getStorageManager().deleteGroup(group.id());
                            MessageUtil.sendPrefixed(sender, "chat.group-deleted",
                                    Argument.string("group", groupName));
                        })
                )
                .withSubcommand(new CommandAPICommand("group")
                        .withPermission("terracraftchat.group.use")
                        .withArguments(new StringArgument("group"))
                        .withOptionalArguments(new GreedyStringArgument("message"))
                        .executesPlayer((sender, args) -> {
                            String groupName = ((String) args.get("group")).toLowerCase();
                            var group = plugin.getGroupManager().getByName(groupName);
                            if (group == null) {
                                MessageUtil.sendPrefixed(sender, "chat.group-not-found");
                                return;
                            }

                            String msg = (String) args.get("message");
                            if (msg == null || msg.isEmpty()) {
                                plugin.getGroupManager().setActive(sender, group.id());
                                MessageUtil.sendPrefixed(sender, "chat.group-active",
                                        Argument.string("name", groupName));
                                return;
                            }

                            if (!plugin.getGroupManager().sendToGroup(sender, group, msg)) {
                                MessageUtil.sendPrefixed(sender, "chat.group-not-member");
                            }
                        })
                )
                .register();
    }
}