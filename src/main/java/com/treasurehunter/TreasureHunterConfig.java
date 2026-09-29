package com.treasurehunter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.treasurehunter.scan.ChestCategory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 简单 JSON 配置（config/treasurehunter.json）。
 */
public final class TreasureHunterConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 兜底扫描间隔（tick）：检查已加载区域里"尚未扫描过"的区块；常规扫描由区块加载事件即时触发。 */
    public int sweepIntervalTicks = 40;
    /** 最多保留的标记数。 */
    public int maxMarkers = 400;
    /** 是否只在单人世界工作（多人服务器上这类扫描通常被视为作弊）。 */
    public boolean singleplayerOnly = true;
    /** 发现新标记时是否发送聊天提示。 */
    public boolean notifyOnNew = true;
    /** 是否把标记登记为 Xaero 路径点。 */
    public boolean waypointsEnabled = true;
    /** 来源智能识别：容器按周边特征标注来源（地牢箱/村庄箱…），刷怪笼标注生物种类。 */
    public boolean smartLabels = true;
    /** 邻近标记合并：半径 5 格内已有同类型同分类标记时不再重复标记；关闭则每个匹配都标记。 */
    public boolean mergeNearbyMarkers = true;
    /** 智能识别中被关闭扫描的容器分类（存 ChestCategory 名称）。 */
    public List<String> disabledChestCategories = new ArrayList<>();

    public boolean isChestCategoryDisabled(ChestCategory category) {
        return disabledChestCategories.contains(category.name());
    }

    public void setChestCategoryEnabled(ChestCategory category, boolean enabled) {
        if (enabled) {
            disabledChestCategories.remove(category.name());
        } else if (!disabledChestCategories.contains(category.name())) {
            disabledChestCategories.add(category.name());
        }
        save();
    }

    public void setAllChestCategoriesEnabled(boolean enabled) {
        disabledChestCategories.clear();
        if (!enabled) {
            for (ChestCategory category : ChestCategory.values()) {
                disabledChestCategories.add(category.name());
            }
        }
        save();
    }

    // 目标开关
    public boolean spawner = true;
    public boolean chest = true;
    public boolean barrel = true;
    public boolean bell = true;
    /** 宝库 / 不祥宝库（试炼密室；26.x 为同一方块，不祥为状态属性）。 */
    public boolean vault = true;
    public boolean ominousVault = true;

    private transient Path path;

    public static TreasureHunterConfig load(Path path) {
        TreasureHunterConfig cfg = null;
        try {
            if (Files.exists(path)) {
                cfg = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), TreasureHunterConfig.class);
            }
        } catch (IOException | JsonSyntaxException e) {
            TreasureHunterMod.LOGGER.warn("Xaero TreasureHunter: 配置读取失败，使用默认值", e);
        }
        if (cfg == null) {
            cfg = new TreasureHunterConfig();
        }
        cfg.path = path;
        cfg.save();
        return cfg;
    }

    public void save() {
        if (path == null) {
            return;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TreasureHunterMod.LOGGER.warn("Xaero TreasureHunter: 配置保存失败", e);
        }
    }

    public boolean isEnabled(TargetType type) {
        return switch (type) {
            case SPAWNER -> spawner;
            case CHEST -> chest;
            case BARREL -> barrel;
            case BELL -> bell;
            case VAULT -> vault;
            case OMINOUS_VAULT -> ominousVault;
        };
    }

    public List<TargetType> enabledTargets() {
        List<TargetType> list = new ArrayList<>();
        for (TargetType type : TargetType.values()) {
            if (isEnabled(type)) {
                list.add(type);
            }
        }
        return list;
    }
}
