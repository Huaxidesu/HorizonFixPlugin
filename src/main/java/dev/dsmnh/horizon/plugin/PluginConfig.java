package dev.dsmnh.horizon.plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

/** config.yml 的只读快照。重新加载时整体替换，运行期不再访问 FileConfiguration。 */
public final class PluginConfig {

    public final boolean respawnEnabled;
    public final long respawnDelayTicks;
    public final Set<String> respawnBlockedWorlds;

    public final boolean zeroHealthEnabled;
    public final double zeroHealthMinimum;
    public final long zeroHealthScanIntervalTicks;
    public final boolean zeroHealthBroadcast;
    public final String zeroHealthBroadcastMessage;

    public final boolean debugEnabled;

    private PluginConfig(boolean respawnEnabled, long respawnDelayTicks, Set<String> respawnBlockedWorlds,
                         boolean zeroHealthEnabled, double zeroHealthMinimum, long zeroHealthScanIntervalTicks,
                         boolean zeroHealthBroadcast, String zeroHealthBroadcastMessage, boolean debugEnabled) {
        this.respawnEnabled = respawnEnabled;
        this.respawnDelayTicks = respawnDelayTicks;
        this.respawnBlockedWorlds = respawnBlockedWorlds;
        this.zeroHealthEnabled = zeroHealthEnabled;
        this.zeroHealthMinimum = zeroHealthMinimum;
        this.zeroHealthScanIntervalTicks = zeroHealthScanIntervalTicks;
        this.zeroHealthBroadcast = zeroHealthBroadcast;
        this.zeroHealthBroadcastMessage = zeroHealthBroadcastMessage;
        this.debugEnabled = debugEnabled;
    }

    public static PluginConfig read(FileConfiguration file) {
        List<String> worlds = file.getStringList("respawn.blocked-worlds");
        Set<String> blocked = worlds == null
                ? Collections.<String>emptySet()
                : new HashSet<String>(worlds);

        return new PluginConfig(
                file.getBoolean("respawn.enabled", true),
                // 死亡界面最早在下一 tick 关闭，0 或负数没有意义
                Math.max(1L, file.getLong("respawn.delay-ticks", 5L)),
                blocked,
                file.getBoolean("zero-health.enabled", true),
                Math.max(1.0D, file.getDouble("zero-health.minimum-health", 1.0D)),
                Math.max(1L, file.getLong("zero-health.scan-interval-ticks", 20L)),
                file.getBoolean("zero-health.broadcast", true),
                file.getString("zero-health.broadcast-message",
                        "&e[提示] &f玩家 &b%player% &f的血量显示异常，已自动修正"),
                file.getBoolean("debug.enabled", false));
    }
}
