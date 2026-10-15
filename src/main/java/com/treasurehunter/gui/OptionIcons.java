package com.treasurehunter.gui;

import com.treasurehunter.TargetType;
import com.treasurehunter.scan.ChestCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 设置页图标；26.3 的物品组件必须从当前连接的注册表取得，不能静态创建物品栈。 */
public final class OptionIcons {
    private OptionIcons() { }

    public static ItemStack targetIcon(TargetType type) {
        return icon(switch (type) {
            case SPAWNER -> Items.SPAWNER;
            case CHEST -> Items.CHEST;
            case BARREL -> Items.BARREL;
            case BELL -> Items.BELL;
            case VAULT -> Items.VAULT;
            case OMINOUS_VAULT -> Items.OMINOUS_TRIAL_KEY;
        });
    }

    public static ItemStack categoryIcon(ChestCategory category) {
        return icon(switch (category) {
            case TREASURE -> Items.FILLED_MAP;
            case TRIAL -> Items.TRIAL_KEY;
            case MINESHAFT -> Items.RAIL;
            case DUNGEON -> Items.SPAWNER;
            case BASTION -> Items.GILDED_BLACKSTONE;
            case FORTRESS -> Items.NETHER_BRICK;
            case END_CITY -> Items.PURPUR_BLOCK;
            case ANCIENT_CITY -> Items.SCULK_SENSOR;
            case VILLAGE -> Items.EMERALD;
            case DESERT -> Items.TNT;
            case JUNGLE -> Items.MOSSY_COBBLESTONE;
            case STRONGHOLD -> Items.ENDER_EYE;
            case OUTPOST -> Items.CROSSBOW;
            case MANSION -> Items.TOTEM_OF_UNDYING;
            case SHIPWRECK -> Items.OAK_BOAT;
            case IGLOO -> Items.SNOWBALL;
            case OCEAN_RUINS -> Items.BRUSH;
            case RUINED_PORTAL -> Items.CRYING_OBSIDIAN;
            case OTHER_LOOT -> Items.PAPER;
            case OTHER -> Items.CHEST;
        });
    }

    private static ItemStack icon(Item item) {
        var client = Minecraft.getInstance();
        var connection = client == null ? null : client.getConnection();
        return connection == null ? ItemStack.EMPTY : icon(item, connection.registryAccess());
    }

    /** 缺少物品或组件时只省略图标，仍完整创建设置按钮；主菜单没有同步注册表。 */
    static ItemStack icon(Item item, RegistryAccess registries) {
        var registry = registries.lookup(Registries.ITEM).orElse(null);
        if (registry == null) return ItemStack.EMPTY;
        var key = BuiltInRegistries.ITEM.getResourceKey(item).orElse(null);
        var holder = key == null ? null : registry.get(key).orElse(null);
        if (holder == null || !holder.areComponentsBound()) return ItemStack.EMPTY;
        return new ItemStack(holder);
    }
}
