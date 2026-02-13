package baffledhedgehog.innofixes.fix;

import artifacts.component.SwimData;
import artifacts.platform.PlatformServices;
import artifacts.registry.ModItems;
import baffledhedgehog.innofixes.InnoFixes;
import it.hurts.sskirillss.relics.api.events.common.LivingSlippingEvent;
import it.hurts.sskirillss.relics.init.ItemRegistry;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID)
public final class RollerSkatesHeliumFlamingoSpeedFix {
    private static final double ROLLER_SKATES_SLIPPERINESS = 1.075D;
    private static final double GROUND_DRAG_FACTOR = 0.91D;
    private static final float AIRBORNE_SAFE_DRAG = (float) (ROLLER_SKATES_SLIPPERINESS * GROUND_DRAG_FACTOR);
    private static final double VANILLA_GROUND_ACCELERATION_FACTOR = 0.21600002D;
    private static final double MAX_STABLE_GROUND_DRAG = 0.99D;

    private RollerSkatesHeliumFlamingoSpeedFix() {
    }

    @SubscribeEvent
    public static void onPlayerTickClampSpeed(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;
        if (player.onGround() || player.isInWater() || player.isFallFlying()) {
            return;
        }

        ItemStack rollerSkatesStack = EntityUtils.findEquippedCurio(player, ItemRegistry.ROLLER_SKATES.get());
        if (!isConflictActive(player, rollerSkatesStack)) {
            return;
        }

        double maxGroundSpeed = getRollerSkatesGroundSpeedCap(player);
        clampHorizontalSpeed(player, maxGroundSpeed);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingSlipping(LivingSlippingEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.onGround()) {
            return;
        }

        ItemStack rollerSkatesStack = EntityUtils.findEquippedCurio(player, ItemRegistry.ROLLER_SKATES.get());
        if (!isConflictActive(player, rollerSkatesStack)) {
            return;
        }

        // Match skates-like glide while keeping drag below 1.0 (no geometric growth).
        event.setFriction(Math.min(event.getFriction(), AIRBORNE_SAFE_DRAG));
    }

    private static boolean isConflictActive(Player player, ItemStack rollerSkatesStack) {
        if (rollerSkatesStack.isEmpty()) {
            return false;
        }

        if (!ModItems.HELIUM_FLAMINGO.get().isEquippedBy(player)) {
            return false;
        }

        SwimData swimData = PlatformServices.platformHelper.getSwimData(player);
        if (swimData != null && swimData.isSwimming()) {
            return true;
        }

        return player.isSwimming() && !player.isInWater();
    }

    private static double getRollerSkatesGroundSpeedCap(Player player) {
        double movementSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (movementSpeed <= 0.0D) {
            return 0.0D;
        }

        double groundDrag = Math.min(ROLLER_SKATES_SLIPPERINESS * GROUND_DRAG_FACTOR, MAX_STABLE_GROUND_DRAG);
        if (groundDrag <= 0.0D || groundDrag >= 1.0D) {
            return movementSpeed;
        }

        double groundAcceleration = movementSpeed
            * (VANILLA_GROUND_ACCELERATION_FACTOR
            / (ROLLER_SKATES_SLIPPERINESS * ROLLER_SKATES_SLIPPERINESS * ROLLER_SKATES_SLIPPERINESS));
        return (groundAcceleration * groundDrag) / (1.0D - groundDrag);
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
