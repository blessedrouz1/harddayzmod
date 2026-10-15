package com.harddayz.client;

import com.harddayz.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Оружие в руке: двуручный хват со стороны, в первом лице — у бедра, при прицеливании ствол уходит к центру
 * и прицельная линия совпадает с центром экрана; отдача и перезарядка анимированы.
 */
public final class GunRender implements IClientItemExtensions {
    public static final GunRender INSTANCE = new GunRender();

    private GunRender() {
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        // винтовку держат двумя руками у плеча, пистолет — на вытянутой руке
        return stack.getItem() instanceof GunItem g && g.spec.mag() <= 17 && !g.spec.rocket() && g.spec.zoom() > 0.8f
                ? HumanoidModel.ArmPose.BOW_AND_ARROW : HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float pt, float equip, float swing) {
        if (!(stack.getItem() instanceof GunItem g)) {
            return false;
        }
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float aim = ClientGuns.aimK();
        float rec = ClientGuns.recoilK(pt);
        // перезарядка: ствол наклоняется вниз и в сторону
        float reload = 0;
        if (Minecraft.getInstance().level != null) {
            long next = com.harddayz.Compat.getLong(com.harddayz.Compat.data(stack), "next", 0);
            float left = next - Minecraft.getInstance().level.getGameTime() - pt;
            if (left > g.spec.cooldown() + 2) {
                reload = Mth.clamp(left / 10f, 0, 1) * Mth.clamp((g.spec.reload() - left) / 8f, 0, 1);
            }
        }
        float x = Mth.lerp(aim, 0.42f * side, 0);
        float y = Mth.lerp(aim, -0.40f, sightHeight(stack)) - equip * 0.6f - reload * 0.25f;
        float z = Mth.lerp(aim, -0.62f, -0.42f) + rec * 0.06f * (1 - aim * 0.5f);
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(aim, 6 * side, 0)));
        pose.mulPose(Axis.XP.rotationDegrees(rec * (aim > 0.5f ? 2.5f : 5f) - reload * 35f));
        pose.mulPose(Axis.ZP.rotationDegrees(reload * 25f * side));
        return true;
    }

    /** На какую высоту опустить модель, чтобы глаз оказался на линии прицела. */
    private static float sightHeight(ItemStack s) {
        return switch (GunItem.part(s, "sight")) {
            case "reddot" -> -0.205f;
            case "scope" -> -0.235f;
            case "thermal" -> -0.255f;
            default -> -0.165f;
        };
    }
}
