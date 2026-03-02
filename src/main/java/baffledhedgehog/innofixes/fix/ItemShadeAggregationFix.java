package baffledhedgehog.innofixes.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import baffledhedgehog.innofixes.InnoFixes;
import baffledhedgehog.innofixes.entity.ShadeEntity;
import baffledhedgehog.innofixes.registry.InnoEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemShadeAggregationFix {
    private static final int ABSORB_TRIGGER_VISIBLE_ITEMS = ShadeEntity.ABSORB_TRIGGER_VISIBLE_ITEM_ENTITIES;
    private static final int TARGET_VISIBLE_ITEMS = ShadeEntity.TARGET_VISIBLE_ITEM_ENTITIES;
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int MAX_ABSORPTIONS_PER_SCAN = 4096;

    private ItemShadeAggregationFix() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (serverLevel.getGameTime() % SCAN_INTERVAL_TICKS != 0L) {
            return;
        }

        aggregateDenseItemPiles(serverLevel);
    }

    private static void aggregateDenseItemPiles(ServerLevel level) {
        Map<Long, List<ItemEntity>> itemsByCell = new HashMap<>();
        Map<Long, List<ShadeEntity>> shadesByCell = new HashMap<>();

        for (Entity entity : level.getAllEntities()) {
            if (!entity.isAlive()) {
                continue;
            }

            if (entity instanceof ItemEntity itemEntity) {
                BlockPos itemCell = itemEntity.blockPosition();
                itemsByCell.computeIfAbsent(itemCell.asLong(), ignored -> new ArrayList<>()).add(itemEntity);
                continue;
            }

            if (entity instanceof ShadeEntity shadeEntity) {
                BlockPos shadeCell = shadeEntity.getAnchorPos();
                shadesByCell.computeIfAbsent(shadeCell.asLong(), ignored -> new ArrayList<>()).add(shadeEntity);
            }
        }

        int absorbedThisScan = 0;
        for (Map.Entry<Long, List<ItemEntity>> cellEntry : itemsByCell.entrySet()) {
            if (absorbedThisScan >= MAX_ABSORPTIONS_PER_SCAN) {
                return;
            }

            List<ItemEntity> cellItems = cellEntry.getValue();
            if (cellItems.size() <= ABSORB_TRIGGER_VISIBLE_ITEMS) {
                continue;
            }

            Map<ItemStackKey, List<ItemEntity>> groupedByStack = new HashMap<>();
            for (ItemEntity itemEntity : cellItems) {
                if (!itemEntity.isAlive() || itemEntity.getItem().isEmpty()) {
                    continue;
                }

                ItemStackKey key = ItemStackKey.of(itemEntity.getItem());
                groupedByStack.computeIfAbsent(key, ignored -> new ArrayList<>()).add(itemEntity);
            }

            for (List<ItemEntity> identicalItems : groupedByStack.values()) {
                if (absorbedThisScan >= MAX_ABSORPTIONS_PER_SCAN) {
                    return;
                }

                int identicalCount = identicalItems.size();
                if (identicalCount <= ABSORB_TRIGGER_VISIBLE_ITEMS) {
                    continue;
                }

                long cellKey = cellEntry.getKey();
                int remainingBudget = MAX_ABSORPTIONS_PER_SCAN - absorbedThisScan;
                int toAbsorb = Math.min(identicalCount - TARGET_VISIBLE_ITEMS, remainingBudget);
                if (toAbsorb <= 0) {
                    continue;
                }

                ItemStack sample = identicalItems.get(0).getItem();
                List<ShadeEntity> shadesInCell = shadesByCell.computeIfAbsent(cellKey, ignored -> new ArrayList<>());
                ShadeEntity targetShade = findMatchingShade(shadesInCell, sample);
                if (targetShade == null) {
                    targetShade = createShade(level, BlockPos.of(cellKey), sample);
                    if (targetShade == null) {
                        continue;
                    }
                    shadesInCell.add(targetShade);
                }

                for (ItemEntity itemEntity : identicalItems) {
                    if (toAbsorb <= 0) {
                        break;
                    }

                    if (!itemEntity.isAlive()) {
                        continue;
                    }

                    if (targetShade.absorb(itemEntity)) {
                        toAbsorb--;
                        absorbedThisScan++;
                    }
                }
            }
        }
    }

    private static ShadeEntity findMatchingShade(List<ShadeEntity> shades, ItemStack sampleStack) {
        for (ShadeEntity shade : shades) {
            if (!shade.isAlive() || shade.getStoredItemCount() <= 0L) {
                continue;
            }

            if (shade.matchesStack(sampleStack)) {
                return shade;
            }
        }

        return null;
    }

    private static ShadeEntity createShade(ServerLevel level, BlockPos anchorPos, ItemStack sampleStack) {
        ShadeEntity shade = InnoEntities.SHADE.get().create(level);
        if (shade == null) {
            return null;
        }

        shade.initialize(anchorPos, sampleStack);
        shade.moveTo(anchorPos.getX() + 0.5D, anchorPos.getY() + 0.01D, anchorPos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(shade)) {
            return null;
        }

        return shade;
    }

    private static final class ItemStackKey {
        private final CompoundTag normalizedStackNbt;
        private final int hash;

        private ItemStackKey(CompoundTag normalizedStackNbt) {
            this.normalizedStackNbt = normalizedStackNbt;
            this.hash = normalizedStackNbt.hashCode();
        }

        public static ItemStackKey of(ItemStack stack) {
            CompoundTag normalized = new CompoundTag();
            stack.save(normalized);
            normalized.remove("Count");
            return new ItemStackKey(normalized);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof ItemStackKey other)) {
                return false;
            }

            return this.normalizedStackNbt.equals(other.normalizedStackNbt);
        }
    }
}
