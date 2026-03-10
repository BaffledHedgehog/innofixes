package baffledhedgehog.innofixes.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "org.valkyrienskies.clockwork.ClockworkModClient", remap = false)
public abstract class ClockworkClientMenuSpamGuardMixin {
    @Inject(method = "initClient$lambda$3", at = @At("HEAD"), cancellable = true, remap = false)
    private static void innofixes$skipClockworkTickWhenNoWorld(final Minecraft minecraft, final CallbackInfo ci) {
        if (minecraft == null || minecraft.level == null) {
            ci.cancel();
        }
    }
}
