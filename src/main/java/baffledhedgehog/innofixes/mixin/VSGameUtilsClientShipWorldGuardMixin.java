package baffledhedgehog.innofixes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.internal.world.VsiClientShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Pseudo
@Mixin(targets = "org.valkyrienskies.mod.common.VSGameUtilsKt", remap = false)
public abstract class VSGameUtilsClientShipWorldGuardMixin {
    @Inject(
        method = "getShipObjectWorld(Lnet/minecraft/client/Minecraft;)Lorg/valkyrienskies/core/internal/world/VsiClientShipWorld;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void innofixes$avoidMenuShipWorldLookupFromMinecraft(
        final Minecraft minecraft,
        final CallbackInfoReturnable<VsiClientShipWorld> cir
    ) {
        if (minecraft == null || minecraft.level == null) {
            cir.setReturnValue(VSGameUtilsKt.getVsCore().getDummyShipWorldClient());
        }
    }

    @Inject(
        method = "getShipObjectWorld(Lnet/minecraft/client/multiplayer/ClientLevel;)Lorg/valkyrienskies/core/internal/world/VsiClientShipWorld;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void innofixes$avoidMenuShipWorldLookup(
        final ClientLevel level,
        final CallbackInfoReturnable<VsiClientShipWorld> cir
    ) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            cir.setReturnValue(VSGameUtilsKt.getVsCore().getDummyShipWorldClient());
        }
    }
}
