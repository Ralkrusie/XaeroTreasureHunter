package com.treasurehunter.scan;

import com.treasurehunter.TargetType;
import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.xaero.XaeroBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 扫描客户端已加载区块中的目标方块，并登记为标记 / Xaero 路径点。
 *
 * <p>
 * 只扫描"新加载 / 尚未扫过"的区块：区块加载事件即时入队，另有每 sweepIntervalTicks 一次的
 * 兜底检查（覆盖整个客户端已加载区域，只处理未扫过的区块）。每 tick 处理有 3ms 预算，
 * 不阻塞渲染线程。
 */
public final class MarkerScanner {
    /** 每 tick 的扫描时间预算（纳秒）。 */
    private static final long SCAN_BUDGET_NANOS = 3_000_000L;
    private static final int VALIDATE_RANGE = 8;
    /** 去重半径（格）：附近已有同类型同分类标记时不再重复标记。 */
    private static final int DUPLICATE_RANGE = 5;

    private final TreasureHunterConfig config;
    private boolean enabled = true;

    private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Map<Long, Marker> markers = new HashMap<>();
    private final Set<Long> openedContainers = new HashSet<>();
    /** 已搜刮（打开过）的容器坐标，按维度记录：重扫时不再登记。 */
    private final Map<Object, Set<Long>> scavenged = new HashMap<>();
    /** 已扫描过的区块（区块坐标包），按会话记录；清空标记时会重置，允许重新发现。 */
    private final Set<Long> scannedChunks = new HashSet<>();

    private Object lastDimension;
    private int tickCounter;
    private int notifyCooldown;

    public MarkerScanner(TreasureHunterConfig config) {
        this.config = config;
    }

    /** 已登记的标记（key 为 {@link BlockPos#asLong()}）。 */
    public record Marker(long key, TargetType type, BlockPos pos, String name, ChestCategory category,
            Object waypoint, int entityId) {
    }

    public boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    /** 关闭扫描（用于"清空标记"时顺手停扫；不会自动重新登记）。 */
    public void disable() {
        enabled = false;
    }

    public void reset() {
        XaeroBridge.removeAll();
        queue.clear();
        queued.clear();
        markers.clear();
        openedContainers.clear();
        scavenged.clear();
        scannedChunks.clear();
        lastDimension = null;
        tickCounter = 0;
        notifyCooldown = 0;
    }

    public void onChunkLoaded(ClientLevel level, LevelChunk chunk) {
        // 新加载（含重新加载）的区块：记录并立即入队
        scannedChunks.add(chunk.getPos().pack());
        enqueue(chunk.getPos());
    }

