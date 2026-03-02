package baffledhedgehog.innofixes.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import baffledhedgehog.innofixes.entity.ShadeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public final class ShadeEntityRenderer extends EntityRenderer<ShadeEntity> {
    public ShadeEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(ShadeEntity entity, Frustum frustum, double x, double y, double z) {
        return false;
    }

    @Override
    public void render(
        ShadeEntity entity,
        float entityYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight
    ) {
    }

    @Override
    public ResourceLocation getTextureLocation(ShadeEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
