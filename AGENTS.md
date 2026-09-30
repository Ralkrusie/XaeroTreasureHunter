# AGENTS.md — Xaero TreasureHunter

本仓库的 AI 开发约定。动手改代码前请先读完与任务相关的小节；本文件的结论都标了出处，与代码冲突时以代码为准并顺手修正这里。

## 项目是什么

Xaero 小地图 / 世界地图的**非官方客户端附属 mod**（Fabric · Minecraft 26.3 · Java 25）。它扫描客户端已加载区块中的目标方块（刷怪笼 / 箱子 / 陷阱箱 / 木桶 / 运输矿车箱 / 钟 / 宝库），按**周边特征方块 + 群系 + 高度**推断结构来源，并写成 Xaero 的**临时路径点**。

- 纯客户端：不修改世界数据、不发自定义包、无服务端组件。
- 不含任何 Xaero 代码：仅通过字符串反射调用其公开内部 API，API 变动时静默降级并只警告一次。
- 默认仅在单人世界启用（多人服务器上此类扫描通常被视为透视）。

## 构建 / 部署 / 联调

```powershell
.\gradlew.bat build --console=plain          # 产物：build\libs\xaero-treasurehunter-<version>.jar(+ -sources.jar)
.\gradlew.bat clean build --console=plain    # 从零验证整条流水线
```

改完代码要进游戏验证时，把 jar 拷进实际测试实例（本机路径）：

```powershell
Copy-Item build\libs\xaero-treasurehunter-<version>.jar 'D:\Games\MC\.minecraft\versions\26.3-Fabric 0.19.5\mods\' -Force
```

