package com.treasurehunter.scan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 当前存档或服务器的已打开记录；按维度保存，使用临时文件替换避免写到一半损坏。 */
public final class ScavengedMemory {
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;
    private final Logger logger;
    private final String description;
    private Path path;
    private Data data;
    private boolean dirty;
    private boolean writable = true;
    private boolean warned;

    /** 文件格式独立于配置；坐标包含目标类型，矿车存 UUID，避免串到同坐标的其他目标。 */
    private static final class Data {
        int version = VERSION;
        String world;
        Map<String, DimensionData> dimensions = new HashMap<>();
    }

    private static final class DimensionData {
        Set<String> blocks = new HashSet<>();
        Set<String> entities = new HashSet<>();
    }

    public ScavengedMemory(Path directory, Logger logger) {
        this(directory, logger, "已打开记录");
    }

    /** 放置记录复用同一安全格式，但使用独立目录与日志名称。 */
    public ScavengedMemory(Path directory, Logger logger, String description) {
        this.directory = directory;
        this.logger = logger;
        this.description = description;
    }

    /** 打开一个世界的记录；切换前先保存旧世界。同一身份重复调用不会重读文件。 */
    public void open(String world) {
        if (data != null && world.equals(data.world)) {
            return;
        }
        close();
        path = directory.resolve(fileName(world));
        data = new Data();
        data.world = world;
        writable = true;
        warned = false;
        try {
            if (Files.exists(path)) {
                Data loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Data.class);
                if (loaded == null || loaded.version != VERSION || !world.equals(loaded.world) || loaded.dimensions == null
                        || loaded.dimensions.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                                || entry.getValue() == null || entry.getValue().blocks == null || entry.getValue().entities == null
                                || entry.getValue().blocks.contains(null) || entry.getValue().entities.contains(null))) {
                    throw new JsonParseException("已打开记录格式或版本不匹配");
                }
                data = loaded;
            }
        } catch (IOException | JsonParseException e) {
            // 保留损坏或未知版本的原文件，避免用空记录覆盖；明确重置时才允许覆盖。
            writable = false;
            logger.warn("Xaero TreasureHunter: {}读取失败，保留原文件，本次仅记在内存：{}", description, path, e);
        }
    }

    public boolean containsBlock(String dimension, String type, long pos) {
        DimensionData bucket = bucket(dimension, false);
        return bucket != null && bucket.blocks.contains(type + ":" + pos);
    }

    public boolean containsEntity(String dimension, UUID uuid) {
        DimensionData bucket = bucket(dimension, false);
        return bucket != null && bucket.entities.contains(uuid.toString());
    }

    public void addBlock(String dimension, String type, long pos) {
        DimensionData bucket = bucket(dimension, true);
        if (bucket != null) {
            dirty |= bucket.blocks.add(type + ":" + pos);
        }
    }

    public void addEntity(String dimension, UUID uuid) {
        DimensionData bucket = bucket(dimension, true);
        if (bucket != null) {
            dirty |= bucket.entities.add(uuid.toString());
        }
    }

    public void removeBlock(String dimension, String type, long pos) {
        DimensionData bucket = bucket(dimension, false);
        if (bucket != null) dirty |= bucket.blocks.remove(type + ":" + pos);
    }

    private DimensionData bucket(String dimension, boolean create) {
        if (data == null) {
            return null;
        }
        return create ? data.dimensions.computeIfAbsent(dimension, key -> new DimensionData()) : data.dimensions.get(dimension);
    }

    /** 保存待写记录；失败时保留 dirty，下一次保存继续重试。 */
    public void flush() {
        if (!dirty || !writable || path == null) {
            return;
        }
        try {
            Files.createDirectories(directory);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            warned = false;
        } catch (IOException e) {
            if (!warned) {
                warned = true;
                logger.warn("Xaero TreasureHunter: {}保存失败，将稍后重试：{}", description, path, e);
            }
        }
    }

    /** 显式重置当前世界所有维度的记录，不影响其他世界。 */
    public void clear() {
        if (data != null) {
            data.dimensions.clear();
            writable = true;
            dirty = true;
            flush();
        }
    }

    public void close() {
        flush();
        data = null;
        path = null;
        dirty = false;
    }

    /** 身份散列只用来生成合法文件名；文件内保留完整身份供校验。 */
    private static String fileName(String world) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(world.getBytes(StandardCharsets.UTF_8))) + ".json";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行时缺少 SHA-256", e);
        }
    }
}
