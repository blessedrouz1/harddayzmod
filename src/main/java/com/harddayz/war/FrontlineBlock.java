package com.harddayz.war;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Рубеж: точка захвата матча. */
public class FrontlineBlock extends Block {
    public FrontlineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer p)) {
            return;
        }
        if (!War.active()) {
            p.displayClientMessage(Component.translatable("message.harddayz.war_none").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        Frontline.add(pos);
    }
}