- `run\mods\` 里已备好 Xaero 小地图 26.5.3 与 Xaero 世界地图 1.46.4，供 `gradlew runClient` 联调。
- `~\.gradle` 下**没有** `caches\fabric-loom\assets`，所以首次 `runClient` 需要联网下载 MC 资源（数百 MB）。
- 项目**没有测试**：没有 `src/test`、没有测试框架、没有 CI。验证 = 编译 + `scratch\` 探针 + 进游戏实测。

### ⚠️ DSH 沙箱前提（重要）

Gradle 的 `GRADLE_USER_HOME` 默认是 `C:\Users\<用户>\.gradle`（本机约 2.8 GB，含 loom 的 MC 反混淆 jar、`modules-2` 依赖缓存、Gradle 9.5.1 发行包），它在工作区**之外**。

在 `workspace-write` 沙箱下运行 `gradlew` 会在创建 `wrapper\dists\...\gradle-9.5.1-bin.zip.lck` 时被拒（`java.io.FileNotFoundException ... 拒绝访问`）。**构建、部署、`runClient` 都需要本会话处于「完全权限」**。

若希望长期在沙箱内构建：把 `~\.gradle` 复制进工作区、设 `GRADLE_USER_HOME` 指向它，并把该目录写进 `.gitignore`。

### 版本号同步点

一次发版要改这几处，漏一处就会出现"jar 名对不上 README"：

1. `gradle.properties` → `mod_version`
2. `README.md` → 英文段与中文段的 `（0.15.0）` 标题
3. `scratch\release-notes.md` → 安装步骤里的 jar 文件名（该文件不进版本控制）
4. git tag `v<version>`

`fabric.mod.json` 的 `version` 由 `processResources` 从 `project.version` 展开，不用手改。

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

首次构建新 MC 版本会联网下载约 155 MB 的 MC jar，缓存在 `~\.gradle\caches\fabric-loom\minecraftMaven\`。

### 版本升级时的结构数据复核

编译通过**不代表**判定还准。比对新旧 MC jar 里 `data/minecraft/` 的内容：

- 看 `worldgen/structure/*.json` 与 `tags/worldgen/biome/has_structure/*.json` —— 群系表与高度门限的来源。
- 典型**噪声**（不影响 mod）：`spawn_overrides` 的 `maxCount`/`minCount` 合并成 `count`、`air_pocket_probability` 改值。
- 真正要警惕的是**新增群系与新增结构**：26.3 新增了 `dappled_forest` 群系与 `abandoned_camp` 结构。
- 判断新结构是否会误判：看它用了什么证据方块、落在哪些群系。`abandoned_camp` 用的是营火而**非火把**，
  且 `straw_bed` 不在 `minecraft:beds` 标签里 —— 所以村庄规则的两条分支都不会被误触发。

> **26.3 起结构模板 NBT 格式变了**：顶层不再有 `palette`（`Name` 标签出现 0 次），方块名内联在 `state` 里。
> `scratch/` 中按 `\x08\x00\x04Name` 匹配调色板的脚本**在 26.3 数据上会返回空结果**，需要改写。

### 26.3 的 GLFW → SDL 变更

26.3 把窗口层从 GLFW 换成 SDL，类路径上是 `lwjgl-sdl`，`org.lwjgl.glfw` **整个包不存在**：

- `InputConstants.Type.KEYSYM` → `InputConstants.Type.KEYBOARD`（`SCANCODE` 也移除了）
- `GLFW.GLFW_KEY_X` → `InputConstants.KEY_X`（用 MC 自己的常量，不要依赖 LWJGL 的 `SDLKeycode`）

## 仓库结构与「不在版本控制里的东西」

跟踪的只有：源码、`gradle/wrapper/`、`gradlew*`、`README.md`、`LICENSE`、`build.gradle`、`settings.gradle`、`gradle.properties`、`.gitignore`。

以下被 `.gitignore` 排除，但对开发很关键：

| 路径 | 内容 | 备注 |
| --- | --- | --- |
| `scratch\` | 全部 API / 结构数据探针脚本与 `.txt` 输出 | **本项目验证方法论的本体**，新克隆的仓库里没有 |
| `run\` | dev 运行时，`run\mods` 里放 Xaero jar | 进游戏联调用 |
| `tools\` | `gradle-9.5.1-bin.zip` 离线备份 | 网络不稳时的兜底 |
| `.vscode\` | `tasks.json` 5 个任务（build / clean build / runClient / 部署 / appdiag） | 2026-09 从 709 行历史任务精简而来 |

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
  ├─ openedContainers    当前打开的容器
  ├─ scavenged           已搜刮记忆，按维度分桶（仅在断开连接时清空）
  └─ Marker record(key, type, pos, name, category, Object waypoint, entityId)
        └─ waypoint 故意声明为 Object：任何地方都不出现 Xaero 类型名

TargetType.match(BlockState)                 → 兴趣方块识别
StructureGuesser.guessContainerCategory(...) → 容器归类（特征方块 Flags + 群系 + 高度）
ChestCategory                                → 归类结果与筛选开关
XaeroBridge                                  → 反射写/删路径点，Object 传参
gui\*                                        → 两级选项树设置界面
```

要点：

- **单线程纪律**：tick 与区块加载回调都在客户端主线程，`XaeroBridge.OWNED` 是普通 `IdentityHashMap`，靠这个前提保证安全。不要从别的线程调 `MarkerScanner` 或 `XaeroBridge`。
- **`Marker.waypoint` 为 `Object`**、`XaeroBridge` 全部方法 `catch (Throwable)` 返回 `null`/`0`——这样 Xaero 缺席时 mod 仍能正常加载，只在日志/通知里降级。
- **分类前必须确认证据区域已加载**（`StructureGuesser.isClassificationAreaLoaded`），否则 `scannedChunks.remove(...)` 推迟重试。这是避免误判的关键机制，别绕过。

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
- **`en_us.json` 与 `zh_cn.json` 必须逐 key 对应**（当前各 80 行），没有回退文件。新增任何东西都要同时改两个文件。
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
`javap -c` 真的被用来证明过结论，例如客户端 `BlockEntity#getUpdateTag` 返回空 tag（因此开箱前读不到战利品，只能靠启发式分类），以及从 `MonsterRoomFeature` 字节码里读尺寸常量。

- **Fabric API**：`fabric-api-0.161.0+26.3.jar` 是个容器，需要 `jar xf` 出 `META-INF/jars/` 下的嵌套 jar（如 `fabric-key-mapping-api-v1`）再 `javap`——`KeyMappingHelper.registerKeyMapping(KeyMapping)` 就是这么确认的。
- **Xaero API**：直接对 `run\mods\xaerominimap-fabric-26.3-26.5.3.jar` 跑 `javap`，例如枚举 `xaero.hud.minimap.waypoint.WaypointColor` 的合法常量后再选色。
- **结构 JSON / 群系标签**：用 `[System.IO.Compression.ZipFile]::OpenRead($mcj)` 直接读 jar 内 `data/minecraft/worldgen/structure/*.json` 与 `data/minecraft/tags/worldgen/biome/has_structure/*.json`。嵌套的群系标签由递归 `ResolveBiomeTag` 逐级展开（`#` 前缀递归，深度上限 5）。`StructureGuesser` 里的群系表就是这么抄下来的。
- **结构模板 `.nbt`**：没有用 NBT 库，而是 gzip 解压后按字节模式匹配——调色板名匹配 `\x08\x00\x04Name(..)`，方块匹配 `\x09\x00\x03pos\x03...\x03\x00\x05state(....)`，并用 `ReadBEInt` 处理大端与补码。这些模板扫描的统计结果（箱子相对锚点的高度区间、特征方块出现率）就是 `StructureGuesser` 注释里那些数字的来源。

> 探针里的取景半径（`|dx|<=8, |dy|<=4`）与 `StructureGuesser.RADIUS=8` / `Y_RADIUS=4` 是同一套。**改这两个常量会让已记录的校准证据失效**，必须重跑模板扫描。

长时间全量扫描会留下僵尸 PowerShell 进程，`scratch\kill-strays.ps1` 专门清理。

## MC 26.x 踩坑记录（都已在代码里注释）

- **宝库与不祥宝库是同一个方块** `minecraft:vault`，用 `VaultBlock.OMINOUS` 区分；开启进度看 `VaultBlock.STATE` / `VaultState.{INACTIVE,ACTIVE,UNLOCKING,EJECTING}`。
- **客户端方块实体 NBT 是空的**，开箱前读不到战利品——所以只能启发式分类。**刷怪笼是例外**：`SpawnerBlockEntity#getSpawner().getOrCreateDisplayEntity(...)` 能拿到真实同步实体，因此能显示「蜘蛛笼 / 烈焰人笼」。
- **GUI API 变了**：屏幕重写 `extractRenderState(GuiGraphicsExtractor, int, int, float)` 并先调 `super`，用 `graphics.fakeItem(...)` / `centeredText(...)` / `fill(...)` 绘制；切屏用 `Minecraft#setScreenAndShow(...)`，不是 `setScreen`。
- **输入 API**：`KeyMappingHelper.registerKeyMapping(...)`、`KeyMapping.Category.register(Identifier...)`、`InputConstants.Type.KEYSYM`；动作栏 `sendOverlayMessage`，聊天 `sendSystemMessage`。
- **26.x 的 MC 已反混淆**：`build.gradle` 里**没有** `mappings` 块，依赖用普通 `minecraft` / `implementation` 配置而不是旧的 `modImplementation`。
- 用到的包位置：`net.minecraft.world.entity.vehicle.minecart.MinecartChest`、`net.minecraft.world.level.chunk.status.ChunkStatus`、`net.minecraft.world.level.block.entity.vault.VaultState`、`net.minecraft.resources.Identifier`。
- 区块未驻留时 `getChunkSource().getChunk(x, z, ChunkStatus.FULL, false)` 返回 `null`——这正是「是否已加载」的探测手段。

## 已知问题与风险（改到相关代码时留意）

- **`XaeroBridge` 没有重试**：`ensureResolved()` 在尝试前就把 `resolved = true`，任何一次瞬时失败（例如 Xaero 静态初始化尚未完成）会让整局游戏的地图集成失效；约 13 次反射查询是全有全无。`XaeroBridge.isAvailable()` 与 `TreasureHunterMod.config()` 目前无人调用（死代码）。
- **`Flags` 里有只写不读的残留字段**（`netherBricks`、`darkOakFence`、`snow`、`ladder`、`trapdoor`、`containers`）——0.14.2 删规则时没清干净。
- **`StructureGuesser` 的阈值全是硬编码魔数**，不可配置：`RADIUS=8`、`Y_RADIUS=4`、`SURFACE_MIN_Y=50`、`SHALLOW_MIN_Y=35`、`UNDERGROUND_MAX_Y=20`、`SEA_LEVEL_Y=63`、`BASTION_MIN_Y/MAX_Y=20/85`、埋藏宝藏的 `(x&15)==9 && (z&15)==9`、`coveredSides>=3` 等。
- **配置没有版本字段**：`disabledChestCategories` 存的是 `ChestCategory.name()`，**重命名枚举常量会静默丢掉用户的筛选设置**（旧名不会被清理，只是永远匹配不上）。
- **运输矿车路径点不会跟随移动**：`registerMinecart` 以首次见到的 `blockPosition()` 为 key，README 第 22/38 行声称"矿车移动时路径点自动更新"，实际是移出加载区后移除、再重新登记。**README 与实现不一致**。
- **木桶搜刮判定偏粗**：玩家 3 格内有任意 BARREL 且开着任意容器界面就会被判定为已搜刮，可能在开旁边无关容器时误标记（本局永久生效）。
- **`validateNearby` 的重试**：对 `waypoint()==null` 的标记每 10 tick 重试一次，且成功后 `break`——每轮最多恢复一个路径点；Xaero 缺席时这是永久空转。
- **标记上限 400 无淘汰**：达到 `maxMarkers` 后直接拒绝新登记，直到 `H` 清空。
- **性能**：`processQueue` 的 3 ms 预算只在区块之间检查，而 `scanChunk` 暴力遍历整个 16³；`sweepLoadedArea` 每 40 tick 把 `(2*(renderDistance+2)+1)²` 个位置入队并对 `scannedChunks` 全量 `removeIf`。
- **未本地化的硬编码文本**：通知前缀；`fabric.mod.json` 的 `name` / `description`（仅中文，提到"自动 Pie-Ray 扫描"）/ `authors`。
- **GUI 不一致**：`TreasureHunterConfigScreen.onClose()` 调 `setScreenAndShow(null)`，而另外两个界面会恢复 `parent`；`ChestFilterScreen` 的全开/全关是重建整个屏幕；"全关"会对包括 `OTHER` 在内的 19 个分类都调 `removeMarkersOfCategory`。
