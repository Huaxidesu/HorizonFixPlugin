package dev.dsmnh.horizon.plugin.health;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import dev.dsmnh.horizon.plugin.HorizonFixPlugin;
import dev.dsmnh.horizon.plugin.PluginConfig;

/**
 * 修正「玩家存活但血量为 0」的状态。
 *
 * <p>只在伤害结算后与低频巡检时读取血量，不做 NBT 读写，单个玩家的开销与受伤次数成正比。</p>
 *
 * <p>1.7.10 中 {@code Player#isDead()} 反映的是实体是否被移除，死亡等待重生期间仍为
 * {@code false}，无法据此区分「已死亡」与「存活但血量为 0」。因此用死亡 / 重生事件维护
 * 一份待重生名单，名单内的玩家不做修正，避免把死亡中的玩家复活。</p>
 */
public final class ZeroHealthFix implements Listener {

    private static final int LOG_FIRST = 20;
    private static final int LOG_INTERVAL = 50;
    private static final long BROADCAST_DELAY_TICKS = 20L;

    private final HorizonFixPlugin plugin;
    private final Set<UUID> awaitingRespawn = ConcurrentHashMap.newKeySet();
    private final AtomicInteger fixes = new AtomicInteger();

    private volatile BukkitTask scanTask;

    public ZeroHealthFix(HorizonFixPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = plugin.config().zeroHealthScanIntervalTicks;
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                scanOnlinePlayers();
            }
        }, interval, interval);
    }

    public void stop() {
        BukkitTask task = scanTask;
        if (task != null) {
            task.cancel();
            scanTask = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        awaitingRespawn.add(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        awaitingRespawn.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        awaitingRespawn.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!plugin.config().zeroHealthEnabled) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        final Player player = (Player) event.getEntity();
        // 事件在伤害结算之前触发，下一 tick 读取才能拿到结算后的血量
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                repair(player);
            }
        });
    }

    private void scanOnlinePlayers() {
        if (!plugin.config().zeroHealthEnabled) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            repair(player);
        }
    }

    private void repair(Player player) {
        PluginConfig config = plugin.config();
        if (!config.zeroHealthEnabled) {
            return;
        }
        if (!player.isOnline() || player.getHealth() > 0) {
            return;
        }
        if (awaitingRespawn.contains(player.getUniqueId())) {
            return;
        }

        // 客户端在血量为 0 时会停在死亡界面，而服务端并不认为玩家已死亡。
        // 若先回血，客户端后续发出的重生请求会被服务端忽略（血量大于 0 直接返回），
        // 死亡界面永远不会关闭。因此必须先让服务端补发重生包，再把玩家送回原位。
        Location origin = player.getLocation();
        int foodLevel = player.getFoodLevel();
        float saturation = player.getSaturation();
        int expLevel = player.getLevel();
        float exp = player.getExp();

        player.spigot().respawn();

        player.teleport(origin);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setLevel(expLevel);
        player.setExp(exp);
        player.setHealth(config.zeroHealthMinimum);
        broadcastRepair(player, config);

        int total = fixes.incrementAndGet();
        if (total <= LOG_FIRST || total % LOG_INTERVAL == 0) {
            plugin.getLogger().info(String.format(
                    "玩家 %s 处于零血量状态，已恢复至 %.1f（第 %d 次）",
                    player.getName(), config.zeroHealthMinimum, total));
        }
    }

    /**
     * 修正本身对全服可见，便于玩家判断血量异常已被处理，而不是认为出现了外挂或显示错误。
     *
     * <p>零血量最常见于玩家登录时，此时客户端仍停留在加载界面，立即发送的消息会被丢弃，
     * 因此延迟 1 秒再广播。</p>
     */
    private void broadcastRepair(final Player player, PluginConfig config) {
        if (!config.zeroHealthBroadcast) {
            return;
        }
        final String template = config.zeroHealthBroadcastMessage;
        if (template == null || template.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        template.replace("%player%", player.getName())));
            }
        }, BROADCAST_DELAY_TICKS);
    }

    public int count() {
        return fixes.get();
    }
}
