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
 * <li>{@code xaero.common.minimap.waypoints.Waypoint#<init>(int,int,int,String,String,WaypointColor,WaypointPurpose,boolean,boolean)}</li>
 * <li>{@code xaero.hud.minimap.waypoint.WaypointColor} /
 * {@code WaypointPurpose} 枚举常量</li>
 * <li>自建路径点会记录其所在 {@code WaypointSet}，维度切换后仍能精确移除，不会留下无法清除的残留。</li>
 * </ul>
 *
 * <p>
 * Xaero 不在 / API 变化 / 会话未启动时全部安全 no-op，并最多警告一次。
 */
public final class XaeroBridge {
    private static final AtomicBoolean WARNED_DRIFT = new AtomicBoolean(false);

    /** 我们创建过的路径点 → 它所在的那个 WaypointSet（按身份比较）。 */
    private static final Map<Object, Object> OWNED = new IdentityHashMap<>();

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
        if (!ensureResolved()) {
            return null;
        }
        try {
            Object set = currentWaypointSet();
            if (set == null) {
                return null;
            }
            if (hasOurWaypoint(set, name, colorEnumName)) {
                return null;
            }
            Object color = colorClass.getField(colorEnumName).get(null);
            Object purpose = purposeClass.getField("NORMAL").get(null);
            Object waypoint = waypointCtor.newInstance(x, y, z, name, initials, color, purpose, temporary, true);
            setAdd.invoke(set, waypoint, true);
            OWNED.put(waypoint, set);
            return waypoint;
        } catch (Throwable t) {
            warnDriftOnce("emit", t);
            return null;
        }
    }

    /** 从它创建时所在的那个路径点集里移除（维度切换后也不会删错集合）。 */
    public static void remove(Object waypoint) {
        if (!ensureResolved() || waypoint == null) {
            return;
        }
        try {
            Object set = OWNED.remove(waypoint);
            if (set == null) {
                set = currentWaypointSet();
            }
            if (set != null) {
                setRemove.invoke(set, waypoint);
            }
        } catch (Throwable t) {
            warnDriftOnce("remove", t);
        }
    }

    /** 移除我们创建过的所有路径点（各自从记录的集合里删），返回移除数量。 */
    public static int removeAll() {
        if (!ensureResolved()) {
            return 0;
        }
        int count = 0;
        for (Object waypoint : new ArrayList<>(OWNED.keySet())) {
            try {
                Object set = OWNED.remove(waypoint);
                if (set != null) {
                    setRemove.invoke(set, waypoint);
                    count++;
                }
            } catch (Throwable t) {
                warnDriftOnce("removeAll", t);
            }
        }
        return count;
    }

    /** 是否还有我们创建、尚未移除的路径点。 */
    public static boolean hasOwnedWaypoints() {
        return !OWNED.isEmpty();
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
            waypointCtor = waypointClass.getConstructor(int.class, int.class, int.class, String.class, String.class,
                    colorClass, purposeClass, boolean.class, boolean.class);

            available = true;
        } catch (Throwable t) {
            available = false;
            warnDriftOnce("resolve", t);
        }
        return available;
    }

    private static Object currentWaypointSet() throws Exception {
        Object session = sessionGetter.invoke(minimapModule);
        if (session == null) {
            return null;
        }
        Object worldManager = worldManagerGetter.invoke(session);
        if (worldManager == null) {
            return null;
        }
        Object world = currentWorldGetter.invoke(worldManager);
        if (world == null) {
            return null;
        }
        return currentSetGetter.invoke(world);
    }

    private static boolean hasOurWaypoint(Object set, String name, String colorEnumName) {
        try {
            Object iterable = setGetWaypoints.invoke(set);
            if (!(iterable instanceof Iterable<?> waypoints)) {
                return false;
            }
            Method getName = waypointClass.getMethod("getName");
            Method getColor = waypointClass.getMethod("getWaypointColor");
            for (Object waypoint : waypoints) {
                if (!name.equals(getName.invoke(waypoint))) {
                    continue;
                }
                Object color = getColor.invoke(waypoint);
                if (color == null || colorEnumName.equals(((Enum<?>) color).name())) {
                    return true;
                }
            }
        } catch (Throwable t) {
            warnDriftOnce("hasOurWaypoint", t);
        }
        return false;
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
