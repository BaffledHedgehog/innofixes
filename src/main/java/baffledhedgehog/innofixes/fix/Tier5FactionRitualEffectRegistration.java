package baffledhedgehog.innofixes.fix;

import baffledhedgehog.innofixes.InnoFixes;
import com.mna.Registries;
import com.mna.api.rituals.RitualEffect;
import com.mna.rituals.effects.RitualEffectAncientCouncil;
import com.mna.rituals.effects.RitualEffectBurningHells;
import com.mna.rituals.effects.RitualEffectColdDark;
import com.mna.rituals.effects.RitualEffectFaerieCourts;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Tier5FactionRitualEffectRegistration {
    private static final ResourceLocation RITUAL_ANCIENT_COUNCIL_T5 =
        ResourceLocation.fromNamespaceAndPath("mna", "rituals/ancient_council_tier5");
    private static final ResourceLocation RITUAL_BURNING_HELLS_T5 =
        ResourceLocation.fromNamespaceAndPath("mna", "rituals/burning_hells_tier5");
    private static final ResourceLocation RITUAL_FAERIE_COURTS_T5 =
        ResourceLocation.fromNamespaceAndPath("mna", "rituals/faerie_courts_tier5");
    private static final ResourceLocation RITUAL_COLD_DARK_T5 =
        ResourceLocation.fromNamespaceAndPath("mna", "rituals/cold_dark_tier5");

    private Tier5FactionRitualEffectRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterRitualEffects(RegisterEvent event) {
        ResourceKey<? extends Registry<RitualEffect>> key = getRitualEffectRegistryKey();
        if (!event.getRegistryKey().equals(key)) {
            return;
        }

        event.register(key, helper -> {
            helper.register(
                ResourceLocation.fromNamespaceAndPath(InnoFixes.MOD_ID, "ritual_effect_ancient_council_tier5"),
                new RitualEffectAncientCouncil(RITUAL_ANCIENT_COUNCIL_T5)
            );
            helper.register(
                ResourceLocation.fromNamespaceAndPath(InnoFixes.MOD_ID, "ritual_effect_burning_hells_tier5"),
                new RitualEffectBurningHells(RITUAL_BURNING_HELLS_T5)
            );
            helper.register(
                ResourceLocation.fromNamespaceAndPath(InnoFixes.MOD_ID, "ritual_effect_faerie_courts_tier5"),
                new RitualEffectFaerieCourts(RITUAL_FAERIE_COURTS_T5)
            );
            helper.register(
                ResourceLocation.fromNamespaceAndPath(InnoFixes.MOD_ID, "ritual_effect_cold_dark_tier5"),
                new RitualEffectColdDark(RITUAL_COLD_DARK_T5)
            );
        });
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<? extends Registry<RitualEffect>> getRitualEffectRegistryKey() {
        IForgeRegistry<?> registry = (IForgeRegistry<?>) Registries.RitualEffect.get();
        return (ResourceKey<? extends Registry<RitualEffect>>) (ResourceKey<?>) registry.getRegistryKey();
    }
}
