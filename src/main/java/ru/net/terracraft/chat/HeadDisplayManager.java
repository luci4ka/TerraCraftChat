package ru.net.terracraft.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeadDisplayManager {

    private final JavaPlugin plugin;
    private final int durationTicks;

    private final Map<UUID, TextDisplay> activeDisplays = new HashMap<>();
    private final Map<UUID, BukkitTask> removalTasks = new HashMap<>();

    public HeadDisplayManager(JavaPlugin plugin, int durationSeconds) {
        this.plugin = plugin;
        this.durationTicks = Math.max(1, durationSeconds) * 20;
    }

    public void showMessage(Player player, Component message) {
        UUID playerId = player.getUniqueId();

        removeExisting(playerId);

        Location location = player.getLocation().add(0, 2.4, 0);

        TextDisplay display;
        try {
            display = player.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.text(message);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setSeeThrough(false);
                entity.setShadowed(true);
                entity.setDefaultBackground(false);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);

                Transformation transformation = new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(1f, 1f, 1f),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                entity.setTransformation(transformation);
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        // Скрываем дисплей от всех, кроме владельца
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hideEntity(plugin, display);
            }
        }

        activeDisplays.put(playerId, display);

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                removeExisting(playerId), durationTicks);
        removalTasks.put(playerId, task);
    }

    public void updateMessage(Player player, Component message) {
        UUID playerId = player.getUniqueId();
        TextDisplay existing = activeDisplays.get(playerId);

        if (existing != null && existing.isValid()) {
            existing.text(message);

            BukkitTask oldTask = removalTasks.remove(playerId);
            if (oldTask != null) oldTask.cancel();

            BukkitTask newTask = plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    removeExisting(playerId), durationTicks);
            removalTasks.put(playerId, newTask);
        } else {
            showMessage(player, message);
        }
    }

    private void removeExisting(UUID playerId) {
        TextDisplay display = activeDisplays.remove(playerId);
        if (display != null && display.isValid()) {
            display.remove();
        }

        BukkitTask task = removalTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public void shutdown() {
        for (TextDisplay display : activeDisplays.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        activeDisplays.clear();

        for (BukkitTask task : removalTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        removalTasks.clear();
    }
}