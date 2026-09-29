package com.treasurehunter;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 可标记的目标方块类型。
 */
public enum TargetType {
    SPAWNER("刷怪笼", "S", "RED"),
    CHEST("箱子", "箱", "GOLD"),
    BARREL("木桶", "桶", "BROWN"),
    BELL("钟", "B", "AQUA");

    private final String displayName;
    private final String initials;
    private final String colorEnumName;

    TargetType(String displayName, String initials, String colorEnumName) {
        this.displayName = displayName;
        this.initials = initials;
        this.colorEnumName = colorEnumName;
    }

    public String displayName() {
        return displayName;
    }

    /** 地图上显示的简称：容器用单个汉字，其它类型用单个字母。 */
    public String initials() {
        return initials;
    }

    /** Xaero {@code WaypointColor} 枚举常量名。 */
    public String colorEnumName() {
        return colorEnumName;
    }

    /** 判断方块是否属于某个目标类型；不是目标则返回 null。 */
    public static TargetType match(BlockState state) {
        if (state.is(Blocks.SPAWNER)) {
            return SPAWNER;
        }
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            return CHEST;
        }
        if (state.is(Blocks.BARREL)) {
            return BARREL;
        }
        if (state.is(Blocks.BELL)) {
            return BELL;
        }
        return null;
    }
}
