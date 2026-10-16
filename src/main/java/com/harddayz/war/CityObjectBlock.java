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

/** Режим «Штаб и город»: важный объект города (электростанция, вокзал, мэрия). Атака побеждает, разрушив большую часть. */
public class CityObjectBlock extends Block {
    public CityObjectBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer p)) {
            return;
        }
        String err = War.addObject(p, pos);
        p.displayClientMessage(err == null ? Component.translatable("message.harddayz.object_set", War.objectsAlive()).withStyle(ChatFormatting.GREEN)
                : Component.translatable(err).withStyle(ChatFormatting.RED), true);
    }
}
