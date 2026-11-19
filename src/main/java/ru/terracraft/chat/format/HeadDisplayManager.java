package ru.terracraft.chat.format;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import ru.terracraft.chat.TerraCraftChatPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeadDisplayManager {

    private final TerraCraftChatPlugin plugin;
    private int durationTicks;

    private final Map<UUID, TextDisplay> activeDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> removalTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> followTasks = new ConcurrentHashMap<>();

    public HeadDisplayManager(TerraCraftChatPlugin plugin, int durationSeconds) {
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
                // По умолчанию дисплей невидим никому: показываем только владельцу.
                // Это дешевле цикла hideEntity по всем игрокам и заодно решает
                // гонку «игрок зашёл, пока дисплей спавнился».
                entity.setVisibleByDefault(false);
                entity.setSeeThrough(false);
                entity.setShadowed(true);
                entity.setDefaultBackground(false);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                entity.setTeleportDuration(2);  // интерполяция 2 тика

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

        player.showEntity(plugin, display);
        activeDisplays.put(playerId, display);

        // Следование за игроком — каждые 2 тика
        BukkitTask follow = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            TextDisplay d = activeDisplays.get(playerId);
            if (d == null || !d.isValid()) return;
            if (!player.isOnline()) return;

            Location target = player.getLocation().add(0, 2.4, 0);
            if (d.getWorld().equals(target.getWorld())) {
                d.teleport(target);
            }
        }, 2L, 2L);
        followTasks.put(playerId, follow);

        // Удаление через durationTicks
        BukkitTask removal = plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> removeExisting(playerId), durationTicks);
        removalTasks.put(playerId, removal);
    }

    public void updateMessage(Player player, Component message) {
        UUID playerId = player.getUniqueId();
        TextDisplay existing = activeDisplays.get(playerId);

        if (existing != null && existing.isValid()) {
            existing.text(message);

            // Продлеваем удаление
            BukkitTask oldRemoval = removalTasks.remove(playerId);
            if (oldRemoval != null) oldRemoval.cancel();

            BukkitTask newRemoval = plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> removeExisting(playerId), durationTicks);
            removalTasks.put(playerId, newRemoval);
        } else {
            showMessage(player, message);
        }
    }

    /**
     * Дисплеи создаются с {@code setVisibleByDefault(false)} и показываются
     * только владельцу, поэтому скрывать их от входящего игрока не нужно.
     * Метод оставлен для обратной совместимости вызовов.
     */
    public void hideAllFrom(Player viewer) {
        // no-op: см. showMessage() — дисплеи невидимы по умолчанию
    }

    private void removeExisting(UUID playerId) {
        TextDisplay display = activeDisplays.remove(playerId);
        if (display != null && display.isValid()) {
            display.remove();
        }

        BukkitTask follow = followTasks.remove(playerId);
        if (follow != null && !follow.isCancelled()) {
            follow.cancel();
        }

        BukkitTask removal = removalTasks.remove(playerId);
        if (removal != null && !removal.isCancelled()) {
            removal.cancel();
        }
    }

    public void shutdown() {
        for (TextDisplay display : activeDisplays.values()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        activeDisplays.clear();

        for (BukkitTask task : followTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        followTasks.clear();

        for (BukkitTask task : removalTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        removalTasks.clear();
    }

    public void remove(Player player) {
        removeExisting(player.getUniqueId());
    }

    public void reload(int durationSeconds) {
        this.durationTicks = Math.max(1, durationSeconds) * 20;
    }

}