package baffledhedgehog.innofixes.mixin;

import baffledhedgehog.innofixes.fix.create.ExtendedSchematicFormatHelper;
import com.simibubi.create.Create;
import com.simibubi.create.content.schematics.client.ClientSchematicLoader;
import com.simibubi.create.foundation.utility.CreatePaths;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Mixin(value = ClientSchematicLoader.class, remap = false)
public abstract class ClientSchematicLoaderFormatsMixin {
    @Shadow
    @Final
    private List<Component> availableSchematics;

    @Inject(method = "refresh", at = @At("RETURN"))
    private void innofixes$appendExtraFormats(CallbackInfo ci) {
        Set<String> existing = new HashSet<>();
        for (Component component : this.availableSchematics) {
            existing.add(component.getString());
        }

        try (Stream<Path> paths = Files.list(CreatePaths.SCHEMATICS_DIR)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (!ExtendedSchematicFormatHelper.isSupportedSchematicFile(fileName)) {
                    return;
                }
                if (fileName.toLowerCase().endsWith(".nbt")) {
                    return;
                }
                if (existing.add(fileName)) {
                    this.availableSchematics.add(Component.literal(fileName));
                }
            });
        } catch (NoSuchFileException ignored) {
            return;
        } catch (IOException e) {
            Create.LOGGER.error("Failed to refresh schematics", e);
            return;
        }

        this.availableSchematics.sort((aComponent, bComponent) -> {
            String a = ExtendedSchematicFormatHelper.stripSupportedExtension(aComponent.getString());
            String b = ExtendedSchematicFormatHelper.stripSupportedExtension(bComponent.getString());
            return compareNaturally(a, b);
        });
    }

    private static int compareNaturally(String a, String b) {
        int aLength = a.length();
        int bLength = b.length();
        int minSize = Math.min(aLength, bLength);
        boolean asNumeric = false;
        int lastNumericCompare = 0;

        for (int i = 0; i < minSize; ++i) {
            char aChar = a.charAt(i);
            char bChar = b.charAt(i);
            boolean aNumber = aChar >= '0' && aChar <= '9';
            boolean bNumber = bChar >= '0' && bChar <= '9';

            if (asNumeric) {
                if (aNumber && bNumber) {
                    if (lastNumericCompare == 0) {
                        lastNumericCompare = aChar - bChar;
                    }
                } else {
                    if (aNumber) {
                        return 1;
                    }
                    if (bNumber) {
                        return -1;
                    }
                    if (lastNumericCompare != 0) {
                        return lastNumericCompare;
                    }
                    if (aChar != bChar) {
                        return aChar - bChar;
                    }
                    asNumeric = false;
                }
            } else if (aNumber && bNumber) {
                asNumeric = true;
                if (lastNumericCompare == 0) {
                    lastNumericCompare = aChar - bChar;
                }
            } else if (aChar != bChar) {
                return aChar - bChar;
            }
        }

        if (asNumeric) {
            if (aLength > bLength && isAsciiNumber(a.charAt(bLength))) {
                return 1;
            }
            if (bLength > aLength && isAsciiNumber(b.charAt(aLength))) {
                return -1;
            }
            return lastNumericCompare == 0 ? aLength - bLength : lastNumericCompare;
        }

        return aLength - bLength;
    }

    private static boolean isAsciiNumber(char character) {
        return character >= '0' && character <= '9';
    }
}
