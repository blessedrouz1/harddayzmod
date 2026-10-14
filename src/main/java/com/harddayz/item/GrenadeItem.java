package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.entity.Missile;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Граната: ПКМ — бросок. Осколочная рвёт при ударе, дымовая ставит завесу. */
public class GrenadeItem extends Item {
    private final boolean smoke;

    public GrenadeItem(Properties properties, boolean smoke) {
        super(properties);
        this.smoke = smoke;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            var look = sp.getLookAngle();
            Missile m = Missile.launch(sl, Missile.Kind.BOMB, sp.getEyePosition().add(look.scale(0.6)), look.scale(1.1).add(0, 0.2, 0),
                    sp.getEyePosition().add(look.scale(30)), sp).owner(sp.getUUID());
            if (smoke) {
                m.smoke().fuse(50);
            } else {
                m.powerMul(1.1f).fuse(60);
            }
            sl.playSound(null, sp.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1f, 0.6f);
            sp.getItemInHand(hand).consume(1, sp);
            sp.getCooldowns().addCooldown(sp.getItemInHand(hand), 20);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
