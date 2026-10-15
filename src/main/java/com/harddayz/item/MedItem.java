package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.registry.ModEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Бинт, жгут, аптечка, обезболивающее: зажать ПКМ — применить на себя. ПКМ по раненому товарищу — поднять. */
public class MedItem extends Item {
    public enum Kind {
        /** Бинт: останавливает слабое кровотечение, +3 ХП. */
        BANDAGE(40, 3),
        /** Жгут: сразу останавливает любое кровотечение, но замедляет. */
        TOURNIQUET(15, 0),
        /** Аптечка: +12 ХП, останавливает кровотечение. */
        MEDKIT(70, 12),
        /** Обезболивающее: регенерация и сопротивление на время. */
        PAINKILLER(25, 0);

        final int time;
        final float heal;

        Kind(int time, float heal) {
            this.time = time;
            this.heal = heal;
        }
    }

    private final Kind kind;

    public MedItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.CONSUME, player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return kind.time;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.BRUSH;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity e) {
        if (!level.isClientSide()) {
            var bleed = ModEffects.BLEEDING.get();
            switch (kind) {
                case BANDAGE -> {
                    var b = e.getEffect(bleed);
                    if (b != null && b.getAmplifier() == 0) {
                        e.removeEffect(bleed);
                    } else if (b != null) {
                        e.addEffect(new MobEffectInstance(bleed, b.getDuration() / 2, 0)); // сильное — только ослабить
                    }
                }
                case TOURNIQUET -> {
                    e.removeEffect(bleed);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 30, 0));
                }
                case MEDKIT -> e.removeEffect(bleed);
                case PAINKILLER -> {
                    e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 12, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 20, 0));
                }
            }
            if (kind.heal > 0) {
                e.heal(kind.heal);
            }
            if (!(e instanceof Player p && p.isCreative())) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
