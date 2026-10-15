# 0.17.0-beta.2

## 中文

修复开启免扫描区域轮廓时，Xaero 世界地图明显卡顿的问题。提供 Minecraft **26.2 / 26.3** 两个构建；共同功能一致，废弃营地仍仅限 26.3。

- 将逐像素行的小矩形绘制改为整帧一个 GUI 批次，减少原版相交排序、临时对象与顶点生成开销。4K 单圆探针中的 GUI 元素由 **16,360 降至 1**；十个可见区域也只提交一个批次。
- 圆弧自适应细分，弦误差控制在四分之一物理像素内；跳过不可见边缘和屏幕完全处于区域内部的情况。独立区域不再查询 Xaero 路径点世界。
- 保留四层向内渐弱的青色荧光、现有颜色和每层 0.5 GUI 像素宽度；保持平移、缩放、维度及关联区域过滤。
- 两个版本均通过构建、原有持久化/设置/Restore 回归，以及新增 **571 项**轮廓批次、精度、裁剪和 GUI 缩放检查。CPU 探针不代表实际游戏帧率，仍需游戏内确认。

更新时按游戏版本选择安装包，移除旧 TreasureHunter jar，勿同时安装两份。需要 Java 25、Fabric Loader ≥ 0.19.5 和对应的 Fabric API。配置及持久数据格式与 beta.1 一致。

## English

Fixes severe lag in Xaero's World Map when scan-exempt area outlines are enabled. Includes Minecraft **26.2 / 26.3** builds with matching features; Abandoned Camp support remains exclusive to 26.3.

- Replaced per-pixel scanline rectangles with one GUI batch per frame, reducing intersection sorting, temporary objects and vertex generation. The 4K single-circle probe drops from **16,360 GUI elements to 1**; ten visible areas also share one batch.
- Adaptively subdivides arcs with a quarter-physical-pixel chord error limit. Skips invisible edges and areas whose interior covers the entire viewport. Standalone areas no longer query Xaero waypoint worlds.
- Preserves the four inward cyan glow bands, existing colors and 0.5 GUI pixel width per band, alongside pan, zoom, dimension and linked-area filtering.
- Both builds passed compilation, existing persistence/settings/Restore regressions, and **571 additional outline checks** covering batching, precision, culling and GUI scaling. CPU probes do not measure actual in-game FPS; in-game confirmation is still needed.

Choose the jar matching your Minecraft version and remove the previous TreasureHunter jar; install only one build. Requires Java 25, Fabric Loader ≥ 0.19.5 and matching Fabric API. Configuration and persistent data formats are unchanged from beta.1.

# 0.17.0-beta.1

## 中文

此 beta 同时提供 Minecraft **26.2** 和 **26.3** 的 Fabric 客户端构建。功能保持一致；**废弃营地仅在 26.3 中可用**。请安装文件名与游戏版本对应的 jar，更新前移除旧版。

### 扫描与筛选

- 单人及本机 LAN 房主直接读取内置服务器的真实战利品表，只标记仍有战利品表的箱子、木桶和运输矿车箱；读取过程不展开战利品。确认无表的玩家储物箱及已展开战利品容器自动跳过。
- 已知原版表按结构分类，映射之外的表归为「非原版战利品表容器」，仅在单人配置中显示。服务器客户端继续使用周边特征推断，「其他箱」仅在服务器栏目中配置。
- 服务器疑似玩家容器过滤提供「不过滤 / 保守 / 激进」三档，默认保守；加入服务端确认的自己放置记录，以及漏斗、仓库和结构证据判断。改进掠夺者前哨站塔楼识别，减少误归为其他箱。
- 已确认的目标保持分类和路径点，不因普通方块变化或区块重发删除重建、重复提示；保留附近存在性及单人无表复核。

### 路径点与记忆

- 已开启宝箱/木桶/运输矿车及已解锁宝库记录按世界、玩家与维度持久保存，重启后仍生效；双联箱记录两半，矿车使用 UUID。
- 自动生成的临时路径点现在由独立文件保存，退出后重进会恢复，不重复发送发现提示。旧版本已经丢失且未保存的临时点需重新扫描一次。
- 修复 Xaero **Restore（恢复为永久点）** 后仍被清除的问题：永久点交回玩家管理，按 H、开箱及筛选清理都会保留。
- H 清除当前世界的受管理临时点及其缓存，并暂停扫描；已开启记忆和玩家保留的永久点不受影响。

### 免扫描区域与设置

- 新增「免扫描区域」，支持圆形/正方形、0–128 区块半径（0 关闭），覆盖全部高度，可独立创建或关联 Xaero 路径点。
- Xaero 新建/编辑路径点页在确认按钮上方提供 **TreasureHunter: 设为免扫描区域** 入口；最终确认才保存，取消不写入。启用关联区域会将该点保存为普通永久点。
- 世界地图显示向内渐弱的青色轮廓，可关闭显示。区域数据独立保存，不修改 Xaero 文件格式或世界数据，卸载本模组不影响普通路径点。
- 重构设置首页与「结构筛选 / 世界设置」，区分通用、仅单人和仅服务器客户端的选项；修复选项缺失、图标初始化及长文本显示问题，恢复红绿状态标记。
- 支持通过 Mod Menu 打开设置；补全中英文本，迁移旧配置。

