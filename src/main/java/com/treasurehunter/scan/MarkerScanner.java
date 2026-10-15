package com.treasurehunter.scan;

import com.treasurehunter.TargetType;
import com.treasurehunter.TreasureHunterConfig;
import com.treasurehunter.TreasureHunterMod;
import com.treasurehunter.xaero.XaeroBridge;
import com.treasurehunter.xaero.XaeroWaypointEditor;
import com.treasurehunter.mixin.ClientLevelAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 扫描客户端已加载区块中的目标方块，并登记为标记 / Xaero 路径点。
 *
 * <p>
 * 只扫描"新加载 / 尚未扫过"的区块：区块加载事件即时入队，另有每 sweepIntervalTicks 一次的
 * 兜底检查（覆盖整个客户端已加载区域，只处理未扫过的区块）。每 tick 处理有 3ms 预算，
 * 预算在区块之间检查，单个区块仍可能超过预算。
 */
public final class MarkerScanner {
    /** 每 tick 的扫描时间预算（纳秒）。 */
    private static final long SCAN_BUDGET_NANOS = 3_000_000L;
    private static final int VALIDATE_RANGE = 8;
    /** 去重半径（格）：附近已有同类型同分类标记时不再重复标记。 */
    private static final int DUPLICATE_RANGE = 5;
    /** 等待实际容器界面/宝库动画确认交互的时间（tick），超时的失败点击不写记忆。 */
    private static final int INTERACTION_TIMEOUT_TICKS = 40;

    private final TreasureHunterConfig config;
    private final ScavengedMemory scavenged;
    private final ScavengedMemory placed;
    private final IgnoreRegionStore ignored;
    private final ScanMarkerMemory savedMarkers;
    private final Map<Long, XaeroBridge.Location> markerLocations = new HashMap<>();
    private final Map<Long, PendingPlacement> pendingPlacements = new HashMap<>();
    private final Map<ChunkPos, Integer> dirtyChunks = new HashMap<>();
    private long ignoreRevision;
    private final SingleplayerLootReader lootReader = new SingleplayerLootReader();
    private boolean enabled = true;

    private final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Map<Long, Marker> markers = new HashMap<>();
    private ContainerVisit pendingContainer;
    private OpenedContainer openedContainer;
    private PendingVault pendingVault;
    /** 已扫描过的区块（区块坐标包），按会话记录；清空标记时会重置，允许重新发现。 */
    private final Set<Long> scannedChunks = new HashSet<>();

    private Object lastDimension;
    private ClientLevel lastLevel;
    private int tickCounter;
    private int notifyCooldown;
    private String worldIdentity;

    public MarkerScanner(TreasureHunterConfig config, Path memoryDirectory) {
        this(config, memoryDirectory, new IgnoreRegionStore(memoryDirectory.resolveSibling("treasurehunter-ignore"), TreasureHunterMod.LOGGER));
    }

    public MarkerScanner(TreasureHunterConfig config, Path memoryDirectory, IgnoreRegionStore ignored) {
        this.config = config;
        this.scavenged = new ScavengedMemory(memoryDirectory, TreasureHunterMod.LOGGER);
        this.placed = new ScavengedMemory(memoryDirectory.resolveSibling("treasurehunter-placed"), TreasureHunterMod.LOGGER, "自己放置记录");
        this.ignored = ignored;
        this.savedMarkers = new ScanMarkerMemory(memoryDirectory.resolveSibling("treasurehunter-markers"), TreasureHunterMod.LOGGER);
    }

    private record PendingPlacement(String dimension, TargetType type, int sequence, int expiresAt) { }

    /** 点击只产生候选；真正收到新的容器菜单后才记为已打开。双联箱记录两半。 */
    private record ContainerVisit(String dimension, TargetType type, List<BlockPos> positions,
            UUID entityUuid, int entityId, int expiresAt, AbstractContainerMenu previousMenu) {
    }

    private record OpenedContainer(ContainerVisit visit, AbstractContainerMenu menu) {
    }

    private record PendingVault(String dimension, BlockPos pos, int expiresAt) {
    }

