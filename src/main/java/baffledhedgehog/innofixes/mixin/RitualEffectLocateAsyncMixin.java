package baffledhedgehog.innofixes.mixin;

import brightspark.asynclocator.AsyncLocator;
import com.mna.api.rituals.IRitualContext;
import com.mna.entities.utility.PresentItem;
import com.mna.items.ItemInit;
import com.mna.items.artifice.ThaumaturgicCompass;
import com.mna.items.ritual.ThaumaturgicLink;
import com.mna.rituals.effects.RitualEffectLocate;
import com.mna.rituals.effects.WorldUtils;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Mixin(value = RitualEffectLocate.class, remap = false)
public abstract class RitualEffectLocateAsyncMixin {
    private static final int STRUCTURE_SEARCH_RADIUS_CHUNKS = 100;
    private static final long STRUCTURE_LOCATE_TIMEOUT_SECONDS = 30L;

    @Inject(method = "applyRitualEffect", at = @At("HEAD"), cancellable = true)
    private void innofixes$applyRitualEffectAsync(IRitualContext context, CallbackInfoReturnable<Boolean> cir) {
        Optional<ItemStack> maybeLink = context.getCollectedReagents(
            stack -> stack.getItem() == ItemInit.THAUMATURGIC_LINK.get()
        ).stream().findFirst();

        if (maybeLink.isEmpty()) {
            return;
        }

        if (!(context.getLevel() instanceof ServerLevel level)) {
            cir.setReturnValue(false);
            return;
        }

        BlockPos center = context.getCenter().immutable();
        ItemStack linkStack = maybeLink.get().copy();
        ItemStack compass = new ItemStack(ItemInit.THAUMATURGIC_COMPASS.get());
        List<ItemStack> reagentsToReturn = context.getCollectedReagents().stream()
            .map(ItemStack::copy)
            .collect(Collectors.toList());

        ThaumaturgicLink linkItem = (ThaumaturgicLink) ItemInit.THAUMATURGIC_LINK.get();
        ResourceLocation locationKey = linkItem.getLocationKey(linkStack);

        if (locationKey == null) {
            this.innofixes$handleFailedLocate(context, center, reagentsToReturn);
            cir.setReturnValue(false);
            return;
        }

        HolderSet<Structure> structures = innofixes$resolveStructureHolderSet(level, locationKey);
        if (structures == null) {
            this.innofixes$finishLocate(context, level, center, linkStack, compass, null, reagentsToReturn);
            cir.setReturnValue(false);
            return;
        }

        AsyncLocator.LocateTask<Pair<BlockPos, Holder<Structure>>> locateTask = AsyncLocator.locate(
            level,
            structures,
            center,
            STRUCTURE_SEARCH_RADIUS_CHUNKS,
            false
        );

        locateTask.completableFuture()
            .orTimeout(STRUCTURE_LOCATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete((result, error) -> level.getServer().execute(() -> {
                if (error != null) {
                    locateTask.cancel();
                }

                BlockPos structurePos = result == null ? null : result.getFirst();
                this.innofixes$finishLocate(
                    context,
                    level,
                    center,
                    linkStack,
                    compass,
                    structurePos,
                    reagentsToReturn
                );
            }));

        cir.setReturnValue(false);
    }

    private void innofixes$finishLocate(
        IRitualContext context,
        ServerLevel level,
        BlockPos center,
        ItemStack linkStack,
        ItemStack compass,
        BlockPos structurePos,
        List<ItemStack> reagentsToReturn
    ) {
        boolean biomeTrack = false;
        BlockPos target = structurePos;

        if (target == null) {
            biomeTrack = true;
            target = WorldUtils.locateBiome(level, center, linkStack);
        }

        if (target == null) {
            this.innofixes$handleFailedLocate(context, center, reagentsToReturn);
            return;
        }

        level.playSound(null, center, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 1.0F);

        ThaumaturgicCompass.setTrackedPosition(
            compass,
            level.dimension(),
            target,
            ((ThaumaturgicLink) ItemInit.THAUMATURGIC_LINK.get()).getLocationKey(linkStack),
            biomeTrack ? ThaumaturgicCompass.TrackType.Biome : ThaumaturgicCompass.TrackType.Structure
        );

        PresentItem presentItem = new PresentItem(
            level,
            center.getX(),
            center.above().getY(),
            center.getZ(),
            compass
        );
        level.addFreshEntity(presentItem);
    }

    private void innofixes$handleFailedLocate(IRitualContext context, BlockPos center, List<ItemStack> reagentsToReturn) {
        for (ItemStack stack : reagentsToReturn) {
            ItemEntity itemEntity = new ItemEntity(
                context.getLevel(),
                center.getX(),
                center.above().getY(),
                center.getZ(),
                stack
            );
            context.getLevel().addFreshEntity(itemEntity);
        }

        Player caster = context.getCaster();
        if (caster != null) {
            caster.sendSystemMessage(Component.translatable("mna:rituals/locating.failed"));
        }
    }

    private static HolderSet<Structure> innofixes$resolveStructureHolderSet(ServerLevel level, ResourceLocation structureId) {
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure structure = structureRegistry.get(structureId);
        if (structure == null) {
            return null;
        }

        Optional<ResourceKey<Structure>> key = structureRegistry.getResourceKey(structure);
        if (key.isEmpty()) {
            return null;
        }

        Optional<Holder.Reference<Structure>> holder = structureRegistry.getHolder(key.get());
        return holder.<HolderSet<Structure>>map(HolderSet::direct).orElse(null);
    }
}
