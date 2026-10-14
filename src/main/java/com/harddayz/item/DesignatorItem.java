package com.harddayz.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
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
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            Vec3 eye = sp.getEyePosition();
            Vec3 end = eye.add(sp.getLookAngle().scale(RANGE));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, sp));
            if (hit.getType() == HitResult.Type.MISS) {
                sp.sendOverlayMessage(Component.translatable("message.harddayz.no_target").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            BlockPos p = hit.getBlockPos();
            CompoundTag tag = new CompoundTag();
            tag.putInt("tx", p.getX());
            tag.putInt("ty", p.getY());
            tag.putInt("tz", p.getZ());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            int dist = (int) Math.sqrt(p.distToCenterSqr(sp.position()));
            sp.sendOverlayMessage(Component.translatable("message.harddayz.target_set", p.getX(), p.getY(), p.getZ(), dist)
                    .withStyle(ChatFormatting.GREEN));
            level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), sp.getSoundSource(), 0.6f, 1.8f);
        }
        return InteractionResult.SUCCESS;
    }

    /** Сохранённая цель или null. */
    public static BlockPos target(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        if (!tag.contains("tx")) {
            return null;
        }
        return new BlockPos(tag.getIntOr("tx", 0), tag.getIntOr("ty", 0), tag.getIntOr("tz", 0));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.harddayz.designator.desc").withStyle(ChatFormatting.GRAY));
        BlockPos t = target(stack);
        if (t != null) {
            builder.accept(Component.translatable("item.harddayz.designator.target", t.getX(), t.getY(), t.getZ()).withStyle(ChatFormatting.GREEN));
        }
    }
}
