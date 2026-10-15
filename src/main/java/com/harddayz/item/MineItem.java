package com.harddayz.item;

import java.util.function.Consumer;

import com.harddayz.entity.Mine;
import com.harddayz.registry.ModEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/** Установить мину на верх блока. */
public class MineItem extends Item {
    private final Mine.Kind kind;

    public MineItem(Properties properties, Mine.Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        Vec3 at = Vec3.atBottomCenterOf(ctx.getClickedPos().above());
        Mine m = ModEntities.MINE.get().create(level);
        if (m == null) {
            return InteractionResult.FAIL;
        }
        m.setup(kind, ctx.getPlayer().getUUID());
        m.setPos(at.x, at.y - 0.02, at.z);
        m.setYRot(level.getRandom().nextFloat() * 360);
        level.addFreshEntity(m);
        level.playSound(null, m.blockPosition(), SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
        com.harddayz.Compat.consume(ctx.getItemInHand(), ctx.getPlayer());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable net.minecraft.world.level.Level tooltipLevel, java.util.List<Component> builder, TooltipFlag flag) {
        builder.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
