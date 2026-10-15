package com.harddayz.war;

import com.harddayz.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;

/** Хранилище штаба: сюда приходят поставки, открывают только свои. */
public class HqBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    public HqBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HQ_BE.get(), pos, state);
    }

    /** Положить поставку; возвращает то, что не влезло. */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < items.size() && !rest.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, rest)) {
                int n = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(n);
                rest.shrink(n);
            }
        }
        for (int i = 0; i < items.size() && !rest.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        setChanged();
        return rest;
    }

    @Override
    protected void saveAdditional(CompoundTag output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    public void load(CompoundTag input) {
        super.load(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.harddayz.hq");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }
}
