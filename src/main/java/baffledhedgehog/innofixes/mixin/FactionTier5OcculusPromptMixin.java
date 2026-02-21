package baffledhedgehog.innofixes.mixin;

import com.mna.factions.Council;
import com.mna.factions.Demons;
import com.mna.factions.FeyCourt;
import com.mna.factions.Undead;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Council.class, Demons.class, FeyCourt.class, Undead.class})
public abstract class FactionTier5OcculusPromptMixin {
    @Inject(method = "getOcculusTaskPrompt", at = @At("HEAD"), cancellable = true, remap = false)
    private void innofixes$useTier5PromptForHighTierProgression(int currentTier, CallbackInfoReturnable<Component> cir) {
        if (currentTier < 4) {
            return;
        }

        Object self = this;
        if (self instanceof Council) {
            cir.setReturnValue(Component.translatable("mna:rituals/ancient_council_tier5"));
        } else if (self instanceof Demons) {
            cir.setReturnValue(Component.translatable("mna:rituals/burning_hells_tier5"));
        } else if (self instanceof FeyCourt) {
            cir.setReturnValue(Component.translatable("mna:rituals/faerie_courts_tier5"));
        } else if (self instanceof Undead) {
            cir.setReturnValue(Component.translatable("mna:rituals/cold_dark_tier5"));
        }
    }
}
