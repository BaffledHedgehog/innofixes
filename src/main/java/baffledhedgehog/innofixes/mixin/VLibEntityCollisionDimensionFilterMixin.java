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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(targets = "g_mungus.vlib.util.EntityCollisionUtilsKt", remap = false)
public abstract class VLibEntityCollisionDimensionFilterMixin {
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
        final String dimensionId = VSGameUtilsKt.getDimensionId(world);
        return innofixes$collectShipsByChunkWindow(shipData, aabb, dimensionId);
    }

    private static Iterable<?> innofixes$collectShipsByChunkWindow(
        final VsiQueryableShipData<?> shipData,
        final AABBdc aabb,
        final String dimensionId
    ) {
        final int minChunkX = innofixes$blockToChunk(aabb.minX());
        final int maxChunkX = innofixes$blockToChunk(Math.nextAfter(aabb.maxX(), Double.NEGATIVE_INFINITY));
        final int minChunkZ = innofixes$blockToChunk(aabb.minZ());
        final int maxChunkZ = innofixes$blockToChunk(Math.nextAfter(aabb.maxZ(), Double.NEGATIVE_INFINITY));

        final List<Ship> snapshot = new ArrayList<>();
        final Set<Long> seenIds = new HashSet<>();

        int chunkX = minChunkX;
        while (true) {
            int chunkZ = minChunkZ;
            while (true) {
                final Ship ship = innofixes$getShipByChunkPos(shipData, chunkX, chunkZ, dimensionId);
                if (ship != null
                    && seenIds.add(ship.getId())
                    && dimensionId.equals(ship.getChunkClaimDimension())
                    && innofixes$intersects(aabb, ship.getWorldAABB())) {
                    snapshot.add(ship);
                }

                if (chunkZ == maxChunkZ || chunkZ == Integer.MAX_VALUE) {
                    break;
                }
                chunkZ++;
            }

            if (chunkX == maxChunkX || chunkX == Integer.MAX_VALUE) {
                break;
            }
            chunkX++;
        }

        return snapshot;
    }

    private static Ship innofixes$getShipByChunkPos(
        final VsiQueryableShipData<?> shipData,
        final int chunkX,
        final int chunkZ,
        final String dimensionId
    ) {
        try {
            return shipData.getByChunkPos(chunkX, chunkZ, dimensionId);
        } catch (final Throwable ignored) {
            try {
                final Ship ship = shipData.getByChunkPos(chunkX, chunkZ);
                if (ship != null && dimensionId.equals(ship.getChunkClaimDimension())) {
                    return ship;
                }
            } catch (final Throwable ignoredFallback) {
                return null;
            }
            return null;
        }
    }

    private static int innofixes$blockToChunk(final double coordinate) {
        if (coordinate <= Integer.MIN_VALUE) {
            return Integer.MIN_VALUE >> 4;
        }
        if (coordinate >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE >> 4;
        }
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static boolean innofixes$intersects(final AABBdc a, final AABBdc b) {
        return a.minX() <= b.maxX() && a.maxX() >= b.minX()
            && a.minY() <= b.maxY() && a.maxY() >= b.minY()
            && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
    }
}
