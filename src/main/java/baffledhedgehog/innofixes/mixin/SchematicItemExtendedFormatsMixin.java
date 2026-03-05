package baffledhedgehog.innofixes.mixin;

import baffledhedgehog.innofixes.fix.create.ExtendedSchematicFormatHelper;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.foundation.utility.CreatePaths;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mixin(value = SchematicItem.class, remap = false)
public abstract class SchematicItemExtendedFormatsMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "loadSchematic", at = @At("HEAD"), cancellable = true)
    private static void innofixes$loadExtendedFormats(
        Level level,
        ItemStack blueprint,
        CallbackInfoReturnable<StructureTemplate> cir
    ) {
        if (!blueprint.hasTag()) {
            return;
        }

        String schematic = blueprint.getTag().getString("File");
        if (!ExtendedSchematicFormatHelper.isExtendedSchematicFile(schematic)) {
            return;
        }

        StructureTemplate template = new StructureTemplate();
        String owner = blueprint.getTag().getString("Owner");

        Path baseDir;
        Path filePath;
        if (level.isClientSide()) {
            baseDir = CreatePaths.SCHEMATICS_DIR;
            filePath = Paths.get(schematic);
        } else {
            baseDir = CreatePaths.UPLOADED_SCHEMATICS_DIR;
            filePath = Paths.get(owner, schematic);
        }

        Path path = baseDir.resolve(filePath).normalize();
        if (!path.startsWith(baseDir)) {
            cir.setReturnValue(template);
            return;
        }

        try {
            template = ExtendedSchematicFormatHelper.loadExtendedTemplate(level, path, schematic);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to read schematic", e);
        }

        if (template.getSize().equals(Vec3i.ZERO)) {
            LOGGER.warn("Extended schematic '{}' produced zero bounds after conversion", schematic);
        }

        cir.setReturnValue(template);
    }
}
