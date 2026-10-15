package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.entity.Launcher;
import com.harddayz.registry.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Поставить пусковую установку на землю (ПКМ по блоку), кабиной от игрока. */
public class LauncherItem extends Item {
    private final Launcher.Kind kind;

    public LauncherItem(Properties properties, Launcher.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        Vec3 at = Vec3.atBottomCenterOf(ctx.getClickedPos().relative(ctx.getClickedFace()));
        if (!level.noCollision(new AABB(at.x - 1.4, at.y + 0.05, at.z - 1.4, at.x + 1.4, at.y + 2.5, at.z + 1.4))) {
            ctx.getPlayer().displayClientMessage(Component.translatable("message.harddayz.no_space").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        Launcher l = ModEntities.LAUNCHER.get().create(level);
        if (l == null) {
            return InteractionResult.FAIL;
        }
        l.setKind(kind);
        l.setPos(at);
        l.setYRot(ctx.getPlayer().getYRot());
        l.setOwner(ctx.getPlayer());
        level.addFreshEntity(l);
        com.harddayz.Compat.consume(ctx.getItemInHand(), ctx.getPlayer());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
