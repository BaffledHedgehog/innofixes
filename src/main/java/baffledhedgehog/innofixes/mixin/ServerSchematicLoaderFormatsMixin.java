package baffledhedgehog.innofixes.mixin;

import baffledhedgehog.innofixes.fix.create.ExtendedSchematicFormatHelper;
import com.simibubi.create.content.schematics.ServerSchematicLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerSchematicLoader.class, remap = false)
public abstract class ServerSchematicLoaderFormatsMixin {
    @Redirect(
        method = "handleNewUpload",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;endsWith(Ljava/lang/String;)Z")
    )
    private boolean innofixes$allowExtraExtensionsOnUpload(String fileName, String suffix) {
        return ExtendedSchematicFormatHelper.isSupportedSchematicFile(fileName);
    }

    @Redirect(
        method = "handleInstantSchematic",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;endsWith(Ljava/lang/String;)Z")
    )
    private boolean innofixes$allowExtraExtensionsOnInstant(String fileName, String suffix) {
        return ExtendedSchematicFormatHelper.isSupportedSchematicFile(fileName);
    }
}