### 升级与已知限制

- 需要 Java 25、Fabric Loader ≥ 0.19.5 和对应游戏版本的 Fabric API。Xaero 与 Mod Menu 为可选集成；`-sources.jar` 是源码包，不用于安装。
- 服务器扫描默认关闭，远程结构识别仍为启发式。激进过滤可能漏掉自然或模组容器。
- 同目录重建世界、同地址服务器换档或 Bingo 换回合不会自动获得新身份。需要时按 H 重新扫描，并在「世界设置」重置已开启/放置记录、检查免扫描区域。
- 数据分别保存在 `config/treasurehunter-{memory,placed,markers,ignore}/`；损坏或未知版本文件不会被自动覆盖。升级前建议备份配置和 Xaero 路径点。
- 发布验证：两个构建均通过从零构建、真实 Fabric/Knot 的 Mixin 与 Xaero 接口加载，以及持久化、完整设置页、退出重进和 Restore 行为探针（26.3：314 项；26.2：310 项）。这些探针不替代完整的游戏内视觉与联机实测。

## English

This beta provides Fabric client builds for Minecraft **26.2** and **26.3** with the same features. **Abandoned Camp support is exclusive to 26.3.** Choose the jar matching your game version and remove the previous jar before updating.

### Scanning and filters

- Singleplayer and local LAN hosts read actual loot tables from the integrated server. Only chests, barrels and chest minecarts with a remaining loot table are marked; reading does not generate loot. Confirmed table-less storage and containers whose loot has already generated are skipped.
- Known vanilla table IDs use structure categories. Unmapped tables use the singleplayer-only **Non-vanilla Loot Table Container** category. Remote clients keep using surroundings to infer structures, with **Other Chest** in the server-only section.
- Server player-container filtering offers **Off / Conservative / Aggressive**, defaulting to Conservative. It considers server-confirmed self-placement, hoppers, storage arrangements and structure evidence. Improved pillager outpost tower detection reduces incorrect Other Chest results.
- Confirmed targets keep their category and waypoint. Ordinary block changes and chunk resends no longer recreate them or repeat discovery messages. Nearby existence and singleplayer loot-table checks remain active.

### Waypoints and memory

- Opened chest/barrel/minecart and unlocked vault records persist across restarts, separated by world, player and dimension. Double chests record both halves; minecarts use UUIDs.
- Generated temporary waypoints now persist in a separate file and return after reconnecting without duplicate discovery messages. Temporary points already lost before this feature need one rescan.
- Fixed Xaero **Restore** points being removed during cleanup. Restored permanent points return to player management and survive H, container opening and filter cleanup.
- H clears managed temporary points and their cached records in the current world, then pauses scanning. Opened-container memory and player-retained permanent points remain.

### Scan-exempt areas and settings

- Added **Scan-exempt areas**: circles or squares with a 0–128 chunk radius (0 disables), covering every height. Create standalone areas or link them to Xaero waypoints.
- Xaero's new/edit waypoint form has a **TreasureHunter: Set as scan-exempt area** button above Confirm. Only final confirmation saves the draft; Cancel discards it. Enabling a linked area saves its anchor as an ordinary permanent point.
- World Map shows a cyan outline fading inward, with a display toggle. Area data lives separately without extending Xaero's file format or world data; uninstalling leaves normal waypoints intact.
- Reorganized the homepage, **Structure filters** and **World settings**, separating general, singleplayer-only and server-client-only options. Fixed missing options, icon initialization and clipped long labels, and restored red/green state indicators.
- Added Mod Menu settings access, completed Chinese/English text and migrated legacy settings.

### Updating and known limitations

- Requires Java 25, Fabric Loader ≥ 0.19.5 and Fabric API for the matching game version. Xaero and Mod Menu integrations are optional. `-sources.jar` is for developers, not installation.
- Server scanning is disabled by default. Remote structure classification remains heuristic; Aggressive filtering may hide natural or modded loot.
- Recreating a save at the same path, replacing a server world at the same address, or starting another Bingo round does not create a new persistence identity. Use H for a fresh scan, reset opened/placed records in **World settings**, and review scan-exempt areas when needed.
- Data is stored under `config/treasurehunter-{memory,placed,markers,ignore}/`. Damaged or unknown-version files are preserved. Back up configuration and Xaero waypoints before updating.
- Release validation: both builds passed clean builds, actual Fabric/Knot Mixin and Xaero interface loading, plus persistence, settings, reconnect and Restore probes (314 checks for 26.3; 310 for 26.2). These probes do not replace full in-game visual and multiplayer testing.