    /** 已登记的标记（key 为 {@link BlockPos#asLong()}）。 */
    public record Marker(long key, TargetType type, BlockPos pos, String name, ChestCategory category,
            Object waypoint, int entityId, UUID entityUuid) {
        public Marker(long key, TargetType type, BlockPos pos, String name, ChestCategory category, Object waypoint, int entityId) {
            this(key, type, pos, name, category, waypoint, entityId, null);
        }
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
        checkpointMarkers(true);
        savedMarkers.close();
        XaeroBridge.releaseOwnership();
        queue.clear();
        queued.clear();
        markers.clear();
        markerLocations.clear();
        pendingContainer = null;
        openedContainer = null;
        pendingVault = null;
        scavenged.close();
        placed.close();
        ignored.close();
        pendingPlacements.clear();
        dirtyChunks.clear();
        worldIdentity = null;
        lootReader.clear();
        scannedChunks.clear();
        lastDimension = null;
        lastLevel = null;
        tickCounter = 0;
        notifyCooldown = 0;
    }

    public void onChunkLoaded(ClientLevel level, LevelChunk chunk) {
        // 已完成的区块重新同步不能删除或重新登记已确认标记；未完成证据仍允许重试。
        if (scannedChunks.add(chunk.getPos().pack())) {
            lootReader.invalidateChunk(chunk.getPos());
            enqueue(chunk.getPos());
        }
    }

