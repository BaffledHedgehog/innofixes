package baffledhedgehog.innofixes.mixin;

import com.mna.api.capabilities.IPlayerProgression;
import com.mna.factions.Factions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.mna.capabilities.playerdata.progression.PlayerProgression")
public abstract class TierFiveAscensionGateMixin {
    private static final String KEY_LAST_FACTION_RITUAL = "innofixes_last_faction_ritual";

    @Inject(method = "setTier(ILnet/minecraft/world/entity/player/Player;Z)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void innofixes$requireTier5FactionRitualForTierFiveAscension(
        int newTier,
        Player player,
        boolean notifyPlayer,
        CallbackInfo ci
    ) {
        if (player == null) {
            return;
        }

        IPlayerProgression progression = (IPlayerProgression) (Object) this;
        int currentTier = progression.getTier();
        if (currentTier != 4 || newTier != 5) {
            return;
        }

        if (player.isCreative() || player.hasPermissions(2)) {
            return;
        }

        String expectedKey = getExpectedTier5RitualLangKey(progression);
        if (expectedKey == null) {
            return;
        }

        String lastRitual = player.getPersistentData().getString(KEY_LAST_FACTION_RITUAL);
        if (isAllowedTier5Ritual(lastRitual, progression)) {
            return;
        }

        player.sendSystemMessage(
            Component.translatable(
                "innofixes:rituals/tier5_requires_new_ritual",
                Component.translatable(expectedKey)
            )
        );
        ci.cancel();
    }

    private static boolean isAllowedTier5Ritual(String lastRitual, IPlayerProgression progression) {
        if (lastRitual == null || lastRitual.isBlank()) {
            return false;
        }

        if (progression.getAlliedFaction() == Factions.COUNCIL) {
            return "ancient_council_tier5".equals(lastRitual);
        }
        if (progression.getAlliedFaction() == Factions.DEMONS) {
            return "burning_hells_tier5".equals(lastRitual);
        }
        if (progression.getAlliedFaction() == Factions.FEY) {
            return "faerie_courts_tier5".equals(lastRitual);
        }
        if (progression.getAlliedFaction() == Factions.UNDEAD) {
            return "cold_dark_tier5".equals(lastRitual);
        }

        return false;
    }

    private static String getExpectedTier5RitualLangKey(IPlayerProgression progression) {
        if (progression.getAlliedFaction() == Factions.COUNCIL) {
            return "mna:rituals/ancient_council_tier5";
        }
        if (progression.getAlliedFaction() == Factions.DEMONS) {
            return "mna:rituals/burning_hells_tier5";
        }
        if (progression.getAlliedFaction() == Factions.FEY) {
            return "mna:rituals/faerie_courts_tier5";
        }
        if (progression.getAlliedFaction() == Factions.UNDEAD) {
            return "mna:rituals/cold_dark_tier5";
        }

        return null;
    }
}
