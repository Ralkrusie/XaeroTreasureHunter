# Xaero TreasureHunter

## Beta builds / 测试版

The 0.17.0-beta.1 builds share the same features. Choose the jar for your Minecraft version; only Minecraft 26.3 includes Abandoned Camp support. This branch targets **Minecraft 26.3**. See [bilingual release notes](CHANGELOG.md).

0.17.0-beta.1 两个构建功能同步，请按 Minecraft 版本选择安装包；仅 26.3 包含废弃营地识别。当前分支面向 **Minecraft 26.3**。详见[双语改动说明](CHANGELOG.md)。

| Minecraft | Branch / 分支 | Installation jar / 安装包 |
| --- | --- | --- |
| 26.2 | [`main`](https://github.com/Ralkrusie/XaeroTreasureHunter/tree/main) | `xaero-treasurehunter-0.17.0-beta.1+mc26.2.jar` |
| 26.3 | [`mc-26.3`](https://github.com/Ralkrusie/XaeroTreasureHunter/tree/mc-26.3) | `xaero-treasurehunter-0.17.0-beta.1+mc26.3.jar` |

Install one matching jar in the client `mods` folder with Fabric API and Java 25; remove the previous TreasureHunter jar first. `-sources.jar` is for developers. Beta builds are for testing.

将一份对应版本的 jar 放入客户端 `mods`，需 Fabric API 和 Java 25；更新前移除旧 TreasureHunter jar。`-sources.jar` 是开发用源码包。此版本为测试版。

An unofficial client-side addon for Xaero's Minimap / World Map that scans target blocks in nearby **loaded chunks** and displays them on the map as **temporary waypoints**.

## Target blocks (0.17.0-beta.1+mc26.3)

- Spawners (monster rooms, nether fortresses, strongholds, abandoned mineshafts)
- Chests / trapped chests / copper chests / barrels / minecarts with chests (monster rooms, villages, buried treasure, abandoned mineshafts, shipwrecks, …)
- Bells (villages)
- Vaults / ominous vaults (trial chambers)

## Usage

- Default keybinds:
  - `G` — toggle scanning
  - `H` — clear scan markers and their temporary waypoints, preserving points made permanent in Xaero; also turns scanning off (press `G` to re-enable)
  - `J` — open server scanning permission, target scans, intelligent structure identification, structure filters, discovery messages, merging and World settings in that order. World settings contain scan-exempt areas and both record resets. Mod Menu opens the same page and returns to the original parent.
- **Intelligent structure identification** (settings homepage; controls marker names):
  - In singleplayer (including a local LAN host), **only chests, barrels and chest minecarts with an actual loot table are marked**. The integrated server reads loaded chunks/entities without generating loot. Confirmed table-less containers, including player storage and generated loot, are skipped; unavailable data is deferred and retried. Filtering stays active when smart labels are off. Either half of a double chest may provide its table.
    Known table IDs use existing categories. All 58 vanilla container table IDs are recognized: the bonus chest keeps the ordinary Chest label, and the jungle-temple dispenser table belongs to Jungle Temple. Tables outside that mapping use **Non-vanilla Loot Table Container** (`OL`), shown only in the Singleplayer-only filter section. Assigned vanilla non-container tables may also fall outside the mapping; this label cannot prove mod origin. Overridden vanilla IDs still use their ID category. Known-table barrels keep the Barrel label; unmapped-table barrels use the extra category. Filters work independently of name display.
    The following block-based rules describe multiplayer heuristics; they remain guesses even with strong evidence.
    Category names follow the official structure names on the Minecraft Wiki; the biome tables and height thresholds are read directly from the 26.3 structure data (structure JSON + `has_structure` tags expanded level by level) and calibrated against measured templates.
  - `[Buried Treasure Chest]`: chunk-local X/Z both = 9 + a single normal chest + covered (top mandatory, ≥ 3 sides) + beach biome — very high confidence.
  - `[Monster Room Chest]` requires "spawner + mossy cobblestone". `[Mineshaft Chest]` relies on **minecart entity scanning**. Servers corroborate mineshafts with rails + wood + either low height or cobwebs; arbitrary player minecarts are not automatically labeled as mineshaft loot. Waypoints stay at the first discovered position.
  - `[Village Chest]`: hay bales / composters / bells, or bed + torch + dirt path (signs of village housing; desert villages have no dirt paths so torches serve as a fallback), plus a torches-only fallback (mine features and dark oak builds excluded) — requires a village biome (plains / meadow / desert / savanna / snowy plains / taiga) + y > 50.
  - `[Woodland Mansion Chest]`: dark oak planks + dark forest (including pale garden) biome + y > 50 (most mansion chests have no bookshelves nearby, so bookshelves are no longer required).
  - `[Pillager Outpost Chest]`: at least two white wall banners + dark oak + cobblestone family provide a strong tower signature without a biome/height gate. Both tower templates have four banners in the scan window; white wall banners occur only in outposts across all 1511 templates and every palette. The older dark oak + stone/wool + outpost biome + y > 50 rule remains a fallback.
  - `[Jungle Temple Chest]`: mossy cobblestone + (tripwire trap / chiseled stone bricks / dispenser) + jungle or bamboo jungle biome + y > 35.
  - `[Ocean Ruins Chest]`: underwater + stone brick family (cold ocean) or sandstone family (warm ocean) + ocean biome + below sea level (wiki re-checked: both cold and warm variants contain loot chests).
  - `[Desert Pyramid Chest]`: sandstone + terracotta / TNT + desert biome + not underwater + y > 35 (prevents misclassifying underwater ruins that contain sandstone).
  - `[Ruined Portal Chest]`: obsidian + crying obsidian (in the Overworld, netherrack is additionally accepted as corroborating evidence; in the Nether, obsidian alone decides — neither nether fortresses nor bastion remnants contain obsidian) — the structure generates in almost every biome, on the surface or buried, so no biome / height thresholds are applied.
  - `[Abandoned Camp Chest]`: any colour of wool stairs — a 26.3-exclusive block that appears in no other structure (verified across 1511 structure templates), so it decides on its own; the copper chest (also camp-exclusive) is recognised directly from the container block. Campfires are deliberately **not** used as evidence: they also generate in ancient cities, trail ruins and villages, so they are not exclusive.
  - `[Shipwreck Chest]`: any wooden component (logs / planks / stairs / slabs / fences / doors / trapdoors, any wood species) + shipwreck biomes (all oceans + beaches) + no torches / rails / cobwebs indicating player activity (village houses and player builds are filtered out); the underwater variant is submerged, the beached variant sits on the shore.
  - `[Nether Fortress Chest]` / `[Bastion Remnant Chest]`: Nether dimension + the respective biome tables; **if and only if** nether bricks lie directly below the chest = Nether Fortress (no surrounding-evidence fallback, so chests of an adjacent bastion remnant are not stolen); blackstone family (including gilded blackstone) = Bastion Remnant (surrounding evidence as fallback; bastion remnants additionally require anchor height 20–85).
  - `[End City Chest]`: End dimension + purpur / end stone bricks (no biome check). `[Ancient City Chest]`: sculk or deepslate tiles + y < 20 (deep dark biome no longer required). `[Trial Chambers Chest]`: tuff bricks / trial spawner + y < 20.
  - `[Igloo Chest]`: uses only basement features (brewing stand + oak sign) + y > 35 (no biome restriction; avoids misclassifying shipwrecks in frozen oceans as igloos). `[Stronghold Chest]`: stone brick family (also covers chests without nearby bookshelves outside the library; a code-generated ring-shaped structure, so no height threshold is possible).
  - Height heuristics at a glance: underground y < 20 (trial chambers / ancient cities); surface y > 50 (villages / woodland mansions / pillager outposts); semi-underground conservative lower bound y > 35 (desert pyramids / jungle temples / igloos) — used to exclude player-built surface/underground structures.
  - Waypoints are colored per category, and initials are localized: Chinese single characters (宝/试/矿/怪/堡/要/末/古/村/沙/林/要/哨/邸/船/雪/海/门/营/异), English abbreviations (BT / TC / MS / MR / BA / NF / EC / AC / VL / DP / JT / SH / PO / WM / SW / IG / OR / RP / CP / OL / OT). Spawners use S and bells use B.
  - Spawners show the mob type, e.g. `[Spider Spawner]`, `[Blaze Spawner]` (real synced client-side data, not a guess).
  - Double chests are registered as a single waypoint.
  - Chest minecarts: singleplayer requires actual loot tables; multiplayer uses surroundings for classification and filtering. Validation near recorded waypoints removes markers whose entities have disappeared.
  - Vaults / ominous vaults: points of interest in trial chambers (a single block in 26.x, distinguished by its ominous state) with independent toggles; once the player opens one with a key (unlocking animation / loot ejection phase), the marker and waypoint are removed automatically and it is not registered again on rescan.
- **Structure filters**: common structure categories followed by **Singleplayer only** (Non-vanilla Loot Table Container) and **Server clients only** (Other Chest and Possibly player-placed containers). Category buttons use the same cell width as common filters; the longer player-container filter spans the remaining columns (or a full row on narrow screens). Buttons have green/red state indicators; the panel scrolls. Enable all / Disable all affect categories in every section. Disabling removes temporary markers, re-enabling requests a rescan. Name display remains independent.
- **Possibly player-placed containers** (Structure filters → Server clients only): **Off / Conservative / Aggressive**, default Conservative. Off only disables player-container filtering. Conservative hides confirmed self-placed containers and obvious hopper/storage setups while preserving structure exemptions (except Village/Stronghold). Aggressive additionally hides unknown/weak sources and removes hopper/storage exemptions; copper chest material alone is insufficient. It can miss natural/modded loot. Storage counts individual container blocks, including both double-chest halves: at least 8 containers and 4 vertical pairs in horizontal ±4 / vertical ±3.
- Filtering runs at discovery and relevant hopper/container updates, coalesced over 6 ticks for previously hidden or undiscovered targets. Confirmed markers keep the same category and waypoint object; ordinary block changes and chunk resends do not delete/recreate them. Confirmed self-placement records persist in `config/treasurehunter-placed/` and can be reset in World settings.
- **Opened-container memory**: the player's clicked chest/barrel/minecart is recorded when its actual container menu opens, and its waypoint is removed when that menu closes. Merely clicking a blocked container does not record it. Both halves of a double chest are remembered; minecarts use stable UUIDs. Vaults are remembered after a key interaction is confirmed by their unlocking/ejection state. Opening is remembered even if no items are taken, and memory tracking continues while scanning is off or restricted to singleplayer.
- **Persistent memory**: records are saved immediately under `config/treasurehunter-memory/`, separately for each local save directory or server address + player UUID, and for each dimension. They survive disconnects and game restarts. World settings → "Reset opened chest/vault records in this world" clears all dimensions of the current world and allows eligible targets to be rediscovered; other worlds are unaffected. A server reset at the same address, or replacing/refilling a container at the same coordinates, requires resetting these records when you want to scan it again. Visits made before this feature existed cannot be reconstructed.
- **Map reloads / round-based worlds**: ClientLevel changes only switch scan/loot caches and clear pending interactions. Confirmed markers survive chunk reloads, dimension changes and reconnects. Persistence uses save path or server address + player UUID, plus dimension—not a round ID or seed. Rebuilding at the same identity (including Bingo) reuses scan/opened/placed records and independent scan-exempt areas. Use H for a fresh scan, reset opened/placed records in World settings and review scan-exempt areas when needed.
- **Localization**: UI text, category names, waypoint initials, the key category and notification messages are bilingual (Chinese/English, switching automatically with the game language); notifications show the actual bound keys.
- **Merge nearby waypoints** (can be disabled in settings): no new marker when an identical marker (same type + same category) already exists within 5 blocks; when disabled, every match is marked.
- **Waypoint lifecycle**: self-created waypoints record their original waypoint set. Cleanup only deletes points that are still temporary. Using Xaero's **Restore** (or changing a point to permanent) transfers it to player management: clearing (`H`), opening containers, changing filters, and dimension changes all preserve it. Delete saved points through Xaero when desired.
- **Temporary marker persistence**: `config/treasurehunter-markers/<world hash>.json` stores confirmed positions, labels, categories, minecart UUIDs and original Xaero world/set paths. Xaero itself skips temporary points when saving. Disconnects save this sidecar and release references without deleting points; reconnects restore temporary points without rescanning or duplicate discovery messages. H clears both managed temporary points and their cached records. Restore hands a point to Xaero’s permanent storage; later cleanup never takes it back. Corrupt/future files are preserved. Temporary points already lost before this feature require one rescan.
- **Scanning strategy**: new or incomplete chunks, with a fallback every 2 seconds. Confirmed targets are not reclassified or repeatedly announced; nearby existence/opened-loot checks remain. Relevant container updates may discover hidden targets without replacing confirmed points. Scan-exempt areas cover every target and height.
- Clearing markers (`H`) resets the chunk scan record, while persistent opened-container memory is kept. Re-enabling a target/category requests a rescan; changing detailed names or waypoint merging refreshes existing markers.
- **Config**: `config/treasurehunter.json`. Singleplayer is always allowed; server scanning is an opt-in setting on the homepage. Old `singleplayerOnly` and filter flags migrate automatically. The previous `singleplayerScanEnabled` field is ignored and removed when saving.
- Enabled only in **singleplayer** worlds by default (on multiplayer servers this kind of scanning is commonly treated as an x-ray/cheat — decide for yourself).

## Requirements

- Fabric Loader ≥ 0.19.5 (26.3)
- Fabric API
- Optional: Xaero's Minimap / World Map and Mod Menu 21.0.0. Checked Minimap 26.5.3 + World Map 1.46.4 and Minimap 26.6.0 + World Map 1.47.0. No Xaero or Mod Menu code is bundled; Mod Menu is compile-only. Missing Xaero leaves normal scanning available, and a changed editor/outline API only disables that extension.
- **Scan-exempt areas**: World settings support independent areas and pagination. The **TreasureHunter: Set as scan-exempt area** button above Confirm/Cancel in Xaero’s new/edit waypoint form opens radius/shape controls. Circle or Square, 0–128 chunks (0 disables; 1 chunk = 16 blocks), centered on the center block and covering every height. Data is isolated by world and dimension. Only final Xaero Confirm saves the draft; Cancel discards it. Enabling a linked area saves temporary/destination points as normal permanent points. Explicit range changes in batch editing apply to all selected points.
- Linked areas follow confirmed name, position, destination set and dimension changes. Deleted/temporary/missing anchors stop applying at the next refresh (normally within 1 second); sidecar records remain for removal or rebinding. Identical waypoint signatures share one anchor. Whole-set renaming or edits while this mod is absent may require rebinding.
- Areas use only versioned `config/treasurehunter-ignore/<world hash>.json` with atomic replacement, without extra Xaero fields, name suffixes or custom world data. Removing TreasureHunter leaves standard waypoints and harmless unused JSON. Damaged/future files are preserved, with session-only edits indicated in the UI.
- World Map draws four inward cyan glow bands with half the initial width and opacity: 0.5 GUI pixels per band, 2 GUI pixels in total; alpha values 111 / 68 / 40 / 20. Outlines follow pan, zoom and dimension; the toggle only affects display. Standalone areas support World Map without Minimap; ambiguous linked worlds are skipped.

## Warnings

- Speedrun leaderboards generally disallow third-party information mods like this; use it for practice / singleplayer fun.
- Multiplayer: effectively "legal x-ray"; many servers deploy countermeasures (AntiPieRay etc.) — use at your own risk.

---

# Xaero TreasureHunter（中文）

Xaero 小地图 / 世界地图的非官方附属 mod：客户端扫描附近**已加载区块**中的目标方块，并把它们以**临时路径点**的形式显示在地图上。

## 目标方块（0.17.0-beta.1+mc26.3）

- 刷怪笼（刷怪房、下界要塞、要塞、废弃矿井）
- 箱子 / 陷阱箱 / 铜箱子 / 木桶 / 运输矿车箱（刷怪房、村庄、埋藏的宝藏、废弃矿井、沉船……）
- 钟（村庄）
- 宝库 / 不祥宝库（试炼密室）

## 使用

- 默认按键：
  - `G`：开关扫描
  - `H`：清空扫描标记及其临时路径点，保留在 Xaero 中转为永久的点，同时关闭扫描（按 `G` 重新开启）
  - `J`：首页依次为允许在服务器扫描、扫描目标、智能识别结构、结构筛选、聊天提示、合并邻近标记和「世界设置」。世界设置收纳免扫描区域、已开启宝箱/宝库记录重置和服务器放置记录重置。Mod Menu 也可打开同一页面，并返回原模组列表。
- 智能识别结构（设置首页，控制标记名称）：
  - 单人世界（含本机作为 LAN 房主）中，**只标记有真实战利品表的箱子、木桶和运输矿车箱**：内置服务器只读取已加载区块/实体，不生成战利品。确认无表的容器（含玩家储物箱、战利品已展开的箱子）直接过滤；数据不可用时推迟重试。关闭智能标签也不会关闭过滤。双联箱任一半有表即可保留。
    原版 58 个容器表 ID 均已覆盖：奖励箱保留普通箱名，丛林神庙发射器表归丛林神庙。映射之外的表归入**非原版战利品表容器**（「异」/ `OL`），仅在单人栏目配置；指令指定原版非容器表也可能进入此类，不能据此断言模组来源。原版 ID 被数据包覆盖仍按 ID 分类。已知表木桶保留「木桶」名称，未知表木桶使用该额外分类。分类筛选独立于名称显示。
    以下特征方块规则用于服务器上的客户端观察；即使证据很强，也仍然属于推断。
    分类名与 Minecraft Wiki 的结构名对齐；群系表与高度门限直读 26.3 结构数据（结构 JSON + `has_structure` 标签逐级展开）并按模板实测校准
  - `[埋藏的宝藏箱]`：区块局部 X/Z 均为 9 + 单个普通箱子 + 被遮盖（上必须、侧 ≥3 面）+ 海滩群系，置信度很高
  - `[刷怪房箱]`（俗称地牢箱）要求「刷怪笼 + 苔石」；`[废弃矿井箱]` 为**运输矿车实体扫描**。服务器上用铁轨 + 木支撑 + 低高度或蛛网佐证，不再把任意玩家矿车直接判为矿井箱；路径点目前固定在首次发现的位置。
  - `[村庄箱]`：干草捆 / 堆肥桶 / 钟，或床+火把+土径（村庄房屋的生活痕迹；沙漠村庄没有土径，走火把兜底），或火把兜底（需排除矿井特征与深色橡木建筑）——要求村庄群系（平原/草甸/沙漠/热带草原/积雪平原/针叶林）+ y > 50
  - `[林地府邸箱]`：深色橡木木板 + 深色森林（含浅色花园）群系 + y > 50（府邸箱子多数附近没有书架，书架不再是必要条件）
  - `[掠夺者前哨站箱]`：至少两面白色墙旗 + 深色橡木 + 圆石族构成哨塔强特征，不受群系/高度门限限制。两个哨塔模板在取景内均有 4 面白旗；扫描全部 1511 个模板与所有调色板，白色墙旗仅出现在前哨站。原来的深色橡木 + 石/羊毛 + 前哨站群系 + y > 50 保留为兜底。
  - `[丛林神庙箱]`：苔石 +（绊线陷阱 / 雕纹石砖 / 发射器）+ 丛林或竹林群系 + y > 35
  - `[海底废墟箱]`：水下 + 石砖族（冷海）或砂岩族（暖海）+ 海洋群系 + 海平面以下（wiki 复核：冷/暖变体都有战利品箱）
  - `[沙漠神殿箱]`：砂岩 + 陶瓦/TNT + 沙漠群系 + 非水下 + y > 35（避免误判含砂岩的水下废墟）
  - `[废弃传送门箱]`：黑曜石 + 哭泣的黑曜石（主世界可加 下界岩 佐证；下界直接以黑曜石判定——下界要塞/堡垒遗迹都不含黑曜石）——变体覆盖几乎所有群系、地表与掩埋都有，故不加群系/高度门限
  - `[废弃营地箱]`：任意颜色的羊毛楼梯即可定案——26.3 新增方块，实测 1511 个结构模板中仅出现在废弃营地；铜箱子（同为营地独有）直接由容器方块本身判定。**不**用营火作证据——远古城市 / 古迹废墟 / 村庄也生成营火，不具排他性
  - `[沉船箱]`：任意木制部件（原木 / 木板 / 楼梯 / 台阶 / 栅栏 / 门 / 活板门，任意木种）+ 沉船群系（全部海洋 + 海滩）+ 无火把/铁轨/蛛网等人为痕迹（村庄房屋、玩家建筑会被排除）；水下变体泡在水里，搁浅变体在海滩
  - `[下界要塞箱]` / `[堡垒遗迹箱]`：下界维度 + 各自群系表；**当且仅当**下界砖在箱子正下方 = 下界要塞（不再用周边证据兜底，避免抢走紧邻的堡垒遗迹箱）；黑石族（含镶金黑石）= 堡垒遗迹（周边证据兜底；堡垒遗迹另加锚点高度 20~85）
  - `[末地城箱]`：末地维度 + 紫珀/末地石砖（不再检测群系）；`[远古城市箱]`：幽匿 或 深层板岩砖 + y < 20（不再强制深暗之域群系）；`[试炼密室箱]`：凝灰岩砖/试炼刷怪笼 + y < 20
  - `[雪屋箱]`：只用地下室特征（酿造台 + 橡木告示牌）+ y > 35（不限制群系；避免结冰海洋的沉船被误判为雪屋）；`[要塞箱]`：石砖族（图书馆之外、附近没有书架的箱子也覆盖；代码生成的环状结构，无法加门限）
  - 高度辅助汇总：地下 y < 20（试炼密室 / 远古城市）；地表 y > 50（村庄 / 林地府邸 / 掠夺者前哨站）；半地下保守下限 y > 35（沙漠神殿 / 丛林神庙 / 雪屋）——用于排除玩家自建的地表/地下结构
  - 路径点按分类着色，简称随语言本地化：中文单字（宝/试/矿/怪/堡/要/末/古/村/沙/林/要/哨/邸/船/雪/海/门/营/异），英文缩写（BT/TC/MS/MR/BA/NF/EC/AC/VL/DP/JT/SH/PO/WM/SW/IG/OR/RP/CP/OL/OT），刷怪笼/钟为 S/B
  - 刷怪笼显示生物种类，如 `[蜘蛛笼]`、`[烈焰人笼]`（客户端同步的真实数据，非推断）
  - 双联箱只登记一个路径点
  - 运输矿车箱：单人要求真实战利品表；服务器按周边特征分类与过滤。在已记录路径点附近复核时，会移除对应实体已消失的标记。
  - 宝库 / 不祥宝库：试炼密室的兴趣方块（26.x 为同一方块、由不祥属性区分），独立开关；玩家用钥匙开启后（开启动画 / 弹出战利品阶段）自动移除标记与路径点，重扫不再登记
- 结构筛选：通用结构分类下方依次为「仅单人生效」（非原版战利品表容器）与「仅服务器客户端生效」（其他箱、可能由人为放置的容器）。专用分类按钮与通用网格同宽；较长的玩家容器过滤选项占剩余列，窄屏另起整行。按钮显示红绿状态块。面板支持滚动，全开/全关作用于所有栏目的分类；关闭移除临时点，重开请求重扫，与名称显示开关独立。
- 可能由人为放置的容器（结构筛选 → 仅服务器客户端生效）：**不过滤 / 保守 / 激进**，默认保守。不过滤只关闭该过滤，结构筛选和免扫描区域仍生效。保守过滤服务端确认的自己放置容器和明显漏斗/仓库，保留原结构豁免（村庄、要塞除外）。激进进一步隐藏未知及弱证据容器，漏斗/仓库不再获豁免；铜箱材质不能单独豁免，可能漏掉自然或模组战利品。仓库门限为水平 ±4 / 垂直 ±3 内至少 8 容器方块、4 对上下堆叠；双联箱两半各算一个。
- 过滤在首次发现和漏斗/容器相关变化后判断，合并 6 tick 后发现未登记或曾过滤的目标。已确认标记保持分类与路径点对象，不因普通方块变化或区块重发删除重建。自己放置记录独立存 `config/treasurehunter-placed/`，跨重启保留，世界设置可重置。
- 已打开容器记忆：玩家点击的箱子/木桶/矿车在实际容器界面打开后立即记录，关闭该界面后移除对应路径点；只点击打不开的容器不会记录。双联箱记录两半，矿车使用稳定 UUID；宝库在持钥匙交互并确认解锁/弹出战利品后记录。没拿物品也算打开过；暂停扫描或关闭服务器扫描许可时，记忆追踪仍然工作。
- 持久记忆：记录立即保存到 `config/treasurehunter-memory/`，按本地存档目录或服务器地址 + 玩家 UUID 隔离，并分别保存各维度；断开连接、重启游戏后仍保留。设置里的「重置当前世界已开启宝箱/宝库记录」会清除当前世界全部维度的记忆并允许重新发现符合筛选条件的目标，不影响其他世界。服务器在同地址换档，或在原坐标替换/补充容器后，想重新扫描时需重置记录；旧版本未保存的历史访问无法补回。
- 地图重载/回合制地图：ClientLevel 更换只切换扫描/战利品缓存并丢弃交互候选，已确认标记跨区块重载、切维度和重连保留。持久身份仍为存档目录或服务器地址 + 玩家 UUID，再按维度分桶，不包含回合或种子。同身份重建地图（如 Bingo）需按 H 重新扫描，并按需在世界设置重置已开启/放置记录、检查免扫描区域。
- 语言支持：界面文字、分类名、路径点简称、键位分类名与提示消息均为中英双语（随游戏语言自动切换）；提示中的按键名显示实际键位
- 邻近路径点合并（可在设置里关）：半径 5 格内已有相同标记（同类型 + 同分类）时不再重复标记；关闭后每个匹配都会标记
- 路径点生命周期：自建路径点记录创建时的路径点集，清理只删除仍为临时状态的点。在 Xaero 中 **Restore（恢复）** 或改为永久的点交由玩家管理，清空（H）、开箱、修改过滤设置、切换维度均会保留；需要删除时请在 Xaero 中操作
- 临时标记持久化：`config/treasurehunter-markers/<世界哈希>.json` 记录已确认的位置、名称、分类、矿车 UUID 及原 Xaero 世界/集合。Xaero 原生保存器会跳过临时点，所以由本模组单独保存；断线只保存并释放引用，重进自动恢复临时点，不重新分类或重复提示。H 同时清除管理中的临时点和其缓存记录。Restore 后永久点由 Xaero 保存并交还玩家管理。未知/损坏文件保留；旧版本已丢失且未保存的临时点需重扫一次。
- 扫描策略：只处理新加载或尚未完成的区块，每 2 秒兜底；已确认目标不重新分类、不重复提示。保留附近存在性与已展开战利品复核，容器相关变化可发现未登记目标，但不替换已确认点。免扫描区域覆盖全部目标和高度。
- 清空标记（`H`）会重置区块扫描记录，但持久的已打开记忆保留；重新启用目标/分类会请求重扫，切换智能识别或邻近合并会刷新现有标记。
- 配置：`config/treasurehunter.json`。单人无需另行许可；「允许在服务器扫描」在首页，默认关闭。旧 `singleplayerOnly` 和过滤开关自动迁移，旧 `singleplayerScanEnabled` 不再生效，保存时移除。两个记录重置均位于「世界设置」。
- 默认仅在**单人世界**启用（多人服务器上这类扫描通常被视为透视/作弊，请自行判断）。

## 依赖

- Fabric Loader ≥ 0.19.5（26.3）
- Fabric API
- 可选：Xaero's Minimap / World Map，以及 Mod Menu 21.0.0。已核对小地图 26.5.3 + 世界地图 1.46.4、小地图 26.6.0 + 世界地图 1.47.0。Mod Menu 仅用于编译，不随产物打包。Xaero 缺席时扫描可用；编辑/轮廓接口变化只降级该扩展。
- 免扫描区域：世界设置支持独立区域增删改和分页；Xaero 新建/编辑路径点表单内、确认/取消上方的「TreasureHunter: 设为免扫描区域」按钮打开半径和形状。圆形/正方形、0–128 区块半径（0 关闭、1 区块 = 16 格），以中心方块中心计算，覆盖全部高度，按世界/维度隔离。草稿在最后确认 Xaero 路径点时保存，取消不落盘；启用范围会将临时/到达后消失的点转为普通永久点。批量编辑的显式范围改动应用于全部选中点。
- 关联区域随确认后的名称、位置、目标集合和维度迁移。删除、转临时或缺少对应点时，在下次刷新后停用（通常不超过 1 秒），独立记录保留供删除/重绑；完全相同的路径点身份共用锚点。整体重命名集合、卸载本模组期间编辑路径点，可能需重绑。
- 数据只写版本化的 `config/treasurehunter-ignore/<世界哈希>.json` 并原子替换，不扩展 Xaero 字段、不追加名称后缀、不写自定义世界数据。卸载后，路径点仍为普通 Xaero 点，只留下无害的未使用 JSON。损坏/未来版本文件保留原内容，界面提示修改仅本次有效。
- 世界地图绘制四层向内渐弱的青色荧光，宽度和不透明度各为初版约一半：每层 0.5 GUI 像素，总宽 2 GUI 像素；alpha 为 111 / 68 / 40 / 20。跟随平移、缩放与维度，开关只影响显示。独立区域支持只安装世界地图；无法唯一对应的关联世界不画错区域。

## 提醒

- 速通正式成绩提交基本不允许这类第三方信息 mod；请用于练习 / 单机娱乐。
- 多人服务器：相当于“合法透视”，很多服有反制插件（AntiPieRay 等），使用风险自负。
