package com.treasurehunter.scan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 独立保存忽略区域；不更改 Xaero 文件、路径点字段或世界数据，卸载后仅留下无害 JSON。 */
public final class IgnoreRegionStore {
    public static final int MAX_RADIUS_CHUNKS = 128;
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 半径单位为区块（16 格），以中心方块的中心为圆心/正方形中心，覆盖所有高度。 */
    public enum Shape { CIRCLE, SQUARE }

    /** Xaero 没有持久 UUID；外部关联使用世界路径、集合、名称和坐标，编辑确认时迁移。 */
    public record Anchor(String worldPath, String set, String name, int x, int y, int z) {
        public Anchor {
            Objects.requireNonNull(worldPath);
            Objects.requireNonNull(set);
            Objects.requireNonNull(name);
        }
    }

    public record Region(String id, String name, String dimension, int x, int z,
            int radiusChunks, Shape shape, Anchor anchor) {
        public Region {
            UUID.fromString(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(shape);
            if (radiusChunks < 1 || radiusChunks > MAX_RADIUS_CHUNKS) throw new IllegalArgumentException("忽略半径越界");
        }

        public boolean contains(int blockX, int blockZ) {
            long dx = (long) blockX - x;
            long dz = (long) blockZ - z;
            long radius = radiusChunks * 16L;
            return shape == Shape.SQUARE ? Math.abs(dx) <= radius && Math.abs(dz) <= radius
                    : Math.abs(dx) <= radius && Math.abs(dz) <= radius && dx * dx + dz * dz <= radius * radius;
        }
    }

    private static final class Data {
        int version = VERSION;
        String world;
        List<Region> regions = new ArrayList<>();
    }

    private final Path directory;
    private final Logger logger;
    private Path path;
    private Data data;
    private boolean writable = true;
    private boolean dirty;
    private boolean warned;
    private Set<Anchor> liveAnchors = Set.of();
    private String scanningWorldPath;
    private long revision;

    public IgnoreRegionStore(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public void open(String world) {
        if (data != null && world.equals(data.world)) return;
        close();
        path = directory.resolve(fileName(world));
        data = new Data();
        data.world = world;
        writable = true;
        warned = false;
        try {
            if (Files.exists(path)) {
                var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!json.has("version") || !json.has("world") || !json.has("regions")) {
                    throw new JsonParseException("忽略区域缺少格式字段");
                }
                Data loaded = GSON.fromJson(json, Data.class);
                if (loaded == null || loaded.version != VERSION || !world.equals(loaded.world) || loaded.regions == null
                        || loaded.regions.stream().anyMatch(Objects::isNull)
                        || loaded.regions.stream().map(Region::id).distinct().count() != loaded.regions.size()) {
                    throw new JsonParseException("忽略区域格式或版本不匹配");
                }
                data = loaded;
            }
        } catch (IOException | RuntimeException e) {
            writable = false;
            logger.warn("Xaero TreasureHunter: 忽略区域读取失败，保留原文件，本次只在内存修改：{}", path, e);
        }
        revision++;
    }

    public boolean isOpen() { return data != null; }
    public boolean isWritable() { return data != null && writable; }

    public List<Region> regions() { return data == null ? List.of() : List.copyOf(data.regions); }

    public Region find(Anchor anchor) {
        return regions().stream().filter(region -> anchor.equals(region.anchor())).findFirst().orElse(null);
    }

    public boolean isActive(Region region) { return region.anchor() == null
            || (liveAnchors.contains(region.anchor()) && region.anchor().worldPath().equals(scanningWorldPath)); }

    public boolean isLinkedAvailable(Region region) {
        return region.anchor() != null && liveAnchors.contains(region.anchor());
    }

    public List<Region> activeRegions(String dimension) {
        return regions().stream().filter(region -> dimension.equals(region.dimension()) && isActive(region)).toList();
    }

    public boolean contains(String dimension, int x, int z) {
        return data != null && data.regions.stream().anyMatch(region -> dimension.equals(region.dimension())
                && isActive(region) && region.contains(x, z));
    }

    public void setLiveAnchors(Set<Anchor> anchors, String worldPath) {
        var before = regions().stream().filter(region -> region.anchor() != null && isActive(region))
                .map(Region::id).collect(java.util.stream.Collectors.toSet());
        liveAnchors = Set.copyOf(anchors);
        scanningWorldPath = worldPath;
        var after = regions().stream().filter(region -> region.anchor() != null && isActive(region))
                .map(Region::id).collect(java.util.stream.Collectors.toSet());
        if (!before.equals(after)) revision++;
    }

    public long revision() { return revision; }

    public void put(Region region) {
        if (data == null) return;
        data.regions.removeIf(old -> old.id().equals(region.id())
                || (region.anchor() != null && region.anchor().equals(old.anchor())));
        data.regions.add(region);
        changed();
    }

    public void remove(String id) {
        if (data != null && data.regions.removeIf(region -> id.equals(region.id()))) changed();
    }

    /** 一次编辑中的旧锚点删除与新锚点登记一起落盘，不产生半完成的关联文件。 */
    public void replaceLinked(List<String> removedIds, List<Region> replacements) {
        if (data == null || (removedIds.isEmpty() && replacements.isEmpty())) return;
        var distinct = new java.util.LinkedHashMap<Anchor, Region>();
        for (var region : replacements) distinct.put(region.anchor(), region);
        data.regions.removeIf(region -> removedIds.contains(region.id())
                || distinct.containsKey(region.anchor()));
        data.regions.addAll(distinct.values());
        changed();
    }

    private void changed() {
        dirty = true;
        revision++;
        flush();
    }

    public void flush() {
        if (!dirty || !writable || data == null) return;
        try {
            Files.createDirectories(directory);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            warned = false;
        } catch (IOException e) {
            if (!warned) logger.warn("Xaero TreasureHunter: 忽略区域保存失败，将稍后重试：{}", path, e);
            warned = true;
        }
    }

    public void close() {
        flush();
        data = null;
        path = null;
        dirty = false;
        liveAnchors = Set.of();
        scanningWorldPath = null;
        revision++;
    }

    private static String fileName(String world) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(world.getBytes(StandardCharsets.UTF_8))) + ".json";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }
}
