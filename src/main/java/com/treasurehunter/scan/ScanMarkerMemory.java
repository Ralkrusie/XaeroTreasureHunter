package com.treasurehunter.scan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.treasurehunter.TargetType;
import com.treasurehunter.xaero.XaeroBridge.Location;
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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 已确认扫描标记的独立存档；Xaero 原生不保存临时点，因此记录足以在重进后恢复的元数据。 */
public final class ScanMarkerMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;
    private final Path directory;
    private final Logger logger;
    private Path path;
    private Data data;
    private boolean writable;
    private boolean dirty;
    private boolean warned;

    public record Entry(long position, TargetType type, String name, ChestCategory category,
            UUID entityUuid, Location location) { }

    private static final class Data {
        int version = VERSION;
        String world;
        Map<String, List<Entry>> dimensions = new HashMap<>();
    }

    public ScanMarkerMemory(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public void open(String world) {
        if (data != null && world.equals(data.world)) return;
        close();
        data = new Data();
        data.world = world;
        path = directory.resolve(fileName(world));
        writable = true;
        warned = false;
        try {
            if (Files.exists(path)) {
                var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!json.has("version") || !json.has("world") || !json.has("dimensions")) {
                    throw new JsonParseException("扫描标记文件缺少必要字段");
                }
                Data loaded = GSON.fromJson(json, Data.class);
                if (loaded.version != VERSION || !world.equals(loaded.world) || loaded.dimensions == null
                        || loaded.dimensions.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                        || entry.getValue() == null || entry.getValue().stream().anyMatch(marker -> marker == null
                        || marker.type() == null || marker.name() == null
                        || marker.location() != null && (marker.location().worldPath() == null || marker.location().set() == null)))) {
                    throw new JsonParseException("扫描标记格式或版本不匹配");
                }
                data = loaded;
            }
        } catch (IOException | JsonParseException | IllegalStateException | IllegalArgumentException error) {
            writable = false;
            logger.warn("Xaero TreasureHunter: 扫描标记读取失败，保留原文件，本次仅记在内存：{}", path, error);
        }
    }

    public List<Entry> entries(String dimension) {
        return data == null ? List.of() : List.copyOf(data.dimensions.getOrDefault(dimension, List.of()));
    }

    public void replace(String dimension, List<Entry> entries) {
        if (data == null || entries.equals(data.dimensions.getOrDefault(dimension, List.of()))) return;
        if (entries.isEmpty()) data.dimensions.remove(dimension);
        else data.dimensions.put(dimension, new ArrayList<>(entries));
        dirty = true;
    }

    /** 明确清除临时标记时允许重置，其他世界与已开启记忆不受影响。 */
    public void clear() {
        if (data == null) return;
        data.dimensions.clear();
        writable = true;
        dirty = true;
        flush();
    }

    public void flush() {
        if (!dirty || !writable || path == null) return;
        try {
            Files.createDirectories(directory);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
            warned = false;
        } catch (IOException error) {
            if (!warned) logger.warn("Xaero TreasureHunter: 扫描标记保存失败，将稍后重试：{}", path, error);
            warned = true;
        }
    }

    public void close() {
        flush();
        data = null;
        path = null;
        dirty = false;
    }

    private static String fileName(String world) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(world.getBytes(StandardCharsets.UTF_8))) + ".json"; }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("运行时缺少 SHA-256", error); }
    }
}
