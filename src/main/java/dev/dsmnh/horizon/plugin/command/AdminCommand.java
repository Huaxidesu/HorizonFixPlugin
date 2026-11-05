package dev.dsmnh.horizon.plugin.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import dev.dsmnh.horizon.plugin.HorizonFixPlugin;
import dev.dsmnh.horizon.plugin.PluginConfig;

/** 管理命令：{@code /hfp reload}、{@code /hfp status}、{@code /hfp debug}。 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "horizonfixplugin.admin";
    private static final String PERM_DEBUG = "horizonfixplugin.debug";

    private static final String DEBUG = "debug";
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList("reload", "status");

    private final HorizonFixPlugin plugin;

    public AdminCommand(HorizonFixPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("用法：/" + label + " <reload|status|debug>");
            return true;
        }

        String sub = args[0].toLowerCase();
        if (DEBUG.equals(sub)) {
            return triggerZeroHealth(sender, label, args);
        }

        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("[HorizonFixPlugin] 缺少权限 " + PERM_ADMIN);
            return true;
        }

        if ("reload".equals(sub)) {
            plugin.reload();
            sender.sendMessage("[HorizonFixPlugin] 配置已重新加载");
            return true;
        }

        if ("status".equals(sub)) {
            PluginConfig config = plugin.config();
            sender.sendMessage("[HorizonFixPlugin] 自动重生：" + (config.respawnEnabled ? "开" : "关")
                    + "，延迟 " + config.respawnDelayTicks + " tick，已重生 " + plugin.autoRespawn().count() + " 次");
            sender.sendMessage("[HorizonFixPlugin] 零血量修正：" + (config.zeroHealthEnabled ? "开" : "关")
                    + "，巡检周期 " + config.zeroHealthScanIntervalTicks + " tick，已修正 " + plugin.zeroHealthFix().count() + " 次");
            sender.sendMessage("[HorizonFixPlugin] 调试模式：" + (config.debugEnabled ? "开" : "关"));
            return true;
        }

        sender.sendMessage("未知子命令：" + args[0] + "，可用：reload、status、debug");
        return true;
    }

    /**
     * 把指定玩家的血量直接置为 0，用于制造零血量状态。
     *
     * <p>在 GTNH 这类环境中，血量归零会立即触发死亡判定，玩家当场死亡；此时需要在死亡界面
     * 退出游戏，退出时保存的数据血量即为 0，重新登录才能复现「存活但血量为 0」的状态。
     * 未触发死亡判定的服务端会直接进入待修正状态。</p>
     */
    private boolean triggerZeroHealth(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERM_DEBUG)) {
            sender.sendMessage("[HorizonFixPlugin] 缺少权限 " + PERM_DEBUG);
            return true;
        }
        if (!plugin.config().debugEnabled) {
            sender.sendMessage("[HorizonFixPlugin] 调试模式未开启，请先在 config.yml 设置 debug.enabled: true");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("用法：/" + label + " debug <玩家>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("[HorizonFixPlugin] 玩家不在线：" + args[1]);
            return true;
        }
        if (target.isDead() || target.getHealth() <= 0) {
            sender.sendMessage("[HorizonFixPlugin] " + target.getName() + " 当前不是存活状态，无法触发");
            return true;
        }

        target.setHealth(0.0D);
        sender.sendMessage("[HorizonFixPlugin] 已将 " + target.getName()
                + " 的血量置为 0；若该玩家随即死亡，请在死亡界面退出游戏后重进，即可复现登录时的零血量状态");
        plugin.getLogger().info(String.format("调试：%s 手动触发零血量状态", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<String>();
            if (sender.hasPermission(PERM_DEBUG)) {
                result.add(DEBUG);
            }
            if (sender.hasPermission(PERM_ADMIN)) {
                result.addAll(ADMIN_SUBCOMMANDS);
            }
            List<String> matched = new ArrayList<String>();
            for (String sub : result) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    matched.add(sub);
                }
            }
            return matched;
        }

        if (args.length == 2 && DEBUG.equalsIgnoreCase(args[0]) && sender.hasPermission(PERM_DEBUG)) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    names.add(player.getName());
                }
            }
            return names;
        }

        return Collections.emptyList();
    }
}
