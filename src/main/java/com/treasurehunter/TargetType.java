package com.treasurehunter;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 可标记的目标方块类型（名称与简称走语言文件，中英双语）。
 *
 * <p>
 * 26.x 的宝库与不祥宝库是同一方块（{@code minecraft:vault}），
 * 由方块状态属性 {@link VaultBlock#OMINOUS} 区分。
 */
public enum TargetType {
    SPAWNER("spawner", "RED"),
    CHEST("chest", "GOLD"),
    BARREL("barrel", "BROWN"),
    BELL("bell", "AQUA"),
    VAULT("vault", "YELLOW"),
    OMINOUS_VAULT("ominous_vault", "DARK_RED");

    private final String id;
    private final String colorEnumName;

    TargetType(String id, String colorEnumName) {
        this.id = id;
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

    /** 地图上显示的简称（随语言本地化）。 */
    public String initials() {
        return Component.translatable("initial.treasurehunter." + id).getString();
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
        if (state.is(Blocks.VAULT)) {
            // 26.x：宝库与不祥宝库是同一方块，不祥由方块状态属性 OMINOUS 区分
            return state.getValue(VaultBlock.OMINOUS) ? OMINOUS_VAULT : VAULT;
        }
        return null;
    }
}
