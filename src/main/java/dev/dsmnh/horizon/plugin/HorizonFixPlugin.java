package dev.dsmnh.horizon.plugin;

import java.util.logging.Level;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import dev.dsmnh.horizon.plugin.command.AdminCommand;
import dev.dsmnh.horizon.plugin.health.ZeroHealthFix;
import dev.dsmnh.horizon.plugin.respawn.AutoRespawn;

/**
 * Bukkit 侧修复合集：死亡自动重生、零血量状态修正。
 *
 * <p>两项功能各自独立开关，关闭后对应监听器不注册、定时任务不启动，运行期开销为零。
 * 全部使用 Bukkit 公开 API，不依赖第三方 NBT 库，也不访问 NMS。</p>
 */
public final class HorizonFixPlugin extends JavaPlugin {

    private PluginConfig config;
    private AutoRespawn autoRespawn;
    private ZeroHealthFix zeroHealthFix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = PluginConfig.read(getConfig());

        autoRespawn = new AutoRespawn(this);
        getServer().getPluginManager().registerEvents(autoRespawn, this);

        zeroHealthFix = new ZeroHealthFix(this);
        getServer().getPluginManager().registerEvents(zeroHealthFix, this);
        zeroHealthFix.start();

        PluginCommand command = getCommand("hfp");
        if (command != null) {
            command.setExecutor(new AdminCommand(this));
        } else {
            getLogger().log(Level.WARNING, "plugin.yml 缺少 hfp 命令定义，管理命令不可用");
        }

        getLogger().log(Level.INFO, String.format(
                "已启用：自动重生 %s，零血量修正 %s",
                config.respawnEnabled ? "开" : "关",
                config.zeroHealthEnabled ? "开" : "关"));
    }

    @Override
    public void onDisable() {
        if (zeroHealthFix != null) {
            zeroHealthFix.stop();
        }
    }

    /** 重新读取 config.yml，并让定时任务的周期与新配置保持一致。 */
    public void reload() {
        reloadConfig();
        config = PluginConfig.read(getConfig());
        zeroHealthFix.restart();
    }

    public PluginConfig config() {
        return config;
    }

    public AutoRespawn autoRespawn() {
        return autoRespawn;
    }

    public ZeroHealthFix zeroHealthFix() {
        return zeroHealthFix;
    }
}
