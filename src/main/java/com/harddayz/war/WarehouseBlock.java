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

/** Склад фракции: сюда приходят поставки (первый склад — основной). */
public class WarehouseBlock extends Block {
    public WarehouseBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof ServerPlayer p)) {
            return;
        }
        War.Faction f = War.of(p.getUUID());
        if (f == null) {
            p.sendOverlayMessage(Component.translatable("message.harddayz.hq_no_faction").withStyle(ChatFormatting.YELLOW));
            return;
        }
        f.warehouses.add(new int[] {pos.getX(), pos.getY(), pos.getZ()});
        War.save();
        p.sendOverlayMessage(Component.translatable("message.harddayz.warehouse_set", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GREEN));
    }
}
