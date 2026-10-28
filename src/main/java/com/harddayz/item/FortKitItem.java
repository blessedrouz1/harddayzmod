package com.harddayz.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Готовая позиция: ПКМ по земле — копается окоп или блиндаж в сторону взгляда. */
public class FortKitItem extends Item {
    private final boolean dugout;

    public FortKitItem(Properties p, boolean dugout) {
        super(p);
        this.dugout = dugout;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level) || ctx.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        Direction f = ctx.getPlayer().getDirection();
        Direction side = f.getClockWise();
        BlockPos o = ctx.getClickedPos();
        if (dugout) {
            dugout(level, o, f, side);
        } else {
            trench(level, o, f, side);
        }
        level.playSound(null, o, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.5f, 0.8f);
        com.harddayz.Compat.consume(ctx.getItemInHand(), ctx.getPlayer());
        return InteractionResult.SUCCESS;
    }

    private static BlockPos at(BlockPos o, Direction f, Direction side, int along, int across, int dy) {
        return o.relative(f, along).relative(side, across).above(dy);
    }

    /** Окоп: 10 блоков вперёд, ширина 2, глубина 2, по краям бруствер из мешков. */
    private static void trench(ServerLevel level, BlockPos o, Direction f, Direction side) {
        for (int a = 0; a < 10; a++) {
            for (int c = 0; c <= 1; c++) {
                dig(level, at(o, f, side, a, c, 0));
                dig(level, at(o, f, side, a, c, -1));
            }
            place(level, at(o, f, side, a, -1, 1), com.harddayz.registry.ModBlocks.SANDBAG_WALL.get().defaultBlockState());
            place(level, at(o, f, side, a, 2, 1), com.harddayz.registry.ModBlocks.SANDBAG_WALL.get().defaultBlockState());
        }
    }

    /** Блиндаж: спуск в 2 ступени и комната 3×3 под накатом из брёвен и земли. */
    private static void dugout(ServerLevel level, BlockPos o, Direction f, Direction side) {
        dig(level, at(o, f, side, 0, 0, 0));
        dig(level, at(o, f, side, 1, 0, 0));
        dig(level, at(o, f, side, 1, 0, -1));
        for (int a = 2; a <= 4; a++) {
            for (int c = -1; c <= 1; c++) {
                dig(level, at(o, f, side, a, c, -1));
                dig(level, at(o, f, side, a, c, -2));
                force(level, at(o, f, side, a, c, 0), Blocks.SPRUCE_LOG.defaultBlockState());
                place(level, at(o, f, side, a, c, 1), Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
    }

    private static boolean soft(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        float h = s.getDestroySpeed(level, p);
        return !s.isAir() && h >= 0 && h <= 3 && level.getBlockEntity(p) == null && s.getFluidState().isEmpty();
    }

    private static void dig(ServerLevel level, BlockPos p) {
        if (soft(level, p)) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void place(ServerLevel level, BlockPos p, BlockState s) {
        if (level.getBlockState(p).canBeReplaced()) {
            level.setBlock(p, s, 3);
        }
    }

    private static void force(ServerLevel level, BlockPos p, BlockState s) {
        if (level.getBlockState(p).canBeReplaced() || soft(level, p)) {
            level.setBlock(p, s, 3);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, java.util.List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
