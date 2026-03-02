package baffledhedgehog.innofixes;

import baffledhedgehog.innofixes.registry.InnoEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("innofixes")
public class InnoFixes {
    public static final String MOD_ID = "innofixes";

    public InnoFixes() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        InnoEntities.register(modEventBus);
    }
}
