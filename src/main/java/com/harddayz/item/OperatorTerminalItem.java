package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.entity.Drone;
import com.harddayz.war.War;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Пульт оператора: ПКМ — взять под управление ближайший свой летящий «Шахед» (до 420 блоков). */
public class OperatorTerminalItem extends Item {
    public OperatorTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            // сначала — свой квадрокоптер поблизости (FPV / разведчик)
            Drone quad = null;
            double qd = Double.MAX_VALUE;
            for (Drone d : sl.getEntitiesOfClass(Drone.class, new AABB(sp.position(), sp.position()).inflate(Drone.SIGNAL_RANGE))) {
                if (!d.kind().autonomous && d.pilotId() < 0 && d.battery() > 0 && War.allied(sp.getUUID(), d.owner()) && d.distanceToSqr(sp) < qd) {
                    qd = d.distanceToSqr(sp);
                    quad = d;
                }
            }
            if (quad != null && quad.connect(sp)) {
                sp.sendOverlayMessage(Component.translatable("message.harddayz.drone_linked", (int) Math.sqrt(qd)).withStyle(ChatFormatting.GREEN));
                level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), sp.getSoundSource(), 0.8f, 1.6f);
                return InteractionResult.SUCCESS;
            }
            Drone best = null;
            double bestD = Double.MAX_VALUE;
            for (Drone d : sl.getEntitiesOfClass(Drone.class, new AABB(sp.position(), sp.position()).inflate(Drone.linkRange(sl.getServer())))) {
                if (!d.kind().autonomous || d.pilotId() >= 0 || !War.allied(sp.getUUID(), d.owner())) {
                    continue;
                }
                double dist = d.distanceToSqr(sp);
                if (dist < bestD) {
                    bestD = dist;
                    best = d;
                }
            }
            if (best == null || !best.takeControl(sp)) {
                sp.sendOverlayMessage(Component.translatable("message.harddayz.no_shahed").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            sp.sendOverlayMessage(Component.translatable("message.harddayz.shahed_linked", (int) Math.sqrt(bestD)).withStyle(ChatFormatting.GREEN));
            level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), sp.getSoundSource(), 0.8f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.harddayz.operator_terminal.desc").withStyle(ChatFormatting.GRAY));
    }
}
