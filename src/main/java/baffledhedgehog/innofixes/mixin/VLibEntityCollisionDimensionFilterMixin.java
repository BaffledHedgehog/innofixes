package baffledhedgehog.innofixes.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.primitives.AABBdc;
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
import java.util.List;
import java.util.Map;

@Mixin(targets = "g_mungus.vlib.util.EntityCollisionUtilsKt", remap = false)
public abstract class VLibEntityCollisionDimensionFilterMixin {
    private static final double MAX_ABS_WORLD_COORD = 30_000_000.0;

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
        return innofixes$collectShipsByShipMap(shipData, aabb, dimensionId);
    }

    private static Iterable<?> innofixes$collectShipsByShipMap(
        final VsiQueryableShipData<?> shipData,
        final AABBdc aabb,
        final String dimensionId
    ) {
        final List<Ship> snapshot = new ArrayList<>();
        try {
            final Map<Long, ?> idToShipData = shipData.getIdToShipData();
            idToShipData.forEach((id, value) -> {
                if (!(value instanceof Ship ship)) {
                    return;
                }
                if (!dimensionId.equals(ship.getChunkClaimDimension())) {
                    return;
                }
                final AABBdc shipWorldAabb = ship.getWorldAABB();
                if (!innofixes$isValidAabb(shipWorldAabb) || !innofixes$isReasonableWorldAabb(shipWorldAabb)) {
                    return;
                }
                if (!innofixes$intersects(aabb, shipWorldAabb)) {
                    return;
                }
                snapshot.add(ship);
            });
        } catch (final Throwable ignored) {
            // Return whatever was safely gathered before an unexpected map iteration failure.
        }
        return snapshot;
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
}
