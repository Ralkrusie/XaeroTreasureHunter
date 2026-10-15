package com.treasurehunter.xaero;

import com.treasurehunter.TreasureHunterMod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xaero's Minimap 公开内部 API 的反射桥。
 *
 * <p>
 * Xaero 不开源也不提供正式的 Java API，这里通过反射调用其公开成员
 * （签名已在 xaerominimap 26.5.3 (MC 26.3) 上用 javap 逐项校验：下列类与成员、
 * 9 参 {@code Waypoint} 构造器、{@code WaypointPurpose.NORMAL}
 * 与全部 20 个 {@code WaypointColor} 常量均确认存在）：
 * <ul>
 * <li>{@code xaero.hud.minimap.BuiltInHudModules.MINIMAP}（public static
 * field）</li>
 * <li>{@code xaero.hud.module.HudModule#getCurrentSession()}</li>
 * <li>{@code xaero.hud.minimap.module.MinimapSession#getWorldManager()}</li>
 * <li>{@code xaero.hud.minimap.world.MinimapWorldManager#getCurrentWorld()}</li>
 * <li>{@code xaero.hud.minimap.world.MinimapWorld#getCurrentWaypointSet()}</li>
 * <li>{@code xaero.hud.minimap.waypoint.set.WaypointSet#add(Waypoint, boolean)}
 * / {@code #remove(Waypoint)} / {@code #getWaypoints()}</li>
 * <li>{@code xaero.common.minimap.waypoints.Waypoint#<init>(int,int,int,String,String,WaypointColor,WaypointPurpose,boolean,boolean)}
 * / {@code #isTemporary()}</li>
 * <li>{@code xaero.hud.minimap.waypoint.WaypointColor} /
 * {@code WaypointPurpose} 枚举常量</li>
 * <li>自建路径点会记录其所在 {@code WaypointSet}；只清理仍为临时状态的点，玩家 Restore 后的永久点保留。</li>
 * </ul>
 *
 * <p>
 * Xaero 不在 / API 变化 / 会话未启动时全部安全 no-op，并最多警告一次。
 */
public final class XaeroBridge {
    private static final AtomicBoolean WARNED_DRIFT = new AtomicBoolean(false);

    /** 我们尚未交还管理权的路径点 → 创建时的 WaypointSet（按身份比较）。 */
    private static final Map<Object, Object> OWNED = new IdentityHashMap<>();
    private static final Map<Object, Location> LOCATIONS = new IdentityHashMap<>();

    /** 存档只保留原世界/集合的字符串位置，不保存 Xaero 对象。 */
    public record Location(String worldPath, String set) { }

    private static boolean resolved;
    private static boolean available;

    private static Object minimapModule;
    private static Method sessionGetter;
    private static Method worldManagerGetter;
    private static Method currentWorldGetter;
    private static Method currentSetGetter;
    private static Method setAdd;
    private static Method setRemove;
    private static Method setGetWaypoints;
    private static Method waypointIsTemporary;
    private static Constructor<?> waypointCtor;
    private static Class<?> waypointClass;
    private static Class<?> colorClass;
    private static Class<?> purposeClass;

    private XaeroBridge() {
    }

    public static boolean isAvailable() {
        return ensureResolved();
    }

    /**
     * 在当前世界的当前路径点集里创建一个临时路径点。
     *
     * @return 创建的 Waypoint 对象；失败（Xaero 不在 / API 变化 / 重名）返回 null。
     */
    public static Object emit(String name, String initials, int x, int y, int z,
            String colorEnumName, boolean temporary) {
        return emitAt(name, initials, x, y, z, colorEnumName, temporary, null);
    }

    /** 重进时只恢复到原来的 Xaero 世界与集合；会话未就绪返回 null，不写入错误集合。 */
    public static Object emitAt(String name, String initials, int x, int y, int z,
            String colorEnumName, boolean temporary, Location location) {
        if (!ensureResolved()) {
            return null;
        }
        try {
            Object world = currentWorld();
            if (world == null) return null;
            if (location != null && !location.worldPath().equals(XaeroAccess.worldPath(world))) {
                Object matched = null;
                for (Object candidate : XaeroAccess.allWorlds(world)) {
                    if (location.worldPath().equals(XaeroAccess.worldPath(candidate))) { matched = candidate; break; }
                }
                if (matched == null) return null;
                world = matched;
            }
            Object set = location == null ? currentSetGetter.invoke(world)
                    : XaeroAccess.call(world, "getWaypointSet", location.set());
            if (set == null) return null;
            Location actual = new Location(XaeroAccess.worldPath(world), (String) XaeroAccess.call(set, "getName"));
            Object existing = matchingWaypoint(set, name, colorEnumName, x, y, z);
            if (existing != null) {
                // 永久点始终归玩家；只有存档明确记录的临时点才重新接管。
                if (location != null && Boolean.TRUE.equals(waypointIsTemporary.invoke(existing))) OWNED.put(existing, set);
                LOCATIONS.put(existing, actual);
                return existing;
            }
            Object color = colorClass.getField(colorEnumName).get(null);
            Object purpose = purposeClass.getField("NORMAL").get(null);
            Object waypoint = waypointCtor.newInstance(x, y, z, name, initials, color, purpose, temporary, true);
            setAdd.invoke(set, waypoint, true);
            OWNED.put(waypoint, set);
            LOCATIONS.put(waypoint, actual);
            return waypoint;
        } catch (Throwable t) {
            warnDriftOnce("emit", t);
            return null;
        }
    }

    /** 从创建时的集合移除仍为临时状态的自建点；玩家恢复的永久点交还管理权并保留。 */
    public static void remove(Object waypoint) {
        if (!ensureResolved() || waypoint == null) {
            return;
        }
        try {
            removeOwnedTemporary(waypoint);
        } catch (Throwable t) {
            warnDriftOnce("remove", t);
        }
    }

    /** 清理全部自建点的管理记录，只删除仍为临时状态的点，返回实际删除数量。 */
    public static int removeAll() {
        if (!ensureResolved()) {
            return 0;
        }
        int count = 0;
        for (Object waypoint : new ArrayList<>(OWNED.keySet())) {
            try {
                if (removeOwnedTemporary(waypoint)) {
                    count++;
                }
            } catch (Throwable t) {
                warnDriftOnce("removeAll", t);
            }
        }
        return count;
    }

    /** 是否还有尚未清理的自建路径点管理记录。 */
    public static boolean hasOwnedWaypoints() {
        return !OWNED.isEmpty();
    }

    public static Location location(Object waypoint) { return LOCATIONS.get(waypoint); }

    /** Xaero 还在切换世界时不创建路径点，避免恢复到上一维度。 */
    public static boolean isReady(String dimension) {
        if (!ensureResolved()) return false;
        try {
            Object world = currentWorld();
            return world != null && dimension.equals(XaeroAccess.dimension(world));
        } catch (Throwable error) { return false; }
    }

    /** Restore 或用户主动删除后立即交还管理权，防止存档重新生成用户已处理的点。 */
    public static void synchronizeOwnership() {
        for (Object waypoint : new ArrayList<>(OWNED.keySet())) {
            if (!isManaged(waypoint)) OWNED.remove(waypoint);
        }
    }

    /** 退出时 Xaero 可能已卸载集合，只检查最后的管理权和点的真实临时状态。 */
    public static boolean isManagedSnapshot(Object waypoint) {
        if (!OWNED.containsKey(waypoint)) return false;
        try { return Boolean.TRUE.equals(waypointIsTemporary.invoke(waypoint)); }
        catch (Throwable error) { return false; }
    }

    public static boolean isManaged(Object waypoint) {
        Object set = OWNED.get(waypoint);
        if (set == null) return false;
        try {
            if (!Boolean.TRUE.equals(waypointIsTemporary.invoke(waypoint))) return false;
            for (Object member : (Iterable<?>) setGetWaypoints.invoke(set)) if (member == waypoint) return true;
        } catch (Throwable error) { warnDriftOnce("ownership", error); }
        return false;
    }

    /** 断线仅释放引用，不删除临时点；它们由独立存档在下次会话恢复。 */
    public static void releaseOwnership() {
        OWNED.clear();
        LOCATIONS.clear();
    }

    /**
     * javap 校验（Xaero 26.5.3）：Restore 在原对象上执行 setTemporary(false) 并保存世界。
     * 先交还管理权，再读取实时状态；读取失败时也不删除，避免误删玩家保留的点。
     */
    private static boolean removeOwnedTemporary(Object waypoint) throws Exception {
        Object set = OWNED.remove(waypoint);
        LOCATIONS.remove(waypoint);
        if (set == null || !Boolean.TRUE.equals(waypointIsTemporary.invoke(waypoint))) {
            return false;
        }
        setRemove.invoke(set, waypoint);
        return true;
    }

    private static synchronized boolean ensureResolved() {
        if (resolved) {
            return available;
        }
        resolved = true;
        try {
            Class<?> builtInModules = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            Field minimapField = builtInModules.getField("MINIMAP");
            minimapModule = minimapField.get(null);

            Class<?> hudModuleClass = Class.forName("xaero.hud.module.HudModule");
            sessionGetter = hudModuleClass.getMethod("getCurrentSession");

            Class<?> sessionClass = Class.forName("xaero.hud.minimap.module.MinimapSession");
            worldManagerGetter = sessionClass.getMethod("getWorldManager");

            Class<?> worldManagerClass = Class.forName("xaero.hud.minimap.world.MinimapWorldManager");
            currentWorldGetter = worldManagerClass.getMethod("getCurrentWorld");

            Class<?> worldClass = Class.forName("xaero.hud.minimap.world.MinimapWorld");
            currentSetGetter = worldClass.getMethod("getCurrentWaypointSet");

            Class<?> waypointSetClass = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet");
            waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            colorClass = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");
            purposeClass = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose");

            setAdd = waypointSetClass.getMethod("add", waypointClass, boolean.class);
            setRemove = waypointSetClass.getMethod("remove", waypointClass);
            setGetWaypoints = waypointSetClass.getMethod("getWaypoints");
            waypointIsTemporary = waypointClass.getMethod("isTemporary");
            waypointCtor = waypointClass.getConstructor(int.class, int.class, int.class, String.class, String.class,
                    colorClass, purposeClass, boolean.class, boolean.class);

            available = true;
        } catch (Throwable t) {
            available = false;
            warnDriftOnce("resolve", t);
        }
        return available;
    }

    private static Object currentWorld() throws Exception {
        if (minimapModule == null) {
            minimapModule = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
            if (minimapModule == null) return null;
        }
        Object session = sessionGetter.invoke(minimapModule);
        if (session == null) {
            return null;
        }
        Object worldManager = worldManagerGetter.invoke(session);
        if (worldManager == null) {
            return null;
        }
        return currentWorldGetter.invoke(worldManager);
    }

    private static Object matchingWaypoint(Object set, String name, String colorEnumName, int x, int y, int z) {
        try {
            Object iterable = setGetWaypoints.invoke(set);
            if (!(iterable instanceof Iterable<?> waypoints)) {
                return null;
            }
            Method getName = waypointClass.getMethod("getName");
            Method getColor = waypointClass.getMethod("getWaypointColor");
            for (Object waypoint : waypoints) {
                if (!name.equals(getName.invoke(waypoint))) {
                    continue;
                }
                if ((int) XaeroAccess.call(waypoint, "getX") != x || (int) XaeroAccess.call(waypoint, "getY") != y
                        || (int) XaeroAccess.call(waypoint, "getZ") != z) continue;
                Object color = getColor.invoke(waypoint);
                if (color == null || colorEnumName.equals(((Enum<?>) color).name())) {
                    return waypoint;
                }
            }
        } catch (Throwable t) {
            warnDriftOnce("hasOurWaypoint", t);
        }
        return null;
    }

    /** Xaero 是否在类路径上（用于区分"没装"和"API 变了"）。 */
    private static boolean xaeroPresent() {
        try {
            Class.forName("xaero.hud.minimap.BuiltInHudModules");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void warnDriftOnce(String operation, Throwable cause) {
        if (!xaeroPresent()) {
            return;
        }
        if (WARNED_DRIFT.compareAndSet(false, true)) {
            TreasureHunterMod.LOGGER.warn("Xaero TreasureHunter: Xaero 地图集成已禁用（API 变化 @ '{}'），"
                    + "请反馈你的 Xaero's Minimap 版本：{}", operation, cause.toString());
        }
    }
}
