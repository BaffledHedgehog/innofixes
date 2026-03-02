package baffledhedgehog.innofixes.registry;

import baffledhedgehog.innofixes.InnoFixes;
import baffledhedgehog.innofixes.entity.ShadeEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class InnoEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, InnoFixes.MOD_ID);

    public static final RegistryObject<EntityType<ShadeEntity>> SHADE = ENTITY_TYPES.register(
        "shade",
        () -> EntityType.Builder.<ShadeEntity>of(ShadeEntity::new, MobCategory.MISC)
            .sized(0.01F, 0.01F)
            .clientTrackingRange(1)
            .updateInterval(20)
            .setShouldReceiveVelocityUpdates(false)
            .noSummon()
            .build(ResourceLocation.fromNamespaceAndPath(InnoFixes.MOD_ID, "shade").toString())
    );

    private InnoEntities() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
