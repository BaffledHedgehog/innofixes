package baffledhedgehog.innofixes.mixin;

import brightspark.asynclocator.AsyncLocator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mixin(value = AsyncLocator.class, remap = false)
public abstract class AsyncLocatorTimeoutMixin {
    private static final long LOCATE_TIMEOUT_SECONDS = 30L;
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "innofixes-async-locator-timeout");
            thread.setDaemon(true);
            return thread;
        });

    @Inject(
        method = "locate(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/tags/TagKey;Lnet/minecraft/core/BlockPos;IZ)Lbrightspark/asynclocator/AsyncLocator$LocateTask;",
        at = @At("RETURN")
    )
    private static void innofixes$addTagLocateTimeout(
        ServerLevel level,
        TagKey<Structure> structureTag,
        BlockPos origin,
        int searchRadius,
        boolean skipReferenced,
        CallbackInfoReturnable<AsyncLocator.LocateTask<BlockPos>> cir
    ) {
        innofixes$scheduleTimeout(cir.getReturnValue());
    }

    @Inject(
        method = "locate(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lbrightspark/asynclocator/AsyncLocator$LocateTask;",
        at = @At("RETURN")
    )
    private static void innofixes$addHolderSetLocateTimeout(
        ServerLevel level,
        HolderSet<Structure> structures,
        BlockPos origin,
        int searchRadius,
        boolean skipReferenced,
        CallbackInfoReturnable<AsyncLocator.LocateTask<?>> cir
    ) {
        innofixes$scheduleTimeout(cir.getReturnValue());
    }

    private static void innofixes$scheduleTimeout(AsyncLocator.LocateTask<?> task) {
        if (task == null) {
            return;
        }

        TIMEOUT_EXECUTOR.schedule(() -> {
            if (task.completableFuture().isDone()) {
                return;
            }

            // Complete with "not found" so command/ritual callbacks can notify the player in chat.
            task.completableFuture().complete(null);
            // Try to interrupt the stalled worker task.
            task.taskFuture().cancel(true);
            // Rebuild async-locator executor so next requests are not blocked by a stuck worker.
            AsyncLocator.setupExecutorService();
        }, LOCATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
