package com.treasurehunter.scan;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 结构来源的启发式识别。
 *
 * <p>
 * 客户端的方块实体 NBT 默认是空的（{@code BlockEntity#getUpdateTag} 返回空 tag，
 * 箱子没有覆写），所以箱子的战利品表/内容在开箱前拿不到。这里改用"周边特征方块"
 * 推断容器来源；刷怪笼是例外——它的 NBT 会同步到客户端，可以直接读出生物种类。
 *
 * <p>
 * 所有推断都是启发式的：玩家自建建筑、混合结构可能误判；埋藏宝藏与刷怪笼生物种类例外，
 * 前者基于原版固定的 (9,9) 生成规律，后者是客户端同步的真实数据。
 */
public final class StructureGuesser {
    /** 分类扫描半径（水平）：刷怪房最大 9 格宽、刷怪笼居中、箱子贴墙（离中心 ≤4 格），8 格已很富余。 */
    private static final int RADIUS = 8;
    private static final int Y_RADIUS = 4;
    /** 宝藏箱"单箱"判定的邻近范围（保持原始 6 格，不随分类半径变化）。 */
    private static final int TREASURE_RANGE = 6;
    /** 高度辅助：纯地表建筑（府邸 / 前哨站）的判定下限。 */
    private static final int SURFACE_MIN_Y = 50;
    /**
     * 高度辅助：带地下部分的结构（沙漠神殿深坑宝库 / 丛林神庙宝库 / 雪屋地下室）的判定下限。
     * 取值保守（宁可放低也不漏检）：沙漠神殿宝库可在地表下十几格，雪屋地下室在地表下数格。
     */
    private static final int SHALLOW_MIN_Y = 35;
    /** 高度辅助：地下结构（试炼密室锚点 -40~-20、远古城市锚点 -27）的判定上限，仅用于排除地表建筑。 */
    private static final int UNDERGROUND_MAX_Y = 20;
    /** 海平面高度：海底废墟等水下结构的判定上限。 */
    private static final int SEA_LEVEL_Y = 63;
    /** 堡垒遗迹锚点 y=33（26.2 数据）；模板箱子相对锚 +1~+29，装配后实测约 34~62，取宽松区间。 */
    private static final int BASTION_MIN_Y = 20;
    private static final int BASTION_MAX_Y = 85;

    private StructureGuesser() {
    }

    /** 刷怪笼：客户端能拿到笼内生物数据，直接显示生物种类（返回可本地化的组件）。 */
    public static Component guessSpawnerLabel(ClientLevel level, BlockPos pos) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SpawnerBlockEntity spawner) {
                Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, pos);
                if (display != null) {
                    return Component.translatable("message.treasurehunter.spawner_label",
                            display.getType().getDescription());
                }
            }
        } catch (Throwable ignored) {
            // 读取失败时退回默认名称
        }
        return Component.translatable("target.treasurehunter.spawner");
    }

    /** 箱子 / 木桶：按周边特征方块 + 群系 + 高度推断来源分类。 */
    public static ChestCategory guessContainerCategory(ClientLevel level, BlockPos pos) {
        Flags flags = scan(level, pos);
        int y = pos.getY();
        boolean inNether = level.dimension().equals(Level.NETHER);
        boolean inEnd = level.dimension().equals(Level.END);

        // 废弃传送门箱：黑曜石 + 哭泣的黑曜石（该组合在世界生成中唯一属于废弃传送门）；
        // 主世界可退一步用 黑曜石 + 下界岩 佐证；下界直接以黑曜石判定
        // （下界要塞/堡垒遗迹都不含黑曜石，黑曜石在下界只会来自废弃传送门）
        // 门限说明：废弃传送门变体覆盖几乎所有主世界/下界群系、地表与掩埋都有，故不加群系/高度门限
        if (flags.obsidian && (flags.cryingObsidian
                || (flags.netherrack && level.dimension().equals(Level.OVERWORLD))
                || level.dimension().equals(Level.NETHER))) {
            return ChestCategory.RUINED_PORTAL;
        }
        // 埋藏的宝藏箱：原版生成规律极强——区块局部 X/Z 都是 9、单个普通箱子、上方与四侧基本被遮盖；
        // 且只生成在海滩群系（26.2 数据：beach / snowy_beach）
        if (isBuriedTreasurePattern(level, pos, flags) && isBeachBiome(level, pos)) {
            return ChestCategory.TREASURE;
        }
        // 试炼密室箱：锚点 y -40~-20、模板箱子相对锚 +1~+9，恒在深层地下；
        // 群系范围=几乎整个主世界，没有过滤价值，只用高度门限
        if (flags.trial && y < UNDERGROUND_MAX_Y) {
            return ChestCategory.TRIAL;
        }
        // 刷怪房箱（俗称地牢箱）：刷怪笼 + 苔石（缺一不可）。
        // 门限说明：原版刷怪房是代码型特征，无群系/高度数据（任意群系的地下都有）
        if (flags.spawner && flags.mossy) {
            return ChestCategory.DUNGEON;
        }
        // 下界结构：维度 + 群系表 + "脚下方块"直接区分（NBT 实测：下界要塞箱 100% 踩下界砖、堡垒遗迹箱 100% 踩镶金黑石）；
        // 下界要塞当且仅当下界砖在正下方——不再用"周边有下界砖"兜底，那会把紧邻要塞的堡垒遗迹箱抢走
        // 26.2 数据：下界要塞群系=荒芜/灵魂沙谷/绯红/诡异 + 玄武岩三角洲；堡垒遗迹不含玄武岩三角洲、锚点 y=33
        BlockState below = level.getBlockState(pos.below());
        if (inNether && isNetherFortressBiome(level, pos) && isNetherBrick(below)) {
            return ChestCategory.FORTRESS;
        }
        if (inNether && isBastionBiome(level, pos) && y >= BASTION_MIN_Y && y <= BASTION_MAX_Y
                && (isBlackstoneFamily(below) || flags.blackstone)) {
            return ChestCategory.BASTION;
        }
        // 末地城（含末地船）：末地维度 + 紫珀/末地石砖（不再检测群系——紫珀只在末地城出现）
        if (flags.endCity && inEnd) {
            return ChestCategory.END_CITY;
        }
        // 远古城市：幽匿 或 深层板岩砖 + 锚点 y=-27 高度上限
        // （不强制深暗之域群系——部分古城箱子生成在群系边界之外）
        if ((flags.sculk || flags.deepslateTiles) && y < UNDERGROUND_MAX_Y) {
            return ChestCategory.ANCIENT_CITY;
        }
        // 村庄：干草捆 / 堆肥桶 / 钟；床+火把+土径（村庄房屋的生活痕迹组合，沙漠村庄无土径、走下面的火把兜底）；
        // 火把仅在排除矿井特征（铁轨、蛛网、刷怪笼）与深色橡木建筑（前哨站有火把）后作为证据。
        // 26.2 数据：村庄群系=平原/草甸/沙漠/热带草原/积雪平原/针叶林，全部为地表结构
        if ((flags.hay || flags.composter || flags.bell
                || (flags.path && flags.bed && flags.torch)
                || (flags.torch && !flags.rail && !flags.cobweb && !flags.spawner && !flags.darkOak))
                && isVillageBiome(level, pos) && y > SURFACE_MIN_Y) {
            return ChestCategory.VILLAGE;
        }
        // 沙漠神殿：砂岩 + 陶瓦/TNT + 沙漠群系 + 非水下（排除含砂岩的水下废墟）；宝库在深坑里，用保守下限
        if (flags.sandstone && (flags.terracotta || flags.tnt) && !flags.water
                && biomeIn(level, pos, Biomes.DESERT) && y > SHALLOW_MIN_Y) {
            return ChestCategory.DESERT;
        }
        // 丛林神庙：苔石 +（绊线陷阱 / 雕纹石砖 / 发射器），必须在丛林系群系；宝库在地下，用保守下限
        if (flags.mossy && (flags.tripwire || flags.chiseledStoneBricks || flags.dispenser)
                && isJungleBiome(level, pos) && y > SHALLOW_MIN_Y) {
            return ChestCategory.JUNGLE;
        }
        // 雪屋：地下室特征（酿造台 + 橡木告示牌——NBT 实测地下室必含）或 雪块 + 梯子/陷阱门等木质暗道；
        // 不设群系门限（地下室在地表深处，群系可能与雪原不一致）；雪地村庄房屋无梯子/陷阱门，不会误捕
        if (((flags.brewingStand && flags.oakSign) || (flags.snow && (flags.ladder || flags.trapdoor)))
                && y > SHALLOW_MIN_Y) {
            return ChestCategory.IGLOO;
        }
        // 要塞：石砖族（图书馆之外、附近没有书架的走廊/传送门室箱同样覆盖）。
        // 门限说明：要塞由代码生成（环状分布），任意群系、任意深度；排除水下（水下石砖交给海底废墟）
        if (flags.stoneBricks && !flags.water) {
            return ChestCategory.STRONGHOLD;
        }
        // 林地府邸箱：深色橡木木板 + 深色森林/浅色花园群系 + 地表高度
        // （府邸箱子多数附近没有书架，书架不再是必要条件；用木板排除自然深色橡木树的原木）
        if (flags.darkOakPlanks && isMansionBiome(level, pos) && y > SURFACE_MIN_Y) {
            return ChestCategory.MANSION;
        }
        // 掠夺者前哨站箱：深色橡木 + 圆石/苔石族（哨塔顶层实测必有，生长变体为苔石）或羊毛帐篷 + 前哨站群系 + 地表高度；
        // 沉船只有木头、没有圆石，因此不会被误判到这里
        if (flags.darkOak && !flags.bookshelf && isOutpostBiome(level, pos)
                && (flags.cobble || flags.mossy || flags.wool) && y > SURFACE_MIN_Y) {
            return ChestCategory.OUTPOST;
        }
        // 废弃矿井箱：原版矿井的战利品全部在运输矿车（实体）里，没有普通箱方块——
        // 矿车箱由 MarkerScanner 的实体扫描直接归类，这里不设方块判定
        // 沉船：任意木制部件（wiki：仅由 原木/木板/楼梯/台阶/栅栏/门/活板门 组成，任意木种、通常一船两种木）
        // + 沉船群系（全部海洋 + 海滩）+ 无火把/铁轨/蛛网等人为痕迹（村庄房屋、玩家建筑会被排除）
        if (flags.wood && isShipwreckBiome(level, pos) && !flags.torch && !flags.rail && !flags.cobweb
                && (flags.water || isBeachBiome(level, pos))) {
            return ChestCategory.SHIPWRECK;
        }
        // 海底废墟：水下 + 海洋群系 + 海平面以下 + 冷海石砖族（石砖/苔石砖/裂纹石砖）
        // 或暖海砂岩族（砂岩/切制砂岩/雕纹砂岩）——wiki：冷/暖变体都有战利品箱，两者组成互不重叠
        if (flags.water && isOceanRuinBiome(level, pos)
                && (flags.stoneBricks || flags.sandstone) && y < SEA_LEVEL_Y) {
            return ChestCategory.OCEAN_RUINS;
        }
        return ChestCategory.OTHER;
    }

    /**
     * 群系门限：任一命中即通过。
     *
     * <p>
     * 下方所有群系表均直读 26.2 的 {@code data/.../worldgen/structure/*.json} 与
     * {@code tags/worldgen/biome/has_structure/*.json}（含嵌套标签展开），并按模板实测校准过。
     */
    @SafeVarargs
    private static boolean biomeIn(ClientLevel level, BlockPos pos, ResourceKey<Biome>... biomes) {
        var biome = level.getBiome(pos);
        for (ResourceKey<Biome> key : biomes) {
            if (biome.is(key)) {
                return true;
            }
        }
        return false;
    }

    /** 海滩群系：埋藏宝藏与搁浅沉船的生成范围（beach / snowy_beach）。 */
    private static boolean isBeachBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.BEACH, Biomes.SNOWY_BEACH);
    }

    /** 府邸群系：深色森林（含浅色花园）。府邸只在这些群系生成，前哨站不在这里。 */
    private static boolean isMansionBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.DARK_FOREST, Biomes.PALE_GARDEN);
    }

    /** 丛林神庙群系：丛林 / 竹林（原版标签 #has_structure/jungle_temple 只有这两个）。 */
    private static boolean isJungleBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE);
    }

    /** 村庄群系：五种村庄的并集（平原+草甸 / 沙漠 / 热带草原 / 积雪平原 / 针叶林）。 */
    private static boolean isVillageBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.PLAINS, Biomes.MEADOW, Biomes.DESERT, Biomes.SAVANNA,
                Biomes.SNOWY_PLAINS, Biomes.TAIGA);
    }

    /** 前哨站群系（26.2 数据；含各山地变体，天然不含深色森林）。 */
    private static boolean isOutpostBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.DESERT, Biomes.PLAINS, Biomes.SAVANNA, Biomes.SNOWY_PLAINS,
                Biomes.TAIGA, Biomes.MEADOW, Biomes.FROZEN_PEAKS, Biomes.JAGGED_PEAKS,
                Biomes.STONY_PEAKS, Biomes.SNOWY_SLOPES, Biomes.CHERRY_GROVE, Biomes.GROVE);
    }

    /** 下界要塞群系：荒芜 / 灵魂沙谷 / 绯红森林 / 诡异森林 + 玄武岩三角洲。 */
    private static boolean isNetherFortressBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY,
                Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST, Biomes.BASALT_DELTAS);
    }

    /** 堡垒遗迹群系（26.2 数据不含玄武岩三角洲）。 */
    private static boolean isBastionBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY,
                Biomes.CRIMSON_FOREST, Biomes.WARPED_FOREST);
    }

    /** 沉船群系：全部海洋 + 海滩（水下与搁浅两个变体）。 */
    private static boolean isShipwreckBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.OCEAN, Biomes.DEEP_OCEAN, Biomes.COLD_OCEAN,
                Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN,
                Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.WARM_OCEAN,
                Biomes.BEACH, Biomes.SNOWY_BEACH);
    }

    /** 海底废墟群系：冷海 6 + 暖海 3（两个变体的并集）。 */
    private static boolean isOceanRuinBiome(ClientLevel level, BlockPos pos) {
        return biomeIn(level, pos, Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN,
                Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN,
                Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN);
    }

    /**
     * 埋藏宝藏判定（三条同时满足）：
     * <ol>
     * <li>箱子所在方块在区块内的局部坐标 X=9 且 Z=9（原版固定生成位置）；</li>
     * <li>单个普通箱子（陷阱箱排除），水平 6 格内没有第二个容器；</li>
     * <li>被遮盖：上方必顶住非空气/非水源，四个侧面至少 3 面非空气/非水源。</li>
     * </ol>
     */
    private static boolean isBuriedTreasurePattern(ClientLevel level, BlockPos pos, Flags flags) {
        if ((pos.getX() & 15) != 9 || (pos.getZ() & 15) != 9) {
            return false;
        }
        if (!level.getBlockState(pos).is(Blocks.CHEST) || flags.containersNear > 1) {
            return false;
        }
        if (!isCovered(level, pos.above())) {
            return false;
        }
        int coveredSides = 0;
        if (isCovered(level, pos.north())) {
            coveredSides++;
        }
        if (isCovered(level, pos.south())) {
            coveredSides++;
        }
        if (isCovered(level, pos.east())) {
            coveredSides++;
        }
        if (isCovered(level, pos.west())) {
            coveredSides++;
        }
        return coveredSides >= 3;
    }

    private static boolean isCovered(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.is(Blocks.WATER);
    }

    /**
     * 分类证据区域（±{@link #RADIUS} 格所覆盖的区块）是否已全部加载。
     *
     * <p>
     * 区块加载/传送时，目标所在区块可能先于旁边区块被扫描；此时围证方块读出来是空气，
     * 会把目标误分类。调用方应在未加载完整时推迟登记，等兜底扫描重试。
     */
    public static boolean isClassificationAreaLoaded(ClientLevel level, BlockPos pos) {
        int minX = (pos.getX() - RADIUS) >> 4;
        int maxX = (pos.getX() + RADIUS) >> 4;
        int minZ = (pos.getZ() - RADIUS) >> 4;
        int maxZ = (pos.getZ() + RADIUS) >> 4;
        var source = level.getChunkSource();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (source.getChunk(cx, cz, ChunkStatus.FULL, false) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isNetherBrick(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.NETHER_BRICKS || block == Blocks.CRACKED_NETHER_BRICKS
                || block == Blocks.CHISELED_NETHER_BRICKS || block == Blocks.RED_NETHER_BRICKS;
    }

    private static boolean isBlackstoneFamily(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.GILDED_BLACKSTONE || block == Blocks.BLACKSTONE
                || block == Blocks.POLISHED_BLACKSTONE || block == Blocks.POLISHED_BLACKSTONE_BRICKS;
    }

    private static Flags scan(ClientLevel level, BlockPos center) {
        Flags flags = new Flags();
        BlockPos min = center.offset(-RADIUS, -Y_RADIUS, -RADIUS);
        BlockPos max = center.offset(RADIUS, Y_RADIUS, RADIUS);
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(cursor);
            Block block = state.getBlock();
            if (block == Blocks.TRIAL_SPAWNER || block == Blocks.TUFF_BRICKS
                    || block == Blocks.CHISELED_TUFF_BRICKS) {
                flags.trial = true;
            } else if (block == Blocks.SPAWNER) {
                flags.spawner = true;
            } else if (block == Blocks.MOSSY_COBBLESTONE) {
                flags.mossy = true;
            } else if (block == Blocks.COBBLESTONE) {
                flags.cobble = true;
            } else if (isBlackstoneFamily(state)) {
                flags.blackstone = true;
            } else if (isNetherBrick(state)) {
                flags.netherBricks = true;
            } else if (block == Blocks.PURPUR_BLOCK || block == Blocks.PURPUR_PILLAR
                    || block == Blocks.END_STONE_BRICKS) {
                flags.endCity = true;
            } else if (block == Blocks.SCULK || block == Blocks.SCULK_SENSOR) {
                flags.sculk = true;
            } else if (block == Blocks.DEEPSLATE_TILES) {
                flags.deepslateTiles = true;
            } else if (state.is(BlockTags.BEDS)) {
                flags.bed = true;
            } else if (block == Blocks.DIRT_PATH) {
                flags.path = true;
            } else if (block == Blocks.HAY_BLOCK) {
                flags.hay = true;
            } else if (block == Blocks.COMPOSTER) {
                flags.composter = true;
            } else if (block == Blocks.BELL) {
                flags.bell = true;
            } else if (block == Blocks.TORCH || block == Blocks.WALL_TORCH) {
                flags.torch = true;
            } else if (block == Blocks.SANDSTONE || block == Blocks.CHISELED_SANDSTONE
                    || block == Blocks.CUT_SANDSTONE || block == Blocks.SMOOTH_SANDSTONE) {
                flags.sandstone = true;
            } else if (state.is(BlockTags.TERRACOTTA)) {
                flags.terracotta = true;
            } else if (block == Blocks.TNT) {
                flags.tnt = true;
            } else if (block == Blocks.BOOKSHELF || block == Blocks.CHISELED_BOOKSHELF) {
                flags.bookshelf = true;
            } else if (block == Blocks.CHISELED_STONE_BRICKS) {
                flags.chiseledStoneBricks = true;
                flags.stoneBricks = true;
            } else if (block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS
                    || block == Blocks.CRACKED_STONE_BRICKS) {
                flags.stoneBricks = true;
            } else if (block == Blocks.DARK_OAK_PLANKS) {
                flags.darkOak = true;
                flags.darkOakPlanks = true;
                flags.wood = true;
            } else if (block == Blocks.DARK_OAK_LOG) {
                flags.darkOak = true;
                flags.wood = true;
            } else if (block == Blocks.DARK_OAK_FENCE) {
                flags.darkOakFence = true;
                flags.wood = true;
            } else if (state.is(BlockTags.WOOL)) {
                flags.wool = true;
            } else if (state.is(BlockTags.RAILS)) {
                flags.rail = true;
            } else if (block == Blocks.COBWEB) {
                flags.cobweb = true;
            } else if (block == Blocks.TRIPWIRE || block == Blocks.TRIPWIRE_HOOK) {
                flags.tripwire = true;
            } else if (block == Blocks.DISPENSER) {
                // 丛林神庙的箭矢发射器（wiki：构成方块含发射器）
                flags.dispenser = true;
            } else if (block == Blocks.OBSIDIAN) {
                flags.obsidian = true;
            } else if (block == Blocks.CRYING_OBSIDIAN) {
                flags.cryingObsidian = true;
            } else if (block == Blocks.NETHERRACK) {
                flags.netherrack = true;
            } else if (block == Blocks.WATER) {
                flags.water = true;
            } else if (block == Blocks.SNOW_BLOCK || block == Blocks.ICE
                    || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) {
                flags.snow = true;
            } else if (block == Blocks.LADDER) {
                flags.ladder = true;
            } else if (block == Blocks.BREWING_STAND) {
                flags.brewingStand = true;
            } else if (block == Blocks.OAK_SIGN || block == Blocks.OAK_WALL_SIGN) {
                flags.oakSign = true;
            } else if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS)
                    || state.is(BlockTags.WOODEN_STAIRS) || state.is(BlockTags.WOODEN_SLABS)
                    || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_DOORS)
                    || state.is(BlockTags.WOODEN_TRAPDOORS)) {
                // 沉船的全部结构件（wiki：仅由 原木/木板/楼梯/台阶/栅栏/门/活板门 组成，任意木种）
                flags.wood = true;
                if (state.is(BlockTags.WOODEN_TRAPDOORS)) {
                    flags.trapdoor = true; // 雪屋暗道/木结构证据
                }
            } else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL) {
                flags.containers++;
                if (Math.abs(cursor.getX() - center.getX()) <= TREASURE_RANGE
                        && Math.abs(cursor.getZ() - center.getZ()) <= TREASURE_RANGE) {
                    flags.containersNear++;
                }
            }
        }
        return flags;
    }

    private static final class Flags {
        boolean trial;
        boolean spawner;
        boolean mossy;
        boolean cobble;
        boolean blackstone;
        boolean netherBricks;
        boolean endCity;
        boolean sculk;
        boolean deepslateTiles;
        boolean bed;
        boolean path;
        boolean hay;
        boolean composter;
        boolean bell;
        boolean torch;
        boolean sandstone;
        boolean terracotta;
        boolean tnt;
        boolean bookshelf;
        boolean stoneBricks;
        boolean chiseledStoneBricks;
        boolean darkOak;
        boolean darkOakPlanks;
        boolean darkOakFence;
        boolean wool;
        boolean rail;
        boolean cobweb;
        boolean tripwire;
        boolean obsidian;
        boolean cryingObsidian;
        boolean netherrack;
        boolean water;
        boolean snow;
        boolean ladder;
        boolean trapdoor;
        boolean brewingStand;
        boolean oakSign;
        boolean wood;
        boolean dispenser;
        int containers;
        int containersNear;
    }
}
