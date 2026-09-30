# Xaero TreasureHunter

An unofficial client-side addon for Xaero's Minimap / World Map that scans target blocks in nearby **loaded chunks** and displays them on the map as **temporary waypoints**.

## Target blocks (0.14.2)

- Spawners (monster rooms, nether fortresses, strongholds, abandoned mineshafts)
- Chests / trapped chests / barrels / minecarts with chests (monster rooms, villages, buried treasure, abandoned mineshafts, shipwrecks, …)
- Bells (villages)
- Vaults / ominous vaults (trial chambers)

## Usage

- Default keybinds:
  - `G` — toggle scanning
  - `H` — clear all markers (and attempt to remove their waypoints); also turns scanning off (press `G` to re-enable)
  - `J` — open scan settings (two-level option tree: the main screen has entries for "Scan targets…" and "Chest type filter…" plus toggles for Smart structure labels / Chat notifications / Singleplayer only / Merge nearby waypoints; both screens use icons and ✔/✘ state indicators; changes are saved instantly)
- **Smart structure labels** (can be disabled in settings):
  - Chests / barrels are classified by surrounding evidence blocks + biome + height and labeled e.g. `[Monster Room Chest]`, `[Village Chest]`, `[Bastion Remnant Chest]`, `[Trial Chambers Chest]`.
    Category names follow the official structure names on the Minecraft Wiki; the biome tables and height thresholds are read directly from the 26.2 structure data (structure JSON + `has_structure` tags expanded level by level) and calibrated against measured templates.
  - `[Buried Treasure Chest]`: chunk-local X/Z both = 9 + a single normal chest + covered (top mandatory, ≥ 3 sides) + beach biome — very high confidence.
  - `[Monster Room Chest]` requires "spawner + mossy cobblestone". `[Mineshaft Chest]` relies on **minecart entity scanning** (vanilla mineshaft loot sits entirely in minecarts, not in chest blocks, so no evidence blocks are needed; waypoints update automatically as the minecart moves or when it is broken).
  - `[Village Chest]`: hay bales / composters / bells, or bed + torch + dirt path (signs of village housing; desert villages have no dirt paths so torches serve as a fallback), plus a torches-only fallback (mine features and dark oak builds excluded) — requires a village biome (plains / meadow / desert / savanna / snowy plains / taiga) + y > 50.
  - `[Woodland Mansion Chest]`: dark oak planks + dark forest (including pale garden) biome + y > 50 (most mansion chests have no bookshelves nearby, so bookshelves are no longer required).
  - `[Pillager Outpost Chest]`: dark oak + cobblestone / mossy cobblestone / wool evidence + outpost biomes (all mountain variants, dark forest excluded) + y > 50 (distinguished from shipwrecks: shipwrecks have wood but no cobblestone).
  - `[Jungle Temple Chest]`: mossy cobblestone + (tripwire trap / chiseled stone bricks / dispenser) + jungle or bamboo jungle biome + y > 35.
  - `[Ocean Ruins Chest]`: underwater + stone brick family (cold ocean) or sandstone family (warm ocean) + ocean biome + below sea level (wiki re-checked: both cold and warm variants contain loot chests).
  - `[Desert Pyramid Chest]`: sandstone + terracotta / TNT + desert biome + not underwater + y > 35 (prevents misclassifying underwater ruins that contain sandstone).
  - `[Ruined Portal Chest]`: obsidian + crying obsidian (in the Overworld, netherrack is additionally accepted as corroborating evidence; in the Nether, obsidian alone decides — neither nether fortresses nor bastion remnants contain obsidian) — the structure generates in almost every biome, on the surface or buried, so no biome / height thresholds are applied.
  - `[Shipwreck Chest]`: any wooden component (logs / planks / stairs / slabs / fences / doors / trapdoors, any wood species) + shipwreck biomes (all oceans + beaches) + no torches / rails / cobwebs indicating player activity (village houses and player builds are filtered out); the underwater variant is submerged, the beached variant sits on the shore.
  - `[Nether Fortress Chest]` / `[Bastion Remnant Chest]`: Nether dimension + the respective biome tables; **if and only if** nether bricks lie directly below the chest = Nether Fortress (no surrounding-evidence fallback, so chests of an adjacent bastion remnant are not stolen); blackstone family (including gilded blackstone) = Bastion Remnant (surrounding evidence as fallback; bastion remnants additionally require anchor height 20–85).
  - `[End City Chest]`: End dimension + purpur / end stone bricks (no biome check). `[Ancient City Chest]`: sculk or deepslate tiles + y < 20 (deep dark biome no longer required). `[Trial Chambers Chest]`: tuff bricks / trial spawner + y < 20.
  - `[Igloo Chest]`: uses only basement features (brewing stand + oak sign) + y > 35 (no biome restriction; avoids misclassifying shipwrecks in frozen oceans as igloos). `[Stronghold Chest]`: stone brick family (also covers chests without nearby bookshelves outside the library; a code-generated ring-shaped structure, so no height threshold is possible).
  - Height heuristics at a glance: underground y < 20 (trial chambers / ancient cities); surface y > 50 (villages / woodland mansions / pillager outposts); semi-underground conservative lower bound y > 35 (desert pyramids / jungle temples / igloos) — used to exclude player-built surface/underground structures.
  - Waypoints are colored per category, and initials are localized: single Chinese characters (宝/试/矿/怪/堡/要/末/古/村/沙/林/要/哨/邸/船/雪/海/门) or English two-letter abbreviations (BT / TC / MS / MR / BA / NF / EC / AC / VL / DP / JT / SH / PO / WM / SW / IG / OR / RP / OT); spawners use S and bells use B.
  - Spawners show the mob type, e.g. `[Spider Spawner]`, `[Blaze Spawner]` (real synced client-side data, not a guess).
  - Double chests are registered as a single waypoint.
  - Chest minecarts: all vanilla mineshaft loot is inside minecarts with chests, so a chest minecart is directly labeled `[Mineshaft Chest]` (the waypoint is cleaned up automatically when the minecart is destroyed or leaves the loaded area).
  - Vaults / ominous vaults: points of interest in trial chambers (a single block in 26.x, distinguished by its ominous state) with independent toggles; once the player opens one with a key (unlocking animation / loot ejection phase), the marker and waypoint are removed automatically and it is not registered again on rescan.
