package baffledhedgehog.innofixes.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBdc;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.collision.VsiEntityPolygonCollider;
import org.valkyrienskies.core.internal.ships.VsiQueryableShipData;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.lang.reflect.Field;

@Mixin(targets = "g_mungus.vlib.util.EntityCollisionUtilsKt", remap = false)
public abstract class VLibEntityCollisionDimensionFilterMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAX_ABS_WORLD_COORD = 30_000_000.0;
    private static final int LARGE_COLLISION_SET_THRESHOLD = 1000;
    private static final long LARGE_COLLISION_LOG_COOLDOWN_TICKS = 100L;
    private static final Map<Object, Long> SHIP_CACHE_TICK =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, List<Ship>> SHIP_CACHE_SHIPS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Long> LAST_LARGE_SNAPSHOT_LOG_TICK =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Long> LAST_LARGE_INTERSECTING_LOG_TICK =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Field EM_CHUNK_INDEX_FIELD;
    private static volatile Field DS_CHUNK_MAP_FIELD;

    @Redirect(
        method = "getShipPolygonsCollidingWithEntity",
        at = @At(
            value = "INVOKE",
            target = "Lorg/valkyrienskies/core/internal/ships/VsiQueryableShipData;getIntersecting(Lorg/joml/primitives/AABBdc;)Ljava/lang/Iterable;"
        ),
        remap = false
    )
    private static Iterable<?> innofixes$useDimensionScopedIntersecting(
        @Coerce final VsiQueryableShipData<?> shipData,
        @Coerce final AABBdc aabb,
        final Entity entity,
        final Vec3 movement,
        final AABB entityBoundingBox,
        final Level world,
        final VsiEntityPolygonCollider collider
    ) {
        if (!innofixes$isValidAabb(aabb) || !innofixes$isReasonableWorldAabb(aabb)) {
            return Collections.emptyList();
        }
        final String dimensionId = VSGameUtilsKt.getDimensionId(world);
        return innofixes$collectShipsFromChunkIndex(shipData, aabb, dimensionId, world.getGameTime());
    }

    private static Iterable<?> innofixes$collectShipsFromChunkIndex(
        final VsiQueryableShipData<?> shipData,
        final AABBdc aabb,
        final String dimensionId,
        final long gameTime
    ) {
        final List<Ship> allShips = innofixes$getCachedShips(shipData, gameTime);
        innofixes$logLargeSnapshotIfNeeded(shipData, gameTime, allShips.size());
        if (allShips.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Ship> intersecting = new ArrayList<>();
        for (final Ship ship : allShips) {
            if (!dimensionId.equals(ship.getChunkClaimDimension())) {
                continue;
            }
            final AABBdc shipWorldAabb = ship.getWorldAABB();
            if (!innofixes$isValidAabb(shipWorldAabb) || !innofixes$isReasonableWorldAabb(shipWorldAabb)) {
                continue;
            }
            if (!innofixes$intersects(aabb, shipWorldAabb)) {
                continue;
            }
            intersecting.add(ship);
        }
        innofixes$logLargeIntersectingIfNeeded(shipData, gameTime, dimensionId, aabb, intersecting.size(), allShips.size());
        return intersecting;
    }

    private static List<Ship> innofixes$getCachedShips(
        final VsiQueryableShipData<?> shipData,
        final long gameTime
    ) {
        final Long cachedTick = SHIP_CACHE_TICK.get(shipData);
        final List<Ship> cachedShips = SHIP_CACHE_SHIPS.get(shipData);
        if (cachedTick != null && cachedShips != null && cachedTick == gameTime) {
            return cachedShips;
        }

        final List<Ship> rebuilt = innofixes$rebuildShipSnapshot(shipData);
        SHIP_CACHE_TICK.put(shipData, gameTime);
        SHIP_CACHE_SHIPS.put(shipData, rebuilt);
        return rebuilt;
    }

    private static List<Ship> innofixes$rebuildShipSnapshot(final VsiQueryableShipData<?> shipData) {
        final Object index = innofixes$getChunkIndex(shipData);
        if (index == null) {
            return Collections.emptyList();
        }
        final Object chunkMapObj = innofixes$getChunkMap(index);
        if (!(chunkMapObj instanceof it.unimi.dsi.fastutil.longs.Long2ObjectMap<?> chunkMap)) {
            return Collections.emptyList();
        }

        final List<Ship> ships = new ArrayList<>();
        final Set<Long> seenShipIds = new HashSet<>();
        for (final Object value : chunkMap.values()) {
            if (!(value instanceof Ship ship)) {
                continue;
            }
            if (!seenShipIds.add(ship.getId())) {
                continue;
            }
            ships.add(ship);
        }
        return ships;
    }

    private static Object innofixes$getChunkIndex(final Object shipData) {
        try {
            Field field = EM_CHUNK_INDEX_FIELD;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(shipData.getClass())) {
                field = innofixes$findFieldByType(shipData.getClass(), "org.valkyrienskies.core.impl.shadow.Ds");
                if (field == null) {
                    return null;
                }
                field.setAccessible(true);
                EM_CHUNK_INDEX_FIELD = field;
            }
            return field.get(shipData);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static Object innofixes$getChunkMap(final Object chunkIndex) {
        try {
            Field field = DS_CHUNK_MAP_FIELD;
            final Class<?> indexClass = chunkIndex.getClass();
            if (field == null || !field.getDeclaringClass().isAssignableFrom(indexClass)) {
                field = innofixes$findFieldByType(indexClass, "it.unimi.dsi.fastutil.longs.Long2ObjectMap");
                if (field == null) {
                    return null;
                }
                field.setAccessible(true);
                DS_CHUNK_MAP_FIELD = field;
            }
            return field.get(chunkIndex);
        } catch (final Throwable ignored) {
            return null;
        }
    }

    private static Field innofixes$findFieldByType(final Class<?> startClass, final String fqcn) {
        Class<?> cls = startClass;
        while (cls != null && cls != Object.class) {
            for (final Field field : cls.getDeclaredFields()) {
                if (field.getType().getName().equals(fqcn)) {
                    return field;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static boolean innofixes$intersects(final AABBdc a, final AABBdc b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX()
            && a.minY() <= b.maxY() && a.maxY() >= b.minY()
            && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }

    private static boolean innofixes$isValidAabb(final AABBdc box) {
        return box != null
            && innofixes$isFinite(box.minX()) && innofixes$isFinite(box.maxX())
            && innofixes$isFinite(box.minY()) && innofixes$isFinite(box.maxY())
            && innofixes$isFinite(box.minZ()) && innofixes$isFinite(box.maxZ())
            && box.minX() <= box.maxX()
            && box.minY() <= box.maxY()
            && box.minZ() <= box.maxZ();
    }

    private static boolean innofixes$isReasonableWorldAabb(final AABBdc box) {
        return Math.abs(box.minX()) <= MAX_ABS_WORLD_COORD
            && Math.abs(box.maxX()) <= MAX_ABS_WORLD_COORD
            && Math.abs(box.minY()) <= MAX_ABS_WORLD_COORD
            && Math.abs(box.maxY()) <= MAX_ABS_WORLD_COORD
            && Math.abs(box.minZ()) <= MAX_ABS_WORLD_COORD
            && Math.abs(box.maxZ()) <= MAX_ABS_WORLD_COORD;
    }

    private static boolean innofixes$isFinite(final double value) {
        return Double.isFinite(value);
    }

    private static void innofixes$logLargeSnapshotIfNeeded(
        final Object shipData,
        final long gameTime,
        final int snapshotSize
    ) {
        if (snapshotSize <= LARGE_COLLISION_SET_THRESHOLD) {
            return;
        }
        final Long lastTick = LAST_LARGE_SNAPSHOT_LOG_TICK.get(shipData);
        if (lastTick != null && (gameTime - lastTick) < LARGE_COLLISION_LOG_COOLDOWN_TICKS) {
            return;
        }
        LAST_LARGE_SNAPSHOT_LOG_TICK.put(shipData, gameTime);
        LOGGER.warn(
            "[InnoFixes/VS] Large ship collision snapshot: size={} (threshold={}, cooldown={}t)",
            snapshotSize,
            LARGE_COLLISION_SET_THRESHOLD,
            LARGE_COLLISION_LOG_COOLDOWN_TICKS
        );
    }

    private static void innofixes$logLargeIntersectingIfNeeded(
        final Object shipData,
        final long gameTime,
        final String dimensionId,
        final AABBdc queryAabb,
        final int intersectingSize,
        final int snapshotSize
    ) {
        if (intersectingSize <= LARGE_COLLISION_SET_THRESHOLD) {
            return;
        }
        final Long lastTick = LAST_LARGE_INTERSECTING_LOG_TICK.get(shipData);
        if (lastTick != null && (gameTime - lastTick) < LARGE_COLLISION_LOG_COOLDOWN_TICKS) {
            return;
        }
        LAST_LARGE_INTERSECTING_LOG_TICK.put(shipData, gameTime);
        LOGGER.warn(
            "[InnoFixes/VS] Large intersecting ship set: intersecting={} snapshot={} dim={} aabb=[{},{},{} -> {},{},{}] (threshold={}, cooldown={}t)",
            intersectingSize,
            snapshotSize,
            dimensionId,
            queryAabb.minX(),
            queryAabb.minY(),
            queryAabb.minZ(),
            queryAabb.maxX(),
            queryAabb.maxY(),
            queryAabb.maxZ(),
            LARGE_COLLISION_SET_THRESHOLD,
            LARGE_COLLISION_LOG_COOLDOWN_TICKS
        );
    }

}
