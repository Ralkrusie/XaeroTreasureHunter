package com.treasurehunter.scan;

import net.minecraft.network.chat.Component;

/**
 * 容器（箱子 / 木桶 / 运输矿车）的来源分类：用于路径点标签、颜色、简称与"逐类扫描开关"。
 *
 * <p>
 * 显示名走语言文件（zh_cn / en_us 双语），中文命名与 Minecraft Wiki 的结构名对齐
 * （如 埋藏的宝藏 / 刷怪房 / 废弃矿井 / 堡垒遗迹 / 下界要塞 / 废弃传送门）。
 */
public enum ChestCategory {
    TREASURE("treasure", "GOLD"),
    TRIAL("trial", "AQUA"),
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
    OTHER("other", "GRAY");

    private final String id;
    private final String colorEnumName;

    ChestCategory(String id, String colorEnumName) {
        this.id = id;
        this.colorEnumName = colorEnumName;
    }

    /** 语言键（zh_cn / en_us 两套翻译）。 */
    public String translationKey() {
        return "category.treasurehunter." + id;
    }

    /** 本地化的分类名（如「废弃矿井箱」/"Mineshaft Chest"）。 */
    public Component label() {
        return Component.translatable(translationKey());
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
