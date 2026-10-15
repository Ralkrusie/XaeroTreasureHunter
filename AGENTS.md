# AGENTS.md — Xaero TreasureHunter

本仓库的 AI 开发约定。动手改代码前请先读完与任务相关的小节；本文件的结论都标了出处，与代码冲突时以代码为准并顺手修正这里。

## 项目是什么

Xaero 小地图 / 世界地图的**非官方客户端附属 mod**（Fabric · Minecraft 26.2 · Java 25）。它扫描客户端已加载区块中的目标方块；单人只保留有真实战利品表的箱子、木桶和运输矿车箱，服务器按**周边特征方块 + 群系 + 高度**推断来源，并可保守过滤疑似仓库/自动化容器。结果写成 Xaero 的**临时路径点**。

- 纯客户端：不修改世界数据、不发自定义包、无服务端组件。
- 不含任何 Xaero 代码：普通标记桥调用公开内部 API；忽略扩展反射读取编辑/地图字段、调整编辑表单，并通过 Xaero 原生确认保存。扩展失败只降级该扩展并警告一次。
- 默认仅在单人世界启用（多人服务器上此类扫描通常被视为透视）。

## 分支约定（别再搞错）

- `main` —— **稳定发布线**。维持 Minecraft 26.2；0.17.0-beta.1 与 26.3 同步功能，稳定发布 tag 仍为 v0.14.2。给旧版本发 hotfix 走这里。
- `mc-26.3` —— **当前开发线**（26.3 移植 + 废弃营地）。日常开发在这里。
- 升级 MC 版本时新建 `mc-XX` 分支；**不要把新版本快进或合并进 `main`**，
  除非用户明确要求 —— 何时把 `main` 挪到新版本由用户决定。
  （2026-09 就是擅自把 `main` 快进到 `mc-26.3`，之后不得不强推回滚。）
- 发布 tag 打在对应的开发线上：`v0.15.0` / `v0.16.0` 都在 `mc-26.3`。
- 0.17.0-beta.1 使用同一份 GitHub beta 草稿容纳两个 jar，mod_version 分别为 `0.17.0-beta.1+mc26.2` / `0.17.0-beta.1+mc26.3`。`main` 直接回移植共同功能，不合入 26.3 历史；唯一玩法差别为废弃营地。双语改动说明跟踪在 `CHANGELOG.md`，草稿目标为 26.3 提交，正文注明 26.2 提交。


## 构建 / 部署 / 联调

```powershell
.\gradlew.bat build --console=plain          # 产物：build\libs\xaero-treasurehunter-<version>.jar(+ -sources.jar)
.\gradlew.bat clean build --console=plain    # 从零验证整条流水线
```

改完代码要进游戏验证时，把 jar 拷进实际测试实例（本机路径）：

```powershell
Copy-Item build\libs\xaero-treasurehunter-<version>.jar 'D:\Games\MC\.minecraft\versions\26.2-Fabric 0.19.5\mods\' -Force
```

- 26.2 联调使用 Xaero 小地图 26.5.1 / 世界地图 1.46.1，以及 Mod Menu 20.0.2；可从实际 26.2 实例复制到本工作区 `run\mods\`。
- `~\.gradle` 下**没有** `caches\fabric-loom\assets`，所以首次 `runClient` 需要联网下载 MC 资源（数百 MB）。
- 项目没有 src/test、测试框架或 CI。验证 = 编译 + scratch 行为探针 / 真实 Fabric-Knot 目标变换加载探针 + 进游戏实测。

> ⚠️ **游戏运行时 `mods\` 里的 jar 会被锁住**：`Remove-Item` 报「正由另一进程使用」，
> 于是一次看似成功的「删旧 + 拷新」会**静默失败**并留下两个版本 —— Fabric Loader 会因重复 mod id 直接拒绝加载。
> 换版本前先确认游戏已退出；拷完用 `Get-FileHash` 比对源与目标，确认进去的确实是当前构建
> （`Copy-Item` 会保留源文件的修改时间，光看时间戳会误判）。

### ⚠️ DSH 沙箱前提（重要）

Gradle 的 `GRADLE_USER_HOME` 默认是 `C:\Users\<用户>\.gradle`（本机约 2.8 GB，含 loom 的 MC 反混淆 jar、`modules-2` 依赖缓存、Gradle 9.5.1 发行包），它在工作区**之外**。

在 `workspace-write` 沙箱下运行 `gradlew` 会在创建 `wrapper\dists\...\gradle-9.5.1-bin.zip.lck` 时被拒（`java.io.FileNotFoundException ... 拒绝访问`）。**构建、部署、`runClient` 都需要本会话处于「完全权限」**。

若希望长期在沙箱内构建：把 `~\.gradle` 复制进工作区、设 `GRADLE_USER_HOME` 指向它，并把该目录写进 `.gitignore`。

若 JDK 25 在沙箱长临时目录下报 `Unable to establish loopback connection`（堆栈为
`PipeImpl` → `UnixDomainSockets.connect`），本机已验证可在**本次构建进程**设置
`$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=E:/Code/MC/trackit/scratch'`。
使用 `JAVA_TOOL_OPTIONS` 才会传给 Gradle 守护进程；只设 `JAVA_OPTS` 不够。不要改全局环境变量。

