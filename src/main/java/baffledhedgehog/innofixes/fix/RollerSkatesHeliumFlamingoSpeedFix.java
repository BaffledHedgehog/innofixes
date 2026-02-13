package baffledhedgehog.innofixes.fix;

import artifacts.component.SwimData;
import artifacts.platform.PlatformServices;
import artifacts.registry.ModItems;
import baffledhedgehog.innofixes.InnoFixes;
import it.hurts.sskirillss.relics.init.ItemRegistry;
import it.hurts.sskirillss.relics.items.relics.base.IRelicItem;
import it.hurts.sskirillss.relics.items.relics.feet.RollerSkatesItem;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID)
public final class RollerSkatesHeliumFlamingoSpeedFix {
    private static final double DEFAULT_BLOCK_FRICTION = 0.6D;
    private static final double GROUND_DRAG_FACTOR = 0.91D;
    private static final double VANILLA_GROUND_ACCELERATION_FACTOR = 0.21600002D;
    private static final double MAX_STABLE_GROUND_DRAG = 0.99D;

    private RollerSkatesHeliumFlamingoSpeedFix() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        if (player.level().isClientSide || player.onGround() || player.isInWater() || player.isFallFlying()) {
            return;
        }

        SwimData swimData = PlatformServices.platformHelper.getSwimData(player);
        if (swimData == null || !swimData.isSwimming()) {
            return;
        }

        if (!ModItems.HELIUM_FLAMINGO.get().isEquippedBy(player)) {
            return;
        }

        ItemStack rollerSkatesStack = EntityUtils.findEquippedCurio(player, ItemRegistry.ROLLER_SKATES.get());
        if (rollerSkatesStack.isEmpty()) {
            return;
        }

        double maxGroundSpeed = getRollerSkatesGroundSpeedCap(player, rollerSkatesStack);
        clampHorizontalSpeed(player, maxGroundSpeed);
    }

    private static double getRollerSkatesGroundSpeedCap(Player player, ItemStack rollerSkatesStack) {
        double movementSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);

        if (rollerSkatesStack.getItem() instanceof IRelicItem relicItem) {
            int skatingDuration = rollerSkatesStack.getTag() == null
                ? 0
                : rollerSkatesStack.getTag().getInt(RollerSkatesItem.TAG_SKATING_DURATION);
            double speedPerDuration = relicItem.getAbilityValue(rollerSkatesStack, "skating", "speed");
            double skatingMultiplier = Math.max(0.0D, skatingDuration) * Math.max(0.0D, speedPerDuration);
            double skatesEstimatedSpeed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * (1.0D + skatingMultiplier);
            movementSpeed = Math.max(movementSpeed, skatesEstimatedSpeed);
        }

        if (movementSpeed <= 0.0D) {
            return 0.0D;
        }

        double blockFriction = getGroundBlockFriction(player);
        double groundDrag = Math.min(blockFriction * GROUND_DRAG_FACTOR, MAX_STABLE_GROUND_DRAG);
        if (groundDrag <= 0.0D || groundDrag >= 1.0D) {
            return movementSpeed;
        }

        double groundAcceleration = movementSpeed * (VANILLA_GROUND_ACCELERATION_FACTOR / (blockFriction * blockFriction * blockFriction));
        return (groundAcceleration * groundDrag) / (1.0D - groundDrag);
    }

    private static double getGroundBlockFriction(Player player) {
        BlockState state = player.level().getBlockState(player.getOnPos());
        double friction = state.getBlock().getFriction();

        if (!(friction > 0.0D)) {
            friction = DEFAULT_BLOCK_FRICTION;
        }

        return Math.max(0.05D, Math.min(0.98D, friction));
    }

    private static void clampHorizontalSpeed(Player player, double maxSpeed) {
        if (maxSpeed <= 0.0D) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        double horizontalSpeedSqr = velocity.x * velocity.x + velocity.z * velocity.z;
        double maxSpeedSqr = maxSpeed * maxSpeed;

        if (horizontalSpeedSqr <= maxSpeedSqr) {
            return;
        }

        double scale = maxSpeed / Math.sqrt(horizontalSpeedSqr);
        player.setDeltaMovement(velocity.x * scale, velocity.y, velocity.z * scale);
        player.hurtMarked = true;
    }
}
