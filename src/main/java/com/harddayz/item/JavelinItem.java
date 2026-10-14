package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.entity.Missile;
import com.harddayz.entity.Vehicle;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Переносной ПТРК: ПКМ по технике в прицеле (до 300 блоков) — ракета с атакой сверху. Одноразовый. */
public class JavelinItem extends GunItem {
    public JavelinItem(Properties properties) {
        super(properties, new Spec(0f, 40, 0f, 0f, 1, 80, () -> com.harddayz.registry.ModItems.JAVELIN_MISSILE.get(), 300, 1f, false, 0.3f, 2f, true));
    }

    /** Цель в прицеле: техника в конусе 4° до 300 блоков (вызывается и на клиенте для захвата). */
    public static Vehicle target(Level level, Player sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        Vehicle best = null;
        double bestCos = Math.cos(Math.toRadians(4));
        for (Vehicle v : level.getEntitiesOfClass(Vehicle.class, new AABB(eye, eye).inflate(300))) {
            Vec3 to = v.position().add(0, 1, 0).subtract(eye);
            double cos = to.normalize().dot(look);
            if (cos > bestCos && to.length() < 300) {
                bestCos = cos;
                best = v;
            }
        }
        return best;
    }

    /** Пуск — только из прицела и по захваченной цели (захват держит клиент, сервер перепроверяет конус). */
    @Override
    public void shoot(ServerPlayer sp, ItemStack stack, boolean aiming) {
        ServerLevel sl = sp.level();
        if (!aiming) {
            sp.sendOverlayMessage(Component.translatable("message.harddayz.javelin_aim").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Vehicle best = target(sl, sp);
        if (best == null) {
            sp.sendOverlayMessage(Component.translatable("message.harddayz.javelin_no_lock").withStyle(ChatFormatting.RED));
            return;
        }
        if (mag(stack) <= 0) {
            super.shoot(sp, stack, aiming); // перезарядка ракетой из инвентаря
            return;
        }
        super.consumeRound(stack, sl.getGameTime());
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        Missile.launch(sl, Missile.Kind.SAM, eye.add(look.scale(1.2)), look.scale(0.8).add(0, 0.9, 0), best.position(), sp).hunt(best)
                .owner(sp.getUUID()).powerMul(3f).bonus(1f).look(5);
        sl.playSound(null, sp.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2f, 0.8f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.harddayz.javelin.desc").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.harddayz.gun.mag", mag(stack), 1).withStyle(ChatFormatting.YELLOW));
    }
}
