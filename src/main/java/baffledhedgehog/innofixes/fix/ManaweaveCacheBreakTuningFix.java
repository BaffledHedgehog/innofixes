package baffledhedgehog.innofixes.fix;

import baffledhedgehog.innofixes.InnoFixes;
import com.mna.blocks.tileentities.OffsetBlockTile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ManaweaveCacheBreakTuningFix {
    private static final ResourceLocation MANAWEAVE_CACHE_ID = ResourceLocation.fromNamespaceAndPath("mna", "manaweave_cache");
    private static final ResourceLocation FILLER_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("mna", "filler_block");
    private static final float BREAK_TICKS = 20.0F * 20.0F;

    private ManaweaveCacheBreakTuningFix() {}

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (isProtectedCachePartForHarvest(event)) {
            event.setCanHarvest(false);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getPosition().isEmpty()) {
            return;
        }

        BlockPos pos = event.getPosition().get();
        BlockState state = event.getState();
        if (!isProtectedCachePart(event.getEntity().level(), pos, state)) {
            return;
        }

        float hardness = state.getDestroySpeed(event.getEntity().level(), pos);
        if (hardness <= 0.0F) {
            return;
        }

        // For "no correct tool" path, break time is hardness * 100 / speed ticks.
        event.setNewSpeed(hardness * 100.0F / BREAK_TICKS);
    }

    private static boolean isProtectedCachePartForHarvest(PlayerEvent.HarvestCheck event) {
        BlockState state = event.getTargetBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (MANAWEAVE_CACHE_ID.equals(id)) {
            return true;
        }

        if (!FILLER_BLOCK_ID.equals(id)) {
            return false;
        }

        HitResult hitResult = event.getEntity().pick(20.0D, 0.0F, false);
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return false;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState lookedState = event.getEntity().level().getBlockState(pos);
        if (lookedState.getBlock() != state.getBlock()) {
            return false;
        }

        return isProtectedCachePart(event.getEntity().level(), pos, lookedState);
    }

    private static boolean isProtectedCachePart(LevelAccessor level, BlockPos pos, BlockState state) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (MANAWEAVE_CACHE_ID.equals(id)) {
            return true;
        }

        if (!FILLER_BLOCK_ID.equals(id)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof OffsetBlockTile offsetTile)) {
            return false;
        }

        BlockPos cachePos = pos.offset(offsetTile.getOffset());
        BlockState cacheState = level.getBlockState(cachePos);
        ResourceLocation cacheId = ForgeRegistries.BLOCKS.getKey(cacheState.getBlock());
        return MANAWEAVE_CACHE_ID.equals(cacheId);
    }
}
