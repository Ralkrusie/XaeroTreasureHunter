package com.treasurehunter.scan;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
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
    /** 分类扫描半径（水平）：地牢房间最大 9 格宽、刷怪笼居中、箱子贴墙（离中心 ≤4 格），8 格已很富余。 */
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
    /** 高度辅助：地下结构（试炼大厅锚点 -40~-20、远古城市锚点 -27）的判定上限，仅用于排除地表建筑。 */
    private static final int UNDERGROUND_MAX_Y = 20;
    /** 海平面高度：海底废墟等水下结构的判定上限。 */
    private static final int SEA_LEVEL_Y = 63;

    private StructureGuesser() {
    }

    /** 刷怪笼：客户端能拿到笼内生物数据，直接显示生物种类。 */
    public static String guessSpawnerLabel(ClientLevel level, BlockPos pos) {
        try {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SpawnerBlockEntity spawner) {
                Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, pos);
                if (display != null) {
                    return display.getType().getDescription().getString() + "笼";
                }
            }
        } catch (Throwable ignored) {
            // 读取失败时退回默认名称
        }
        return "刷怪笼";
    }

    /** 箱子 / 木桶：按周边特征方块 + 群系 + 高度推断来源分类。 */
    public static ChestCategory guessContainerCategory(ClientLevel level, BlockPos pos) {
        Flags flags = scan(level, pos);
        int y = pos.getY();

        // 传送门遗迹：黑曜石 + 哭泣的黑曜石（该组合在世界生成中唯一属于传送门遗迹）；
        // 主世界还可退一步用 黑曜石 + 下界岩 佐证（下界岩在野外只属于传送门遗迹）
        if (flags.obsidian && (flags.cryingObsidian
                || (flags.netherrack && level.dimension().equals(Level.OVERWORLD)))) {
            return ChestCategory.RUINED_PORTAL;
        }
        // 宝藏箱：原版生成规律极强——区块局部 X/Z 都是 9、单个普通箱子、上方与四侧基本被遮盖
        if (isBuriedTreasurePattern(level, pos, flags)) {
            return ChestCategory.TREASURE;
        }
        // 试炼箱：试炼大厅只生成在深层地下（锚点 y -40~-20），用高度上限过滤地面凝灰岩建筑
        if (flags.trial && y < UNDERGROUND_MAX_Y) {
            return ChestCategory.TRIAL;
        }
        // 地牢：刷怪笼 + 苔石（缺一不可，与矿井明显区分）
        if (flags.spawner && flags.mossy) {
            return ChestCategory.DUNGEON;
        }
        // 下界结构用"脚下方块"直接区分（实测：堡垒箱 100% 踩下界砖、堡垒遗迹箱 100% 踩镶金黑石）
        BlockState below = level.getBlockState(pos.below());
        if (isNetherBrick(below)) {
            return ChestCategory.FORTRESS;
        }
        if (isBlackstoneFamily(below)) {
            return ChestCategory.BASTION;
        }
        // 周边证据兜底：天然黑石斑块在下界很常见，而下界砖方块只属于堡垒（全部堡垒遗迹模板均无下界砖），
        // 因此堡垒判定优先于猪灵判定
        if (flags.netherBricks) {
            return ChestCategory.FORTRESS;
        }
        if (flags.blackstone) {
            return ChestCategory.BASTION;
        }
        if (flags.endCity) {
            return ChestCategory.END_CITY;
        }
        // 远古城市：锚点 y=-27（深暗只在地下），用高度上限过滤玩家地面幽匿装饰
        if (flags.sculk && y < UNDERGROUND_MAX_Y) {
            return ChestCategory.ANCIENT_CITY;
        }
        // 村庄：干草捆 / 堆肥桶 / 钟；火把仅在排除矿井特征（铁轨、蛛网、刷怪笼）与深色橡木建筑（前哨站有火把）后作为证据
        if (flags.hay || flags.composter || flags.bell
                || (flags.torch && !flags.rail && !flags.cobweb && !flags.spawner && !flags.darkOak)) {
            return ChestCategory.VILLAGE;
        }
        // 沙漠神殿：砂岩 + 陶瓦/TNT 且非水下（沙漠神殿从不在水下）；宝库在深坑里，用保守下限。
        // 排除水下：海底废墟同样是砂岩/石砖系材料，不排除会被这里抢走
        if (flags.sandstone && (flags.terracotta || flags.tnt) && y > SHALLOW_MIN_Y && !flags.water) {
            return ChestCategory.DESERT;
        }
        // 丛林神庙：苔石 +（绊线陷阱或雕纹石砖），必须在丛林系群系；宝库在地下，用保守下限
        if (flags.mossy && (flags.tripwire || flags.chiseledStoneBricks)
                && isJungleBiome(level, pos) && y > SHALLOW_MIN_Y) {
            return ChestCategory.JUNGLE;
        }
        if (flags.bookshelf && flags.stoneBricks) {
            return ChestCategory.STRONGHOLD;
        }
        // 府邸：书架 + 深色橡木 + 深色森林群系 + 地表高度
        if (flags.bookshelf && flags.darkOak && isMansionBiome(level, pos) && y > SURFACE_MIN_Y) {
            return ChestCategory.MANSION;
        }
        // 前哨站：深色橡木 + 圆石/苔石族（哨塔顶层实测必有，生长变体为苔石）或羊毛帐篷，
        // 不在深色森林（与府邸区分）；沉船只有木头、没有圆石，因此不会被误判到这里
        if (flags.darkOak && !flags.bookshelf && !isMansionBiome(level, pos)
                && (flags.cobble || flags.mossy || flags.wool) && y > SURFACE_MIN_Y) {
            return ChestCategory.OUTPOST;
        }
        // 矿井：刷怪笼+蛛网，或铁轨，或蛛网+木板（合并判定，只保留一处）
        if ((flags.spawner && flags.cobweb) || flags.rail || (flags.cobweb && flags.planks)) {
            return ChestCategory.MINESHAFT;
        }
        // 沉船：水下 + 任意木料（船体可能是深色橡木为主的混合木；只有木头、没有圆石）
        if (flags.water && (flags.planks || flags.darkOak)) {
            return ChestCategory.SHIPWRECK;
        }
        // 海底废墟：水下 + 石砖族（冷海）或砂岩族（暖海），且在海平面以下
        if (flags.water && (flags.stoneBricks || flags.sandstone) && y < SEA_LEVEL_Y) {
            return ChestCategory.OCEAN_RUINS;
        }
        // 雪屋：雪/冰 或 地下室特征（酿造台 + 橡木告示牌）；地下室在地表下，用保守下限
        if ((flags.snow || (flags.brewingStand && flags.oakSign)) && y > SHALLOW_MIN_Y) {
            return ChestCategory.IGLOO;
        }
        return ChestCategory.OTHER;
    }

    /** 府邸群系：深色森林（含浅色花园）。府邸只在这些群系生成，前哨站不在这里。 */
    private static boolean isMansionBiome(ClientLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN);
    }

    /** 丛林神庙群系：丛林 / 竹林（原版标签 #has_structure/jungle_temple 只有这两个）。 */
    private static boolean isJungleBiome(ClientLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return biome.is(Biomes.JUNGLE) || biome.is(Biomes.BAMBOO_JUNGLE);
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
            } else if (block == Blocks.DARK_OAK_PLANKS || block == Blocks.DARK_OAK_LOG) {
                flags.darkOak = true;
            } else if (block == Blocks.DARK_OAK_FENCE) {
                flags.darkOakFence = true;
            } else if (state.is(BlockTags.WOOL)) {
                flags.wool = true;
            } else if (state.is(BlockTags.RAILS)) {
                flags.rail = true;
            } else if (block == Blocks.COBWEB) {
                flags.cobweb = true;
            } else if (block == Blocks.TRIPWIRE || block == Blocks.TRIPWIRE_HOOK) {
                flags.tripwire = true;
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
            } else if (block == Blocks.BREWING_STAND) {
                flags.brewingStand = true;
            } else if (block == Blocks.OAK_SIGN || block == Blocks.OAK_WALL_SIGN) {
                flags.oakSign = true;
            } else if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS)) {
                flags.planks = true;
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
        boolean brewingStand;
        boolean oakSign;
        boolean planks;
        int containers;
        int containersNear;
    }
}
