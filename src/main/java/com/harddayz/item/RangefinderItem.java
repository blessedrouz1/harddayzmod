package com.harddayz.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Лазерный дальномер: ПКМ — смотреть (приближение, дальность и координаты), ЛКМ — точку в журнал целей. */
public class RangefinderItem extends Item {
    public RangefinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable("item.harddayz.rangefinder.desc").withStyle(ChatFormatting.GRAY));
    }
}
