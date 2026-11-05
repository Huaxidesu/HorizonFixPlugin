# HorizonFixPlugin

GTNH 1.7.10 服务端的 Bukkit 侧修复合集，提供死亡自动重生与零血量状态修正两项功能。
两项功能各自独立开关，关闭后对应监听器不注册、定时任务不启动，运行期开销为零。

目标运行环境为 Thermos 及其分支 Crucible。Thermos 是 KCauldron 的延续，前身依次为
MCPC、MCPC+、Cauldron，在 Forge 之上实现了 Spigot/Bukkit API，因此本插件只使用 `org.bukkit` 公开接口，
不访问 NMS。

与模组侧的 [HorizonFix](https://github.com/Huaxidesu/HorizonFix) 分工：需要修改模组内部逻辑的修复放进该模组
（Forge 事件或反射访问模组字段），纯 Bukkit API 能解决的功能放在本插件。两者互不依赖，可单独安装。

## 1. 功能

| 模块 | 行为 |
|---|---|
| `respawn` | 玩家死亡后延迟若干 tick 自动重生，可排除指定世界 |
| `zero-health` | 玩家存活但血量为 0 时恢复到 `minimum-health`，使其脱离零血量状态 |

## 2. 实现说明

### 2.1 自动重生

`PlayerDeathEvent` 上以 `MONITOR` 优先级投递一个延迟任务，任务内检查玩家是否仍在线、血量是否仍为 0，
然后调用 `Player#spigot().respawn()`。血量大于 0 说明已被其他插件重生，此时直接返回，避免二次传送。

死亡界面无法在玩家死亡的同一 tick 内关闭，因此 `delay-ticks` 小于 1 时按 1 处理。

在 GTNH 2.9.0（含 ServerUtilities 2.4.22）实测：死亡当 tick 内 OpenBlocks 生成墓碑，重生任务在其之后执行，
两者不冲突；玩家死亡后按配置延迟重生，无需点击死亡界面。

### 2.2 零血量修正

零血量的表现是玩家仍然存活、血量却为 0：客户端显示空血条，服务端也不认为玩家已死亡。
修正只在伤害结算后与低频巡检时各读取一次血量，不做任何 NBT 读写，单个玩家的开销与受伤次数成正比，
与在线人数无关。

本插件改为三个触发点：

1. `EntityDamageEvent` 之后投递一个下一 tick 的任务，读取结算后的血量；
2. 每 `scan-interval-ticks` 巡检一次在线玩家，覆盖不触发伤害事件的改动路径；
3. 巡检只读取血量，不做任何序列化。

1.7.10 中 `Player#isDead()` 反映实体是否被移除，死亡等待重生期间仍为 `false`，无法区分「已死亡」与
「存活但血量为 0」。因此用 `PlayerDeathEvent`、`PlayerRespawnEvent`、`PlayerQuitEvent` 维护一份待重生名单，
名单内的玩家不做修正，避免把死亡中的玩家复活。

修正发生时默认向全服发送一条提示，内容与开关由 `zero-health.broadcast` 与
`zero-health.broadcast-message` 控制。提示的作用是让玩家知道血量异常已被处理，而不是认为出现了外挂或显示错误。

血量归零时客户端会停在死亡界面，而服务端并不认为玩家已死亡。若直接回血，客户端之后发出的重生请求会被
服务端忽略（`Player.Spigot#respawn()` 在血量大于 0 时直接返回），死亡界面永远不会关闭。因此修正顺序为：
先调用 `respawn()` 让服务端补发重生包，再把玩家送回原位置，最后写入 `minimum-health`。
重生会重置饥饿值与经验，修正前记录、修正后恢复。

该状态最常见于玩家带着 0 血量存档登录：客户端在登录瞬间收到血量为 0 的更新包，直接显示死亡界面。

### 2.3 目标平台与兼容性

Thermos 与 Crucible 属于 KCauldron 系，在 Forge 之上实现了 Spigot/Bukkit API，与 Spigot 同版本的
`org.bukkit` 方法签名一致。`Crucible.jar` 内可直接确认 `Player$Spigot#respawn()` 等本插件用到的方法存在。

两者的 NMS 与 Spigot 并不相同：Cauldron 系对 `net.minecraft` 包做过重映射与补丁，字段名和内部类结构
与 Spigot 有差异。这正是本插件不访问 NMS 的原因——任何依赖 NMS 字段名的实现在 Thermos 与 Crucible
之间迁移时都可能失效。

插件只用 `org.bukkit` 公开接口，因此同一份 jar 可在 Thermos 与 Crucible 上通用。

## 3. 配置

文件：`plugins/HorizonFixPlugin/config.yml`，首次启动自动生成。
该文件为 UTF-8；用不保留 UTF-8 的编辑器保存会使 `broadcast-message` 中的中文损坏。

### `respawn`

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 自动重生总开关 |
| `delay-ticks` | `5` | 重生延迟，单位为 tick，小于 1 时按 1 处理 |
| `blocked-worlds` | `[]` | 保留原版死亡界面的世界名，按 `world.getName()` 精确匹配，例如 `DIM-1` |

### `zero-health`

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 零血量修正总开关 |
| `minimum-health` | `1.0` | 修正后写入的血量，1.0 为半颗心；取值过大会变成治疗 |
| `scan-interval-ticks` | `20` | 在线玩家巡检周期，单位为 tick |
| `broadcast` | `true` | 修正时是否向全服发送提示 |
| `broadcast-message` | 见下 | 提示内容，支持 `&` 颜色码与 `%player%` 占位符；设为空字符串则不发送 |

默认提示：

```text
&e[提示] &f玩家 &b%player% &f的血量显示异常，已自动修正
```

### `debug`

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | `false` | 允许使用 `/hfp debug` 手动触发零血量状态 |

## 4. 命令与权限

| 命令 | 说明 | 权限 |
|---|---|---|
| `/hfp status` | 输出两项功能的开关状态与累计次数 | `horizonfixplugin.admin` |
| `/hfp reload` | 重新读取 `config.yml`，并按新周期重启巡检任务 | `horizonfixplugin.admin` |
| `/hfp debug <玩家>` | 把指定玩家的血量置为 0，复现零血量状态 | `horizonfixplugin.debug` |

| 权限 | 默认 | 说明 |
|---|---|---|
| `horizonfixplugin.respawn` | 所有玩家 | 允许自动重生，移除该权限的玩家保留原版死亡界面 |
| `horizonfixplugin.admin` | OP | 使用 `/hfp status` 与 `/hfp reload` |
| `horizonfixplugin.debug` | OP | 使用 `/hfp debug` |

### 4.1 调试命令

`/hfp debug <玩家>` 需要 `debug.enabled` 为 `true`，作用是把目标玩家的血量设为 0。

在 GTNH 这类环境中血量归零会立即触发死亡判定，玩家当场死亡，需要在死亡界面退出游戏：
退出时保存的数据血量为 0，重新登录即可复现「存活但血量为 0」的状态，由插件在登录时修正。
未触发死亡判定的服务端会直接进入待修正状态，不需要退出。

该命令可以让任意在线玩家掉到 0 血，生产环境应保持 `debug.enabled: false`，
需要验证时临时开启，验证完成后关闭。

## 5. 安装与回滚

1. 将 `target/HorizonFixPlugin-1.2.2.jar` 放入服务端 `plugins/`；
2. 若 `plugins/` 中已有功能重叠的插件，先移除，否则同一功能会有两套实现同时生效；
3. 重启服务端，启动日志出现 `[HorizonFixPlugin] 已启用` 即为加载成功。

回滚：删除 jar 并重启。插件不写玩家数据，`plugins/HorizonFixPlugin/config.yml` 可一并删除。

## 6. 构建

### 6.1 依赖来源

编译期接口有两个来源，由 Maven profile 按文件存在性自动切换，不需要手工指定：

1. **`libs/bukkit-api-1.7.10.jar`（首选）**：由 `build.bat` 从 GTNH 官方 Release 的
   `Thermos-1.7.10-1614.UNOFFICIAL-server.jar` 中提取 `org/bukkit` 包生成，接口面与实际运行的服务端一致。
   下载后校验 SHA-1（`b02e4c9d6cd2df87aac34f314c35fb1d5995e72c`），不匹配则拒绝使用。
2. **`org.spigotmc:spigot-api:1.7.10-R0.1-SNAPSHOT`（后备）**：本地文件不存在时使用。SpigotMC 官方仓库
   只提供 1.8 及以上，1.7.10 的构件只在社区镜像 `repo.glaremasters.me` 与 `maven.elmakers.com` 中，
   两者互为备份。

Thermos 仓库的 `libraries/` 目录不含 Bukkit API：其中只有构建依赖与原版混淆的 `server-1.7.10.jar`，
API 是 `bukkit` 子模块在构建时编进服务器 jar 的。因此接口只能从服务器 jar 中提取，没有独立的 API 构件。

### 6.2 执行

```
build.bat
```

首次运行下载约 13 MB 的服务端 jar 并提取接口，之后直接跳过，再执行 `mvn clean package`，
产物为 `target/HorizonFixPlugin-1.2.2.jar`。

只执行 `mvn clean package` 也可构建，此时走后备来源。

### 6.3 编译目标

`maven.compiler.release` 固定为 8，产物字节码版本 52，可在 Java 8 及以上运行。

## 7. 已知限制

- 修正依赖 `Player.Spigot#respawn()`。该方法只存在于 Spigot 及其分支（Thermos、Crucible），
  纯 CraftBukkit 服务端没有，因此本插件不适用于此类服务端。
- 修正是通过补发一次重生包实现的，客户端会重新加载所在区块，出现一次短暂的画面刷新。
  位置、饥饿值与经验在修正前后保存并恢复；重生流程重置的其他状态未逐一核对。
- 玩家在死亡界面退出后重进，修正会将其放在登录位置（通常是出生点），而不是死亡地点。
  死亡地点的物品由 OpenBlocks 墓碑保存，与原流程一致。
- 待重生名单依赖 `PlayerDeathEvent`。若某个模组绕过该事件直接终止玩家，该玩家会被误判为
  「存活但血量为 0」，在巡检时被恢复血量。
- `/hfp debug` 在 GTNH 中会让目标玩家真实死亡（触发掉落与墓碑），仅用于测试，
  生产环境应保持 `debug.enabled: false`。
- 修正只写入血量，不移除吸收效果，也不修改 `AbsorptionAmount`。
