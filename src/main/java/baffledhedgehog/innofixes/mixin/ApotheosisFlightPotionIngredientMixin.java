package baffledhedgehog.innofixes.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.shadowsoffire.apotheosis.potion.PotionModule")
public abstract class ApotheosisFlightPotionIngredientMixin {
    @Redirect(
        method = "lambda$init$1",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/item/Items;f_42731_:Lnet/minecraft/world/item/Item;"
        ),
        remap = false,
        require = 0
    )
    private static Item innofixes$useManaAndArtificeFlightPylon() {
        Item pylon = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("mna", "pylon/elytra_flight"));
        if (pylon != null && pylon != Items.AIR) {
            return pylon;
        }

        return Items.POPPED_CHORUS_FRUIT;
    }
}
