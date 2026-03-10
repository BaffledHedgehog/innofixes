package baffledhedgehog.innofixes.mixin;

import com.simibubi.create.content.equipment.clipboard.ClipboardScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClipboardScreen.class, remap = false)
public abstract class ClipboardScreenReadonlySyncGuardMixin {
    @Shadow
    private boolean readonly;

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void innofixes$skipReadonlyClipboardSync(CallbackInfo ci) {
        if (this.readonly) {
            ci.cancel();
        }
    }
}
