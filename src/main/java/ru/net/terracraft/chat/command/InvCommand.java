package ru.net.terracraft.chat.command;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.PlayerProfileArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.net.terracraft.chat.TerraCraftChatPlugin;
import ru.net.terracraft.chat.model.InvViewHolder;
import ru.net.terracraft.chat.util.MessageUtil;

import java.util.List;
import java.util.UUID;

public class InvCommand {

    private final TerraCraftChatPlugin plugin;

    public InvCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("inv_show")
                // /inv_show player <ник> — только админ
                .withSubcommand(new CommandAPICommand("player")
                        .withPermission("terracraftchat.inv.admin")
                        .withArguments(new PlayerProfileArgument("target"))
                        .executesPlayer((sender, args) -> {
                            List<PlayerProfile> profiles = (List<PlayerProfile>) args.get("target");
                            if (profiles == null || profiles.isEmpty()) return;

                            UUID targetId = profiles.get(0).getUniqueId();
                            Player target = Bukkit.getPlayer(targetId);
                            if (target == null) {
                                MessageUtil.sendPrefixed(sender, "chat.player-not-found");
                                return;
                            }

                            if (target.getUniqueId().equals(sender.getUniqueId())) {
                                MessageUtil.sendPrefixed(sender, "chat.inv-self-not-allowed");
                                return;
                            }

                            if (plugin.getChatLogger() != null) {
                                plugin.getChatLogger().log("INVSEE", sender.getName(),
                                        null, "viewed inventory of " + target.getName());
                            }

                            openInventory(sender, target);
                        })
                )
                // /inv_show <token> — обычный игрок
                .withPermission("terracraftchat.inv")
                .withArguments(new StringArgument("token"))
                .executesPlayer((sender, args) -> {
                    String token = (String) args.get("token");

                    Player target = plugin.getInvTokenManager().resolve(token, sender);
                    if (target == null) {
                        MessageUtil.sendPrefixed(sender, "chat.inv-token-invalid");
                        return;
                    }

                    // Не инвалидируем — TTL 60 сек достаточно
                    openInventory(sender, target);
                })
                .register();
    }

    private void openInventory(Player viewer, Player target) {
        InvViewHolder holder = new InvViewHolder(target.getName());

        String titleRaw = plugin.getChatConfig().display().invGuiTitle()
                .replace("<player>", target.getName());

        Inventory view = Bukkit.createInventory(
                holder,
                45,
                MiniMessage.miniMessage().deserialize(titleRaw)
        );
        holder.setInventory(view);

        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            view.setItem(i, contents[i]);
        }

        view.setItem(36, target.getInventory().getHelmet());
        view.setItem(37, target.getInventory().getChestplate());
        view.setItem(38, target.getInventory().getLeggings());
        view.setItem(39, target.getInventory().getBoots());
        view.setItem(40, target.getInventory().getItemInOffHand());

        viewer.openInventory(view);
    }
}