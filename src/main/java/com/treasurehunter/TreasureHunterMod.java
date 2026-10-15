package com.treasurehunter;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import com.treasurehunter.gui.TreasureHunterConfigScreen;
import com.treasurehunter.scan.MarkerScanner;
import com.treasurehunter.scan.IgnoreRegionStore;
import com.treasurehunter.xaero.XaeroWaypointEditor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TreasureHunter 客户端入口。
 *
 * <p>
 * 客户端扫描附近已加载区块中的目标方块（刷怪笼 / 箱子 / 木桶 / 钟），
 * 并通过 Xaero 的公开内部 API（反射调用）把它们显示为临时路径点。
 */
public class TreasureHunterMod implements ClientModInitializer {
    public static final String MOD_ID = "treasurehunter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category
            .register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    private static TreasureHunterConfig config;
    private static MarkerScanner scanner;
    private static IgnoreRegionStore ignoreRegions;
    private static KeyMapping toggleKey;
    private static KeyMapping clearKey;
    private static KeyMapping settingsKey;

    public static TreasureHunterConfig config() {
        return config;
    }

    public static MarkerScanner scanner() {
        return scanner;
    }

    public static IgnoreRegionStore ignoreRegions() { return ignoreRegions; }

    /** 从旧模组 ID 的配置（trackit.json）迁移一次，避免改名后丢失设置。 */
    private static void migrateLegacyConfig(Path configPath) {
        try {
            Path legacy = configPath.resolveSibling("trackit.json");
            if (Files.notExists(configPath) && Files.exists(legacy)) {
                Files.copy(legacy, configPath);
                LOGGER.info("Xaero TreasureHunter: 已迁移旧配置 trackit.json");
            }
        } catch (IOException e) {
            LOGGER.warn("Xaero TreasureHunter: 旧配置迁移失败", e);
        }
    }

    @Override
    public void onInitializeClient() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        migrateLegacyConfig(configPath);
        config = TreasureHunterConfig.load(configPath);
        ignoreRegions = new IgnoreRegionStore(configPath.resolveSibling("treasurehunter-ignore"), LOGGER);
        scanner = new MarkerScanner(config, configPath.resolveSibling("treasurehunter-memory"), ignoreRegions);
        XaeroWaypointEditor.register();

        // 26.2 仍使用 GLFW keysym；与原版 Type.KEYSYM 配套注册，保持默认 G / H / J。
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.treasurehunter.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY));
        clearKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.treasurehunter.clear", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, KEY_CATEGORY));
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.treasurehunter.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                boolean enabled = scanner.toggle();
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal("Xaero TreasureHunter: ")
                            .append(Component.translatable(enabled
                                    ? "message.treasurehunter.toggle_on"
                                    : "message.treasurehunter.toggle_off")));
                }
            }
            while (clearKey.consumeClick()) {
                int removed = scanner.clearMarkers();
                scanner.disable();
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal("Xaero TreasureHunter: ")
                            .append(Component.translatable("message.treasurehunter.cleared", removed,
                                    toggleKey.getTranslatedKeyMessage())));
                }
            }
            while (settingsKey.consumeClick()) {
                client.setScreenAndShow(new TreasureHunterConfigScreen(config));
            }
            scanner.tick(client);
        });

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> scanner.onChunkLoaded(level, chunk));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> scanner.reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> scanner.saveMemory());
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level instanceof ClientLevel clientLevel) {
                scanner.onPlacementUsed(clientLevel, hand, hit);
                scanner.onBlockUsed(clientLevel, hit.getBlockPos(), hand);
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level instanceof ClientLevel clientLevel) {
                scanner.onEntityUsed(clientLevel, entity);
            }
            return InteractionResult.PASS;
        });

        LOGGER.info("Xaero TreasureHunter: 初始化完成，已启用目标：{}", config.enabledTargets());
    }
}
