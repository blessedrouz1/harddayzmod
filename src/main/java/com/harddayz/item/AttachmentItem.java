package com.harddayz.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Обвес на оружие: ПКМ, держа ствол в другой руке, — поставить (старый обвес возвращается в инвентарь). */
public class AttachmentItem extends Item {
    private final String slot;
    private final String value;

    public AttachmentItem(Properties properties, String slot, String value) {
        super(properties);
        this.slot = slot;
        this.value = value;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(gun.getItem() instanceof GunItem g) || g.spec.rocket()) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.translatable("message.harddayz.attach_hint").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer sp) {
            String old = GunItem.part(gun, slot);
            if (old.equals(value)) {
                return InteractionResult.FAIL;
            }
            GunItem.setPart(gun, slot, value);
            player.getItemInHand(hand).consume(1, player);
            Item back = switch (old) {
                case "reddot" -> com.harddayz.registry.ModItems.RED_DOT.get();
                case "scope" -> com.harddayz.registry.ModItems.SCOPE.get();
                case "suppressor" -> com.harddayz.registry.ModItems.SUPPRESSOR.get();
                default -> null;
            };
            if (back != null) {
                ItemStack b = new ItemStack(back);
                if (!sp.getInventory().add(b)) {
                    sp.spawnAtLocation(sp.level(), b);
                }
            }
            level.playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1f, 1.4f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.harddayz.attachment.desc").withStyle(ChatFormatting.GRAY));
    }
}
