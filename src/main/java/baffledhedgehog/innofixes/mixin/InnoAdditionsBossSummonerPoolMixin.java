package baffledhedgehog.innofixes.mixin;

import dev.shadowsoffire.apotheosis.adventure.boss.ApothBoss;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.boss.BossRegistry;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.placebo.codec.CodecProvider;
import dev.shadowsoffire.placebo.reload.WeightedDynamicRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(targets = "dev.necr0manthre.innoadditions.items.BossSummonerItem", remap = false)
public abstract class InnoAdditionsBossSummonerPoolMixin {
    @Shadow
    @Final
    private String rarityName;

    @Unique
    private static final Set<ResourceLocation> EPIC_WHITELIST = Set.of(
        innofixes$cataclysm("endermaptera"),
        innofixes$cataclysm("ender_golem"),
        innofixes$cataclysm("ignited_revenant"),
        innofixes$cataclysm("ignited_berserker"),
        innofixes$cataclysm("the_prowler"),
        innofixes$cataclysm("the_watcher"),
        innofixes$cataclysm("the_baby_leviathan"),
        innofixes$cataclysm("coralssus"),
        innofixes$cataclysm("coral_golem"),
        innofixes$cataclysm("deepling_brute"),
        innofixes$cataclysm("deepling_warlock"),
        innofixes$cataclysm("deepling_priest"),
        innofixes$cataclysm("deepling_angler"),
        innofixes$cataclysm("deepling"),
        innofixes$cataclysm("amethyst_crab"),
        innofixes$cataclysm("modern_remnant"),
        innofixes$cataclysm("kobolediator"),
        innofixes$cataclysm("wadjet"),
        innofixes$cataclysm("koboleton"),
        innofixes$cataclysm("aptrgangr"),
        innofixes$cataclysm("elite_draugr"),
        innofixes$cataclysm("royal_draugr"),
        innofixes$cataclysm("draugr"),
        innofixes$cataclysm("clawdian"),
        innofixes$cataclysm("hippocamtus"),
        innofixes$cataclysm("cindaria"),
        innofixes$cataclysm("urchinkin"),
        innofixes$cataclysm("octohost")
    );

    @Unique
    private static final Set<ResourceLocation> MYTHIC_WHITELIST = Set.of(
        innofixes$cataclysm("harbinger"),
        innofixes$cataclysm("ancient_remnant"),
        innofixes$cataclysm("netherite_monstrosity"),
        innofixes$cataclysm("ender_guardian"),
        innofixes$cataclysm("scylla"),
        innofixes$cataclysm("leviathan"),
        innofixes$cataclysm("maledictus"),
        innofixes$cataclysm("ignis")
    );

    @Redirect(
        method = {
            "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            "m_6225_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"
        },
        at = @At(
            value = "INVOKE",
            target = "Ldev/shadowsoffire/placebo/reload/WeightedDynamicRegistry$IDimensional;matches(Lnet/minecraft/world/level/Level;)Ljava/util/function/Predicate;"
        ),
        remap = false
    )
    private Predicate<WeightedDynamicRegistry.IDimensional> innofixes$allowSummonersInAnyDimension(Level level) {
        return ignored -> true;
    }

    @Redirect(
        method = {
            "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            "m_6225_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"
        },
        at = @At(
            value = "INVOKE",
            target = "Ldev/shadowsoffire/apotheosis/adventure/boss/BossRegistry;getRandomItem(Lnet/minecraft/util/RandomSource;F[Ljava/util/function/Predicate;)Ldev/shadowsoffire/placebo/codec/CodecProvider;"
        ),
        remap = false
    )
    private CodecProvider<?> innofixes$limitBossPoolBySummonerRarity(
        BossRegistry instance,
        RandomSource random,
        float luck,
        Predicate<ApothBoss>[] predicates
    ) {
        Set<ResourceLocation> whitelist = innofixes$getWhitelist(this.rarityName);
        if (whitelist == null) {
            return instance.getRandomItem(random, luck, predicates);
        }

        Predicate<ApothBoss>[] withWhitelist = Arrays.copyOf(predicates, predicates.length + 1);
        withWhitelist[predicates.length] = boss -> innofixes$matchesWhitelist(boss, whitelist);
        return instance.getRandomItem(random, luck, withWhitelist);
    }

