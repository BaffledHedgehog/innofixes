package baffledhedgehog.innofixes.mixin;

import com.mna.blocks.runeforging.RunicAnvilBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(RunicAnvilBlock.class)
public abstract class RunicAnvilHammerCooldownMixin {
    @ModifyConstant(method = "onHammerUse", constant = @Constant(intValue = 10), remap = false)
    private int innofixes$removeRunesmithHammerCooldown(int original) {
        return 0;
    }

    @ModifyConstant(method = "onHammerUse", constant = @Constant(intValue = 40), remap = false)
    private int innofixes$removeRunesmithHammerLowTierCooldown(int original) {
        return 0;
    }
}
