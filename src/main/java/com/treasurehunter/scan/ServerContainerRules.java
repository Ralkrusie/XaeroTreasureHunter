package com.treasurehunter.scan;

/** 服务器容器过滤的保守规则；不能从客户端数据证明箱子由谁放置。 */
public final class ServerContainerRules {
    /** 本次保守启发式（非模板排他门限）：范围 ±4 水平/±3 垂直，至少 8 容器和 4 对堆叠。 */
    public static final int STORAGE_RADIUS = 4;
    public static final int STORAGE_Y_RADIUS = 3;
    private static final int STORAGE_MIN_CONTAINERS = 8;
    private static final int STORAGE_MIN_STACKED_PAIRS = 4;

    private ServerContainerRules() {
    }

    /** 白色墙旗在 26.3 全部 1511 个模板（含全部调色板）中仅属于前哨站；哨塔取景内有 4 面。 */
    public static boolean hasOutpostTowerEvidence(int whiteWallBanners, boolean darkOak, boolean cobble) {
        return whiteWallBanners >= 2 && darkOak && cobble;
    }

    /** 强结构证据优先保留；村庄火把和要塞石砖规则较弱，不能单独豁免仓库/自动化特征。 */
    public static boolean shouldHide(ChestCategory category, boolean attachedHopper, int containers, int stackedPairs) {
        boolean protectedStructure = category != ChestCategory.OTHER && category != ChestCategory.VILLAGE
                && category != ChestCategory.STRONGHOLD;
        return !protectedStructure && (attachedHopper
                || (containers >= STORAGE_MIN_CONTAINERS && stackedPairs >= STORAGE_MIN_STACKED_PAIRS));
    }

    /** 激进模式沿用保守仓库启发式门限（非模板校准）；弱证据或明确人工特征不获分类豁免。 */
    public static boolean shouldHideAggressively(boolean strongEvidence, boolean attachedHopper,
            int containers, int stackedPairs) {
        return !strongEvidence || attachedHopper
                || (containers >= STORAGE_MIN_CONTAINERS && stackedPairs >= STORAGE_MIN_STACKED_PAIRS);
    }
}
