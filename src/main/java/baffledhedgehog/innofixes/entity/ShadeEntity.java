package baffledhedgehog.innofixes.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class ShadeEntity extends Entity {
    public static final int ABSORB_TRIGGER_VISIBLE_ITEM_ENTITIES = 100;
    public static final int TARGET_VISIBLE_ITEM_ENTITIES = 70;

    private static final String TAG_ANCHOR_POS = "AnchorPos";
    private static final String TAG_TEMPLATE_STACK = "TemplateStack";
    private static final String TAG_STORED_COUNT = "StoredCount";
    private static final String TAG_RELEASING = "Releasing";
    private static final String TAG_RELEASE_COOLDOWN = "ReleaseCooldown";
    private static final int RELEASE_INTERVAL_TICKS = 4;

    private BlockPos anchorPos = BlockPos.ZERO;
    private ItemStack templateStack = ItemStack.EMPTY;
    private long storedItemCount;
    private boolean releasing;
    private int releaseCooldown = RELEASE_INTERVAL_TICKS;

    public ShadeEntity(EntityType<? extends ShadeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.anchorPos = BlockPos.of(tag.getLong(TAG_ANCHOR_POS));

        if (tag.contains(TAG_TEMPLATE_STACK, Tag.TAG_COMPOUND)) {
            this.templateStack = ItemStack.of(tag.getCompound(TAG_TEMPLATE_STACK));
        } else {
            this.templateStack = ItemStack.EMPTY;
        }

        this.storedItemCount = Math.max(0L, tag.getLong(TAG_STORED_COUNT));

        this.releasing = tag.getBoolean(TAG_RELEASING);
        this.releaseCooldown = Mth.clamp(tag.getInt(TAG_RELEASE_COOLDOWN), 1, RELEASE_INTERVAL_TICKS);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(TAG_ANCHOR_POS, this.anchorPos.asLong());

        if (!this.templateStack.isEmpty()) {
            tag.put(TAG_TEMPLATE_STACK, this.templateStack.save(new CompoundTag()));
        }

        tag.putLong(TAG_STORED_COUNT, this.storedItemCount);
        tag.putBoolean(TAG_RELEASING, this.releasing);
        tag.putInt(TAG_RELEASE_COOLDOWN, this.releaseCooldown);
    }

    @Override
    public void tick() {
        super.tick();

        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
        this.setDeltaMovement(Vec3.ZERO);

        if (this.level().isClientSide) {
            return;
        }

        if (this.templateStack.isEmpty() || this.storedItemCount <= 0L) {
            this.discard();
            return;
        }

        int visibleMatchingEntities = getMatchingGroundItemEntityCount();
        if (visibleMatchingEntities >= TARGET_VISIBLE_ITEM_ENTITIES) {
            this.releasing = false;
            return;
        }

        this.releasing = true;
        if (--this.releaseCooldown > 0) {
            return;
        }

        this.releaseCooldown = RELEASE_INTERVAL_TICKS;
        releaseOneStoredItem();
        if (this.storedItemCount <= 0L) {
            this.discard();
        }
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public void initialize(BlockPos newAnchorPos, ItemStack sampleStack) {
        this.anchorPos = newAnchorPos.immutable();
        this.templateStack = sampleStack.copy();
        this.templateStack.setCount(1);
        this.releasing = false;
        this.releaseCooldown = RELEASE_INTERVAL_TICKS;
    }

    public BlockPos getAnchorPos() {
        return this.anchorPos;
    }

    public boolean isReleasing() {
        return this.releasing;
    }

    public boolean matchesStack(ItemStack stack) {
        return !this.templateStack.isEmpty() && ItemStack.isSameItemSameTags(this.templateStack, stack);
    }

    public long getStoredItemCount() {
        return this.storedItemCount;
    }

    public boolean absorb(ItemEntity itemEntity) {
        if (this.level().isClientSide || !itemEntity.isAlive()) {
            return false;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return false;
        }

        if (this.templateStack.isEmpty()) {
            this.templateStack = stack.copy();
            this.templateStack.setCount(1);
        } else if (!matchesStack(stack)) {
            return false;
        }

        this.storedItemCount += stack.getCount();
        this.releasing = false;
        this.releaseCooldown = RELEASE_INTERVAL_TICKS;
        itemEntity.discard();
        return true;
    }

    private int getMatchingGroundItemEntityCount() {
        AABB anchorBox = new AABB(this.anchorPos);
        return this.level().getEntitiesOfClass(
            ItemEntity.class,
            anchorBox,
            item -> item.isAlive() && ItemStack.isSameItemSameTags(this.templateStack, item.getItem())
        ).size();
    }

    private void releaseOneStoredItem() {
        if (this.storedItemCount <= 0L) {
            return;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int releaseCount = (int) Math.min(this.templateStack.getMaxStackSize(), this.storedItemCount);
        ItemStack releasedStack = this.templateStack.copy();
        releasedStack.setCount(releaseCount);

        ItemEntity restored = new ItemEntity(serverLevel, this.getX(), this.getY(), this.getZ(), releasedStack);
        restored.setDeltaMovement(Vec3.ZERO);
        serverLevel.addFreshEntity(restored);
        this.storedItemCount -= releaseCount;
    }
}
