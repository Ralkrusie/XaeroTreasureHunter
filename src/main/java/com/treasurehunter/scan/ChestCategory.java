package com.treasurehunter.scan;

import net.minecraft.network.chat.Component;

/**
 * 容器来源分类：箱子/矿车按结构分类，范围外真实表使用单人专用的非原版战利品表容器分类。
 *
 * <p>
 * 原版已知表木桶保留类型名；范围外表木桶也参与 OTHER_LOOT 开关，不贴结构箱名。
 *
 * <p>
 * 显示名走语言文件（完整名用于路径点，筛选短名仅用于设置），中文命名与 Minecraft Wiki 的结构名对齐
 * （如 埋藏的宝藏 / 刷怪房 / 废弃矿井 / 堡垒遗迹 / 下界要塞 / 废弃传送门）。
 */
public enum ChestCategory {
    TREASURE("treasure", "GOLD"),
    TRIAL("trial", "LIGHT_BLUE"),
    MINESHAFT("mineshaft", "DARK_GRAY"),
    DUNGEON("dungeon", "DARK_GREEN"),
    BASTION("bastion", "PINK"),
    FORTRESS("fortress", "DARK_RED"),
    END_CITY("end_city", "PURPLE"),
    ANCIENT_CITY("ancient_city", "DARK_AQUA"),
    VILLAGE("village", "GREEN"),
    DESERT("desert", "YELLOW"),
    JUNGLE("jungle", "LIME"),
    STRONGHOLD("stronghold", "LIGHT_BLUE"),
    OUTPOST("outpost", "BROWN"),
    MANSION("mansion", "DARK_PURPLE"),
    SHIPWRECK("shipwreck", "DARK_BLUE"),
    IGLOO("igloo", "WHITE"),
    OCEAN_RUINS("ocean_ruins", "BLUE"),
    RUINED_PORTAL("ruined_portal", "MAGENTA"),
    OTHER_LOOT("other_loot", "MAGENTA"),
    OTHER("other", "GRAY");

    private final String id;
    private final String colorEnumName;

    ChestCategory(String id, String colorEnumName) {
        this.id = id;
        this.colorEnumName = colorEnumName;
    }

    /** 路径点完整名称的语言键。 */
    public String translationKey() {
        return "category.treasurehunter." + id;
    }

    /** 本地化的完整分类名（路径点与提示使用）。 */
    public Component label() {
        return Component.translatable(translationKey());
    }

    /** 筛选按钮专用短名，不改变路径点和提示的完整分类名。 */
    public Component filterLabel() {
        return Component.translatable("filter.treasurehunter." + id);
    }

    /** 地图上显示的简称（随语言本地化：中文单字 / 英文双字母缩写）。 */
    public String initial() {
        return Component.translatable("initial.treasurehunter." + id).getString();
    }

    /** Xaero {@code WaypointColor} 枚举常量名。 */
    public String colorEnumName() {
        return colorEnumName;
    }
}
