package baffledhedgehog.innofixes.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPositionSanityMixin {
    @Unique
    private static final double MAX_ABS_COORD = 30_000_000.0;
    @Unique
    private static final double MIN_Y = -1024.0;
    @Unique
    private static final double MAX_Y = 4096.0;
    @Unique
    private static final Map<UUID, double[]> LAST_SAFE_POSITIONS = new HashMap<>();

    @Inject(method = "doTick", at = @At("HEAD"))
    private void innofixes$guardInvalidServerPlayerCoordinates(final CallbackInfo ci) {
        final ServerPlayer player = (ServerPlayer) (Object) this;
        final double x = player.getX();
        final double y = player.getY();
        final double z = player.getZ();
        final UUID playerId = player.getUUID();

        if (innofixes$isValidPosition(x, y, z)) {
            LAST_SAFE_POSITIONS.put(playerId, new double[]{x, y, z});
            return;
        }

        final double[] safe = LAST_SAFE_POSITIONS.get(playerId);
        final double safeX = safe != null ? safe[0] : 0.5;
        final double safeY = safe != null ? safe[1] : 80.0;
        final double safeZ = safe != null ? safe[2] : 0.5;

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(safeX, safeY, safeZ);
    }

    @Unique
    private static boolean innofixes$isValidPosition(final double x, final double y, final double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && Math.abs(x) <= MAX_ABS_COORD
            && Math.abs(z) <= MAX_ABS_COORD
            && y >= MIN_Y && y <= MAX_Y;
    }
}
