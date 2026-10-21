package ru.net.terracraft.chat.integration;

import com.github.ezplugins.updater.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

public class UpdateNotifier {

    private final JavaPlugin plugin;

    private static final String GITHUB_OWNER = "luci4ka";
    private static final String GITHUB_REPO = "TerraCraftChat";

    public UpdateNotifier(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkAsync() {
        String currentVersion = plugin.getPluginMeta().getVersion();

        UpdateChecker checker = UpdateChecker.builder(
                        GITHUB_OWNER,
                        GITHUB_REPO,
                        currentVersion
                )
                .build();

        checker.checkNowAsync().thenAccept(result -> {
            if (result.hasError()) {
                String err = result.getError()
                        .map(Throwable::getMessage)
                        .orElse("unknown");
                plugin.getLogger().warning("Не удалось проверить обновления: " + err);
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