    public void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            // 玩家实体短暂消失时保存并卸载扫描缓存，不删除地图里的点。
            if (worldIdentity != null || !markers.isEmpty() || !queue.isEmpty() || XaeroBridge.hasOwnedWaypoints()) {
                reset();
            }
            return;
        }
        if (!ensureWorld(client)) {
            return;
        }
        if (notifyCooldown > 0) {
            notifyCooldown--;
        }

        Object dimension = level.dimension();
        if (level != lastLevel || !dimension.equals(lastDimension)) {
            // 切维度只保存当前状态和切换缓存，不删除其他维度的路径点。
            checkpointMarkers();
            markers.clear();
            markerLocations.clear();
            queue.clear();
            queued.clear();
            scannedChunks.clear();
            lootReader.clear();
            pendingPlacements.clear();
            dirtyChunks.clear();
            XaeroWaypointEditor.refreshLinks();
            // 同维度完整重载也丢弃交互候选，不能把上一地图的菜单/宝库动画写入新地图。
            pendingContainer = null;
            openedContainer = null;
            pendingVault = null;
            lastDimension = dimension;
            lastLevel = level;
            restoreMarkers(level);
        }

        tickCounter++;
        if (tickCounter % 10 == 0) {
            XaeroBridge.synchronizeOwnership();
            restoreMissingWaypoints();
        }
        XaeroWaypointEditor.tick();
        if (ignoreRevision != ignored.revision()) clearMarkers();
        pendingPlacements.entrySet().removeIf(entry -> entry.getValue().expiresAt() < tickCounter);
        // 记忆独立于扫描开关和单人限制：服务器上正常打开的容器也要持久保存。
        trackOpenedContainers(client);
        trackOpenedVaults(client, level);
        if (tickCounter % 100 == 0) {
            checkpointMarkers();
            scavenged.flush();
            placed.flush();
            ignored.flush();
        }
        if (!enabled || !client.hasSingleplayerServer() && !config.serverScanEnabled) {
            return;
        }
        processDirtyChunks(level);
        if (tickCounter % Math.max(1, config.sweepIntervalTicks) == 0) {
            sweepLoadedArea(client);
            scanMinecarts(level);
        }
        processQueue(level);
        lootReader.tick(client, level);
        if (tickCounter % 10 == 0) {
            validateNearby(client, level, client.player.blockPosition());
        }
    }

    /**
     * 清除所有标记并返回清除数量。
     *
     * <p>
     * 区块扫描记录会重置（下一轮兜底扫描会重新发现区域内目标）；
     * "已搜刮"黑名单保留（已开过的容器不会重新出现）；玩家 Restore 的永久点保留。
     */
    public int clearMarkers() {
        int count = markers.size();
        // 清理桥内的临时点与管理记录，永久点已交还玩家管理。
        XaeroBridge.removeAll();
        markers.clear();
        markerLocations.clear();
        savedMarkers.clear();
        queue.clear();
        queued.clear();
        scannedChunks.clear();
        lootReader.clear();
        ignoreRevision = ignored.revision();
        dirtyChunks.clear();
        return count;
    }

    /** 重置当前世界所有维度的已打开记忆，并让已加载区块重新扫描。 */
    public void clearScavengedMemory() {
        scavenged.clear();
        pendingContainer = null;
        openedContainer = null;
        pendingVault = null;
        clearMarkers();
    }

    /** 退出游戏时保存，不触碰服务器线程或世界状态。 */
    public void saveMemory() {
        checkpointMarkers(true);
        scavenged.flush();
        placed.flush();
        ignored.flush();
    }

    /** 设置重新启用目标或分类后，使已加载区块也能重新发现目标。 */
    public void requestRescan() {
        scannedChunks.clear();
    }

    /** 手动重置自己放置的容器记忆，独立于已打开记忆。 */
    public void clearPlacedMemory() {
        placed.clear();
        pendingPlacements.clear();
        clearMarkers();
    }

    /** 交互只建立放置候选；必须收到服务端方块状态或对应预测确认后才能登记。 */
    public void onPlacementUsed(ClientLevel level, InteractionHand hand, BlockHitResult hit) {
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != null || client.player == null || !ensureWorld(client)) return;
        var stack = client.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem item)) return;
        TargetType type = TargetType.match(item.getBlock().defaultBlockState());
        if (type != TargetType.CHEST && type != TargetType.BARREL) return;
        var context = new BlockPlaceContext(client.player, hand, stack, hit);
        if (!context.canPlace() || TargetType.match(level.getBlockState(context.getClickedPos())) != null) return;
        var prediction = ((ClientLevelAccessor) (Object) level).treasurehunter$getPredictionHandler();
        int sequence = prediction.currentSequence() + (prediction.isPredicting() ? 0 : 1);
        pendingPlacements.put(context.getClickedPos().asLong(),
                new PendingPlacement(dimensionId(level), type, sequence, tickCounter + INTERACTION_TIMEOUT_TICKS));
    }

    /** 原版预测确认已应用后检查实际状态；失败放置已被原版还原，不会误写记忆。 */
    public void onPredictionAcknowledged(ClientLevel level, int sequence) {
        var candidates = new java.util.ArrayList<>(pendingPlacements.entrySet());
        for (var entry : candidates) {
            var pending = entry.getValue();
            if (pending.sequence() > sequence || !pending.dimension().equals(dimensionId(level))) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            if (level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null) continue;
            var state = level.getBlockState(pos);
            onServerBlockUpdate(level, pos, state, state);
            pendingPlacements.remove(entry.getKey());
        }
    }

    /** 仅接收主线程上已应用的服务端增量更新；初次整区块同步不参与放置推断。 */
    public void onServerBlockUpdate(ClientLevel level, BlockPos pos, BlockState before, BlockState after) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != level || client.getSingleplayerServer() != null || worldIdentity == null) return;
        String dimension = dimensionId(level);
        TargetType type = TargetType.match(after);
        if (type != TargetType.CHEST) placed.removeBlock(dimension, "CHEST", pos.asLong());
        if (type != TargetType.BARREL) placed.removeBlock(dimension, "BARREL", pos.asLong());
        var pending = pendingPlacements.get(pos.asLong());
        boolean confirmed = pending != null && pending.expiresAt() >= tickCounter
                && pending.dimension().equals(dimension) && pending.type() == type;
        if (confirmed) {
            placed.addBlock(dimension, type.name(), pos.asLong());
            placed.flush();
            pendingPlacements.remove(pos.asLong());
        }
        boolean relevant = TargetType.match(before) != null || type != null
                || before.is(net.minecraft.world.level.block.Blocks.HOPPER) || after.is(net.minecraft.world.level.block.Blocks.HOPPER);
        boolean geometryChanged = before.getBlock() != after.getBlock()
                || (after.getBlock() instanceof ChestBlock && before != after)
                || (after.is(net.minecraft.world.level.block.Blocks.HOPPER)
                        && before.getValue(net.minecraft.world.level.block.HopperBlock.FACING)
                            != after.getValue(net.minecraft.world.level.block.HopperBlock.FACING));
        if (((!relevant || !geometryChanged) && !confirmed) || !enabled || !config.serverScanEnabled) return;
        // 本次增量复核：合并 6 tick 内的更新，只扫描取景半径覆盖的区块；不因红石通断反复扫描。
        for (int x = (pos.getX() - 8) >> 4; x <= (pos.getX() + 8) >> 4; x++) {
            for (int z = (pos.getZ() - 8) >> 4; z <= (pos.getZ() + 8) >> 4; z++) {
                dirtyChunks.putIfAbsent(new ChunkPos(x, z), tickCounter + 6);
            }
        }
    }

    private void processDirtyChunks(ClientLevel level) {
        var chunks = dirtyChunks.entrySet().iterator();
        while (chunks.hasNext()) {
            var entry = chunks.next();
            if (entry.getValue() > tickCounter) continue;
            ChunkPos chunk = entry.getKey();
            chunks.remove();
            if (level.getChunkSource().getChunk(chunk.x(), chunk.z(), ChunkStatus.FULL, false) == null) continue;
            scannedChunks.remove(chunk.pack());
            enqueue(chunk);
        }
    }

    private boolean isKnownPlacement(ClientLevel level, TargetType type, BlockPos pos) {
        if (type != TargetType.CHEST && type != TargetType.BARREL) return false;
        if (placed.containsBlock(dimensionId(level), type.name(), pos.asLong())) return true;
        var state = level.getBlockState(pos);
        return type == TargetType.CHEST && state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && placed.containsBlock(dimensionId(level), type.name(), ChestBlock.getConnectedBlockPos(pos, state).asLong());
    }

    /** 用存档目录或原始服务器地址区分世界；联机记录另按玩家 UUID 隔离。 */
    private boolean ensureWorld(Minecraft client) {
        String identity;
        var server = client.getSingleplayerServer();
        if (server != null) {
            identity = "singleplayer:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        } else if (client.getCurrentServer() != null) {
            var address = ServerAddress.parseString(client.getCurrentServer().ip.trim());
            identity = "server:" + address.getHost().toLowerCase(Locale.ROOT) + ":" + address.getPort()
                    + ":" + client.player.getUUID();
        } else {
            return false; // 世界/连接尚未准备好，不在未知身份下写记录。
        }
        if (!identity.equals(worldIdentity)) {
            reset();
            worldIdentity = identity;
            scavenged.open(identity);
            placed.open(identity);
            ignored.open(identity);
            savedMarkers.open(identity);
            XaeroWaypointEditor.refreshLinks();
            ignoreRevision = ignored.revision();
        }
        return true;
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

    /** Fabric 交互回调：记录玩家点击的具体容器，失败点击不会直接写入记忆。 */
    public void onBlockUsed(ClientLevel level, BlockPos pos, InteractionHand hand) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != level || client.player == null || !ensureWorld(client)) {
            return;
        }
        pendingContainer = null;
        pendingVault = null;
        BlockState state = level.getBlockState(pos);
        TargetType type = TargetType.match(state);
        if (type == TargetType.CHEST || type == TargetType.BARREL) {
            List<BlockPos> positions = List.of(pos.immutable());
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                positions = List.of(pos.immutable(), ChestBlock.getConnectedBlockPos(pos, state).immutable());
            }
            pendingContainer = new ContainerVisit(dimensionId(level), type, positions, null, 0,
                    tickCounter + INTERACTION_TIMEOUT_TICKS, client.player.containerMenu);
        } else if (type == TargetType.VAULT || type == TargetType.OMINOUS_VAULT) {
            var key = type == TargetType.OMINOUS_VAULT ? Items.OMINOUS_TRIAL_KEY : Items.TRIAL_KEY;
            var vaultState = state.getValue(VaultBlock.STATE);
            if (client.player.getItemInHand(hand).is(key)
                    && (vaultState == VaultState.ACTIVE || vaultState == VaultState.INACTIVE)) {
                pendingVault = new PendingVault(dimensionId(level), pos.immutable(), tickCounter + INTERACTION_TIMEOUT_TICKS);
            }
        }
    }

    /** 矿车以 UUID 持久记忆，避免移动或重新连接后实体数字 ID 改变。 */
    public void onEntityUsed(ClientLevel level, Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != level || client.player == null || !ensureWorld(client)) {
            return;
        }
        pendingContainer = null;
        pendingVault = null;
        if (entity instanceof MinecartChest cart) {
            pendingContainer = new ContainerVisit(dimensionId(level), TargetType.CHEST, List.of(), cart.getUUID(), cart.getId(),
                    tickCounter + INTERACTION_TIMEOUT_TICKS, client.player.containerMenu);
        }
    }

    /** 收到实际菜单时立即落盘；关闭菜单时删除对应标记，附近无关容器不受影响。 */
    private void trackOpenedContainers(Minecraft client) {
        var menu = client.player.containerMenu;
        if (openedContainer != null && menu != openedContainer.menu()) {
            removeVisitedMarkers(client, openedContainer.visit());
            openedContainer = null;
        }
        if (pendingContainer == null) {
            return;
        }
        if (tickCounter > pendingContainer.expiresAt()) {
            pendingContainer = null;
            return;
        }
        if (menu != client.player.inventoryMenu && menu != pendingContainer.previousMenu()) {
            ContainerVisit visit = pendingContainer;
            if (visit.entityUuid() != null) {
                scavenged.addEntity(visit.dimension(), visit.entityUuid());
            } else {
                for (BlockPos pos : visit.positions()) {
                    scavenged.addBlock(visit.dimension(), memoryType(visit.type()), pos.asLong());
                }
            }
            scavenged.flush();
            openedContainer = new OpenedContainer(visit, menu);
            pendingContainer = null;
        }
    }

    private void removeVisitedMarkers(Minecraft client, ContainerVisit visit) {
        Iterator<Marker> iterator = markers.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            boolean matches = visit.entityUuid() != null ? visit.entityUuid().equals(marker.entityUuid()) || marker.entityId() == visit.entityId()
                    : marker.entityId() == 0 && marker.type() == visit.type() && visit.positions().contains(marker.pos());
            if (matches) {
                if (marker.waypoint() != null) {
                    XaeroBridge.remove(marker.waypoint());
                }
                iterator.remove();
                if (config.notifyOnNew) {
                    client.player.sendOverlayMessage(Component.literal("Xaero TreasureHunter: ")
                            .append(Component.translatable("message.treasurehunter.scavenged", marker.name())));
                }
            }
        }
    }

    /** 只确认本玩家持钥匙点击的宝库，避免把其他玩家的解锁动画记到本人的记录里。 */
    private void trackOpenedVaults(Minecraft client, ClientLevel level) {
        if (pendingVault == null) {
            return;
        }
        if (tickCounter > pendingVault.expiresAt()) {
            pendingVault = null;
            return;
        }
        BlockState state = level.getBlockState(pendingVault.pos());
        if (!(state.getBlock() instanceof VaultBlock)) {
            pendingVault = null;
            return;
        }
        VaultState vaultState = state.getValue(VaultBlock.STATE);
        if (vaultState != VaultState.UNLOCKING && vaultState != VaultState.EJECTING) {
            return;
        }
        scavenged.addBlock(pendingVault.dimension(), "VAULT", pendingVault.pos().asLong());
        scavenged.flush();
        Marker marker = markers.remove(pendingVault.pos().asLong());
        if (marker != null) {
            if (marker.waypoint() != null) {
                XaeroBridge.remove(marker.waypoint());
            }
            if (config.notifyOnNew) {
                client.player.sendOverlayMessage(Component.literal("Xaero TreasureHunter: ")
                        .append(Component.translatable("message.treasurehunter.vault_opened", marker.name())));
            }
        }
        pendingVault = null;
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
                scannedChunks.remove(pos.pack());
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
        if (ignored.contains(dimensionId(level), pos.getX(), pos.getZ())) return;
        if (type == TargetType.CHEST) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                var partner = ChestBlock.getConnectedBlockPos(pos, state);
                if (ignored.contains(dimensionId(level), partner.getX(), partner.getZ())) return;
            }
        }
        if (Minecraft.getInstance().getSingleplayerServer() == null && config.serverFilterMode != ServerFilterMode.OFF
                && isKnownPlacement(level, type, pos)) return;
        if (markers.containsKey(key) || markers.size() >= config.maxMarkers) {
            return;
        }
        // 已搜刮的容器不再登记，否则周期性重扫会把它重新加回来
        if (isScavenged(level, type, key)) {
            return;
        }
        if (type == TargetType.CHEST && isSecondHalfOfDoubleChest(level, pos)) {
            return;
        }

        ChestCategory category = null;
        Component label = type.label();
        Minecraft client = Minecraft.getInstance();
        if (type == TargetType.CHEST || type == TargetType.BARREL) {
            if (client.getSingleplayerServer() != null) {
                // 单人的无表过滤独立于标签开关，木桶也需要真实表；读取失败只重试，不用启发式代替。
                var loot = lootReader.block(Minecraft.getInstance(), pos);
                if (loot == null || !loot.available()) {
                    scannedChunks.remove(ChunkPos.containing(pos).pack());
                    return;
                }
                if (!loot.hasLootTable()) {
                    return;
                }
                ChestCategory fromTable = LootTableCategories.classify(loot.tableId());
                // 原版木桶保留类型名；范围外的真实表使用「非原版战利品表容器」。
                if (type == TargetType.CHEST || fromTable == ChestCategory.OTHER_LOOT) {
                    category = fromTable;
                }
            } else if (config.serverFilterMode != ServerFilterMode.OFF
                    || (type == TargetType.CHEST && (config.smartLabels || !config.disabledChestCategories.isEmpty()))) {
                if (!StructureGuesser.isClassificationAreaLoaded(level, pos)) {
                    scannedChunks.remove(ChunkPos.containing(pos).pack());
                    return;
                }
                var assessment = StructureGuesser.assessContainer(level, pos, false);
                if (shouldHideServerContainer(assessment)) {
                    return;
                }
                if (type == TargetType.CHEST) {
                    category = assessment.category();
                }
            }
            if (category != null && config.isChestCategoryDisabled(category)) {
                return;
            }
            if (showsCategory(category)) {
                label = category.label();
            }
        } else if (config.smartLabels && type == TargetType.SPAWNER) {
            label = StructureGuesser.guessSpawnerLabel(level, pos);
        }

        // 邻近路径点合并（可在设置里关闭）：半径 5 格内已有相同标记（同类型同分类）时不再重复标记
        if (config.mergeNearbyMarkers && hasSameMarkerNearby(type, category, pos)) {
            return;
        }

        String name = "[" + label.getString() + "] " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        Object waypoint = null;
        if (config.waypointsEnabled && XaeroBridge.isReady(dimensionId(level))) {
            waypoint = XaeroBridge.emit(name, initialsFor(type, category), pos.getX(), pos.getY(), pos.getZ(),
                    colorFor(type, category), true);
        }
        markers.put(key, new Marker(key, type, pos.immutable(), name, category, waypoint, 0));

        if (config.notifyOnNew && client.player != null && notifyCooldown <= 0) {
            notifyCooldown = 20;
            client.player.sendSystemMessage(Component.literal("Xaero TreasureHunter: ")
                    .append(Component.translatable("message.treasurehunter.found",
                            label, pos.getX(), pos.getY(), pos.getZ())));
        }
    }

    /** 运输矿车箱扫描：原版废弃矿井的战利品全部在矿车（实体）里，没有普通箱方块。 */
    private void scanMinecarts(ClientLevel level) {
        if (!config.isEnabled(TargetType.CHEST)) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof MinecartChest cart) {
                registerMinecart(level, cart);
            }
        }
    }

    private void registerMinecart(ClientLevel level, MinecartChest cart) {
        if (ignored.contains(dimensionId(level), cart.blockPosition().getX(), cart.blockPosition().getZ())) return;
        int entityId = cart.getId();
        long key = cart.blockPosition().asLong();
        if (markers.size() >= config.maxMarkers || markers.containsKey(key)
                || scavenged.containsEntity(dimensionId(level), cart.getUUID())) {
            return;
        }
        for (Marker marker : markers.values()) {
            if (marker.entityId() == entityId) {
                return; // 同一辆矿车只登记一次：矿车移动后位置键会变，靠实体 id 去重
            }
        }
        BlockPos pos = cart.blockPosition().immutable();
        ChestCategory category = null;
        Component label = TargetType.CHEST.label();
        Minecraft client = Minecraft.getInstance();
        if (client.getSingleplayerServer() != null) {
            var loot = lootReader.minecart(client, cart.getUUID());
            if (loot == null || !loot.available() || !loot.hasLootTable()) {
                return;
            }
            category = LootTableCategories.classify(loot.tableId());
        } else if (config.smartLabels || config.serverFilterMode != ServerFilterMode.OFF
                || !config.disabledChestCategories.isEmpty()) {
            if (!StructureGuesser.isClassificationAreaLoaded(level, pos)) {
                return;
            }
            var assessment = StructureGuesser.assessContainer(level, pos, true);
            if (shouldHideServerContainer(assessment)) {
                return;
            }
            category = assessment.category();
        }
        if (category != null && config.isChestCategoryDisabled(category)) {
            return;
        }
        if (showsCategory(category)) {
            label = category.label();
        }
        if (config.mergeNearbyMarkers && hasSameMarkerNearby(TargetType.CHEST, category, pos)) {
            return;
        }
        String name = "[" + label.getString() + "] " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        Object waypoint = null;
        if (config.waypointsEnabled && XaeroBridge.isReady(dimensionId(level))) {
            waypoint = XaeroBridge.emit(name, initialsFor(TargetType.CHEST, category), pos.getX(), pos.getY(), pos.getZ(),
                    colorFor(TargetType.CHEST, category), true);
        }
        markers.put(key, new Marker(key, TargetType.CHEST, pos, name, category, waypoint, entityId, cart.getUUID()));

        if (config.notifyOnNew && client.player != null && notifyCooldown <= 0) {
            notifyCooldown = 20;
            client.player.sendSystemMessage(Component.literal("Xaero TreasureHunter: ")
                    .append(Component.translatable("message.treasurehunter.found",
                            label, pos.getX(), pos.getY(), pos.getZ())));
        }
    }

    /** 双联箱按实际连接状态去重；并排的两个单箱不当作双联箱。 */
    private static boolean isSecondHalfOfDoubleChest(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return false;
        }
        BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
        return other.getX() < pos.getX() || other.getZ() < pos.getZ();
    }

    /** 附近方块被破坏 / 变化时修正标记，并补登记之前失败的路径点。 */
    private void validateNearby(Minecraft client, ClientLevel level, BlockPos center) {
        Iterator<Map.Entry<Long, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next().getValue();
            if (marker.pos().distSqr(center) > (double) VALIDATE_RANGE * VALIDATE_RANGE) {
                continue;
            }
            if (level.getChunkSource().getChunk(marker.pos().getX() >> 4, marker.pos().getZ() >> 4, ChunkStatus.FULL, false) == null) continue;
            MinecartChest cart = marker.entityId() != 0 ? findMinecart(level, marker) : null;
            if (marker.entityId() != 0 && cart == null && tickCounter < 100) continue;
            boolean stillThere = marker.entityId() != 0
                    ? cart != null
                    : TargetType.match(level.getBlockState(marker.pos())) == marker.type();
            if (!stillThere) {
                if (marker.waypoint() != null) {
                    XaeroBridge.remove(marker.waypoint());
                }
                iterator.remove();
                continue;
            }
            if (client.getSingleplayerServer() != null
                    && (marker.type() == TargetType.CHEST || marker.type() == TargetType.BARREL)) {
                var loot = marker.entityId() == 0 ? lootReader.block(client, marker.pos())
                        : lootReader.minecart(client, cart.getUUID());
                if (loot != null && loot.available() && !loot.hasLootTable()) {
                    if (marker.waypoint() != null) {
                        XaeroBridge.remove(marker.waypoint());
                    }
                    iterator.remove();
                    continue;
                }
            }
        }
    }

    /** 独立存档只记录仍由本模组管理的临时点；永久点由 Xaero 自己保存。 */
    private void checkpointMarkers() {
        checkpointMarkers(false);
    }

    private void checkpointMarkers(boolean disconnecting) {
        if (lastLevel == null || worldIdentity == null) return;
        if (!disconnecting) XaeroBridge.synchronizeOwnership();
        var entries = new java.util.ArrayList<ScanMarkerMemory.Entry>();
        for (var marker : markers.values()) {
            if (marker.waypoint() != null && !(disconnecting ? XaeroBridge.isManagedSnapshot(marker.waypoint())
                    : XaeroBridge.isManaged(marker.waypoint()))) continue;
            XaeroBridge.Location location = XaeroBridge.location(marker.waypoint());
            if (location == null) location = markerLocations.get(marker.key());
            entries.add(new ScanMarkerMemory.Entry(marker.key(), marker.type(), marker.name(), marker.category(), marker.entityUuid(), location));
        }
        savedMarkers.replace(dimensionId(lastLevel), entries);
        savedMarkers.flush();
    }

    /** 恢复已确认结果，不重新分类、不发送重复发现消息，也不强制加载目标区块。 */
    private void restoreMarkers(ClientLevel level) {
        for (var entry : savedMarkers.entries(dimensionId(level))) {
            if (markers.size() >= config.maxMarkers) break;
            BlockPos pos = BlockPos.of(entry.position());
            if (!config.isEnabled(entry.type()) || entry.category() != null && config.isChestCategoryDisabled(entry.category())
                    || ignored.contains(dimensionId(level), pos.getX(), pos.getZ())) continue;
            if (entry.entityUuid() == null ? isScavenged(level, entry.type(), entry.position())
                    : scavenged.containsEntity(dimensionId(level), entry.entityUuid())) continue;
            markers.put(entry.position(), new Marker(entry.position(), entry.type(), pos, entry.name(), entry.category(),
                    null, entry.entityUuid() == null ? 0 : -1, entry.entityUuid()));
            if (entry.location() != null) markerLocations.put(entry.position(), entry.location());
        }
        restoreMissingWaypoints();
    }

    private void restoreMissingWaypoints() {
        if (!config.waypointsEnabled || lastLevel == null || !XaeroBridge.isReady(dimensionId(lastLevel))) return;
        int restored = 0;
        for (var entry : markers.entrySet()) {
            var marker = entry.getValue();
            if (marker.waypoint() != null) continue;
            Object waypoint = XaeroBridge.emitAt(marker.name(), initialsFor(marker.type(), marker.category()), marker.pos().getX(),
                    marker.pos().getY(), marker.pos().getZ(), colorFor(marker.type(), marker.category()), true, markerLocations.get(marker.key()));
            if (waypoint != null) {
                entry.setValue(new Marker(marker.key(), marker.type(), marker.pos(), marker.name(), marker.category(),
                        waypoint, marker.entityId(), marker.entityUuid()));
                if (++restored >= 32) break;
            }
        }
    }

    private static MinecartChest findMinecart(ClientLevel level, Marker marker) {
        if (marker.entityUuid() == null) return level.getEntity(marker.entityId()) instanceof MinecartChest cart ? cart : null;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof MinecartChest cart && marker.entityUuid().equals(cart.getUUID())) return cart;
        }
        return null;
    }

    /** 路径点简称：有明确结构分类的容器用分类代表汉字，其余用类型默认。 */
    private String initialsFor(TargetType type, ChestCategory category) {
        if (showsCategory(category)) {
            return category.initial();
        }
        return type.initials();
    }

    /** 路径点颜色：有明确结构分类的容器用分类颜色，其余用类型默认。 */
    private String colorFor(TargetType type, ChestCategory category) {
        if (showsCategory(category)) {
            return category.colorEnumName();
        }
        return type.colorEnumName();
    }

    private boolean showsCategory(ChestCategory category) {
        return category != null && category != ChestCategory.OTHER
                && (config.smartLabels || category == ChestCategory.OTHER_LOOT);
    }

    private boolean shouldHideServerContainer(StructureGuesser.ContainerAssessment assessment) {
        return switch (config.serverFilterMode) {
            case OFF -> false;
            case CONSERVATIVE -> assessment.suspectedPlayerContainer();
            case AGGRESSIVE -> assessment.aggressiveHidden();
        };
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

    private boolean isScavenged(ClientLevel level, TargetType type, long key) {
        return scavenged.containsBlock(dimensionId(level), memoryType(type), key);
    }

    private static String dimensionId(ClientLevel level) {
        return level.dimension().identifier().toString();
    }

    private static String memoryType(TargetType type) {
        return type == TargetType.OMINOUS_VAULT ? "VAULT" : type.name();
    }

}
