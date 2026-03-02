package baffledhedgehog.innofixes.client;

import baffledhedgehog.innofixes.InnoFixes;
import baffledhedgehog.innofixes.client.renderer.ShadeEntityRenderer;
import baffledhedgehog.innofixes.registry.InnoEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = InnoFixes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InnoClientRegistries {
    private InnoClientRegistries() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(InnoEntities.SHADE.get(), ShadeEntityRenderer::new);
    }
}
