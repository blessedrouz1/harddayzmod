package com.harddayz.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Тепловизор дрона: живые и работающая техника подсвечиваются контуром сквозь стены. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void harddayz$thermal(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (com.harddayz.client.DronePilot.thermalOn() && e != Minecraft.getInstance().getCameraEntity()
                && (e instanceof net.minecraft.world.entity.LivingEntity || e instanceof com.harddayz.entity.Vehicle v && !(v instanceof com.harddayz.entity.Launcher l && (l.wrecked() && l.wreck() > 180 || l.hidden() || l.kind() == com.harddayz.entity.Launcher.Kind.DECOY && false))
                || e instanceof com.harddayz.entity.Drone)) {
            cir.setReturnValue(true);
        }
    }
}