    @Redirect(
        method = {
            "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            "m_6225_(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"
        },
        at = @At(
            value = "INVOKE",
            target = "Ldev/shadowsoffire/apotheosis/adventure/boss/ApothBoss;createBoss(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;FLdev/shadowsoffire/apotheosis/adventure/loot/LootRarity;)Lnet/minecraft/world/entity/Mob;"
        ),
        remap = false
    )
    private Mob innofixes$ensureSecondAffixDropForEpicAndMythic(
        ApothBoss instance,
        ServerLevelAccessor level,
        BlockPos pos,
        RandomSource random,
        float luck,
        LootRarity rarity
    ) {
        Mob mob = instance.createBoss(level, pos, random, luck, rarity);
        if (mob == null) {
            return null;
        }

        if ("epic".equals(this.rarityName) || "mythic".equals(this.rarityName)) {
            innofixes$addSecondGuaranteedAffixDrop(mob, random, rarity);
        }
        return mob;
    }

    @Unique
    private static void innofixes$addSecondGuaranteedAffixDrop(Mob mob, RandomSource random, LootRarity rarity) {
        if (rarity == null) {
            return;
        }

        List<EquipmentSlot> candidates = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            LootCategory category = LootCategory.forItem(stack);
            if (category.isNone()) {
                continue;
            }
            if (AffixHelper.hasAffixes(stack)) {
                continue;
            }
            candidates.add(slot);
        }

        if (candidates.isEmpty()) {
            return;
        }

        EquipmentSlot chosen = candidates.get(random.nextInt(candidates.size()));
        ItemStack source = mob.getItemBySlot(chosen);
        LootCategory category = LootCategory.forItem(source);
        if (category.isNone()) {
            return;
        }

        ItemStack affixed = LootController.createLootItem(source.copy(), category, rarity, random);
        mob.setItemSlot(chosen, affixed);
        mob.setDropChance(chosen, 2.0F);
    }

    @Unique
    private static Set<ResourceLocation> innofixes$getWhitelist(String rarityName) {
        return switch (rarityName) {
            case "epic" -> EPIC_WHITELIST;
            case "mythic" -> MYTHIC_WHITELIST;
            default -> null;
        };
    }

    @Unique
    private static ResourceLocation innofixes$cataclysm(String path) {
        return ResourceLocation.fromNamespaceAndPath("cataclysm", path);
    }

    @Unique
    private static ResourceLocation innofixes$getBossEntityId(ApothBoss boss) {
        return ForgeRegistries.ENTITY_TYPES.getKey(boss.getEntity());
    }

    @Unique
    private static boolean innofixes$matchesWhitelist(ApothBoss boss, Set<ResourceLocation> whitelist) {
        ResourceLocation entityId = innofixes$getBossEntityId(boss);
        if (entityId == null) {
            return false;
        }
        if (whitelist.contains(entityId)) {
            return true;
        }
        if (!"cataclysm".equals(entityId.getNamespace())) {
            return false;
        }

        String path = entityId.getPath();
        if ("drowned_host".equals(path)) {
            return whitelist.contains(innofixes$cataclysm("octohost"));
        }
        if ("octohost".equals(path)) {
            return whitelist.contains(innofixes$cataclysm("drowned_host"));
        }
        if (path.startsWith("the_")) {
            return whitelist.contains(innofixes$cataclysm(path.substring(4)));
        }

        return whitelist.contains(innofixes$cataclysm("the_" + path));
    }
}
