package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.entity.Drone;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Запустить пилотируемый дрон (ПКМ) — камера переключается на него. */
public class DroneItem extends Item {
    private final Drone.Kind kind;

    public DroneItem(Properties properties, Drone.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.FAIL, player.getItemInHand(hand));
        }
        player.getCooldowns().addCooldown(this, 100);
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (kind == Drone.Kind.INTERCEPTOR || kind == Drone.Kind.LANCET || kind == Drone.Kind.WING_RECON || kind == Drone.Kind.MOLNIYA || kind == Drone.Kind.KUB) {
                // ставится на землю; пуск — ПКМ по нему
                Drone.park(sl, sp, kind);
                com.harddayz.Compat.consume(player.getItemInHand(hand), player);
                sp.displayClientMessage(Component.translatable("message.harddayz.parked").withStyle(ChatFormatting.GOLD), true);
                return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
            }
            if (kind.autonomous) {
                // запуск с руки по взгляду: цель — точка целеуказателя или куда смотришь
                net.minecraft.core.BlockPos mark = null;
                for (ItemStack s : sp.getInventory().items) {
                    if (s.is(com.harddayz.registry.ModItems.DESIGNATOR.get()) && DesignatorItem.target(s) != null) {
                        mark = DesignatorItem.target(s);
                        break;
                    }
                }
                net.minecraft.world.phys.Vec3 look = sp.getLookAngle();
                net.minecraft.world.phys.Vec3 aim;
                if (mark != null && kind != Drone.Kind.INTERCEPTOR) {
                    aim = net.minecraft.world.phys.Vec3.atCenterOf(mark);
                } else {
                    var hit = sl.clip(new net.minecraft.world.level.ClipContext(sp.getEyePosition(), sp.getEyePosition().add(look.scale(600)),
                            net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, sp));
                    aim = hit.getLocation();
                }
                net.minecraft.world.phys.Vec3 dir = look.add(0, 0.35, 0).normalize();
                var rnd = sp.getRandom();
                aim = aim.add(rnd.nextGaussian() * 6, 0, rnd.nextGaussian() * 6); // GPS-наведение тоже промахивается
                Drone.launchShahed(sl, sp.getEyePosition().add(look.scale(1.5)), dir, aim, sp.getUUID(), kind);
                sl.playSound(null, sp.blockPosition(), kind.jetDrone() ? com.harddayz.registry.ModSounds.LAUNCH_JETDRONE.get()
                                : kind.shahedLike() ? com.harddayz.registry.ModSounds.LAUNCH_SHAHED.get()
                                : com.harddayz.registry.ModSounds.LAUNCH_DRONE.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 1.2f);
                com.harddayz.Compat.consume(player.getItemInHand(hand), player);
                return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
            }
            Drone.deploy(sl, sp, kind);
            sp.displayClientMessage(Component.translatable("message.harddayz.drone_placed").withStyle(ChatFormatting.GOLD), true);
            com.harddayz.Compat.consume(player.getItemInHand(hand), player);
        }
        return new net.minecraft.world.InteractionResultHolder<>(InteractionResult.SUCCESS, player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
