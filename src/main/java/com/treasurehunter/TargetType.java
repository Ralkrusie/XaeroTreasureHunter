package com.treasurehunter;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 可标记的目标方块类型（名称走语言文件，中英双语）。
 */
public enum TargetType {
    SPAWNER("spawner", "S", "RED"),
    CHEST("chest", "箱", "GOLD"),
    BARREL("barrel", "桶", "BROWN"),
    BELL("bell", "B", "AQUA");

    private final String id;
    private final String initials;
    private final String colorEnumName;

    TargetType(String id, String initials, String colorEnumName) {
        this.id = id;
        this.initials = initials;
        this.colorEnumName = colorEnumName;
    }

    /** 语言键（zh_cn / en_us 两套翻译）。 */
    public String translationKey() {
        return "target.treasurehunter." + id;
    }

    /** 本地化的类型名（如「刷怪笼」/"Spawner"）。 */
    public Component label() {
        return Component.translatable(translationKey());
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