- **Chest type filter** (Settings → "Chest type filter…"): toggle each category on/off; disabled categories are no longer registered (existing markers are removed), and re-enabling one lets the next rescan rediscover it.
- **Auto-remove after looting**: after opening and closing a chest/vault, its waypoint is removed automatically and the container is not re-added on rescan (the memory is cleared when leaving the world.)
- **Localization**: UI text, category names, waypoint initials, the key category and notification messages are bilingual (Chinese/English, switching automatically with the game language); notifications show the actual bound keys.
- **Merge nearby waypoints** (can be disabled in settings): no new marker when an identical marker (same type + same category) already exists within 5 blocks; when disabled, every match is marked.
- **Waypoint lifecycle**: self-created waypoints record the waypoint set of their dimension, so dimension changes / teleporting / clearing (`H`) all clean up reliably without leaving undeletable leftovers.
- **Scanning strategy**: only chunks that are "newly loaded / not yet scanned" are scanned (scanned on entering render distance, finished chunks are never rescanned); coverage spans the entire client-loaded area (render distance is the limit); a fallback pass every 2 seconds catches missed chunks; before classifying a container the scanner confirms the evidence area is fully loaded, deferring and retrying automatically otherwise.
- Clearing markers (`H`) resets the chunk scan record (so targets are rediscovered), while the "already looted" memory is kept.
- **Config**: `config/treasurehunter.json` (fallback sweep interval, target toggles, notifications, singleplayer restriction, etc.).
- Enabled only in **singleplayer** worlds by default (on multiplayer servers this kind of scanning is commonly treated as an x-ray/cheat — decide for yourself).

## Requirements

- Fabric Loader ≥ 0.19.5 (26.2)
- Fabric API
- Optional: Xaero's Minimap (recommended 26.5.1+ for Minecraft 26.2). Without Xaero, the mod silently degrades to scan logs / notifications only. This mod contains no Xaero code and only calls Xaero's public internal API via reflection (cross-version compatibility is not guaranteed; if the API changes, the integration disables itself automatically with a one-time warning).

## Warnings

- Speedrun leaderboards generally disallow third-party information mods like this; use it for practice / singleplayer fun.
- Multiplayer: effectively "legal x-ray"; many servers deploy countermeasures (AntiPieRay etc.) — use at your own risk.

