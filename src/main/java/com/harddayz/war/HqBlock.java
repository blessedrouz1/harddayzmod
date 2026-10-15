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

/**
 * Штаб фракции. Поставленный участником фракции регистрируется: первый — главный, ещё два — резервные.
 * Если все штабы уничтожены — фракция проиграла.
 */
public class HqBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
    public HqBlock(Properties properties) {
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
            p.displayClientMessage(Component.translatable("message.harddayz.hq_no_faction").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int n = War.addHq(f, pos);
        if (n == 0) {
            p.displayClientMessage(Component.translatable("message.harddayz.hq_limit", War.MAX_HQ).withStyle(ChatFormatting.RED), true);
            return;
        }
        // координаты — только своей фракции, остальным — без них
        Component own = Component.translatable(n == 1 ? "message.harddayz.hq_main" : "message.harddayz.hq_reserve", f.title(), n - 1,
                pos.getX(), pos.getY(), pos.getZ());
        Component other = Component.translatable("message.harddayz.hq_placed_enemy", f.title());
        for (ServerPlayer pl : p.getServer().getPlayerList().getPlayers()) {
            pl.sendSystemMessage(f.has(pl.getUUID()) ? own : other);
        }
    }

    @Override
    public @javax.annotation.Nullable net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HqBlockEntity(pos, state);
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HqBlockEntity be) {
            War.Faction owner = War.hqOwner(pos);
            if (owner != null && !owner.has(player.getUUID()) && !player.isCreative()) {
                player.displayClientMessage(Component.translatable("message.harddayz.hq_enemy").withStyle(ChatFormatting.RED), true);
                return net.minecraft.world.InteractionResult.FAIL;
            }
            player.openMenu(be);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }
}
