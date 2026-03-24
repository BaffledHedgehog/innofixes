package baffledhedgehog.innofixes.fix;

import baffledhedgehog.innofixes.InnoFixes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mna.Registries;
import com.mna.api.rituals.RitualEffect;
import com.mna.rituals.effects.RitualEffectCreateEssence;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Reader;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BossSummonerCloneRitualEffectRegistration {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final ResourceLocation RITUAL_EPIC_CLONE =
        ResourceLocation.fromNamespaceAndPath("mna", "rituals/epic_boss_summoner_clone");
    private static final ResourceLocation RITUAL_MYTHIC_CLONE =
        ResourceLocation.fromNamespaceAndPath("mna", "rituals/mythic_boss_summoner_clone");

    private static final ResourceLocation ITEM_EPIC_SUMMONER =
        ResourceLocation.fromNamespaceAndPath("innoadditions", "epic_boss_summoner");
    private static final ResourceLocation ITEM_MYTHIC_SUMMONER =
        ResourceLocation.fromNamespaceAndPath("innoadditions", "mythic_boss_summoner");

    private BossSummonerCloneRitualEffectRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterRitualEffects(RegisterEvent event) {
        ResourceKey<? extends Registry<RitualEffect>> key = getRitualEffectRegistryKey();
        if (!event.getRegistryKey().equals(key)) {
            return;
        }

        event.register(key, helper -> {
            helper.register(
                ResourceLocation.fromNamespaceAndPath(InnoFixes.MOD_ID, "ritual_effect_epic_boss_summoner_clone"),
                new FixedItemRitualEffect(RITUAL_EPIC_CLONE, ITEM_EPIC_SUMMONER)
            );
            LOGGER.info("Registered ritual handler for {}", RITUAL_EPIC_CLONE);

            helper.register(
                ResourceLocation.fromNamespaceAndPath(InnoFixes.MOD_ID, "ritual_effect_mythic_boss_summoner_clone"),
                new FixedItemRitualEffect(RITUAL_MYTHIC_CLONE, ITEM_MYTHIC_SUMMONER)
            );
            LOGGER.info("Registered ritual handler for {}", RITUAL_MYTHIC_CLONE);
        });
    }

    @SuppressWarnings("unchecked")
    private static ResourceKey<? extends Registry<RitualEffect>> getRitualEffectRegistryKey() {
        IForgeRegistry<?> registry = (IForgeRegistry<?>) Registries.RitualEffect.get();
        return (ResourceKey<? extends Registry<RitualEffect>>) (ResourceKey<?>) registry.getRegistryKey();
    }

    private static final class FixedItemRitualEffect extends RitualEffectCreateEssence {
        private final ResourceLocation ritualId;
        private final ResourceLocation fallbackOutputItemId;
        private boolean warnedMissingItem;
        private boolean warnedMissingRecipe;
        private boolean warnedInvalidCreatesItem;

        private FixedItemRitualEffect(ResourceLocation ritualId, ResourceLocation fallbackOutputItemId) {
            super(ritualId);
            this.ritualId = ritualId;
            this.fallbackOutputItemId = fallbackOutputItemId;
        }

        @Override
        public ItemStack getOutputStack() {
            OutputSpec outputSpec = this.resolveOutputSpec();
            Item item = ForgeRegistries.ITEMS.getValue(outputSpec.itemId());
            if (item == null) {
                if (!this.warnedMissingItem) {
                    LOGGER.warn(
                        "Ritual output item {} was not found when applying clone ritual effect.",
                        outputSpec.itemId()
                    );
                    this.warnedMissingItem = true;
                }
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, outputSpec.count());
        }

        private OutputSpec resolveOutputSpec() {
            ResourceLocation recipeJsonId = ResourceLocation.fromNamespaceAndPath(
                this.ritualId.getNamespace(),
                "recipes/" + this.ritualId.getPath() + ".json"
            );
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return new OutputSpec(this.fallbackOutputItemId, 1);
            }

            Optional<Resource> resourceOptional = server.getResourceManager().getResource(recipeJsonId);
            if (resourceOptional.isEmpty()) {
                if (!this.warnedMissingRecipe) {
                    LOGGER.warn(
                        "Could not find ritual recipe json {} while resolving createsItem. Falling back to {} x1.",
                        recipeJsonId,
                        this.fallbackOutputItemId
                    );
                    this.warnedMissingRecipe = true;
                }
                return new OutputSpec(this.fallbackOutputItemId, 1);
            }

            try (Reader reader = resourceOptional.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("createsItem")) {
                    return new OutputSpec(this.fallbackOutputItemId, 1);
                }
                return this.parseCreatesItem(json.get("createsItem").getAsString());
            } catch (Exception ex) {
                if (!this.warnedInvalidCreatesItem) {
                    LOGGER.warn(
                        "Failed to parse createsItem for ritual {}. Falling back to {} x1.",
                        this.ritualId,
                        this.fallbackOutputItemId,
                        ex
                    );
                    this.warnedInvalidCreatesItem = true;
                }
                return new OutputSpec(this.fallbackOutputItemId, 1);
            }
        }

        private OutputSpec parseCreatesItem(String rawCreatesItem) {
            String raw = rawCreatesItem == null ? "" : rawCreatesItem.trim();
            if (raw.isEmpty()) {
                return new OutputSpec(this.fallbackOutputItemId, 1);
            }

            String[] parts = raw.split("\\s+");
            int count = 1;
            String itemIdString;
            if (parts.length > 1) {
                try {
                    count = Math.max(1, Integer.parseInt(parts[0]));
                    itemIdString = parts[1];
                } catch (NumberFormatException ignored) {
                    itemIdString = parts[0];
                }
            } else {
                itemIdString = parts[0];
            }

            ResourceLocation parsedItemId = ResourceLocation.tryParse(itemIdString);
            if (parsedItemId == null) {
                if (!this.warnedInvalidCreatesItem) {
                    LOGGER.warn(
                        "Invalid createsItem '{}' for ritual {}. Falling back to {} x1.",
                        rawCreatesItem,
                        this.ritualId,
                        this.fallbackOutputItemId
                    );
                    this.warnedInvalidCreatesItem = true;
                }
                return new OutputSpec(this.fallbackOutputItemId, 1);
            }

            return new OutputSpec(parsedItemId, count);
        }
    }

    private record OutputSpec(ResourceLocation itemId, int count) {
    }
}
