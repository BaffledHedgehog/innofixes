package baffledhedgehog.innofixes.fix;

import artifacts.component.SwimData;
import artifacts.platform.PlatformServices;
import artifacts.registry.ModItems;
import baffledhedgehog.innofixes.InnoFixes;
import it.hurts.sskirillss.relics.api.events.common.LivingSlippingEvent;
import it.hurts.sskirillss.relics.init.ItemRegistry;
import it.hurts.sskirillss.relics.utils.EntityUtils;
import net.minecraft.util.Mth;
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
    private static final double VANILLA_AIR_ACCELERATION = 0.02D;
    private static final double VANILLA_GROUND_ACCELERATION_FACTOR = 0.21600002D;
    private static final double MAX_STABLE_GROUND_DRAG = 0.99D;
    private static final double MIN_AIRBORNE_DRAG = 0.91D;
    private static final double MAX_AIRBORNE_DRAG = 0.9995D;

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

        applyGroundLikeAcceleration(player);

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
        double maxGroundSpeed = getRollerSkatesGroundSpeedCap(player);
        double dynamicDrag = getDynamicAirborneDragLimit(player, maxGroundSpeed);
        event.setFriction((float) Math.min(event.getFriction(), dynamicDrag));
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
        double groundAcceleration = getRollerSkatesGroundAcceleration(player);
        double groundDrag = getRollerSkatesGroundDrag();
        return solveTerminalSpeed(groundAcceleration, groundDrag);
    }

    private static void applyGroundLikeAcceleration(Player player) {
        float strafe = player.xxa;
        float forward = player.zza;
        if (strafe == 0.0F && forward == 0.0F) {
            return;
        }

        double groundAcceleration = getRollerSkatesGroundAcceleration(player);
        double extraAcceleration = groundAcceleration - VANILLA_AIR_ACCELERATION;
        if (extraAcceleration <= 0.0D) {
            return;
        }

        player.moveRelative((float) extraAcceleration, new Vec3(strafe, 0.0D, forward));
    }

    private static double getRollerSkatesGroundAcceleration(Player player) {
        double movementSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (movementSpeed <= 0.0D) {
            return 0.0D;
        }

        return movementSpeed
            * (VANILLA_GROUND_ACCELERATION_FACTOR
            / (ROLLER_SKATES_SLIPPERINESS * ROLLER_SKATES_SLIPPERINESS * ROLLER_SKATES_SLIPPERINESS));
    }

    private static double getRollerSkatesGroundDrag() {
        return Math.min(ROLLER_SKATES_SLIPPERINESS * GROUND_DRAG_FACTOR, MAX_STABLE_GROUND_DRAG);
    }

    private static double solveTerminalSpeed(double acceleration, double drag) {
        if (acceleration <= 0.0D || drag <= 0.0D) {
            return 0.0D;
        }

        if (drag >= 1.0D) {
            return Double.POSITIVE_INFINITY;
        }

        return (acceleration * drag) / (1.0D - drag);
    }

    private static double getDynamicAirborneDragLimit(Player player, double targetGroundSpeed) {
        if (!(targetGroundSpeed > 0.0D) || Double.isInfinite(targetGroundSpeed)) {
            return MIN_AIRBORNE_DRAG;
        }

        double inputStrength = getInputStrength(player);
        double effectiveAirAcceleration = VANILLA_AIR_ACCELERATION * inputStrength;
        if (effectiveAirAcceleration <= 0.0D) {
            return getRollerSkatesGroundDrag();
        }

        // Invert v* = (a * d) / (1 - d) -> d = v* / (v* + a).
        double solvedDrag = targetGroundSpeed / (targetGroundSpeed + effectiveAirAcceleration);
        return Mth.clamp(solvedDrag, MIN_AIRBORNE_DRAG, MAX_AIRBORNE_DRAG);
    }

    private static double getInputStrength(Player player) {
        double strafe = player.xxa;
        double forward = player.zza;
        double length = Math.sqrt(strafe * strafe + forward * forward);
        return Mth.clamp(length, 0.0D, 1.0D);
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
