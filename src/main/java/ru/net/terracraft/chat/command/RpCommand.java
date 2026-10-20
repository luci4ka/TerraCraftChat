package ru.net.terracraft.chat.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.net.terracraft.chat.TerraCraftChatPlugin;

import java.util.Random;

public class RpCommand {

    private final TerraCraftChatPlugin plugin;
    private final Random random = new Random();

    public RpCommand(TerraCraftChatPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        // /me <action>
        LiteralArgumentBuilder<CommandSourceStack> me = Commands.literal("me")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
                            String action = StringArgumentType.getString(ctx, "action");
                            broadcastRp(sender, plugin.getChatFormatter().formatMe(sender, action, sender));
                            return 1;
                        }));

        // /do <action>
        LiteralArgumentBuilder<CommandSourceStack> doCmd = Commands.literal("do")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
                            String action = StringArgumentType.getString(ctx, "action");
                            broadcastRp(sender, plugin.getChatFormatter().formatDo(sender, action, sender));
                            return 1;
                        }));

        // /try <action>
        LiteralArgumentBuilder<CommandSourceStack> tryCmd = Commands.literal("try")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
                            String action = StringArgumentType.getString(ctx, "action");
                            boolean success = random.nextBoolean();
                            broadcastRp(sender, plugin.getChatFormatter().formatTry(sender, action, success, sender));
                            return 1;
                        }));

        // /roll [max]
        LiteralArgumentBuilder<CommandSourceStack> roll = Commands.literal("roll")
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
                    int result = random.nextInt(6) + 1;
                    broadcastRp(sender, plugin.getChatFormatter().formatRoll(sender, result, 6, sender));
                    return 1;
                })
                .then(Commands.argument("max", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player sender)) return 0;
                            int max = IntegerArgumentType.getInteger(ctx, "max");
                            int result = random.nextInt(max) + 1;
                            broadcastRp(sender, plugin.getChatFormatter().formatRoll(sender, result, max, sender));
                            return 1;
                        }));

        // Регистрация через LifecycleEventManager
        plugin.getLifecycleManager().registerEventHandler(
                LifecycleEvents.COMMANDS,
                event -> {
                    event.registrar().register(me.build());
                    event.registrar().register(doCmd.build());
                    event.registrar().register(tryCmd.build());
                    event.registrar().register(roll.build());
                }
        );
    }

    /**
     * Рассылает RP-сообщение в радиусе из конфига.
     * Если radius <= 0 — всем на сервере.
     * Учитывает ignore-лист (viewer игнорирует sender — не видит).
     */
    private void broadcastRp(Player sender, Component message) {
        var ignoreManager = plugin.getIgnoreManager();
        int radius = plugin.getChatConfig().channels().rpRadius();

        if (radius <= 0) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (ignoreManager.isIgnoring(viewer, sender)) continue;
                viewer.sendMessage(message);
            }
            return;
        }

        int radiusSq = radius * radius;
        boolean senderIncluded = false;

        for (Player viewer : sender.getWorld().getPlayers()) {
            if (ignoreManager.isIgnoring(viewer, sender)) continue;
            if (viewer.getLocation().distanceSquared(sender.getLocation()) > radiusSq) continue;

            viewer.sendMessage(message);
            if (viewer.equals(sender)) senderIncluded = true;
        }

        // Отправитель всегда видит своё RP-сообщение, даже если вне радиуса
        if (!senderIncluded) {
            sender.sendMessage(message);
        }
    }
}