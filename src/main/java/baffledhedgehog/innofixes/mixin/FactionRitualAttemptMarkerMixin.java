package baffledhedgehog.innofixes.mixin;

import com.mna.api.rituals.IRitualContext;
import com.mna.capabilities.playerdata.progression.PlayerProgressionProvider;
import com.mna.rituals.effects.RitualEffectAncientCouncil;
import com.mna.rituals.effects.RitualEffectBurningHells;
import com.mna.rituals.effects.RitualEffectColdDark;
import com.mna.rituals.effects.RitualEffectFaerieCourts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({RitualEffectAncientCouncil.class, RitualEffectBurningHells.class, RitualEffectFaerieCourts.class, RitualEffectColdDark.class})
public abstract class FactionRitualAttemptMarkerMixin {
    private static final String KEY_LAST_FACTION_RITUAL = "innofixes_last_faction_ritual";
    private static final String KEY_LAST_FACTION_RITUAL_TIME = "innofixes_last_faction_ritual_time";

    @Inject(method = "applyRitualEffect", at = @At("HEAD"), remap = false)
    private void innofixes$rememberWhichFactionRitualWasUsed(
        IRitualContext context,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (context == null || context.getCaster() == null || context.getRecipe() == null || context.getRecipe().getRegistryId() == null) {
            return;
        }

        String ritualId = normalizeRecipePath(context.getRecipe().getRegistryId());
        if (ritualId == null) {
            return;
        }

        Player caster = context.getCaster();
        caster.getPersistentData().putString(KEY_LAST_FACTION_RITUAL, ritualId);
        caster.getPersistentData().putLong(KEY_LAST_FACTION_RITUAL_TIME, caster.level().getGameTime());
    }

    @Inject(method = "applyRitualEffect", at = @At("RETURN"), remap = false)
    private void innofixes$forceTierFiveAfterSuccessfulTierFiveRitual(
        IRitualContext context,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (context == null || !cir.getReturnValueZ() || context.getCaster() == null || context.getCaster().level().isClientSide()) {
            return;
        }

        if (context.getRecipe() == null || context.getRecipe().getRegistryId() == null) {
            return;
        }

        String ritualId = normalizeRecipePath(context.getRecipe().getRegistryId());
        if (!isTierFiveFactionRitual(ritualId)) {
            return;
        }

        Player caster = context.getCaster();
        caster.getCapability(PlayerProgressionProvider.PROGRESSION).ifPresent(progression -> {
            if (progression.getTier() == 4) {
                progression.setTier(5, caster);
            }
        });
    }

    private static String normalizeRecipePath(ResourceLocation id) {
        String path = id.getPath();
        String normalized = path.substring(path.lastIndexOf('/') + 1);

        return switch (normalized) {
            case "ancient_council", "burning_hells", "faerie_courts", "cold_dark",
                "ancient_council_tier5", "burning_hells_tier5", "faerie_courts_tier5", "cold_dark_tier5" -> normalized;
            default -> null;
        };
    }

    private static boolean isTierFiveFactionRitual(String ritualId) {
        return "ancient_council_tier5".equals(ritualId)
            || "burning_hells_tier5".equals(ritualId)
            || "faerie_courts_tier5".equals(ritualId)
            || "cold_dark_tier5".equals(ritualId);
    }
}
