package com.treasurehunter.scan;

/** 原版战利品表到现有来源分类的映射（表名已从 26.3 的 MC jar 逐项复核）。 */
public final class LootTableCategories {
    private LootTableCategories() {
    }

    /** 按真实表 ID 分类；奖励箱返回 null，保留普通箱名；范围外表归 OTHER_LOOT。 */
    public static ChestCategory classify(String tableId) {
        return switch (tableId) {
            // 26.3 原版容器表复核：奖励箱没有结构来源，不能误放进非原版表分类。
            case "minecraft:chests/spawn_bonus_chest" -> null;
            case "minecraft:chests/village/village_cartographer", "minecraft:chests/village/village_fisher",
                    "minecraft:chests/village/village_armorer", "minecraft:chests/village/village_fletcher",
                    "minecraft:chests/village/village_butcher", "minecraft:chests/village/village_plains_house",
                    "minecraft:chests/village/village_shepherd", "minecraft:chests/village/village_mason",
                    "minecraft:chests/village/village_weaponsmith", "minecraft:chests/village/village_snowy_house",
                    "minecraft:chests/village/village_toolsmith", "minecraft:chests/village/village_desert_house",
                    "minecraft:chests/village/village_tannery", "minecraft:chests/village/village_temple",
                    "minecraft:chests/village/village_savanna_house", "minecraft:chests/village/village_taiga_house" -> ChestCategory.VILLAGE;
            case "minecraft:chests/trial_chambers/intersection_barrel", "minecraft:chests/trial_chambers/corridor",
                    "minecraft:chests/trial_chambers/entrance", "minecraft:chests/trial_chambers/intersection",
                    "minecraft:chests/trial_chambers/supply", "minecraft:chests/trial_chambers/reward",
                    "minecraft:chests/trial_chambers/reward_common", "minecraft:chests/trial_chambers/reward_rare",
                    "minecraft:chests/trial_chambers/reward_unique", "minecraft:chests/trial_chambers/reward_ominous",
                    "minecraft:chests/trial_chambers/reward_ominous_common", "minecraft:chests/trial_chambers/reward_ominous_rare",
                    "minecraft:chests/trial_chambers/reward_ominous_unique" -> ChestCategory.TRIAL;
            case "minecraft:chests/buried_treasure" -> ChestCategory.TREASURE;
            case "minecraft:chests/abandoned_mineshaft" -> ChestCategory.MINESHAFT;
            case "minecraft:chests/simple_dungeon" -> ChestCategory.DUNGEON;
            case "minecraft:chests/bastion_bridge", "minecraft:chests/bastion_other",
                    "minecraft:chests/bastion_hoglin_stable", "minecraft:chests/bastion_treasure" -> ChestCategory.BASTION;
            case "minecraft:chests/nether_bridge" -> ChestCategory.FORTRESS;
            case "minecraft:chests/end_city_treasure" -> ChestCategory.END_CITY;
            case "minecraft:chests/ancient_city", "minecraft:chests/ancient_city_ice_box" -> ChestCategory.ANCIENT_CITY;
            case "minecraft:chests/desert_pyramid" -> ChestCategory.DESERT;
            case "minecraft:chests/jungle_temple", "minecraft:chests/jungle_temple_dispenser" -> ChestCategory.JUNGLE;
            case "minecraft:chests/stronghold_corridor", "minecraft:chests/stronghold_library",
                    "minecraft:chests/stronghold_crossing" -> ChestCategory.STRONGHOLD;
            case "minecraft:chests/pillager_outpost" -> ChestCategory.OUTPOST;
            case "minecraft:chests/woodland_mansion" -> ChestCategory.MANSION;
            case "minecraft:chests/shipwreck_supply", "minecraft:chests/shipwreck_map",
                    "minecraft:chests/shipwreck_treasure" -> ChestCategory.SHIPWRECK;
            case "minecraft:chests/igloo_chest" -> ChestCategory.IGLOO;
            case "minecraft:chests/underwater_ruin_big", "minecraft:chests/underwater_ruin_small" -> ChestCategory.OCEAN_RUINS;
            case "minecraft:chests/ruined_portal" -> ChestCategory.RUINED_PORTAL;
            case "minecraft:chests/abandoned_camp_common_chest",
                    "minecraft:chests/abandoned_camp_secret_chest" -> ChestCategory.ABANDONED_CAMP;
            default -> ChestCategory.OTHER_LOOT;
        };
    }
}
