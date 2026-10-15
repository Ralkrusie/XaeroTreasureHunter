package com.treasurehunter.scan;

import com.treasurehunter.TreasureHunterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 单人真实战利品表读取：服务器线程批量读 getter，客户端线程接收不可变结果。 */
public final class SingleplayerLootReader {
    private static final int MAX_BATCH_SIZE = 128;
    private static final int CACHE_TICKS = 100;
    private static final LootResult UNAVAILABLE = new LootResult(false, null);

    private final Map<Query, CachedResult> results = new HashMap<>();
    private final Set<Query> pending = new HashSet<>();
    private long generation;
    private long lastPrunedAt;
    private boolean inFlight;
    private boolean warned;

    /** available=true 且 ID=null 才代表确认无表；对象为 null 表示等待，读取失败不可当成无表。 */
    public record LootResult(boolean available, String tableId) {
        public boolean hasLootTable() {
            return available && tableId != null;
        }
    }

    private record CachedResult(LootResult value, long expiresAt) {
    }

    /** 方块与矿车分别以坐标、稳定 UUID 查询，实体查询不依赖会随移动变化的坐标。 */
    private record Query(BlockPos pos, UUID entityUuid) {
    }

    /** 查询方块；尚在等待时返回 null，未确认读取时 available=false。 */
    public LootResult block(Minecraft client, BlockPos pos) {
        return request(client, new Query(pos.immutable(), null));
    }

    /** 查询矿车；尚在等待时返回 null，未确认读取时 available=false。 */
    public LootResult minecart(Minecraft client, UUID uuid) {
        return request(client, new Query(null, uuid));
    }

    private LootResult request(Minecraft client, Query query) {
        if (client.getSingleplayerServer() == null) {
            return UNAVAILABLE;
        }
        CachedResult result = results.get(query);
        if (result != null && client.level != null && client.level.getGameTime() < result.expiresAt()) {
            return result.value();
        }
        results.remove(query);
        pending.add(query);
        return null;
    }

    /** 只由客户端主线程调用；只读已加载区块，不生成战利品、不加载远处区块。 */
    public void tick(Minecraft client, ClientLevel level) {
        var server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - lastPrunedAt >= CACHE_TICKS) {
            results.values().removeIf(result -> now >= result.expiresAt());
            lastPrunedAt = now;
        }
        if (pending.isEmpty() || inFlight) {
            return;
        }
        var batch = new HashSet<Query>();
        var iterator = pending.iterator();
        while (iterator.hasNext() && batch.size() < MAX_BATCH_SIZE) {
            batch.add(iterator.next());
            iterator.remove();
        }
        long requestGeneration = generation;
        var dimension = level.dimension();
        inFlight = true;
        server.execute(() -> {
            var values = new HashMap<Query, LootResult>();
            try {
                var serverLevel = server.getLevel(dimension);
                for (Query query : batch) {
                    LootResult value = UNAVAILABLE;
                    if (serverLevel != null) {
                        if (query.pos() != null) {
                            BlockPos pos = query.pos();
                            var chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                            if (chunk != null && chunk.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                                var table = container.getLootTable(); // javap 校验：直接返回字段，不触发 unpackLootTable
                                value = new LootResult(true, table == null ? null : table.identifier().toString());
                                var state = chunk.getBlockState(pos);
                                // 双联箱可能只在另一半存表；不能因为登记这一半无表就丢掉整个容器。
                                if (table == null && state.getBlock() instanceof ChestBlock
                                        && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                                    var other = ChestBlock.getConnectedBlockPos(pos, state);
                                    var otherChunk = serverLevel.getChunkSource().getChunkNow(other.getX() >> 4, other.getZ() >> 4);
                                    value = UNAVAILABLE;
                                    if (otherChunk != null && otherChunk.getBlockEntity(other) instanceof RandomizableContainerBlockEntity partner) {
                                        var partnerTable = partner.getLootTable();
                                        value = new LootResult(true, partnerTable == null ? null : partnerTable.identifier().toString());
                                    }
                                }
                            }
                        } else if (serverLevel.getEntity(query.entityUuid()) instanceof MinecartChest cart) {
                            var table = cart.getContainerLootTable();
                            value = new LootResult(true, table == null ? null : table.identifier().toString());
                        }
                    }
                    values.put(query, value);
                }
            } catch (RuntimeException e) {
                for (Query query : batch) {
                    values.putIfAbsent(query, UNAVAILABLE);
                }
                client.execute(() -> {
                    if (generation == requestGeneration && !warned) {
                        warned = true;
                        TreasureHunterMod.LOGGER.warn("Xaero TreasureHunter: 单人战利品表读取失败，暂缓标记并稍后重试", e);
                    }
                });
            }
            client.execute(() -> {
                // 清空、断线、换维度之后到达的旧结果必须丢弃。
                if (generation == requestGeneration && client.level == level && client.getSingleplayerServer() == server) {
                    values.forEach((query, value) -> {
                        if (value.available()) {
                            results.put(query, new CachedResult(value, level.getGameTime() + CACHE_TICKS));
                        }
                    });
                    pending.removeAll(values.keySet());
                    inFlight = false;
                }
            });
        });
    }

    public void invalidateChunk(ChunkPos pos) {
        results.keySet().removeIf(query -> query.pos() != null && ChunkPos.containing(query.pos()).equals(pos));
    }

    /** 换维度或清空扫描时清掉缓存，并使正在执行的旧批次失效。 */
    public void clear() {
        generation++;
        results.clear();
        pending.clear();
        inFlight = false;
        lastPrunedAt = 0;
    }
}
