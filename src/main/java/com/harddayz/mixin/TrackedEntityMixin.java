package com.harddayz.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.harddayz.entity.PilotView;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Пока игрок ведёт дрон, видимость сущностей считается от дрона, а не от тела игрока — иначе дрон и всё вокруг
 * него пропадают у пилота, как только дрон улетает дальше прорисовки от тела.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin {
    @Shadow
    @Final
    ServerEntity serverEntity;
    @Shadow
    @Final
    Entity entity;
    @Shadow
    @Final
    private Set<ServerPlayerConnection> seenBy;

    @Shadow
    public abstract void removePlayer(ServerPlayer player);

    @Shadow
    private int getEffectiveRange() {
        throw new AssertionError();
    }

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void harddayz$fromDrone(ServerPlayer player, CallbackInfo ci) {
        Vec3 eye = PilotView.viewPos(player);
        if (eye == null || player == entity) {
            return;
        }
        double range = Math.min(getEffectiveRange(), player.getServer().getPlayerList().getViewDistance() * 16);
        double dx = entity.getX() - eye.x;
        double dz = entity.getZ() - eye.z;
        boolean pilotedDrone = PilotView.isPiloted(player, entity);
        boolean visible = (pilotedDrone || dx * dx + dz * dz <= range * range) && entity.broadcastToPlayer(player);
        if (visible) {
            if (seenBy.add(player.connection)) {
                serverEntity.addPairing(player);
            }
        } else {
            removePlayer(player);
        }
        ci.cancel();
    }
}