### 版本号同步点

一次发版要改这几处，漏一处就会出现"jar 名对不上 README"：

1. `gradle.properties` → `mod_version`
2. `README.md` → 英文段与中文段的版本标题、顶部双版本安装包表
3. `scratch\release-notes.md` → 安装步骤里的 jar 文件名（该文件不进版本控制）
4. git tag `v<version>`
5. 重新生成发布载荷（`release-body.json` / `modrinth-changelog.md`，见下）

`fabric.mod.json` 的 `version` 由 `processResources` 从 `project.version` 展开，不用手改。

> **发布载荷有两份，字段名不同，别混用：**
> - `scratch\release-body.json` → **GitHub Release** 用（create-release API 的字段是 `body`）
> - `scratch\modrinth-changelog.md` → **Modrinth** 用（版本 API 的字段是 `changelog`；网页端直接粘贴该文件）
>
> 两份都由 `scratch\release-notes.md` 生成。⚠️ **不要**把 `Get-Content` 的输出对象直接 `ConvertTo-Json` —— 那会带上
> `value` / `PSPath` / `PSParentPath` / `PSChildName` / `PSDrive` / `PSProvider` / `ReadCount` 等 PowerShell 元数据，
> 成为坏载荷（历史上 `release-body.json` 就是这么坏的）：
>
> ```powershell
> $b = [System.IO.File]::ReadAllText('scratch\release-notes.md', [System.Text.Encoding]::UTF8)
> [System.IO.File]::WriteAllText('scratch\release-body.json', (@{ body = $b } | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))
> [System.IO.File]::WriteAllText('scratch\modrinth-changelog.md', $b, (New-Object System.Text.UTF8Encoding($false)))
> ```
>
> 校验：`body` 必须与 `release-notes.md` 逐字符相等（`-ceq`），顶层只有 `body` 一个键，且不带 BOM。

### 升级 Minecraft 版本（26.2 → 26.3 的实际流程）

1. **建分支**：`git checkout -b mc-XX`。迁移期主分支会编译不过，main 保持可发布。
2. **改四处**：`gradle.properties` 的 `minecraft_version` / `fabric_api_version` / `mod_version`，
   以及 **`fabric.mod.json` 的 `minecraft` 约束（`~26.2` → `~26.3`）**。
   ⚠️ 漏掉最后这处，构建照样成功，但 Fabric Loader 进游戏时直接拒绝加载 —— 最容易白跑一轮的地方。
