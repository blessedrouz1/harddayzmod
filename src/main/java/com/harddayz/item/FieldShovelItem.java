package com.harddayz.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;

/** Сапёрная лопата: ПКМ по земле — выкопать участок окопа 3 блока вперёд глубиной 2 (только мягкий грунт). */
public class FieldShovelItem extends Item {
    public FieldShovelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        Direction dir = ctx.getPlayer().getDirection();
        BlockPos start = ctx.getClickedPos();
        int dug = 0;
        for (int i = 0; i < 3; i++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockPos p = start.relative(dir, i).below(dy);
                var s = level.getBlockState(p);
                if (!s.isAir() && s.is(BlockTags.MINEABLE_WITH_SHOVEL) && s.getDestroySpeed(level, p) < 3) {
                    level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    dug++;
                }
            }
        }
        if (dug > 0) {
            level.playSound(null, start, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.2f, 0.8f);
            ctx.getItemInHand().hurtAndBreak(1, ctx.getPlayer(), ctx.getHand());
            ctx.getPlayer().getCooldowns().addCooldown(ctx.getItemInHand(), 15);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.harddayz.field_shovel.desc").withStyle(ChatFormatting.GRAY));
    }
}
