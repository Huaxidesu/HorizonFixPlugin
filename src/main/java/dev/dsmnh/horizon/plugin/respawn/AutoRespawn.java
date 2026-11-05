package dev.dsmnh.horizon.plugin.respawn;

import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import dev.dsmnh.horizon.plugin.HorizonFixPlugin;
import dev.dsmnh.horizon.plugin.PluginConfig;

/**
 * 死亡后自动重生。
 *
 * <p>只在死亡事件上挂一次监听、投递一个延迟任务，不读取也不写入玩家 NBT，
 * 因此单个玩家的开销与死亡次数成正比，与在线人数无关。</p>
 */
public final class AutoRespawn implements Listener {

    private final HorizonFixPlugin plugin;
    private final AtomicInteger respawns = new AtomicInteger();

    public AutoRespawn(HorizonFixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        final PluginConfig config = plugin.config();
        if (!config.respawnEnabled) {
            return;
        }

        final Player player = event.getEntity();
        if (!player.hasPermission("horizonfixplugin.respawn")) {
            return;
        }
        if (config.respawnBlockedWorlds.contains(player.getWorld().getName())) {
            return;
        }

        // 死亡界面无法在同一 tick 内关闭，最早只能下一个 tick 重生
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                // 血量大于 0 说明已被其他插件重生，重复调用会二次传送
                if (player.getHealth() > 0) {
                    return;
                }
                player.spigot().respawn();
                respawns.incrementAndGet();
            }
        }, config.respawnDelayTicks);
    }

    public int count() {
        return respawns.get();
    }
}