    public void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            // 玩家实体短暂消失（如维度传送瞬间）时也要清理我们创建过的路径点
            if (!markers.isEmpty() || !queue.isEmpty() || XaeroBridge.hasOwnedWaypoints()) {
                reset();
            }
            return;
        }
        if (!enabled) {
            return;
        }
        if (config.singleplayerOnly && !client.hasSingleplayerServer()) {
            return;
        }
        if (notifyCooldown > 0) {
            notifyCooldown--;
        }

        Object dimension = level.dimension();
        if (!dimension.equals(lastDimension)) {
            // 用桥内注册表清掉全部自建路径点：即使维度切换时集合对不上，也不会留下无法清除的残留
            XaeroBridge.removeAll();
            markers.clear();
            queue.clear();
            queued.clear();
            scannedChunks.clear();
            lastDimension = dimension;
        }

        tickCounter++;
        if (tickCounter % Math.max(1, config.sweepIntervalTicks) == 0) {
            sweepLoadedArea(client);
            scanMinecarts(level);
        }
        processQueue(level);
        if (tickCounter % 10 == 0) {
            validateNearby(client, level, client.player.blockPosition());
        }
        trackOpenedContainers(client, level);
        trackOpenedVaults(client, level);
    }

    /**
     * 清除所有标记并返回清除数量。
     *
     * <p>
     * 区块扫描记录会重置（下一轮兜底扫描会重新发现区域内目标）；
     * "已搜刮"黑名单保留（已开过的容器不会重新出现）。
     */
    public int clearMarkers() {
        int count = markers.size();
        // 用桥内注册表清理：即使标记表因维度切换等出现过丢失，也不会留下无法删除的残留
        XaeroBridge.removeAll();
        markers.clear();
        queue.clear();
        queued.clear();
        openedContainers.clear();
        scannedChunks.clear();
        return count;
    }

    /** 移除某一类型的所有标记与路径点（在设置菜单里关闭该类型时调用）。 */
    public void removeMarkersOfType(TargetType type) {
        Iterator<Map.Entry<Long, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next().getValue();
            if (marker.type() == type) {
                if (marker.waypoint() != null) {
                    XaeroBridge.remove(marker.waypoint());
                }
                iterator.remove();
            }
        }
    }

    /** 移除某一分类的所有容器标记与路径点（在过滤菜单里关闭该分类时调用）。 */
    public void removeMarkersOfCategory(ChestCategory category) {
        Iterator<Map.Entry<Long, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next().getValue();
            if (marker.category() == category) {
                if (marker.waypoint() != null) {
                    XaeroBridge.remove(marker.waypoint());
                }
                iterator.remove();
            }
        }
    }

    /**
     * 开箱检测：玩家打开并关闭容器后，自动移除对应的标记与路径点。
     *
     * <p>
     * 箱子：客户端有开合度（{@code LidBlockEntity#getOpenNess}），打开时大于 0；木桶没有
     * 开合度可读，用"正在开容器 + 距离很近"近似。仅当玩家自己的容器界面打开时才会候选，
     * 减少其它玩家开箱造成的误删。
     */
    private void trackOpenedContainers(Minecraft client, ClientLevel level) {
        if (client.player == null) {
            openedContainers.clear();
            return;
        }
        Iterator<Long> iterator = openedContainers.iterator();
        while (iterator.hasNext()) {
            if (!markers.containsKey(iterator.next())) {
                iterator.remove();
            }
        }
        boolean containerOpen = client.player.containerMenu != client.player.inventoryMenu;
        if (containerOpen) {
            BlockPos playerPos = client.player.blockPosition();
            for (Marker marker : markers.values()) {
                if (marker.type() != TargetType.CHEST && marker.type() != TargetType.BARREL) {
                    continue;
                }
                double limit = marker.type() == TargetType.BARREL ? 9.0 : 36.0;
                if (marker.pos().distSqr(playerPos) > limit) {
                    continue;
                }
                if (marker.type() == TargetType.BARREL || marker.entityId() != 0
                        || (level.getBlockEntity(marker.pos()) instanceof LidBlockEntity lid
                                && lid.getOpenNess(0.0F) > 0.05F)) {
                    openedContainers.add(marker.key());
                }
            }
        } else if (!openedContainers.isEmpty()) {
            for (long key : openedContainers) {
                scavenged.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(key);
                Marker marker = markers.remove(key);
                if (marker == null) {
                    continue;
                }
                if (marker.waypoint() != null) {
                    XaeroBridge.remove(marker.waypoint());
                }
                if (config.notifyOnNew && client.player != null) {
                    client.player.sendOverlayMessage(Component.literal("Xaero TreasureHunter: ")
                            .append(Component.translatable("message.treasurehunter.scavenged", marker.name())));
                }
            }
            openedContainers.clear();
        }
    }

    /**
     * 宝库检测：玩家解锁宝库后，自动移除标记与路径点。
     *
     * <p>
     * 宝库的开启进度走方块状态（{@link VaultBlock#STATE}，客户端同步）：静默期为
     * INACTIVE（持钥匙玩家靠近时短暂 ACTIVE），一旦进入 UNLOCKING/EJECTING
     * 就说明玩家已开启，按"已搜刮"处理（重扫不再登记）。
     */
    private void trackOpenedVaults(Minecraft client, ClientLevel level) {
        if (client.player == null || markers.isEmpty()) {
            return;
        }
        BlockPos playerPos = client.player.blockPosition();
        Iterator<Map.Entry<Long, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next().getValue();
            if ((marker.type() != TargetType.VAULT && marker.type() != TargetType.OMINOUS_VAULT)
                    || marker.pos().distSqr(playerPos) > 64.0) {
                continue;
            }
            BlockState state = level.getBlockState(marker.pos());
            if (!(state.getBlock() instanceof VaultBlock)) {
                continue; // 方块已消失：交给 validateNearby 处理
            }
            VaultState vaultState = state.getValue(VaultBlock.STATE);
            if (vaultState == VaultState.INACTIVE || vaultState == VaultState.ACTIVE) {
                continue; // 尚未开启
            }
            if (marker.waypoint() != null) {
                XaeroBridge.remove(marker.waypoint());
            }
            iterator.remove();
            scavenged.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(marker.key());
            if (config.notifyOnNew) {
                client.player.sendOverlayMessage(Component.literal("Xaero TreasureHunter: ")
                        .append(Component.translatable("message.treasurehunter.vault_opened", marker.name())));
            }
        }
    }

    private void enqueue(ChunkPos pos) {
        if (queued.add(pos.pack())) {
            queue.add(pos);
        }
    }

    /**
     * 兜底扫描：把客户端已加载区域（视距 +2 区块）里"尚未扫描过"的区块入队。
     *
     * <p>
     * 区块加载事件已经会即时入队，这里负责补齐漏网的（例如清空标记后、扫描开关重新打开后）。
     * 因为只处理未扫过的区块，范围取满视距也不会产生重复开销。
     */
    private void sweepLoadedArea(Minecraft client) {
        BlockPos center = client.player.blockPosition();
        int centerX = center.getX() >> 4;
        int centerZ = center.getZ() >> 4;
        int halfSize = client.options.renderDistance().get() + 2;
        for (int dx = -halfSize; dx <= halfSize; dx++) {
            for (int dz = -halfSize; dz <= halfSize; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                if (scannedChunks.add(ChunkPos.pack(x, z))) {
                    enqueue(new ChunkPos(x, z));
                }
            }
        }
        // 定期清理远处的扫描记录，避免长时间游玩后集合无限增长
        // （这些区块已卸载，重新进入时会经由加载事件再扫）
        int pruneLimit = halfSize * 4 + 32;
        scannedChunks.removeIf(pack -> Math.abs(ChunkPos.getX(pack) - centerX) > pruneLimit
                || Math.abs(ChunkPos.getZ(pack) - centerZ) > pruneLimit);
    }

    private void processQueue(ClientLevel level) {
        long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            ChunkPos pos = queue.poll();
            queued.remove(pos.pack());
            LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }
            scanChunk(level, chunk);
        }
    }

    private void scanChunk(ClientLevel level, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            int baseY = (minSectionY + index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        TargetType type = TargetType.match(state);
                        if (type == null || !config.isEnabled(type)) {
                            continue;
                        }
                        register(level, type, new BlockPos(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
    }

    private void register(ClientLevel level, TargetType type, BlockPos pos) {
        long key = pos.asLong();
        if (markers.containsKey(key) || markers.size() >= config.maxMarkers) {
            return;
        }
        // 已搜刮的容器不再登记，否则周期性重扫会把它重新加回来
        if (isScavenged(level, key)) {
            return;
        }
        if (type == TargetType.CHEST && isSecondHalfOfDoubleChest(level, pos)) {
            return;
        }

        ChestCategory category = null;
        Component label = type.label();
        if (config.smartLabels) {
            if (type == TargetType.CHEST || type == TargetType.BARREL) {
                // 证据区域必须已完整加载：区块边缘/传送瞬载时先把本区块标记为"未扫"，
                // 由 2 秒兜底扫描稍后重试，避免把尚未加载的证据当成不存在而误分类
                if (!StructureGuesser.isClassificationAreaLoaded(level, pos)) {
                    scannedChunks.remove(ChunkPos.containing(pos).pack());
                    return;
                }
                category = StructureGuesser.guessContainerCategory(level, pos);
                if (category != ChestCategory.OTHER) {
                    label = category.label();
                }
                if (config.isChestCategoryDisabled(category)) {
                    return; // 该类被关闭：不扫描
                }
            } else if (type == TargetType.SPAWNER) {
                label = StructureGuesser.guessSpawnerLabel(level, pos);
            }
        }

        // 邻近标记合并（可在设置里关闭）：半径 5 格内已有相同标记（同类型同分类）时不再重复标记
        if (config.mergeNearbyMarkers && hasSameMarkerNearby(type, category, pos)) {
            return;
        }

        String name = "[" + label.getString() + "] " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        Object waypoint = null;
        if (config.waypointsEnabled) {
            waypoint = XaeroBridge.emit(name, initialsFor(type, category), pos.getX(), pos.getY(), pos.getZ(),
                    colorFor(type, category), true);
        }
        markers.put(key, new Marker(key, type, pos.immutable(), name, category, waypoint, 0));

        Minecraft client = Minecraft.getInstance();
        if (config.notifyOnNew && client.player != null && notifyCooldown <= 0) {
            notifyCooldown = 20;
            client.player.sendSystemMessage(Component.literal("Xaero TreasureHunter: ")
                    .append(Component.translatable("message.treasurehunter.found",
                            label, pos.getX(), pos.getY(), pos.getZ())));
        }
    }

    /** 运输矿车箱扫描：原版废弃矿井的战利品全部在矿车（实体）里，没有普通箱方块。 */
    private void scanMinecarts(ClientLevel level) {
        if (!config.isEnabled(TargetType.CHEST) || config.isChestCategoryDisabled(ChestCategory.MINESHAFT)) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof MinecartChest cart) {
                registerMinecart(level, cart);
            }
        }
    }

    private void registerMinecart(ClientLevel level, MinecartChest cart) {
        int entityId = cart.getId();
        long key = cart.blockPosition().asLong();
        if (markers.size() >= config.maxMarkers || isScavenged(level, key)) {
            return;
        }
        for (Marker marker : markers.values()) {
            if (marker.entityId() == entityId) {
                return; // 同一辆矿车只登记一次：矿车移动后位置键会变，靠实体 id 去重
            }
        }
        BlockPos pos = cart.blockPosition().immutable();
        if (config.mergeNearbyMarkers && hasSameMarkerNearby(TargetType.CHEST, ChestCategory.MINESHAFT, pos)) {
            return;
        }
        Component label = ChestCategory.MINESHAFT.label();
        String name = "[" + label.getString() + "] " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        Object waypoint = null;
        if (config.waypointsEnabled) {
            waypoint = XaeroBridge.emit(name, ChestCategory.MINESHAFT.initial(), pos.getX(), pos.getY(), pos.getZ(),
                    ChestCategory.MINESHAFT.colorEnumName(), true);
        }
        markers.put(key, new Marker(key, TargetType.CHEST, pos, name, ChestCategory.MINESHAFT, waypoint, entityId));

        Minecraft client = Minecraft.getInstance();
        if (config.notifyOnNew && client.player != null && notifyCooldown <= 0) {
            notifyCooldown = 20;
            client.player.sendSystemMessage(Component.literal("Xaero TreasureHunter: ")
                    .append(Component.translatable("message.treasurehunter.found",
                            label, pos.getX(), pos.getY(), pos.getZ())));
        }
    }

    /** 双联箱只登记一个：西/北侧是相同方块时由那一半登记。 */
    private static boolean isSecondHalfOfDoubleChest(ClientLevel level, BlockPos pos) {
        Block self = level.getBlockState(pos).getBlock();
        return level.getBlockState(pos.west()).is(self) || level.getBlockState(pos.north()).is(self);
    }

    /** 附近方块被破坏 / 变化时修正标记，并补登记之前失败的路径点。 */
    private void validateNearby(Minecraft client, ClientLevel level, BlockPos center) {
        Iterator<Map.Entry<Long, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next().getValue();
            if (marker.pos().distSqr(center) > (double) VALIDATE_RANGE * VALIDATE_RANGE) {
                continue;
            }
            boolean stillThere = marker.entityId() != 0
                    ? level.getEntity(marker.entityId()) instanceof MinecartChest
                    : TargetType.match(level.getBlockState(marker.pos())) == marker.type();
            if (!stillThere) {
                if (marker.waypoint() != null) {
                    XaeroBridge.remove(marker.waypoint());
                }
                iterator.remove();
                continue;
            }
            if (marker.waypoint() == null && config.waypointsEnabled) {
                Object waypoint = XaeroBridge.emit(marker.name(), initialsFor(marker.type(), marker.category()),
                        marker.pos().getX(), marker.pos().getY(), marker.pos().getZ(),
                        colorFor(marker.type(), marker.category()), true);
                if (waypoint != null) {
                    iterator.remove();
                    markers.put(marker.key(), new Marker(marker.key(), marker.type(), marker.pos(),
                            marker.name(), marker.category(), waypoint, marker.entityId()));
                    // 重新插入后迭代继续即可，无需特殊处理。
                    break;
                }
            }
        }
    }

    /** 路径点简称：有明确结构分类的容器用分类代表汉字，其余用类型默认。 */
    private static String initialsFor(TargetType type, ChestCategory category) {
        if (category != null && category != ChestCategory.OTHER) {
            return category.initial();
        }
        return type.initials();
    }

    /** 路径点颜色：有明确结构分类的容器用分类颜色，其余用类型默认。 */
    private static String colorFor(TargetType type, ChestCategory category) {
        if (category != null && category != ChestCategory.OTHER) {
            return category.colorEnumName();
        }
        return type.colorEnumName();
    }

    /** 半径 {@link #DUPLICATE_RANGE} 格内已有相同标记（同类型 + 同分类）时返回 true。 */
    private boolean hasSameMarkerNearby(TargetType type, ChestCategory category, BlockPos pos) {
        double limit = (double) DUPLICATE_RANGE * DUPLICATE_RANGE;
        for (Marker marker : markers.values()) {
            if (marker.type() == type && marker.category() == category
                    && marker.pos().distSqr(pos) <= limit) {
                return true;
            }
        }
        return false;
    }

    private boolean isScavenged(ClientLevel level, long key) {
        Set<Long> set = scavenged.get(level.dimension());
        return set != null && set.contains(key);
    }

}
