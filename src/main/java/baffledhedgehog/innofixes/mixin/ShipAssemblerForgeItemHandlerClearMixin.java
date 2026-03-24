package baffledhedgehog.innofixes.mixin;

import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "org.valkyrienskies.mod.common.assembly.ShipAssembler", remap = false)
public abstract class ShipAssemblerForgeItemHandlerClearMixin {
    @Redirect(
        method = "moveBlocksFromTo",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/Clearable;m_18908_(Ljava/lang/Object;)V"
        )
    )
    private static void innofixes$clearForgeItemHandlersToo(Object maybeContainer) {
        if (maybeContainer instanceof Clearable clearable) {
            clearable.clearContent();
        }

        if (!(maybeContainer instanceof BlockEntity blockEntity)) {
            return;
        }

        blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(
            ShipAssemblerForgeItemHandlerClearMixin::innofixes$clearItemHandler
        );
    }

    @Unique
    private static void innofixes$clearItemHandler(IItemHandler handler) {
        if (handler instanceof IItemHandlerModifiable modifiable) {
            for (int slot = 0; slot < modifiable.getSlots(); slot++) {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
            return;
        }

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            while (!handler.getStackInSlot(slot).isEmpty()) {
                ItemStack extracted = handler.extractItem(slot, Integer.MAX_VALUE, false);
                if (extracted.isEmpty()) {
                    break;
                }
            }
        }
    }
}
