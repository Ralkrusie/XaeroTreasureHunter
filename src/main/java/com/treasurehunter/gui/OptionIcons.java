package com.treasurehunter.gui;

import com.treasurehunter.TargetType;
import com.treasurehunter.scan.ChestCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 设置界面用的代表图标（原版物品纹理，只读复用）。
 */
public final class OptionIcons {
    private static final ItemStack TARGET_SPAWNER = new ItemStack(Items.SPAWNER);
    private static final ItemStack TARGET_CHEST = new ItemStack(Items.CHEST);
    private static final ItemStack TARGET_BARREL = new ItemStack(Items.BARREL);
    private static final ItemStack TARGET_BELL = new ItemStack(Items.BELL);

    private static final ItemStack OPTION_NOTIFY = new ItemStack(Items.NAME_TAG);
    private static final ItemStack OPTION_SINGLEPLAYER = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack OPTION_SMART = new ItemStack(Items.SPYGLASS);
    private static final ItemStack OPTION_FILTER = new ItemStack(Items.HOPPER);
    private static final ItemStack OPTION_MERGE = new ItemStack(Items.BUNDLE);

    private static final ItemStack CAT_TREASURE = new ItemStack(Items.FILLED_MAP);
    private static final ItemStack CAT_TRIAL = new ItemStack(Items.TRIAL_KEY);
    private static final ItemStack CAT_MINESHAFT = new ItemStack(Items.RAIL);
    private static final ItemStack CAT_DUNGEON = new ItemStack(Items.SPAWNER);
    private static final ItemStack CAT_BASTION = new ItemStack(Items.GILDED_BLACKSTONE);
    private static final ItemStack CAT_FORTRESS = new ItemStack(Items.NETHER_BRICK);
    private static final ItemStack CAT_END_CITY = new ItemStack(Items.PURPUR_BLOCK);
    private static final ItemStack CAT_ANCIENT_CITY = new ItemStack(Items.SCULK_SENSOR);
    private static final ItemStack CAT_VILLAGE = new ItemStack(Items.EMERALD);
    private static final ItemStack CAT_DESERT = new ItemStack(Items.TNT);
    private static final ItemStack CAT_JUNGLE = new ItemStack(Items.MOSSY_COBBLESTONE);
    private static final ItemStack CAT_STRONGHOLD = new ItemStack(Items.ENDER_EYE);
    private static final ItemStack CAT_OUTPOST = new ItemStack(Items.CROSSBOW);
    private static final ItemStack CAT_MANSION = new ItemStack(Items.TOTEM_OF_UNDYING);
    private static final ItemStack CAT_SHIPWRECK = new ItemStack(Items.OAK_BOAT);
    private static final ItemStack CAT_IGLOO = new ItemStack(Items.SNOWBALL);
    private static final ItemStack CAT_OCEAN_RUINS = new ItemStack(Items.BRUSH);
    private static final ItemStack CAT_RUINED_PORTAL = new ItemStack(Items.CRYING_OBSIDIAN);
    private static final ItemStack CAT_OTHER = new ItemStack(Items.CHEST);

    private OptionIcons() {
    }

    public static ItemStack targetIcon(TargetType type) {
        return switch (type) {
            case SPAWNER -> TARGET_SPAWNER;
            case CHEST -> TARGET_CHEST;
            case BARREL -> TARGET_BARREL;
            case BELL -> TARGET_BELL;
        };
    }

    public static ItemStack notifyIcon() {
        return OPTION_NOTIFY;
    }

    public static ItemStack singleplayerIcon() {
        return OPTION_SINGLEPLAYER;
    }

    public static ItemStack smartIcon() {
        return OPTION_SMART;
    }

    public static ItemStack filterIcon() {
        return OPTION_FILTER;
    }

    public static ItemStack mergeIcon() {
        return OPTION_MERGE;
    }

    public static ItemStack categoryIcon(ChestCategory category) {
        return switch (category) {
            case TREASURE -> CAT_TREASURE;
            case TRIAL -> CAT_TRIAL;
            case MINESHAFT -> CAT_MINESHAFT;
            case DUNGEON -> CAT_DUNGEON;
            case BASTION -> CAT_BASTION;
            case FORTRESS -> CAT_FORTRESS;
            case END_CITY -> CAT_END_CITY;
            case ANCIENT_CITY -> CAT_ANCIENT_CITY;
            case VILLAGE -> CAT_VILLAGE;
            case DESERT -> CAT_DESERT;
            case JUNGLE -> CAT_JUNGLE;
            case STRONGHOLD -> CAT_STRONGHOLD;
            case OUTPOST -> CAT_OUTPOST;
            case MANSION -> CAT_MANSION;
            case SHIPWRECK -> CAT_SHIPWRECK;
            case IGLOO -> CAT_IGLOO;
            case OCEAN_RUINS -> CAT_OCEAN_RUINS;
            case RUINED_PORTAL -> CAT_RUINED_PORTAL;
            case OTHER -> CAT_OTHER;
        };
    }
}
