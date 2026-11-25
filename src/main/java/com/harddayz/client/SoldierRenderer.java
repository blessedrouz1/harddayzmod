package com.harddayz.client;

import com.harddayz.HardDayz;
import com.harddayz.entity.Soldier;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Солдат-бот: модель игрока в форме своей стороны, оружие у плеча. */
public class SoldierRenderer extends HumanoidMobRenderer<Soldier, PlayerModel<Soldier>> {
    private static final ResourceLocation[] SKINS = new ResourceLocation[4];

    static {
        for (int i = 0; i < 4; i++) {
            SKINS[i] = new ResourceLocation(HardDayz.MODID, "textures/entity/soldier_" + i + ".png");
        }
    }

    public SoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public void render(Soldier e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        model.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
        super.render(e, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Soldier e) {
        return SKINS[e.faction()];
    }
}
