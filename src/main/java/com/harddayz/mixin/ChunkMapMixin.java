package com.harddayz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.harddayz.entity.PilotView;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;

/**
 * Пилот дрона получает чанки вокруг дрона, а не вокруг тела (в новых версиях — ChunkTrackingView игрока).
 * Подменяем секцию игрока на секцию дрона там, где ChunkMap решает, какие чанки ему слать и грузить.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Redirect(method = {"move", "updatePlayerPos"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"))
    private SectionPos harddayz$section(EntityAccess e) {
        if (e instanceof ServerPlayer p) {
            SectionPos s = PilotView.viewSection(p);
            if (s != null) {
                return s;
            }
        }
        return SectionPos.of(e);
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getBlockX()I"))
    private int harddayz$blockX(ServerPlayer p) {
        SectionPos s = PilotView.viewSection(p);
        return s != null ? s.minBlockX() : p.getBlockX();
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getBlockZ()I"))
    private int harddayz$blockZ(ServerPlayer p) {
        SectionPos s = PilotView.viewSection(p);
        return s != null ? s.minBlockZ() : p.getBlockZ();
    }
}
