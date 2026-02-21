package baffledhedgehog.innofixes.mixin;

import com.mna.ManaAndArtifice;
import com.mna.recipes.manaweaving.ManaweavingPattern;
import com.mna.recipes.manaweaving.ManaweavingPatternSerializer;
import com.mna.tools.manaweave.RecognitionEngine;
import com.mna.tools.manaweave.SampleData;
import com.mna.tools.manaweave.neural.SelfOrganizingMap;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Mixin(value = RecognitionEngine.class, remap = false)
public abstract class ManaweaveRecognitionEngineSafetyMixin {
    @Shadow
    private SelfOrganizingMap net;

    @Shadow
    private boolean halt;

    @Shadow
    @Final
    private HashMap<ResourceLocation, ArrayList<SampleData>> trainingData;

    @Invoker("train")
    protected abstract void innofixes$invokeTrain();

    @Inject(method = "recognize", at = @At("HEAD"), cancellable = true)
    private void innofixes$preventNullNetCrash(boolean[][] pattern, CallbackInfoReturnable<ResourceLocation> cir) {
        if (this.net != null) {
            return;
        }

        RecognitionEngine self = (RecognitionEngine) (Object) this;

        if (this.trainingData.isEmpty() && !ManaweavingPatternSerializer.ALL_RECIPES.isEmpty()) {
            for (Map.Entry<ResourceLocation, ManaweavingPattern> entry : ManaweavingPatternSerializer.ALL_RECIPES.entrySet()) {
                ManaweavingPattern recipe = entry.getValue();
                if (recipe == null) {
                    continue;
                }

                byte[][] bytes = recipe.get();
                if (!is11x11(bytes)) {
                    continue;
                }

                self.registerTrainingDataSample(entry.getKey(), bytes);
            }
            this.halt = false;
        }

        if (this.net == null && !this.trainingData.isEmpty()) {
            this.halt = false;
            try {
                this.innofixes$invokeTrain();
            } catch (Throwable t) {
                ManaAndArtifice.LOGGER.error("InnoFixes: failed to train manaweave recognition network.", t);
            }
        }

        if (this.net == null) {
            ManaAndArtifice.LOGGER.error(
                "InnoFixes: manaweave recognition network is unavailable (trainingData={}, patternRecipes={}). Returning no match to avoid crash.",
                this.trainingData.size(),
                ManaweavingPatternSerializer.ALL_RECIPES.size()
            );
            cir.setReturnValue(null);
        }
    }

    private static boolean is11x11(byte[][] data) {
        if (data == null || data.length != 11) {
            return false;
        }
        for (byte[] row : data) {
            if (row == null || row.length != 11) {
                return false;
            }
        }
        return true;
    }
}

