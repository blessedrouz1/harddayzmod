package com.harddayz.war;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Громкоговоритель: воет, когда РЛС сети засекли цель в 400 блоках от него. */
public class SirenBlock extends Block {
    public SirenBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer p)) {
            return;
        }
        War.addSiren(RadarNet.key(p.getUUID()), pos);
        p.sendOverlayMessage(Component.translatable("message.harddayz.siren_set").withStyle(ChatFormatting.GREEN));
    }
}
