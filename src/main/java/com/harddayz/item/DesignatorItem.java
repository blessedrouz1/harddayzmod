package com.harddayz.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Лазерный целеуказатель: ПКМ — засечь точку, куда смотришь (до 1000 блоков в пределах прорисовки).
 * Потом ПКМ им по пусковой — она получает цель.
 */
public class DesignatorItem extends Item {
    public static final int RANGE = 1000;

    public DesignatorItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Vec3 eye = sp.getEyePosition();
            Vec3 end = eye.add(sp.getLookAngle().scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, sp));
            if (hit.getType() == HitResult.Type.MISS) {
                sp.displayClientMessage(Component.translatable("message.harddayz.no_target").withStyle(ChatFormatting.RED), true);
                return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.FAIL, player.getItemInHand(hand));
            }
            BlockPos p = hit.getBlockPos();
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt("tx", p.getX());
            tag.putInt("ty", p.getY());
            tag.putInt("tz", p.getZ());
            int dist = (int) Math.sqrt(p.distToCenterSqr(sp.position()));
            sp.displayClientMessage(Component.translatable("message.harddayz.target_set", p.getX(), p.getY(), p.getZ(), dist)
                    .withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), sp.getSoundSource(), 0.6f, 1.8f);
        }
        return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
    }

    /** Сохранённая цель или null. */
    public static BlockPos target(ItemStack stack) {
        CompoundTag tag = com.harddayz.Compat.data(stack);
        if (!tag.contains("tx")) {
            return null;
        }
        return new BlockPos(com.harddayz.Compat.getInt(tag, "tx", 0), com.harddayz.Compat.getInt(tag, "ty", 0), com.harddayz.Compat.getInt(tag, "tz", 0));
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.harddayz.designator.desc").withStyle(ChatFormatting.GRAY));
        BlockPos t = target(stack);
        if (t != null) {
            builder.add(Component.translatable("item.harddayz.designator.target", t.getX(), t.getY(), t.getZ()).withStyle(ChatFormatting.GREEN));
        }
    }
}
