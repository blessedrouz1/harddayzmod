package com.harddayz.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Противогаз: надевается на голову (ПКМ), защищает от газа. */
public class GasMaskItem extends Item implements Equipable {
    public GasMaskItem(Properties p) {
        super(p);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.harddayz.gas_mask.desc").withStyle(ChatFormatting.GRAY));
    }
}
