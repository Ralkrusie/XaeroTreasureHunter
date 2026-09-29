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
    TREASURE("treasure", "宝", "GOLD"),
    TRIAL("trial", "试", "AQUA"),
    MINESHAFT("mineshaft", "矿", "DARK_GRAY"),
    DUNGEON("dungeon", "怪", "DARK_GREEN"),
    BASTION("bastion", "堡", "PINK"),
    FORTRESS("fortress", "界", "DARK_RED"),
    END_CITY("end_city", "末", "PURPLE"),
    ANCIENT_CITY("ancient_city", "古", "DARK_AQUA"),
    VILLAGE("village", "村", "GREEN"),
    DESERT("desert", "沙", "YELLOW"),
    JUNGLE("jungle", "林", "LIME"),
    STRONGHOLD("stronghold", "要", "LIGHT_BLUE"),
    OUTPOST("outpost", "哨", "BROWN"),
    MANSION("mansion", "邸", "DARK_PURPLE"),
    SHIPWRECK("shipwreck", "船", "DARK_BLUE"),
    IGLOO("igloo", "雪", "WHITE"),
    OCEAN_RUINS("ocean_ruins", "海", "BLUE"),
    RUINED_PORTAL("ruined_portal", "门", "MAGENTA"),
    OTHER("other", "箱", "GRAY");

    private final String id;
    private final String initial;
    private final String colorEnumName;

    ChestCategory(String id, String initial, String colorEnumName) {
        this.id = id;
        this.initial = initial;
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

    /** 地图上显示的单个代表汉字。 */
    public String initial() {
        return initial;
    }

    /** Xaero {@code WaypointColor} 枚举常量名。 */
    public String colorEnumName() {
        return colorEnumName;
    }
}