---

# Xaero TreasureHunter（中文）

Xaero 小地图 / 世界地图的非官方附属 mod：客户端扫描附近**已加载区块**中的目标方块，并把它们以**临时路径点**的形式显示在地图上。

## 目标方块（0.14.2）

- 刷怪笼（刷怪房、下界要塞、要塞、废弃矿井）
- 箱子 / 陷阱箱 / 木桶 / 运输矿车箱（刷怪房、村庄、埋藏的宝藏、废弃矿井、沉船……）
- 钟（村庄）
- 宝库 / 不祥宝库（试炼密室）

## 使用

- 默认按键：
  - `G`：开关扫描
  - `H`：清空所有标记（并尝试移除路径点），同时关闭扫描（按 `G` 重新开启）
  - `J`：打开扫描设置（两级选项树：主界面 = 「扫描目标…」「箱子类型过滤…」子页入口 + 结构智能识别 / 发现提示 / 仅单人世界 / 邻近路径点合并 开关；子页与主界面均带图标与 ✔/✘ 标识；改动立即保存）
- 结构智能识别（可在设置里关）：
  - 箱子 / 木桶会按周围特征方块 + 群系 + 高度分类标注：`[刷怪房箱]`、`[村庄箱]`、`[堡垒遗迹箱]`、`[试炼密室箱]` 等。
    分类名与 Minecraft Wiki 的结构名对齐；群系表与高度门限直读 26.2 结构数据（结构 JSON + `has_structure` 标签逐级展开）并按模板实测校准
  - `[埋藏的宝藏箱]`：区块局部 X/Z 均为 9 + 单个普通箱子 + 被遮盖（上必须、侧 ≥3 面）+ 海滩群系，置信度很高
  - `[刷怪房箱]`（俗称地牢箱）要求「刷怪笼 + 苔石」；`[废弃矿井箱]` 为**运输矿车实体扫描**（原版矿井战利品全部在矿车里，没有普通箱方块，不需要证据方块；矿车移动 / 被破坏时路径点自动更新）
  - `[村庄箱]`：干草捆 / 堆肥桶 / 钟，或床+火把+土径（村庄房屋的生活痕迹；沙漠村庄没有土径，走火把兜底），或火把兜底（需排除矿井特征与深色橡木建筑）——要求村庄群系（平原/草甸/沙漠/热带草原/积雪平原/针叶林）+ y > 50
  - `[林地府邸箱]`：深色橡木木板 + 深色森林（含浅色花园）群系 + y > 50（府邸箱子多数附近没有书架，书架不再是必要条件）
  - `[掠夺者前哨站箱]`：深色橡木 + 圆石 / 苔石 / 羊毛证据 + 前哨站群系（含各山地变体，不含深色森林）+ y > 50（与沉船区分：沉船只有木头没有圆石）
  - `[丛林神庙箱]`：苔石 +（绊线陷阱 / 雕纹石砖 / 发射器）+ 丛林或竹林群系 + y > 35
  - `[海底废墟箱]`：水下 + 石砖族（冷海）或砂岩族（暖海）+ 海洋群系 + 海平面以下（wiki 复核：冷/暖变体都有战利品箱）
  - `[沙漠神殿箱]`：砂岩 + 陶瓦/TNT + 沙漠群系 + 非水下 + y > 35（避免误判含砂岩的水下废墟）
  - `[废弃传送门箱]`：黑曜石 + 哭泣的黑曜石（主世界可加 下界岩 佐证；下界直接以黑曜石判定——下界要塞/堡垒遗迹都不含黑曜石）——变体覆盖几乎所有群系、地表与掩埋都有，故不加群系/高度门限
  - `[沉船箱]`：任意木制部件（原木 / 木板 / 楼梯 / 台阶 / 栅栏 / 门 / 活板门，任意木种）+ 沉船群系（全部海洋 + 海滩）+ 无火把/铁轨/蛛网等人为痕迹（村庄房屋、玩家建筑会被排除）；水下变体泡在水里，搁浅变体在海滩
  - `[下界要塞箱]` / `[堡垒遗迹箱]`：下界维度 + 各自群系表；**当且仅当**下界砖在箱子正下方 = 下界要塞（不再用周边证据兜底，避免抢走紧邻的堡垒遗迹箱）；黑石族（含镶金黑石）= 堡垒遗迹（周边证据兜底；堡垒遗迹另加锚点高度 20~85）
  - `[末地城箱]`：末地维度 + 紫珀/末地石砖（不再检测群系）；`[远古城市箱]`：幽匿 或 深层板岩砖 + y < 20（不再强制深暗之域群系）；`[试炼密室箱]`：凝灰岩砖/试炼刷怪笼 + y < 20
  - `[雪屋箱]`：只用地下室特征（酿造台 + 橡木告示牌）+ y > 35（不限制群系；避免结冰海洋的沉船被误判为雪屋）；`[要塞箱]`：石砖族（图书馆之外、附近没有书架的箱子也覆盖；代码生成的环状结构，无法加门限）
  - 高度辅助汇总：地下 y < 20（试炼密室 / 远古城市）；地表 y > 50（村庄 / 林地府邸 / 掠夺者前哨站）；半地下保守下限 y > 35（沙漠神殿 / 丛林神庙 / 雪屋）——用于排除玩家自建的地表/地下结构
  - 路径点按分类着色，简称随语言本地化：中文单字（宝/试/矿/怪/堡/要/末/古/村/沙/林/要/哨/邸/船/雪/海/门），英文双字母缩写（BT/TC/MS/MR/BA/NF/EC/AC/VL/DP/JT/SH/PO/WM/SW/IG/OR/RP/OT），刷怪笼/钟为 S/B
  - 刷怪笼显示生物种类，如 `[蜘蛛笼]`、`[烈焰人笼]`（客户端同步的真实数据，非推断）
  - 双联箱只登记一个路径点
  - 运输矿车箱：原版废弃矿井的战利品全部在运输矿车里，矿车箱会被直接标记为 `[废弃矿井箱]`（矿车被破坏或移出加载区时路径点自动清理）
  - 宝库 / 不祥宝库：试炼密室的兴趣方块（26.x 为同一方块、由不祥属性区分），独立开关；玩家用钥匙开启后（开启动画 / 弹出战利品阶段）自动移除标记与路径点，重扫不再登记
