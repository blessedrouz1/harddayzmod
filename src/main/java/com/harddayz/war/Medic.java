package com.harddayz.war;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.harddayz.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Медицина: сильные попадания вызывают кровотечение; смертельное ранение не убивает сразу — игрок лежит
 * минуту, пока его не поднимут (ПКМ по нему, с аптечкой быстрее). Добить лежачего — любой урон.
 */
public final class Medic {
    public static final int DOWN_TICKS = 1200;
    private static final Map<UUID, Integer> DOWNED = new HashMap<>();
    private static final Map<UUID, Integer> REVIVE = new HashMap<>();
    private static final Map<UUID, Integer> GIVE_UP = new HashMap<>();

    private Medic() {
    }

    public static void init() {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener(Medic::onDamage);
        bus.addListener((net.minecraftforge.event.server.ServerStoppedEvent e) -> {
            DOWNED.clear();
            REVIVE.clear();
            GIVE_UP.clear();
        });
        bus.addListener((TickEvent.PlayerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                onTick(e.player);
            }
        });
        bus.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> {
            if (onInteract(e)) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        });
    }

    public static boolean isDowned(Player p) {
        return DOWNED.containsKey(p.getUUID());
    }

    private static void clearDowned(Player p) {
        boolean wasDowned = DOWNED.remove(p.getUUID()) != null;
        REVIVE.remove(p.getUUID());
        GIVE_UP.remove(p.getUUID());
        if (wasDowned) {
            p.setForcedPose(null);
        }
    }

    /** Кровотечение от пуль, осколков и взрывов. */
    private static void onDamage(LivingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide() || !(t instanceof Player) || e.getAmount() < 3.5f || isDowned((Player) t)) {
            return;
        }
        if (t.getRandom().nextFloat() < Math.min(0.8f, e.getAmount() / 12f)) {
            t.addEffect(new MobEffectInstance(com.harddayz.registry.ModEffects.BLEEDING.get(), 20 * 60, e.getAmount() > 9 ? 1 : 0));
        }
    }

    /** Вызывается вместо смерти: true — игрок упал раненым (смерть отменить). */
    public static boolean tryDown(ServerPlayer p, DamageSource source) {
        if (isDowned(p) || p.isCreative() || p.isSpectator() || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            clearDowned(p);
            return false;
        }
        clearDowned(p);
        DOWNED.put(p.getUUID(), DOWN_TICKS);
        p.setHealth(2);
        p.removeAllEffects();
        p.setForcedPose(Pose.SWIMMING);
        p.stopRiding();
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.5f, 0.6f);
        p.sendSystemMessage(Component.translatable("message.harddayz.downed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        War.Faction f = War.of(p.getUUID());
        if (f != null) {
            for (ServerPlayer m : Supply.members(p.getServer(), f)) {
                if (m != p) {
                    m.sendSystemMessage(Component.translatable("message.harddayz.ally_downed", p.getDisplayName(), p.getBlockX(), p.getBlockY(), p.getBlockZ())
                            .withStyle(ChatFormatting.GOLD));
                }
            }
        }
        return true;
    }

    private static void onTick(Player player) {
        if (!(player instanceof ServerPlayer p)) {
            return;
        }
        Integer left = DOWNED.get(p.getUUID());
        if (left == null) {
            return;
        }
        if (!p.isAlive()) {
            clearDowned(p);
            return;
        }
        left--;
        DOWNED.put(p.getUUID(), left);
        p.setForcedPose(Pose.SWIMMING);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        if (p.getHealth() > 2) {
            p.setHealth(2);
        }
        // Shift 3 секунды — сдаться
        int g = p.isShiftKeyDown() ? GIVE_UP.getOrDefault(p.getUUID(), 0) + 1 : 0;
        GIVE_UP.put(p.getUUID(), g);
        int rev = REVIVE.getOrDefault(p.getUUID(), 0);
        if (p.tickCount % 10 == 0) {
            p.displayClientMessage(Component.translatable("message.harddayz.downed_bar", left / 20, rev).withStyle(ChatFormatting.RED), true);
        }
        if (left <= 0 || g >= 60) {
            clearDowned(p);
            p.hurt(p.damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    /** ПКМ по раненому: +20% (с бинтом +35%, с аптечкой +50%); на 100% — поднят. */
    private static boolean onInteract(PlayerInteractEvent.EntityInteractSpecific e) {
        if (!(e.getTarget() instanceof ServerPlayer hurt) || !(e.getEntity() instanceof ServerPlayer medic) || !isDowned(hurt)
                || e.getHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        var stack = medic.getMainHandItem();
        int add = stack.is(ModItems.MEDKIT.get()) ? 50 : stack.is(ModItems.BANDAGE.get()) ? 35 : 20;
        int rev = REVIVE.getOrDefault(hurt.getUUID(), 0) + add;
        medic.level().playSound(null, hurt.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1f, 1f);
        if (rev >= 100) {
            if (add > 20 && !medic.isCreative()) {
                stack.shrink(1);
            }
            clearDowned(hurt);
            hurt.setHealth(add == 50 ? 10 : 6);
            hurt.removeAllEffects();
            hurt.sendSystemMessage(Component.translatable("message.harddayz.revived", medic.getDisplayName()).withStyle(ChatFormatting.GREEN));
            medic.displayClientMessage(Component.translatable("message.harddayz.revived_by_you", hurt.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
        } else {
            REVIVE.put(hurt.getUUID(), rev);
            medic.displayClientMessage(Component.translatable("message.harddayz.reviving", rev).withStyle(ChatFormatting.YELLOW), true);
        }
        return true;
    }
}
