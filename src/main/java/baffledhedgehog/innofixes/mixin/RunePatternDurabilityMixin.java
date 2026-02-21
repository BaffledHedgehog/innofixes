package baffledhedgehog.innofixes.mixin;

import com.mna.items.runes.RunePattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(RunePattern.class)
public abstract class RunePatternDurabilityMixin {
    private static final int INNOFIXES_MAX_PATTERN_USES = 32;

    /**
     * @author baffledhedgehog
     * @reason Increase rune pattern durability from 4 uses to 32 uses.
     */
    @Overwrite
    public int getBarWidth(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("uses")) {
            int uses = Mth.clamp(stack.getTag().getInt("uses"), 0, INNOFIXES_MAX_PATTERN_USES);
            return 13 - Math.round(13.0F * (uses / (float) INNOFIXES_MAX_PATTERN_USES));
        }

        return 0;
    }

    /**
     * @author baffledhedgehog
     * @reason Keep durability bar color logic in sync with increased max uses.
     */
    @Overwrite
    public int getBarColor(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("uses")) {
            int uses = Mth.clamp(stack.getTag().getInt("uses"), 0, INNOFIXES_MAX_PATTERN_USES);
            float durability = Math.max(
                0.0F,
                (INNOFIXES_MAX_PATTERN_USES - uses) / (float) INNOFIXES_MAX_PATTERN_USES
            );
            return Mth.hsvToRgb(durability / 3.0F, 1.0F, 1.0F);
        }

        return Mth.hsvToRgb(0.0F, 1.0F, 1.0F);
    }

    /**
     * @author baffledhedgehog
     * @reason Increase rune pattern break threshold from 4 to 32 uses.
     */
    @Overwrite(remap = false)
    public static boolean incrementDamage(ItemStack stack) {
        CompoundTag nbt = stack.getOrCreateTag();
        int uses = nbt.getInt("uses") + 1;
        nbt.putInt("uses", uses);
        return uses >= INNOFIXES_MAX_PATTERN_USES;
    }
}
