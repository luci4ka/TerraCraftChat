package ru.terracraft.chat.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Мост между main-потоком сервера и блокирующим I/O (JDBC, HTTP, SkinsRestorer).
 *
 * <p>Java 25: все блокирующие задачи выполняются на <b>виртуальных потоках</b>
 * ({@link Executors#newThreadPerTaskExecutor}), поэтому ждать JDBC дешево —
 * поток-носитель не блокируется.
 *
 * <p>Одновременность ограничена семафором: пул соединений MineSQL не бесконечен,
 * и «лавина» из 200 параллельных запросов при массовом входе игроков
 * была бы хуже, чем аккуратная очередь.
 */
public final class Tasks {

    /** Максимум одновременных блокирующих операций. */
    private static final int MAX_CONCURRENT_IO = 32;

    private static final Semaphore PERMITS = new Semaphore(MAX_CONCURRENT_IO);

    private static final ExecutorService IO =
            Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("TerraCraftChat-IO-", 0).factory());

    private Tasks() {}

    /**
     * Выполняет блокирующую операцию на виртуальном потоке.
     * Исключения заворачиваются в лог-предупреждение и {@code null}-результат.
     */
    public static <T> CompletableFuture<T> supply(Plugin plugin, String what, Supplier<T> blocking) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                PERMITS.acquireUninterruptibly();
                try {
                    return blocking.get();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Async task '" + what + "' failed: " + t);
                    return null;
                } finally {
                    PERMITS.release();
                }
            }, IO);
        } catch (RejectedExecutionException ex) {
            // Пул уже закрыт (выключение плагина) — тихо пропускаем
            return CompletableFuture.completedFuture(null);
        }
    }

    /** Выполняет задачу на виртуальном потоке (результат не нужен). */
    public static void runAsync(Plugin plugin, String what, Runnable task) {
        supply(plugin, what, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Возвращает выполнение в main-поток сервера.
     * Если мы уже в main — выполняет сразу (без лишнего тика задержки).
     */
    public static void sync(Plugin plugin, Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
            return;
        }
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * Гасит пул виртуальных потоков (вызывается в onDisable).
     * Даёт задачам до 2 секунд, чтобы дописать данные.
     */
    public static void shutdown() {
        IO.shutdown();
        try {
            if (!IO.awaitTermination(2, TimeUnit.SECONDS)) {
                IO.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            IO.shutdownNow();
        }
    }
}
