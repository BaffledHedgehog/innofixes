package baffledhedgehog.innofixes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(Entity.class)
public abstract class EntitySetPosRawSanityMixin {
    @Unique
    private static final double MAX_ABS_COORD = 30_000_000.0;
    @Unique
    private static final double MIN_Y = -1024.0;
    @Unique
    private static final double MAX_Y = 4096.0;
    @Unique
    private static final Map<UUID, double[]> LAST_SAFE_PLAYER_POSITIONS = new HashMap<>();

    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void innofixes$guardServerPlayerSetPosRaw(
        final double x,
        final double y,
        final double z,
        final CallbackInfo ci
    ) {
        final Entity self = (Entity) (Object) this;
        if (!(self instanceof ServerPlayer player)) {
            return;
        }

        final UUID playerId = player.getUUID();
        if (innofixes$isValidPosition(x, y, z)) {
            LAST_SAFE_PLAYER_POSITIONS.put(playerId, new double[]{x, y, z});
            return;
        }

        double safeX = player.getX();
        double safeY = player.getY();
        double safeZ = player.getZ();
        if (!innofixes$isValidPosition(safeX, safeY, safeZ)) {
            final double[] remembered = LAST_SAFE_PLAYER_POSITIONS.get(playerId);
            if (remembered != null && innofixes$isValidPosition(remembered[0], remembered[1], remembered[2])) {
                safeX = remembered[0];
                safeY = remembered[1];
                safeZ = remembered[2];
            } else {
                final BlockPos spawn = player.serverLevel().getSharedSpawnPos();
                safeX = spawn.getX() + 0.5;
                safeY = spawn.getY() + 1.0;
                safeZ = spawn.getZ() + 0.5;
                if (!innofixes$isValidPosition(safeX, safeY, safeZ)) {
                    safeX = 0.5;
                    safeY = 80.0;
                    safeZ = 0.5;
                }
            }
        }

        LAST_SAFE_PLAYER_POSITIONS.put(playerId, new double[]{safeX, safeY, safeZ});
        player.setDeltaMovement(Vec3.ZERO);
        ci.cancel();
        self.setPos(safeX, safeY, safeZ);
    }

    @Unique
    private static boolean innofixes$isValidPosition(final double x, final double y, final double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x) <= MAX_ABS_COORD
            && Math.abs(z) <= MAX_ABS_COORD
            && y >= MIN_Y && y <= MAX_Y;
    }
}
