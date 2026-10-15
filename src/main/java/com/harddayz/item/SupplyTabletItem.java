package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.network.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Планшет снабжения: ПКМ — магазин фракции (бюджет, заказы, доставка). */
public class SupplyTabletItem extends Item {
    public SupplyTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            Net.toPlayer(sp, Net.shopState(sp, true));
        }
        return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.harddayz.supply_tablet.desc").withStyle(ChatFormatting.GRAY));
    }
}
