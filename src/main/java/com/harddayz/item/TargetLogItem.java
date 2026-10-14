package com.harddayz.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Журнал целей: сюда разведдрон записывает точки (ПКМ с камеры дрона), до 16 штук.
 * ПКМ журналом — список точек в чат, Shift+ПКМ — очистить. ПКМ журналом по пусковой — пульт с точками.
 */
public class TargetLogItem extends Item {
    public static final int MAX = 16;

    public TargetLogItem(Properties properties) {
        super(properties);
    }

    public static List<BlockPos> points(ItemStack stack) {
        List<BlockPos> out = new ArrayList<>();
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) {
            return out;
        }
        int[] a = d.copyTag().getIntArray("pts").orElse(new int[0]);
        for (int i = 0; i + 2 < a.length; i += 3) {
            out.add(new BlockPos(a[i], a[i + 1], a[i + 2]));
        }
        return out;
    }

    private static void save(ItemStack stack, List<BlockPos> pts) {
        int[] a = new int[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) {
            a[i * 3] = pts.get(i).getX();
            a[i * 3 + 1] = pts.get(i).getY();
            a[i * 3 + 2] = pts.get(i).getZ();
        }
        CompoundTag t = new CompoundTag();
        t.putIntArray("pts", a);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /** Заменить список точек (из окна журнала). */
    public static void set(ItemStack stack, List<Integer> xyz) {
        List<BlockPos> pts = new ArrayList<>();
        for (int i = 0; i + 2 < xyz.size() && pts.size() < MAX; i += 3) {
            pts.add(new BlockPos(xyz.get(i), net.minecraft.util.Mth.clamp(xyz.get(i + 1), -2048, 4096), xyz.get(i + 2)));
        }
        save(stack, pts);
    }

    /** Добавить точку; при переполнении самая старая выпадает. Возвращает номер записанной точки. */
    public static int add(ItemStack stack, BlockPos pos) {
        List<BlockPos> pts = points(stack);
        if (pts.size() >= MAX) {
            pts.remove(0);
        }
        pts.add(pos);
        save(stack, pts);
        return pts.size();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            com.harddayz.client.ClientAccess.openTargetLog(hand == InteractionHand.OFF_HAND);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, player.getSoundSource(), 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.harddayz.target_log.desc").withStyle(ChatFormatting.GRAY));
        List<BlockPos> pts = points(stack);
        builder.accept(Component.translatable("item.harddayz.target_log.count", pts.size(), MAX).withStyle(ChatFormatting.YELLOW));
        for (int i = Math.max(0, pts.size() - 4); i < pts.size(); i++) {
            BlockPos p = pts.get(i);
            builder.accept(Component.literal("#" + (i + 1) + "  " + p.getX() + " " + p.getY() + " " + p.getZ()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
