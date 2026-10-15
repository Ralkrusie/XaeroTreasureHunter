package com.treasurehunter.xaero;

import com.treasurehunter.scan.IgnoreRegionStore.Anchor;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** 忽略范围联动的反射面；与路径点发射桥独立，接口变化不会禁用普通扫描。 */
public final class XaeroAccess {
    private record Member(Class<?> owner, String name, int arguments) { }
    private static final Map<Member, Method> METHODS = new HashMap<>();
    private static final Map<Member, Field> FIELDS = new HashMap<>();

    private XaeroAccess() { }

    public static Object call(Object object, String name, Object... arguments) throws ReflectiveOperationException {
        var key = new Member(object.getClass(), name, arguments.length);
        Method method = METHODS.get(key);
        if (method == null) {
            for (Method candidate : object.getClass().getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == arguments.length) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) throw new NoSuchMethodException(name);
            METHODS.put(key, method);
        }
        return method.invoke(object, arguments);
    }

    public static Object field(Object object, String name) throws ReflectiveOperationException {
        return fieldHandle(object.getClass(), name).get(object);
    }

    public static void setField(Object object, String name, Object value) throws ReflectiveOperationException {
        fieldHandle(object.getClass(), name).set(object, value);
    }

    private static Field fieldHandle(Class<?> owner, String name) throws ReflectiveOperationException {
        var key = new Member(owner, name, -1);
        Field field = FIELDS.get(key);
        if (field == null) {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try { field = type.getDeclaredField(name); break; }
                catch (NoSuchFieldException ignored) { }
            }
            if (field == null) throw new NoSuchFieldException(name);
            field.setAccessible(true);
            FIELDS.put(key, field);
        }
        return field;
    }

    public static Object currentWorld() throws ReflectiveOperationException {
        Object minimap = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
        Object session = call(minimap, "getCurrentSession");
        if (session == null) return null;
        Object manager = call(session, "getWorldManager");
        return manager == null ? null : call(manager, "getCurrentWorld");
    }

    public static String dimension(Object world) throws ReflectiveOperationException {
        Object key = call(world, "getDimId");
        return key instanceof ResourceKey<?> resourceKey ? resourceKey.identifier().toString() : null;
    }

    public static String worldPath(Object world) throws ReflectiveOperationException {
        return call(world, "getFullPath").toString();
    }

    public static Anchor anchor(Object world, String set, Object point) throws ReflectiveOperationException {
        return new Anchor(worldPath(world), set, (String) call(point, "getName"),
                (int) call(point, "getX"), (int) call(point, "getY"), (int) call(point, "getZ"));
    }

    public static Iterable<?> allWorlds(Object world) throws ReflectiveOperationException {
        Object root = call(call(world, "getContainer"), "getRoot");
        return (Iterable<?>) call(root, "getAllWorldsIterable");
    }
}
