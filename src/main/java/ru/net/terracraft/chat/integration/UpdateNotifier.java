package ru.net.terracraft.chat.integration;

import com.github.ezplugins.updater.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

public class UpdateNotifier {

    private final JavaPlugin plugin;

    private static final String GITHUB_OWNER = "luci4ka";           // твой ник на GitHub
    private static final String GITHUB_REPO = "TerraCraftChat";     // название репо

    public UpdateNotifier(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Асинхронная проверка обновлений.
     * Не блокирует главный поток.
     */
    public void checkAsync() {
        String currentVersion = plugin.getPluginMeta().getVersion();

        // owner, repo, currentVersion — ИМЕННО В ТАКОМ ПОРЯДКЕ
        UpdateChecker checker = UpdateChecker.builder(
                        GITHUB_OWNER,
                        GITHUB_REPO,
                        currentVersion
                )
                .build();

        checker.checkNowAsync().thenAccept(result -> {
            if (result.hasError()) {
                plugin.getLogger().warning("Не удалось проверить обновления: "
                        + result.getError().orElseThrow().getMessage());
                return;
            }

            if (result.isUpdateAvailable()) {
                plugin.getLogger().info("═══════════════════════════════════════");
                plugin.getLogger().info("  Доступно обновление TerraCraftChat!");
                plugin.getLogger().info("  Текущая версия: " + currentVersion);
                plugin.getLogger().info("  Новая версия:   " + result.getLatestTag().orElse("?"));
                plugin.getLogger().info("  Скачать:        " + result.getReleaseUrl().orElse("?"));
                plugin.getLogger().info("═══════════════════════════════════════");
            } else {
                plugin.getLogger().info("TerraCraftChat обновлён до последней версии.");
            }
        });
    }
}