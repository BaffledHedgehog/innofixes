package baffledhedgehog.innofixes.mixin;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorCreateStructuresFarChunkGuardMixin {
    @Unique
    private static final int MAX_ABS_WORLD_BLOCK = 30_000_000;
    @Unique
    private static final int MAX_ABS_WORLD_CHUNK = (MAX_ABS_WORLD_BLOCK / 16) + 1;

    @Inject(method = "createStructures", at = @At("HEAD"), cancellable = true)
    private void innofixes$skipStructureGenForBrokenFarChunks(
        final RegistryAccess registryAccess,
        final ChunkGeneratorStructureState chunkGeneratorState,
        final StructureManager structureManager,
        final ChunkAccess chunkAccess,
        final StructureTemplateManager structureTemplateManager,
        final CallbackInfo ci
    ) {
        final ChunkPos chunkPos = chunkAccess.getPos();
        if (Math.abs(chunkPos.x) > MAX_ABS_WORLD_CHUNK || Math.abs(chunkPos.z) > MAX_ABS_WORLD_CHUNK) {
            ci.cancel();
        }
    }
}
