package com.treasurehunter.scan;

/**
 * 容器（箱子 / 木桶）的来源分类：用于路径点标签、颜色、简称与"逐类扫描开关"。
 */
public enum ChestCategory {
    TREASURE("宝藏箱", "宝", "GOLD"),
    TRIAL("试炼箱", "试", "AQUA"),
    MINESHAFT("矿井箱", "矿", "DARK_GRAY"),
    DUNGEON("地牢箱", "牢", "DARK_GREEN"),
    BASTION("猪灵箱", "猪", "PINK"),
    FORTRESS("堡垒箱", "堡", "DARK_RED"),
    END_CITY("末地城箱", "末", "PURPLE"),
    ANCIENT_CITY("远古城市箱", "古", "DARK_AQUA"),
    VILLAGE("村庄箱", "村", "GREEN"),
    DESERT("沙漠神殿箱", "沙", "YELLOW"),
    JUNGLE("丛林神庙箱", "林", "LIME"),
    STRONGHOLD("要塞箱", "要", "LIGHT_BLUE"),
    OUTPOST("前哨站箱", "哨", "BROWN"),
    MANSION("府邸箱", "邸", "DARK_PURPLE"),
    SHIPWRECK("沉船箱", "船", "DARK_BLUE"),
    IGLOO("雪屋箱", "雪", "WHITE"),
    OCEAN_RUINS("海底废墟箱", "海", "BLUE"),
    RUINED_PORTAL("传送门遗迹箱", "门", "MAGENTA"),
    OTHER("其他箱", "箱", "GRAY");

    private final String displayName;
    private final String initial;
    private final String colorEnumName;

    ChestCategory(String displayName, String initial, String colorEnumName) {
        this.displayName = displayName;
        this.initial = initial;
        this.colorEnumName = colorEnumName;
    }

    public String displayName() {
        return displayName;
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
