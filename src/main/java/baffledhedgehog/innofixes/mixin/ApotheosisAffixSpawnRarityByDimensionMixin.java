package baffledhedgehog.innofixes.mixin;

import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

@Mixin(targets = "dev.shadowsoffire.apotheosis.adventure.AdventureEvents", remap = false)
public abstract class ApotheosisAffixSpawnRarityByDimensionMixin {
    @Redirect(
        method = "special",
        at = @At(
            value = "INVOKE",
            target = "Ldev/shadowsoffire/apotheosis/adventure/loot/LootController;createRandomLootItem(Lnet/minecraft/util/RandomSource;Ldev/shadowsoffire/apotheosis/adventure/loot/LootRarity;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ServerLevelAccessor;)Lnet/minecraft/world/item/ItemStack;"
        ),
        remap = false
    )
    private ItemStack innofixes$limitAffixSpawnRarityByDimension(
        RandomSource random,
        LootRarity requestedRarity,
        Player player,
        ServerLevelAccessor level,
        MobSpawnEvent.FinalizeSpawn event
    ) {
        LootRarity forcedRarity = innofixes$resolveForcedRarity(random, player, level);
        return LootController.createRandomLootItem(
            random,
            forcedRarity != null ? forcedRarity : requestedRarity,
            player,
            level
        );
    }

    @Unique
    private static LootRarity innofixes$resolveForcedRarity(
        RandomSource random,
        Player ignoredPlayer,
        ServerLevelAccessor level
    ) {
        LootRarity common = innofixes$getRarityById("common");
        LootRarity uncommon = innofixes$getRarityById("uncommon");
        if (common == null && uncommon == null) {
            return null;
        }

        ResourceKey<Level> dimension = level.getLevel().dimension();
        if (dimension == Level.OVERWORLD) {
            return common;
        }
        if (dimension == Level.NETHER) {
            if (common != null && uncommon != null) {
                return random.nextFloat() < 0.5F ? common : uncommon;
            }
            return common != null ? common : uncommon;
        }
        if (dimension == Level.END) {
            return uncommon;
        }

        return null;
    }

    @Unique
    private static LootRarity innofixes$getRarityById(String id) {
        try {
            Class<?> registryClass = Class.forName("dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry");
            Method byLegacyId = registryClass.getMethod("byLegacyId", String.class);
            Object holder = byLegacyId.invoke(null, id);
            if (holder == null) {
                return null;
            }

            Method isBound = holder.getClass().getMethod("isBound");
            boolean bound = (boolean) isBound.invoke(holder);
            if (!bound) {
                return null;
            }

            Method get = holder.getClass().getMethod("get");
            Object rarity = get.invoke(holder);
            return rarity instanceof LootRarity lootRarity ? lootRarity : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