- 箱子类型过滤（设置菜单 → 「箱子类型过滤…」）：逐类开关是否扫描；关闭的类别不再登记（已标记的会移除），重新打开后下一次重扫会重新发现
- 开箱后自动删除：打开并关闭某个箱子/使用钥匙打开宝库后，对应路径点自动移除，且该容器不再被重扫加回（记忆在离开世界时清空）
- 语言支持：界面文字、分类名、路径点简称、键位分类名与提示消息均为中英双语（随游戏语言自动切换）；提示中的按键名显示实际键位
- 邻近路径点合并（可在设置里关）：半径 5 格内已有相同标记（同类型 + 同分类）时不再重复标记；关闭后每个匹配都会标记
- 路径点生命周期：自建路径点会记录所属维度的路径点集，维度切换 / 传送 / 清空（H）都能可靠清理，不会留下无法删除的残留
- 扫描策略：只扫“新加载 / 尚未扫过”的区块（进入视距即扫、不重复扫已完成的区块），范围覆盖整个客户端已加载区域（视距即上限）；每 2 秒兜底检查一次漏网区块；容器分类前会确认证据区域已加载完整，未完成时自动推迟重试
- 清空标记（`H`）会重置区块扫描记录（区域内目标会重新发现），但“已搜刮”记忆保留
- 配置：`config/treasurehunter.json`（兜底扫描间隔、目标开关、通知、单人限定等）
- 默认仅在**单人世界**启用（多人服务器上这类扫描通常被视为透视/作弊，请自行判断）。

## 依赖

- Fabric Loader ≥ 0.19.5（26.2）
- Fabric API
- 可选：Xaero's Minimap（推荐 26.5.1+，26.2 版）。没有 Xaero 时 mod 静默降级，只保留扫描日志 / 通知。本模组不包含任何 Xaero 代码，仅通过反射调用其公开内部 API（不保证跨版本兼容，API 变化时自动禁用集成并给出一次警告）。

## 提醒

- 速通正式成绩提交基本不允许这类第三方信息 mod；请用于练习 / 单机娱乐。
- 多人服务器：相当于“合法透视”，很多服有反制插件（AntiPieRay 等），使用风险自负。