3. **依赖版本从现成实例抄**：`D:\Games\MC\.minecraft\versions\<版本>\mods\` 里已有跑通的 Fabric API 与 Xaero 版本号，比查网页快且准。
4. **Loom 未必需要升**：26.2 → 26.3 时 Loom 1.17.21 直接可用。
5. **编译 → 修错**：用 `javap` 确认新签名，别猜。
6. **复核结构数据**（见下）—— 编译通过查不出来的部分。
7. **验证产物**：解包 `build\libs\*.jar` 直接看 `fabric.mod.json` 的 `version` 与 `minecraft` 约束。
8. **进游戏实测通过后**才打 tag。

实测前预检三点：实例 `mods\` 里**只有一份** treasurehunter jar（否则旧版可能先被加载）；
`fabric-api` 与 loader 版本满足 `fabric.mod.json` 的 `depends`；
实例 `options.txt` 里没有上一版本残留的 `key.treasurehunter.*`（旧键位值是 GLFW keysym，与 26.3 的 scancode 空间不通用）。

首次构建新 MC 版本会联网下载约 155 MB 的 MC jar，缓存在 `~\.gradle\caches\fabric-loom\minecraftMaven\`。

### 版本升级时的结构数据复核

编译通过**不代表**判定还准。比对新旧 MC jar 里 `data/minecraft/` 的内容：

- 看 `worldgen/structure/*.json` 与 `tags/worldgen/biome/has_structure/*.json` —— 群系表与高度门限的来源。
- 典型**噪声**（不影响 mod）：`spawn_overrides` 的 `maxCount`/`minCount` 合并成 `count`、`air_pocket_probability` 改值。
- 真正要警惕的是**新增群系与新增结构**：26.3 新增了 `dappled_forest` 群系与 `abandoned_camp` 结构。
- 判断新结构是否会误判：看它用了什么证据方块、落在哪些群系。`abandoned_camp` 用的是营火而**非火把**，
  且 `straw_bed` 不在 `minecraft:beds` 标签里 —— 所以村庄规则的两条分支都不会被误触发。
- **群系标签不用查**：mod 不使用任何群系标签，全部是显式 `Biomes.X` 常量。
  所以 `is_forest` / `is_overworld` / `spawns_cold_variant_farm_animals` 这类标签的改动与判定无关。
- **方块标签必须查**：判定的依据之一是 11 个 `BlockTags`（`BEDS` / `LOGS` / `PLANKS` / `RAILS` /
  `TERRACOTTA` / `WOODEN_{DOORS,FENCES,SLABS,STAIRS,TRAPDOORS}` / `WOOL`）。
  26.3 新增了 `poplar` 木种（`poplar_planks` 及对应 `wooden_*`，并经 `logs_that_burn` → `poplar_logs` 进入 `LOGS`）——
  因为判定走标签，**新木种自动覆盖，不需要改代码**。

**新增结构的证据方块必须先验证排他性。** 扫全部结构模板、按顶层目录归类，只有单一结构出现的才能单独作证据：

```powershell
# 逐个 gzip 解压 data/minecraft/structure/**.nbt，按 ASCII 匹配候选方块名，按路径首段归类
# 26.3 共 1511 个模板，全量约 1~2 分钟
```

26.3 实测结果：`wool_stairs` 与 `copper_chest` **仅**出现在 `abandoned_camp`；
而 `campfire` 还出现在 `ancient_city` / `trail_ruins` / `village` —— 所以**营火不能作营地证据**
（与营火做「与」只会降低召回，不提升精度）。

> **26.3 的结构模板 NBT 调色板键变了**：本机 26.3 jar（world_version=5023）逐个解析 1511 个模板，确认顶层使用 `palette` 或多套 `palettes`；调色板条目使用 `id` / `properties`，`blocks[].state` 仍是整数索引。读取多调色板模板时必须遍历全部方案。
> `scratch/` 中按 `\x08\x00\x04Name` 匹配调色板的脚本**在 26.3 数据上会返回空结果**，需要改写。

### 26.3 的 GLFW → SDL 变更

26.3 把窗口层从 GLFW 换成 SDL，类路径上是 `lwjgl-sdl`，`org.lwjgl.glfw` **整个包不存在**：

- `InputConstants.Type.KEYSYM` → `InputConstants.Type.KEYBOARD`（`SCANCODE` 也移除了）
- `GLFW.GLFW_KEY_X` → `InputConstants.KEY_X`（用 MC 自己的常量，不要依赖 LWJGL 的 `SDLKeycode`）
- ⚠️ `InputConstants.KEY_*` 是 **SDL scancode**（`KEY_G`=10 / `KEY_H`=11 / `KEY_J`=13），
  **不是**旧的 GLFW keysym（71 / 72 / 74）。`Type.KEYBOARD` 内部就是按这套值查表的，两者必须配套使用；
  看到 `KEY_G = 10` 不要以为是错的。

### 升级 Xaero 依赖时必须复核反射面

`XaeroBridge` 全靠反射，签名对不上时会**静默禁用整个地图集成**（只在日志里警告一次），编译期毫无提示。
升级 `run\mods` 里的 Xaero 后，必须用 `javap` 逐项确认：

```powershell
$jar = 'run\mods\xaerominimap-fabric-26.3-26.5.3.jar'
& "$env:JAVA_HOME\bin\javap.exe" -cp $jar xaero.hud.minimap.BuiltInHudModules
```

需要确认的清单：`BuiltInHudModules.MINIMAP`、`HudModule#getCurrentSession`、`MinimapSession#getWorldManager`、
`MinimapWorldManager#getCurrentWorld`、`MinimapWorld#getCurrentWaypointSet`、
`WaypointSet#{add(Waypoint,boolean), remove(Waypoint), getWaypoints}`、
`Waypoint` 的 9 参构造器与 `getName` / `getWaypointColor` / `isTemporary`、`WaypointPurpose.NORMAL`，
以及 mod 用到的 **20 个 `WaypointColor` 常量**（按字符串名解析，改名不报编译错）。

> 26.5.3 (MC 26.3) 上以上各项已全部确认存在。

### 26.3 分支参考：废弃营地（main 的 26.2 不包含以下营地分类）

- 判定走两条**各自排他**的信号：`BlockTags.WOOL_STAIRS`（16 色），或容器本身是 `BlockTags.COPPER_CHESTS`（8 变体）
- 规则必须放在村庄等启发式**之前**（先匹配先赢），否则营地箱会被村庄的铁轨/干草等规则抢走
- 不加群系 / 高度门限：营地是地表 size 2 的小结构
- 铜箱子 `extends ChestBlock`，因此开箱自动移除、路径点生命周期等既有逻辑**无需改动**；
  它在 `TargetType` 里并入 `CHEST`（不单开目标类型）
- **木桶不贴原版结构箱名**：单人木桶也必须有真实战利品表；已知表保留「木桶」标签，范围外表可进入通用的 `OTHER_LOOT`（非原版战利品表容器）分类。服务器可读取周边结构证据来决定是否过滤木桶，但不把木桶命名成某结构箱。
  原版结构分类名以「箱」结尾，营地里木桶数量与箱子相当（模板实测 384 vs 369 次），
  贴成「废弃营地箱」会误导。已知表木桶受 BARREL 开关控制，范围外表另受 OTHER_LOOT 分类开关控制。

## 仓库结构与「不在版本控制里的东西」

跟踪的包括：源码、`gradle/wrapper/`、`gradlew*`、`README.md`、`CHANGELOG.md`、`AGENTS.md`、`LICENSE`、`build.gradle`、`settings.gradle`、`gradle.properties`、`.gitignore`。

以下被 `.gitignore` 排除，但对开发很关键：

| 路径 | 内容 | 备注 |
| --- | --- | --- |
| `scratch\` | 全部 API / 结构数据探针脚本与 `.txt` 输出 | **本项目验证方法论的本体**，新克隆的仓库里没有 |
| `run\` | dev 运行时，`run\mods` 里放 Xaero jar | 进游戏联调用 |
| `tools\` | `gradle-9.5.1-bin.zip` 离线备份 | 网络不稳时的兜底 |
| `.vscode\` | `tasks.json` 4 个任务（build / clean build / runClient / 部署到测试实例） | 2026-09 从 709 行历史任务精简而来 |

因为 `scratch\` 不进版本控制，**分类阈值背后的实测证据（模板扫描结果、结构 JSON 展开表）只存在于本机**。改动 `StructureGuesser` 的判定规则时，别假设这些证据能被重新推导出来。

## 架构（改代码前先建立这张图）

```
TreasureHunterMod (ClientModInitializer)
  ├─ 配置：config/treasurehunter.json  (Gson，load/save)
  ├─ 键位：G 开关扫描 / H 清空标记 / J 设置界面
  └─ 事件：ClientTickEvents.END_CLIENT_TICK  → MarkerScanner.tick()
           ClientChunkEvents.CHUNK_LOAD      → MarkerScanner.onChunkLoaded()
           ClientPlayConnectionEvents.DISCONNECT → MarkerScanner.reset()

MarkerScanner (有状态，全部在客户端主线程)
  ├─ queue/queued        待扫区块（ChunkPos）
  ├─ scannedChunks       已扫区块记录（H 清空标记会重置，让目标可被重新发现）
  ├─ markers             已登记标记，key = BlockPos.asLong()
  ├─ pendingContainer / openedContainer  实际点击 + 新容器菜单确认，关闭后删标记
  ├─ scavenged           ScavengedMemory，按世界身份与维度持久保存
  ├─ savedMarkers        ScanMarkerMemory，保存已确认标记并在重进后恢复临时点
  ├─ lootReader          SingleplayerLootReader，异步读取内置服务器真实战利品表
  └─ Marker record(key, type, pos, name, category, Object waypoint, entityId, entityUuid)
        └─ waypoint 故意声明为 Object：任何地方都不出现 Xaero 类型名

TargetType.match(BlockState)                 → 兴趣方块识别
StructureGuesser.assessContainer(...)        → 服务器来源推断 + 仓库/漏斗过滤评估
LootTableCategories.classify(...)            → 精确表 ID 映射（范围外 = OTHER_LOOT）
ChestCategory                                → 归类结果与筛选开关
XaeroBridge                                  → 反射写/删路径点，Object 传参
gui/*                                         → 常用设置首页 / 结构筛选模式栏目 / 世界设置
integration/ModMenuIntegration               → 可选 Mod Menu 21.0.0 设置工厂
scan/IgnoreRegionStore                        → 独立区域 JSON 与路径点外部锚点
xaero/XaeroWaypointEditor / IgnoreMapOverlay   → 路径点编辑与地图轮廓扩展
mixin/                                       → 原版按钮确认、增量包观察、预测只读访问器
```

要点：

- **单线程纪律**：tick、区块加载与玩家交互回调都在客户端主线程，`XaeroBridge.OWNED` 是普通 `IdentityHashMap`，靠这个前提保证安全。不要从别的线程调 `MarkerScanner` 或 `XaeroBridge`。`SingleplayerLootReader` 仅在服务器线程读取已加载区块/实体的 getter，再通过 `client.execute` 回传字符串；缓存只能在客户端线程更新。换世界、换 ClientLevel、换维度与清空时递增 generation，丢弃旧异步结果。
- **`Marker.waypoint` 为 `Object`**、`XaeroBridge` 全部方法 `catch (Throwable)` 返回 `null`/`0`——这样 Xaero 缺席时 mod 仍能正常加载，只在日志/通知里降级。
- **服务器启发式分类/过滤前必须确认证据区域已加载**（`StructureGuesser.isClassificationAreaLoaded`），否则推迟重试。单人不走启发式：异步结果为 null 或 available=false 时等待；available=true 且 ID=null 才能过滤无表容器。标签开关不能绕过单人过滤。双联箱登记这一半无表时还要读另一半；getter 缓存 100 tick，附近复核会移除新近展开战利品的容器。
- **已打开记忆**：`config/treasurehunter-memory/<SHA-256 世界身份>.json`，格式版本 1。单人身份取存档绝对目录，联机取规范化服务器地址 + 玩家 UUID；维度用完整 ID。方块存类型 + 坐标，双联箱存两半，矿车存稳定 UUID。确认菜单打开/宝库解锁时立即落盘，断线只保存并卸载内存，不删除文件；H 清标记保留记忆，设置里的显式重置只清当前世界。损坏或未知版本文件保留原内容，禁止自动用空文件覆盖。
- **已确认扫描标记**：`config/treasurehunter-markers/<SHA-256 世界身份>.json`，版本 1，按维度保存位置、类型、名称、分类、矿车 UUID、原 Xaero 世界路径/集合。Xaero 原生 WaypointIO.saveWaypoints 跳过 temporary=true。checkpoint 每 100 tick、切维度、断线和停止时执行；断线检查真实临时状态并保留快照，兼容 Xaero 先卸载集合的时序。退出仅 releaseOwnership，不 removeAll。恢复不扫描区块、不通知；延迟等待正确 Xaero 世界/集合就绪，全局每轮最多补 32 点。未知/损坏文件不自动覆盖；H 显式清当前世界全部维度缓存。已恢复永久点由 Xaero 落盘，不再进入本模组临时存档。
- **服务器过滤**：serverFilterMode 只有 OFF / CONSERVATIVE / AGGRESSIVE，默认保守。OFF 只关玩家容器过滤；保守沿用结构豁免并过滤确认的自己放置；激进隐藏未知/弱证据并取消漏斗/仓库豁免。强证据组合见 assessContainer，不能把类别直接当可信度。仓库按容器方块计数，门限 ±4/±3、至少 8 容器+4 堆叠，为保守启发式而非模板校准。

- **服务器过滤时机**：首次发现与漏斗/容器相关增量变化后评估，其他方块变化不触发重扫。合并 6 tick 后只补未登记/曾过滤的目标；已登记 Marker 和 waypoint 对象保持稳定，避免重复通知和 Restore 操作落到旧引用。区块重发不删标记，已完成区块不重排队；附近存在性/单人无表复核仍有效。放置预测与服务端 ack 关联，失败点击不记录；世界设置可重置放置记忆。

- **模式与迁移**：单人无需许可开关；serverScanEnabled=false，首页可启用服务器扫描；顺序为服务器许可、扫描目标、智能识别、结构筛选、聊天提示、合并、世界设置。旧 singleplayerOnly 迁移为服务器许可；旧严格开关映射 AGGRESSIVE，其余映射 CONSERVATIVE/OFF，显式新字段优先。旧 singleplayerScanEnabled 忽略且不再保存。目标、智能识别结构、提示、合并在首页；结构筛选末尾的 OTHER_LOOT 仅单人，OTHER 与过滤模式仅服务器客户端；世界设置含免扫描区域和两种记录重置。单人无表过滤始终有效。Mod Menu 接收 parent，未安装时不加载其入口。

- **忽略数据契约**：config/treasurehunter-ignore/<SHA-256 世界身份>.json，版本 1，独立于 Xaero 和世界数据。未知/损坏格式禁止覆盖原文件；UI 提示仅本次修改有效。圆/方形半径 0 关闭，1–128 区块×16 格，以中心方块中心计算，全高度；双联箱任一半在内即过滤。独立区域不依赖 Xaero。

- **忽略路径点契约**：Xaero 没有持久 UUID，外部锚点用全世界路径+集合+名称+XYZ；确认前后迁移，旧锚点移除与新区域一次落盘。取消不保存；显式启用范围把临时/目的地点表单转普通永久，由 Xaero 保存。批量范围改动应用全部选中点；每 20 tick 只读刷新永久点，删除/转临时/缺席时停用，数据记录保留。卸载期间编辑/全局集合改名需重绑，同身份共用锚点；无关点变化不能触发全量重扫。

- **忽略反射面**：GuiAddWaypoint 的 confirmButton/editForms/waypointsEdited/mutualForm/defaultWorld/fromSet/worlds/sets/manager；表单 disabledOrTemporary/keepDisabledOrTemporary；MinimapWorld 的 getFullPath/getDimId/getIterableWaypointSets，集合及永久点 getters，根容器 getAllWorldsIterable。GuiMap 的 cameraX/cameraZ/scale/screenScale，getMapProcessor→getMapWorld→getCurrentDimensionId；scale 是物理像素/格，几何按物理像素提交并用 pose 缩回 GUI 空间；四层内光每层 0.5 GUI 像素、总宽 2 GUI 像素，颜色 alpha 为 0x6F/0x44/0x28/0x14，是初版宽度和不透明度约 50%。已核对 26.2 对应的 26.5.1/1.46.1；26.3 分支另核对 26.5.3/1.46.4、26.6.0/1.47.0。编辑页入口为 220×20 按钮「TreasureHunter: 设为免扫描区域」，位于表单内、原生确认按钮上方 26 GUI 像素。Fabric AFTER_INIT 每次初始化/缩放重置局部事件，需重新注册 afterExtract。所有 Xaero 类型以字符串/Object 出现。

- **玩家保留的路径点**：26.5.3/26.6.0 的列表 Restore 与世界地图 toggleTemporaryWaypoint 都在原 Waypoint 对象 setTemporary(false) 后保存世界（javap 复核）。周期同步管理权，永久/主动移除的点退出 OWNED；emit 遇到同名、同坐标、同颜色永久点时只复用、不接管。H、开箱、过滤清理均只删仍管理的临时点。不能因区块同步替换对象，不能按名称盲删。真实原生集合/序列化器回归覆盖重进、先卸载集合再断线、Restore 后 H。
- **前哨站强证据**：26.3 两座哨塔的箱周围 ±8/±4 内均有 4 面白色墙旗；26.2 的 1212 个结构模板另经逐个解压复核，白色墙旗也仅出现在两座前哨站塔。至少 2 面白色墙旗 + 深色橡木 + 圆石族可绕过旧群系/高度门限，避免边界漏判。`WallBannerBlock#getColor` 识别颜色，26.3 不存在 `Blocks.WHITE_WALL_BANNER` 常量。
- **非原版战利品表容器**：保留 OTHER_LOOT 枚举名/id，避免破坏旧配置。26.2 的 56 个原版 chests 表均覆盖，spawn_bonus_chest 返回 null 保留普通箱名，jungle_temple_dispenser 归 JUNGLE；其他未映射表独立进入 OTHER_LOOT。名称指原版容器映射之外，不证明模组来源；原版 ID 被覆盖仍按 ID 分类。不能按目录前缀接纳未知表。

## 代码约定

- **Java 25**（`options.release = 25`）：用 `var`、record、switch 表达式、模式匹配 `instanceof`。
- **按职责分包**：根包 + `.scan` / `.gui` / `.xaero`。工具类 `final` + 私有构造（`XaeroBridge`、`StructureGuesser`、`OptionIcons`）。
- **注释与日志一律中文**（`src/` 内）。英文只出现在 `build.gradle` / `settings.gradle` 的构建说明、README 英文段和 `fabric.mod.json`。每个类有 javadoc；阈值处必须注明**出处**——现成的措辞有「26.2 数据」「wiki 复核」「NBT 实测」「javap 校验」。
- **日志**：SLF4J，消息前缀 `"Xaero TreasureHunter: "`，用 `{}` 占位。
- **可空性**：没有 `@Nullable`/`@NotNull`；javadoc 写明「返回 null」，调用方显式判空。
- **常量**：SCREAMING_SNAKE 命名，集中在类顶部；枚举 id 是小写蛇形字符串，**同时充当语言文件的 key 后缀**。
- **提交信息**：中文，`<版本号>：<要点>；<要点>`，正文用 `-` 列改动与**原因**，常引用探针证据；README 与源码同一次提交更新。
  ```
  0.14.2：试炼箱与不祥宝库颜色互换；雪屋改为只用地下室特征
  ```

## 本地化规则

- 一律用原版 **`Component.translatable(key)`**，没有自定义 helper。唯一硬编码的用户可见文本是通知前缀 `Component.literal("Xaero TreasureHunter: ")`。
- **没有语言代码分支**：不读 `LanguageManager`，也不判断语言。中英差异全部由语言文件承载，例如路径点简称：
  ```java
  public String initials() { return Component.translatable("initial.treasurehunter." + id).getString(); }
  ```
  中文给单字（宝/试/矿/怪/堡/要/末/古/村/沙/林/哨/邸/船/雪/海/门/库），英文给双字母（BT/TC/MS/MR/BA/NF/EC/AC/VL/DP/JT/SH/PO/WM/SW/IG/OR/RP/OT）。中文有意重复：要塞与下界要塞同为「要」，宝库与不祥宝库同为「库」（英文不重复）。
- key 命名空间：`target.` / `category.` / `initial.` / `key.` / `key.category.` / `message.` / `option.` / `button.` / `screen.`，统一 `treasurehunter` 命名空间。
- **`en_us.json` 与 `zh_cn.json` 必须逐 key 对应**（当前各 133 个 key），没有回退文件。新增东西同时改两个文件。
- 路径点名字在登记时就 `label.getString()` 拍平成 `String`，**切换语言不会立刻改写已有路径点**，要等重扫重新登记。
- `README.md` 的分类简称列表是语言文件数据的副本，**加分类时要一起改**（0.14.1 的提交就是 README + 两个语言文件同时动）。

## 检查清单：加一个目标方块 / 容器分类

### 新增 `TargetType` 常量

1. `TargetType` 枚举构造 `(id, colorEnumName)` + `match(BlockState)` 分支。
2. `TreasureHunterConfig` 加布尔字段 **并**补 `isEnabled(TargetType)` 的穷尽 switch。
3. `gui/TargetScanScreen.setTarget` 的 switch（关闭时调用 `removeMarkersOfType`）。
4. `gui/OptionIcons.targetIcon` 的 switch + 新增 `TARGET_*` 图标常量。
5. 两个语言文件各加 `target.treasurehunter.<id>` 与 `initial.treasurehunter.<id>`。
6. 颜色必须是 Xaero `WaypointColor` 里**已存在**的常量名（靠 `getField(name)` 解析）。Xaero 没有 `ORANGE`，宝库因此用 `GOLD`。

设置界面按 `TargetType.values()` 迭代，会自动出现新条目。

### 新增 `ChestCategory` 常量

1. `ChestCategory` 枚举构造 `(id, colorEnumName)`。
2. `StructureGuesser.guessContainerCategory` 的判定链（**顺序敏感，先匹配先赢**）+ 私有 `Flags` 加字段 + `scan(...)` 里加检测分支。
3. `gui/OptionIcons.categoryIcon` 的 switch + `CAT_*` 常量。
4. 两个语言文件各加 `category.treasurehunter.<id>` 与 `initial.treasurehunter.<id>`。
5. `LootTableCategories.classify` 补真实战利品表映射，表名从当前 MC jar 核对。

筛选开关是**按枚举名自动**生效的（`disabledChestCategories`），不用加配置字段。若新分类来自实体或方块实体，还要相应扩展 `scanMinecarts` / `registerMinecart` / `scanChunk` / `register` 与 `validateNearby` 的存在性检查。

> `isEnabled`、`targetIcon`、`categoryIcon` 都是**无 `default` 的穷尽 switch 表达式**，漏改会编译失败。唯一不受编译保护的是 `TargetScanScreen.setTarget`（箭头语句 switch）——这是本仓库最容易漏的一处。

## 验证方法论（`scratch\` 里的探针）

新克隆没有这些脚本，改动判定规则前先确认它们还在。核心手法：

**定位反混淆 jar**（多数脚本硬编码此路径）：
```
C:\Users\<用户>\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar
```

**探 API 签名**——`javap` 是主力：
```powershell
& "$env:JAVA_HOME\bin\javap.exe" -cp $mcj net.minecraft.world.level.block.Blocks
& "$env:JAVA_HOME\bin\javap.exe" -p  -cp $mcj <类>   # 私有/受保护成员
& "$env:JAVA_HOME\bin\javap.exe" -c  -cp $mcj <类>   # 字节码：用于"证明"行为
```
`javap -c` 用于证明行为，例如 `BlockEntity#getUpdateTag` 默认返回空 tag（普通远程客户端不会收到箱子战利品表），以及读取 `MonsterRoomFeature` 的尺寸常量。单人内置服务器 getter 可直接读表；本机已核实 getter 不触发战利品展开。

- **Fabric API**：`fabric-api-0.161.0+26.3.jar` 是个容器，需要 `jar xf` 出 `META-INF/jars/` 下的嵌套 jar（如 `fabric-key-mapping-api-v1`）再 `javap`——`KeyMappingHelper.registerKeyMapping(KeyMapping)` 就是这么确认的。
- **Xaero API**：直接对 `run\mods\xaerominimap-fabric-26.3-26.5.3.jar` 跑 `javap`，例如枚举 `xaero.hud.minimap.waypoint.WaypointColor` 的合法常量后再选色。
- **结构 JSON / 群系标签**：用 `[System.IO.Compression.ZipFile]::OpenRead($mcj)` 直接读 jar 内 `data/minecraft/worldgen/structure/*.json` 与 `data/minecraft/tags/worldgen/biome/has_structure/*.json`。嵌套的群系标签由递归 `ResolveBiomeTag` 逐级展开（`#` 前缀递归，深度上限 5）。`StructureGuesser` 里的群系表就是这么抄下来的。
- **结构模板 `.nbt`**：没有用 NBT 库，而是 gzip 解压后按字节模式匹配——调色板名匹配 `\x08\x00\x04Name(..)`，方块匹配 `\x09\x00\x03pos\x03...\x03\x00\x05state(....)`，并用 `ReadBEInt` 处理大端与补码。这些模板扫描的统计结果（箱子相对锚点的高度区间、特征方块出现率）就是 `StructureGuesser` 注释里那些数字的来源。

> 探针里的取景半径（`|dx|<=8, |dy|<=4`）与 `StructureGuesser.RADIUS=8` / `Y_RADIUS=4` 是同一套。**改这两个常量会让已记录的校准证据失效**，必须重跑模板扫描。

长时间全量扫描会留下僵尸 PowerShell 进程，`scratch\kill-strays.ps1` 专门清理。

## MC 26.x 踩坑记录（都已在代码里注释）

- **宝库与不祥宝库是同一个方块** `minecraft:vault`，用 `VaultBlock.OMINOUS` 区分；开启进度看 `VaultBlock.STATE` / `VaultState.{INACTIVE,ACTIVE,UNLOCKING,EJECTING}`。
- **箱子的正常客户端同步 NBT 是空的**，远程普通客户端读不到真实战利品表。单人服务器线程用 `getChunkNow` 和 `getLootTable`（矿车用 `getContainerLootTable`）只读真实表；确认无表时过滤，不推断是否一定开过。未映射非空表归 `OTHER_LOOT`，读取失败等待重试；已知 ID 被数据包覆写时仍按 ID 分类。刷怪笼可用同步的 `getOrCreateDisplayEntity(...)` 显示生物种类。
- **GUI API 变了**：屏幕重写 `extractRenderState(GuiGraphicsExtractor, int, int, float)` 并先调 `super`，用 `graphics.fakeItem(...)` / `centeredText(...)` / `fill(...)` 绘制；切屏用 `Minecraft#setScreenAndShow(...)`，不是 `setScreen`。
- **输入 API**：`KeyMappingHelper.registerKeyMapping(...)`、`KeyMapping.Category.register(Identifier...)`；动作栏 `sendOverlayMessage`，聊天 `sendSystemMessage`。（`Type.KEYSYM` 在 26.3 已改名 `KEYBOARD`，见下）
- **26.x 的 MC 已反混淆**：`build.gradle` 里**没有** `mappings` 块，依赖用普通 `minecraft` / `implementation` 配置而不是旧的 `modImplementation`。
- 用到的包位置：`net.minecraft.world.entity.vehicle.minecart.MinecartChest`、`net.minecraft.world.level.chunk.status.ChunkStatus`、`net.minecraft.world.level.block.entity.vault.VaultState`、`net.minecraft.resources.Identifier`。
- 区块未驻留时 `getChunkSource().getChunk(x, z, ChunkStatus.FULL, false)` 返回 `null`——这正是「是否已加载」的探测手段。

## 已知问题与风险（改到相关代码时留意）

- **`XaeroBridge` 没有重试**：`ensureResolved()` 在尝试前就把 `resolved = true`，任何一次瞬时失败（例如接口解析时尚未可用）会让整局游戏的地图集成失效；反射查询是全有全无。`XaeroBridge.isAvailable()` 目前无人调用；`TreasureHunterMod.config()` 用于 Mod Menu 和地图轮廓。
- **`Flags` 里有只写不读的残留字段**（`netherBricks`、`darkOakFence`、`snow`、`ladder`、`trapdoor`、`containers`）——0.14.2 删规则时没清干净。
- **`StructureGuesser` 的阈值全是硬编码魔数**，不可配置：`RADIUS=8`、`Y_RADIUS=4`、`SURFACE_MIN_Y=50`、`SHALLOW_MIN_Y=35`、`UNDERGROUND_MAX_Y=20`、`SEA_LEVEL_Y=63`、`BASTION_MIN_Y/MAX_Y=20/85`、埋藏宝藏的 `(x&15)==9 && (z&15)==9`、`coveredSides>=3` 等。
- **配置没有版本字段**：`disabledChestCategories` 存的是 `ChestCategory.name()`，**重命名枚举常量会静默丢掉用户的筛选设置**（旧名不会被清理，只是永远匹配不上）。
- **运输矿车路径点不会跟随移动**：`registerMinecart` 以首次见到的 `blockPosition()` 为 key；只有在该登记位置 8 格内执行验证时才会移除已经消失的实体，远处失效标记可能长期保留。README 已说明固定位置与附近复核；已打开记忆独立使用 UUID。
- **持久记忆的身份限制**：服务器同地址换档、存档原目录重建世界（包括 Bingo 中途换种子/回合）、容器同坐标替换补货，都复用旧持久记录；需显式重置当前世界记忆/放置记录，并检查独立免扫描区域。不能仅凭 ClientLevel 重载清空持久数据，普通传送/重连也会重载。ClientLevel 更换保存/恢复标记缓存并丢弃交互候选，区块重发保留标记；切维度/断线不删临时点，H 清扫描标记缓存，永久点仍受 Restore 保护。旧版本历史访问无法恢复。
- **`validateNearby` 的重试**：对 `waypoint()==null` 的标记每 10 tick 重试一次，且成功后 `break`——每轮最多恢复一个路径点；Xaero 缺席时这是永久空转。
- **标记上限 400 无淘汰**：达到 `maxMarkers` 后直接拒绝新登记，直到 `H` 清空。
- **性能**：`processQueue` 的 3 ms 预算只在区块之间检查，而 `scanChunk` 暴力遍历整个 16³；`sweepLoadedArea` 每 40 tick 把 `(2*(renderDistance+2)+1)²` 个位置入队并对 `scannedChunks` 全量 `removeIf`。
- **未本地化的硬编码文本**：通知前缀；`fabric.mod.json` 的 `name` / `description`（仅中文）/ `authors`。
- **GUI 返回与完整性**：主设置支持 parent，Mod Menu 返回原模组列表，子页返回父页。结构筛选使用原版 AbstractContainerWidget 滚动；320×240 两列，大宽度三列，固定页脚；专用分类按钮同网格宽，长过滤选项跨剩余列、窄屏另起整行，恢复实时红绿状态块。FilterButton 保留原版滚动文字并给图标/状态块留边。OptionIcons 不能静态构造 ItemStack：26.3 BuiltInRegistries.ITEM 的物品组件未绑定，从当前连接 registryAccess 取得对应已绑定 holder 后创建；未连接/未绑定只省略图标，不能中断页面初始化。scratch 的实际 Fabric/Knot 界面探针覆盖此错误及 Xaero 新建/编辑入口。

## 0.17.0-beta.1 的 26.2 回移植验证

- 此分支依赖 MC 26.2 / Fabric API 0.161.0+26.2 / Mod Menu 20.0.2；按键仍配套 Type.KEYSYM 与 GLFW keysym。
- 已去除营地分类、两张表、图标与双语键，以及羊毛楼梯/铜箱标签引用；其余玩法源码与 26.3 相同。
- clean build、真实 Fabric/Knot 目标变换与 Xaero 26.5.1 / 世界地图 1.46.1 编辑/地图反射面验证通过。310 项行为探针覆盖 56 张原版容器表、持久记忆、完整设置、稳定标记、退出重进、Restore 和 H 清除。
- 两套发布包集中在原工作区 scratch/beta-release；发布双语说明在 CHANGELOG.md。
