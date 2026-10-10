package ru.net.terracraft.chat.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.net.terracraft.chat.InvViewHolder;
import ru.net.terracraft.chat.MessageUtil;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

public class InvCommand {

    private final TerraCraftChatPlugin plugin;

    public InvCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new CommandAPICommand("inv_show")
                .withArguments(new EntitySelectorArgument.OnePlayer("target"))
                .executesPlayer((sender, args) -> {
                    Player target = (Player) args.get("target");
                    if (target == null || !target.isOnline()) return;

                    // ЗАПРЕТ: нельзя смотреть свой инвентарь
                    if (sender.getUniqueId().equals(target.getUniqueId())) {
                        MessageUtil.sendPrefixed(sender, "chat.inv-self-not-allowed");
                        return;
                    }

                    // Создаём holder
                    InvViewHolder holder = new InvViewHolder(target.getName());

                    String titleRaw = plugin.getChatConfig().display().invGuiTitle()
                            .replace("<player>", target.getName());

                    Inventory view = Bukkit.createInventory(
                            holder,
                            45,
                            MiniMessage.miniMessage().deserialize(titleRaw)
                    );
                    holder.setInventory(view);

                    // Копируем содержимое инвентаря (слоты 0-35)
                    ItemStack[] contents = target.getInventory().getContents();
                    for (int i = 0; i < Math.min(contents.length, 36); i++) {
                        view.setItem(i, contents[i]);
                    }

                    // Броня (36-39) и оффхенд (40)
                    view.setItem(36, target.getInventory().getHelmet());
                    view.setItem(37, target.getInventory().getChestplate());
                    view.setItem(38, target.getInventory().getLeggings());
                    view.setItem(39, target.getInventory().getBoots());
                    view.setItem(40, target.getInventory().getItemInOffHand());

                    sender.openInventory(view);
                })
                .register();
    }
}